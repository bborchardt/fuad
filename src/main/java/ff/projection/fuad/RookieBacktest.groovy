package ff.projection.fuad

import ff.data.RealisedSeason
import ff.data.Rank
import ff.data.fantasypros.FpRankedPlayer
import ff.league.League
import ff.load.RealisedSeasons
import ff.load.fantasypros.FantasyProsLoader
import ff.load.fuad.FuadValuationLoader
import ff.load.fuad.RookieSeasons
import ff.load.util.LoadUtils
import ff.projection.ByeWeeks
import ff.projection.ExpectedValue
import ff.projection.PointsCurve

/** Leave-one-draft-class-out comparison in FUAD points over replacement, not auction dollars.
 * Later classes may train earlier folds. This is retrospective cross-validation, not a rolling forecast.
 * All of a held-out class's seasons are excluded, including from the veteran replacement curve.
 */
class RookieBacktest {
    static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE']
    static final List<String> MODELS = ['FULL_REFIT', 'ROOKIE_ONLY', 'ROOKIE_POINT', 'DYNASTY_ONLY',
                                        'DYNASTY_CENTERED', 'DYNASTY_SHRUNK']
    static final List<List<Double>> ERRORS = [[-2d, .055d], [-1d, .244d], [0d, .402d], [1d, .244d], [2d, .055d]]
    static final int LAST_WEEK = 14

    /** An observation retains class identity even when its outcome is several years later. */
    static class Observation {
        String draftClass
        String name
        String position
        int rookieRank
        int overallRank
        Integer dynastyRank
        int year
        RealisedSeason outcome
    }

    static List<Observation> observations() {
        def fp = new FantasyProsLoader()
        League.FUAD.seasons.collectMany { String draft ->
            def rookies = fp.loadRankedPlayers(LoadUtils.fpRookieRankingsPprResourcePath(draft)).values()
                    .findAll { it.player.position in POSITIONS }
            def dynasty = fp.loadRankedPlayers(LoadUtils.fpDynastyRankingsPprResourcePath(draft))
            Map<String, Integer> dynastyRanks = rookies.collectEntries { FpRankedPlayer rookie ->
                def matched = dynasty[rookie.player.name]
                if (!matched) {
                    def candidates = dynasty.values().findAll {
                        it.player.position == rookie.player.position &&
                                LoadUtils.isNameMatch(it.player.name, rookie.player.name, 5)
                    }
                    matched = candidates.size() == 1 ? candidates.first() : null
                }
                [(rookie.player.name): matched?.rank?.positionRank]
            }
            (1..5).findAll { (draft.toInteger() + it - 1).toString() in League.FUAD.seasons }
                    .collectMany { int year ->
                String season = (draft.toInteger() + year - 1).toString()
                // Some archived exports repeat positional ranks. Use a unique join index while scoring,
                // then restore the original rank as a predictor; never assign one player's result to a peer.
                def indexed = rookies.withIndex().collect { rookie, index ->
                    new FpRankedPlayer(rookie.player, new Rank(rookie.rank.overallRank, index + 1), rookie.bye)
                }
                def realised = RealisedSeasons.byRank(League.FUAD, { indexed }, [season])
                rookies.withIndex().collect { FpRankedPlayer rookie, int index ->
                    def matchedSeasons = realised[rookie.player.position][index + 1]
                    if (matchedSeasons.size() != 1) throw new IllegalStateException(
                            "Expected one outcome: $draft/$year ${rookie.player.name} ${rookie.player.position}${rookie.rank.positionRank}, got ${matchedSeasons.size()}")
                    new Observation(draftClass: draft, name: rookie.player.name,
                            position: rookie.player.position, rookieRank: rookie.rank.positionRank,
                            overallRank: rookie.rank.overallRank, dynastyRank: dynastyRanks[rookie.player.name],
                            year: year, outcome: matchedSeasons.first())
                }
            }
        }
    }

    static List<Observation> trainingFor(List<Observation> observations, String target) {
        observations.findAll { it.draftClass != target }
    }

    static RookieSeasons seasonsFrom(List<Observation> training) {
        Map<Integer, Map<String, Map<Integer, List<RealisedSeason>>>> years = (1..5).collectEntries { int year ->
            Map<String, Map<Integer, List<RealisedSeason>>> ranks = [:]
            training.findAll { it.year == year }.each { Observation row ->
                if (!ranks.containsKey(row.position)) ranks[row.position] = [:]
                if (!ranks[row.position].containsKey(row.rookieRank)) ranks[row.position][row.rookieRank] = []
                ranks[row.position][row.rookieRank] << row.outcome
            }
            [(year): ranks]
        }
        new RookieSeasons() {
            @Override
            Map<String, Map<Integer, List<RealisedSeason>>> realised(int year) { years[year] }
        }
    }

    /** Training-only version of the production one-sided, tapered dynasty adjustment.
     * Expected ranks, slope, plateau, decay and residual cap are re-estimated in each fold.
     * Smoothing architecture and the one-sided functional form are fixed, not newly validated here.
     */
    static class DynastyFit {
        Map<List, Double> expected = [:]
        double slope = 0d
        double cap = 0d
        int plateau = 4
        double decay = 3d
        double intercept = 0d
        int count

        double residual(Observation row) {
            Double baseline = expected[[row.position, row.rookieRank]]
            baseline && row.dynastyRank ? Math.max(0d, Math.log(baseline / row.dynastyRank)) : 0d
        }

        double adjustment(Observation row) {
            double taper = row.rookieRank <= plateau ? 1d : Math.exp(-(row.rookieRank - plateau) / decay)
            Math.exp(slope * taper * Math.min(cap, residual(row)))
        }
    }

    static DynastyFit fitDynasty(List<Observation> training, RookieSeasons seasons) {
        DynastyFit fit = new DynastyFit()
        def entrants = training.findAll { it.year == 1 }
        POSITIONS.each { String position ->
            int depth = (entrants.findAll { it.position == position }*.rookieRank.max() ?: 0) as int
            if (depth > 0) (1..depth).each { int rank ->
                def nearby = entrants.findAll {
                    it.position == position && Math.abs(it.rookieRank - rank) <= 1 && it.dynastyRank
                }*.dynastyRank.sort()
                if (nearby.size() >= 6) fit.expected[[position, rank]] = nearby[nearby.size().intdiv(2)] as double
            }
        }
        def sample = training.findAll {
            it.year <= 3 && it.outcome.games > 0 && it.outcome.points > 0 && fit.residual(it) > 0 &&
                    seasons.curve(it.year).levelledRate(it.position, it.rookieRank) > 0
        }
        fit.count = sample.size()
        if (sample.size() < 6) return fit
        fit.cap = quantile(sample.collect { fit.residual(it) }, .9d)
        double best = Double.POSITIVE_INFINITY
        // The grid is declared before looking at held-out errors; its winner only sees training classes.
        (1..5).each { int plateau ->
            [1d, 2d, 3d, 5d, 8d].each { double decay ->
                def x = sample.collect {
                    Math.min(fit.cap, fit.residual(it)) *
                            (it.rookieRank <= plateau ? 1d : Math.exp(-(it.rookieRank - plateau) / decay))
                }
                def y = sample.collect {
                    Math.log(it.outcome.rate.toDouble() /
                            seasons.curve(it.year).levelledRate(it.position, it.rookieRank).toDouble())
                }
                // A log residual is not mean-zero around an arithmetic rate curve. Estimate a nuisance
                // intercept so that Jensen's gap cannot suppress an otherwise positive ranking signal.
                // Only the slope enters the production-shaped multiplier; the curve retains its level.
                def regression = regression(x, y)
                double slope = regression.slope
                double loss = regression.loss
                if (loss < best) {
                    best = loss; fit.slope = slope; fit.plateau = plateau; fit.decay = decay
                    fit.intercept = regression.intercept
                }
            }
        }
        fit
    }

    static Map regression(List<Double> x, List<Double> y) {
        double xm = x.sum() / x.size(), ym = y.sum() / y.size()
        double xx = x.sum { Math.pow(it - xm, 2) } as double
        double slope = xx > 0 ? Math.max(0d, x.indices.sum { (x[it] - xm) * (y[it] - ym) } / xx) : 0d
        double intercept = ym - slope * xm
        [slope: slope, intercept: intercept,
         loss: x.indices.sum { Math.pow(y[it] - intercept - slope * x[it], 2) }]
    }

    /** Fixed 2026 lineup, scored under FUAD rules. No target player's career helps set replacement.
     * A standardized bye week is shared by all candidates and observed outcomes.
     */
    static Map<String, Map<Integer, BigDecimal>> replacementFor(List<Observation> target,
                                                               Collection<String> seasons = League.FUAD.seasons) {
        Set<String> names = target*.name as Set
        def fp = new FantasyProsLoader()
        def realised = RealisedSeasons.byRank(League.FUAD, { String season ->
            fp.loadRedraftRankedPlayers(season).values().findAll { ranked ->
                !names.any { String name -> LoadUtils.isNameMatch(name, ranked.player.name, 5) }
            }
        }, seasons)
        def curve = PointsCurve.of(realised)
        ExpectedValue.replacementLevels(curve, new FuadValuationLoader().requirements('2026'),
                new ByeWeeks([:], LAST_WEEK))
    }

    static double realisedVor(RealisedSeason outcome, Map<Integer, BigDecimal> replacement) {
        outcome.games > 0 ? ExpectedValue.expectedValueOverReplacement(outcome.rate,
                [new PointsCurve.Outcome(1d, outcome.games)], replacement, LAST_WEEK, LAST_WEEK).toDouble() : 0d
    }

    /** Positional dynasty rank alone: nearest 20 training player-seasons, including tied distances.
     * Unranked players use unranked training peers (or the deepest 20 when none exist).
     */
    static List<Observation> dynastyNeighbours(List<Observation> training, Observation row) {
        def pool = training.findAll { it.year == row.year && it.position == row.position }
        if (!row.dynastyRank) {
            def missing = pool.findAll { !it.dynastyRank }
            return missing ?: pool.sort(false) { -(it.dynastyRank ?: 10000) }.take(20)
        }
        pool = pool.findAll { it.dynastyRank }
        if (!pool) return []
        def distance = { Observation other -> Math.abs(Math.log(other.dynastyRank / (double) row.dynastyRank)) }
        pool = pool.sort(false) { distance(it) }
        double boundary = distance(pool[Math.min(19, pool.size() - 1)])
        pool.findAll { distance(it) <= boundary }
    }

    static Map prediction(Observation row, String model, List<Observation> training,
                          RookieSeasons seasons, RookieOutcomes outcomes, DynastyFit fit,
                          Map<Integer, BigDecimal> replacement, boolean intervals = true) {
        List<List<Double>> distribution = []
        if (model == 'DYNASTY_ONLY') {
            def peers = dynastyNeighbours(training, row)
            if (!peers) return [predicted: null, low: null, high: null]
            distribution = peers.collect { [realisedVor(it.outcome, replacement), 1d / peers.size()] }
        } else {
            def curve = seasons.curve(row.year)
            double rate = curve.levelledRate(row.position, row.rookieRank).toDouble()
            def spread = outcomes.of(row.position, row.rookieRank, row.year)
            if (rate <= 0 || !spread) return [predicted: null, low: null, high: null]
            double points = curve.seasonPoints(row.position, row.rookieRank).toDouble()
            double error = points > 0 ? curve.standardError(row.position, row.rookieRank).toDouble() / points : 0d
            double adjustment = model == 'FULL_REFIT' ? fit.adjustment(row) : 1d
            def errors = model == 'ROOKIE_POINT' ? [[0d, 1d]] : ERRORS
            def replay = vorAgainst(replacement)
            errors.each { point ->
                double shifted = rate * adjustment * Math.max(0d, 1d + point[0] * error)
                spread.each { outcome ->
                    double vor = replay(shifted, outcome)
                    distribution << [vor, point[1] / spread.size()]
                }
            }
        }
        summarize(distribution, intervals)
    }

    /** Algebraically the production replay, with the standardized last-week bye. The fold's replacement
     * is normally constant, so avoid constructing fourteen BigDecimal week maps for each replay.
     */
    static double outcomeVor(double rate, PointsCurve.Outcome outcome, Map<Integer, BigDecimal> replacement) {
        vorAgainst(replacement)(rate, outcome)
    }

    /** Prepare replacement once per prediction; both entry points share the same replay arithmetic. */
    static Closure<Double> vorAgainst(Map<Integer, BigDecimal> replacement) {
        def thresholds = (1..<LAST_WEEK).collect { (replacement[it] ?: 0g).toDouble() }
        double first = thresholds.first()
        boolean uniform = thresholds.every { it == first }
        return { double rate, PointsCurve.Outcome outcome ->
            double adjusted = rate * outcome.rateMultiplier
            int games = Math.min(outcome.games, LAST_WEEK - 1)
            uniform ? games * Math.max(0d, adjusted - first) :
                    thresholds.sum { Math.max(0d, adjusted - it) } * games / (LAST_WEEK - 1)
        }
    }

    static Map summarize(List<List<Double>> distribution, boolean intervals = true) {
        [predicted: distribution.sum { it[0] * it[1] },
         low: intervals ? weightedQuantile(distribution, .1d) : null,
         high: intervals ? weightedQuantile(distribution, .9d) : null,
         distribution: intervals ? distribution : null]
    }

    static Map run(List<Observation> observations = observations(), Closure progress = { }) {
        List<Map> predictions = [], fits = [], selections = [], calibrationRows = []
        def calibration = new RookieCalibration(observations, progress)
        observations*.draftClass.unique().sort().each { String target ->
            progress("Holding out rookie class ${target}")
            def context = calibration.context([target])
            def selection = calibration.select(target)
            selections.addAll(selection.scores)
            calibrationRows.addAll(context.calibration.collect { key, factor ->
                [draftClass: target, position: key[0], year: key[1], factor: factor]
            })
            def training = context.training
            def heldOut = observations.findAll { it.draftClass == target }
            def seasons = context.seasons
            def outcomes = context.outcomes
            def fit = context.fit
            def replacement = context.replacement
            fits << [draftClass: target, trainingClasses: training*.draftClass.unique().sort().join(','),
                     slope: fit.slope, plateau: fit.plateau, decay: fit.decay, cap: fit.cap,
                     intercept: fit.intercept, fitted: fit.count, strength: selection.strength]
            heldOut.each { Observation row ->
                def base = prediction(row, 'ROOKIE_ONLY', training, seasons, outcomes, fit, replacement[row.position])
                def full = prediction(row, 'FULL_REFIT', training, seasons, outcomes, fit, replacement[row.position])
                double factor = context.calibration.getOrDefault([row.position, row.year], 1d)
                MODELS.each { String model ->
                    def forecast = model == 'ROOKIE_ONLY' ? base : model == 'FULL_REFIT' ? full :
                            model in ['DYNASTY_CENTERED', 'DYNASTY_SHRUNK'] ?
                                    RookieCalibration.blend(base, full, factor, model == 'DYNASTY_CENTERED' ? 1d : selection.strength) :
                                    prediction(row, model, training, seasons, outcomes, fit, replacement[row.position])
                    predictions << [draftClass: target, name: row.name, position: row.position,
                            overallRank: row.overallRank, dynastyRank: row.dynastyRank, year: row.year,
                            model: model, actual: realisedVor(row.outcome, replacement[row.position])] +
                            forecast.subMap(['predicted', 'low', 'high'])
                }
            }
        }
        [predictions: predictions, fits: fits, metrics: metrics(predictions),
         selection: selections, calibration: calibrationRows]
    }

    /** Same evaluable players for every model. Missing levels are counted, never silently scored as zero.
     * Multi-year rows require all years of a career; recent classes are never padded with future zeroes.
     * Only annual predictive intervals are scored: summing marginal quantiles is not a career interval.
     */
    static List<Map> metrics(List<Map> annual) {
        List<Map> contracts = []
        annual.groupBy { [it.draftClass, it.name] }.each { key, player ->
            [1, 3, 5].each { int horizon ->
                def required = player.findAll { it.year <= horizon }
                boolean observed = MODELS.every { model -> required.count { it.model == model } == horizon }
                if (observed) {
                    boolean common = required.every { it.predicted != null }
                    MODELS.each { String model ->
                        def rows = required.findAll { it.model == model }
                        contracts << [draftClass: key[0], name: key[1], position: rows.first().position,
                                overallRank: rows.first().overallRank, model: model, horizon: horizon,
                                actual: rows.sum { it.actual }, predicted: common ? rows.sum { it.predicted } : null,
                                low: horizon == 1 ? rows.first().low : null,
                                high: horizon == 1 ? rows.first().high : null]
                    }
                }
            }
        }
        List<Map> result = []
        (contracts*.draftClass.unique().sort() + ['POOLED']).each { String fold ->
            ['ALL', 'TOP50'].each { String cohort ->
                (['ALL'] + POSITIONS).each { String position ->
                    def selected = contracts.findAll {
                        (fold == 'POOLED' || it.draftClass == fold) &&
                                (cohort == 'ALL' || it.overallRank <= 50) &&
                                (position == 'ALL' || it.position == position)
                    }
                    selected.groupBy { [it.model, it.horizon] }.each { key, rows ->
                        def scored = rows.findAll { it.predicted != null }
                        def errors = scored.collect { it.predicted - it.actual }
                        int n = scored.size()
                        result << [fold: fold, cohort: cohort, position: position, model: key[0], horizon: key[1],
                                eligible: rows.size(), scored: n, missing: rows.size() - n,
                                mae: n ? errors.sum { Math.abs(it) } / n : null,
                                rmse: n ? Math.sqrt(errors.sum { it * it } / n) : null,
                                bias: n ? errors.sum() / n : null,
                                actualMean: n ? scored.sum { it.actual } / n : null,
                                predictedMean: n ? scored.sum { it.predicted } / n : null,
                                spearman: spearman(scored*.predicted, scored*.actual),
                                coverage80: n && key[1] == 1 ? scored.count { it.actual >= it.low && it.actual <= it.high } / (double) n : null,
                                width80: n && key[1] == 1 ? scored.sum { it.high - it.low } / n : null]
                    }
                }
            }
        }
        result
    }

    static Double spearman(List a, List b) {
        if (a.size() < 2) return null
        def ranks = { List values -> values.collect { value ->
            1d + values.count { it < value } + (values.count { it == value } - 1) / 2d
        } }
        def x = ranks(a), y = ranks(b)
        double xm = x.sum() / x.size(), ym = y.sum() / y.size()
        double xx = x.sum { Math.pow(it - xm, 2) }, yy = y.sum { Math.pow(it - ym, 2) }
        xx > 0 && yy > 0 ? x.indices.sum { (x[it] - xm) * (y[it] - ym) } / Math.sqrt(xx * yy) : null
    }

    static double quantile(List<Double> values, double probability) {
        def sorted = values.sort(false)
        sorted[Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(probability * sorted.size()) - 1))]
    }

    static double weightedQuantile(List<List<Double>> values, double probability) {
        double cumulative = 0d
        for (def point : values.sort(false) { it[0] }) {
            cumulative += point[1]
            if (cumulative + 1e-12 >= probability) return point[0]
        }
        values.max { it[0] }[0]
    }
}
