package ff.research.fuad.rookies

import ff.projection.fuad.*

import ff.research.fuad.rookies.RookieBacktest as B
import ff.projection.PointsCurve
import java.util.Locale

/** Compare QB joint-outcome pooling with the rate curves, uncertainty integration and replacement held
 * constant. Inner folds select the mixture by games MSE, never by outer VOR errors. No dynasty adjustment.
 */
class RookieAvailabilityStudy {
    static final List<Double> STRENGTHS = [0d, .25d, .5d, .75d, 1d]
    static final List<String> MODELS = ['CURRENT', 'LOCAL', 'PARTIAL']
    static final Map<String, List<String>> COLUMNS = [
            predictions: 'draftClass name rank overallRank year model strength actualGames games actualZero zero actualVor vor'.tokenize(),
            availability: 'fold cohort band model eligible scored gamesBias gamesRmse zeroMean zeroPredicted zeroBrier'.tokenize(),
            careers: 'fold cohort horizon model eligible scored bias mae rmse spearman'.tokenize(),
            selection: 'outer inner strength eligible scored gamesMse selected'.tokenize()
    ]

    final List<B.Observation> observations
    private final Map<List, Map> contexts = [:]

    RookieAvailabilityStudy(List<B.Observation> observations) { this.observations = observations }

    Map context(List<String> excluded) {
        def key = excluded.unique().sort().asImmutable()
        if (!contexts.containsKey(key)) {
            def training = observations.findAll { !(it.draftClass in key) }
            def seasons = B.seasonsFrom(training)
            contexts[key] = [training: training, seasons: seasons, outcomes: new RookieOutcomes(seasons), pools: [:]]
        }
        contexts[key]
    }

    /** Normalize every donor by its own rank's rate exactly as production does. Local weighting is a
     * fixed exp(-rank distance) kernel, not a bandwidth chosen after seeing held-out results.
     */
    static List<Map> localPool(List<B.Observation> training, def curve, int year, int rank) {
        def pool = training.findAll {
            it.position == 'QB' && it.year == year && curve.levelledRate('QB', it.rookieRank) > 0
        }.collect { row ->
            double rate = curve.levelledRate('QB', row.rookieRank)
            [outcome: new PointsCurve.Outcome(row.outcome.games > 0 ? row.outcome.rate.toDouble() / rate : 0d,
                    row.outcome.games), weight: Math.exp(-Math.abs(row.rookieRank - rank))]
        }
        double total = pool.sum { it.weight } ?: 0d
        total > 0 ? pool.collect { [outcome: it.outcome, weight: it.weight / total] } : []
    }

    static Map poolSummary(List<Map> pool) {
        if (!pool) return [games: null, zero: null]
        [games: pool.sum { Math.min(it.outcome.games, B.LAST_WEEK - 1) * it.weight },
         zero: pool.sum { it.outcome.games == 0 ? it.weight : 0d }]
    }

    Map pools(Map context, B.Observation row) {
        def key = [row.year, row.rookieRank]
        if (!context.pools.containsKey(key)) {
            def curve = context.seasons.curve(row.year)
            def broad = context.outcomes.of('QB', row.rookieRank, row.year)
            context.pools[key] = curve.levelledRate('QB', row.rookieRank) > 0 && broad ?
                    [broad: broad.collect { [outcome: it, weight: 1d / broad.size()] },
                     local: localPool(context.training, curve, row.year, row.rookieRank)] : [broad: [], local: []]
        }
        context.pools[key]
    }

    static Double vor(List<Map> pool, def curve, B.Observation row, Map replacement) {
        if (!pool) return null
        double rate = curve.levelledRate('QB', row.rookieRank)
        double points = curve.seasonPoints('QB', row.rookieRank)
        double error = points > 0 ? curve.standardError('QB', row.rookieRank).toDouble() / points : 0d
        def replay = B.vorAgainst(replacement)
        B.ERRORS.sum { point ->
            double shifted = rate * Math.max(0d, 1d + point[0] * error)
            point[1] * pool.sum { it.weight * replay(shifted, it.outcome) }
        }
    }

    static Double blend(Double broad, Double local, double strength) {
        broad == null || local == null ? null : (1d - strength) * broad + strength * local
    }

    Map select(String outer) {
        List<Map> scores = []
        observations*.draftClass.unique().sort().findAll { it != outer }.each { inner ->
            def context = context([outer, inner])
            def eligible = observations.findAll { it.draftClass == inner && it.position == 'QB' && it.overallRank <= 50 }
            def samples = eligible.collect { row ->
                def pools = pools(context, row)
                [actual: Math.min(row.outcome.games, B.LAST_WEEK - 1),
                 broad: poolSummary(pools.broad).games, local: poolSummary(pools.local).games]
            }.findAll { it.broad != null && it.local != null }
            STRENGTHS.each { strength ->
                scores << [outer: outer, inner: inner, strength: strength, eligible: eligible.size(),
                           scored: samples.size(), gamesMse: samples ? samples.sum {
                    Math.pow(blend(it.broad as Double, it.local as Double, strength) - it.actual, 2)
                } / samples.size() : null]
            }
        }
        double strength = choose(scores)
        [strength: strength, scores: scores.collect { it + [selected: it.strength == strength] }]
    }

    static double choose(List<Map> scores) {
        def comparable = scores.groupBy { it.inner }.values().findAll { rows ->
            STRENGTHS.every { strength -> rows.any { it.strength == strength && it.gamesMse != null } }
        }.flatten()
        if (!comparable) return 0d
        STRENGTHS.min { strength ->
            def losses = comparable.findAll { it.strength == strength }*.gamesMse
            losses.sum() / losses.size()
        }
    }

    Map run(Closure progress = { }) {
        List<Map> predictions = [], selection = []
        observations*.draftClass.unique().sort().each { outer ->
            progress("Availability: outer class $outer")
            def context = context([outer])
            def chosen = select(outer)
            selection.addAll(chosen.scores)
            def heldOut = observations.findAll { it.draftClass == outer }
            def replacement = B.replacementFor(heldOut).QB
            heldOut.findAll { it.position == 'QB' }.each { row ->
                def pools = pools(context, row)
                def broad = poolSummary(pools.broad), local = poolSummary(pools.local)
                Double broadVor = vor(pools.broad, context.seasons.curve(row.year), row, replacement)
                Double localVor = vor(pools.local, context.seasons.curve(row.year), row, replacement)
                MODELS.each { model ->
                    double strength = model == 'CURRENT' ? 0d : model == 'LOCAL' ? 1d : chosen.strength
                    predictions << [draftClass: outer, name: row.name, rank: row.rookieRank, overallRank: row.overallRank,
                                    year: row.year, model: model, strength: strength,
                                    actualGames: Math.min(row.outcome.games, B.LAST_WEEK - 1),
                                    games: blend(broad.games as Double, local.games as Double, strength),
                                    actualZero: row.outcome.games == 0 ? 1 : 0,
                                    zero: blend(broad.zero as Double, local.zero as Double, strength),
                                    actualVor: B.realisedVor(row.outcome, replacement),
                                    vor: blend(broadVor, localVor, strength)]
                }
            }
        }
        [predictions: predictions, selection: selection] + metrics(predictions)
    }

    static Map metrics(List<Map> predictions) {
        List<Map> availability = [], careers = [], contracts = []
        predictions.groupBy { [it.draftClass, it.name] }.each { key, rows ->
            [1, 3, 5].each { horizon ->
                def required = rows.findAll { it.year <= horizon }
                if (MODELS.every { model -> required.count { it.model == model } == horizon }) {
                    boolean supported = required.every { it.vor != null }
                    MODELS.each { model ->
                        def at = required.findAll { it.model == model }
                        contracts << [draftClass: key[0], overallRank: rows.first().overallRank, horizon: horizon,
                                      model: model, actual: at.sum { it.actualVor }, predicted: supported ? at.sum { it.vor } : null]
                    }
                }
            }
        }
        (predictions*.draftClass.unique().sort() + ['POOLED']).each { fold ->
            ['ALL', 'TOP50'].each { cohort ->
                def selected = predictions.findAll { (fold == 'POOLED' || it.draftClass == fold) &&
                        (cohort == 'ALL' || it.overallRank <= 50) }
                ['ALL', 'QB1', 'QB2-3', 'QB4+'].each { band ->
                    selected.findAll { band == 'ALL' || (band == 'QB1' ? it.rank == 1 :
                            band == 'QB2-3' ? it.rank in [2, 3] : it.rank >= 4) }.groupBy { it.model }.each { model, rows ->
                        def scored = rows.findAll { it.games != null && it.zero != null }
                        int n = scored.size()
                        availability << [fold: fold, cohort: cohort, band: band, model: model, eligible: rows.size(), scored: n,
                                         gamesBias: n ? scored.sum { it.games - it.actualGames } / n : null,
                                         gamesRmse: n ? Math.sqrt(scored.sum { Math.pow(it.games - it.actualGames, 2) } / n) : null,
                                         zeroMean: n ? scored.sum { it.actualZero } / n : null,
                                         zeroPredicted: n ? scored.sum { it.zero } / n : null,
                                         zeroBrier: n ? scored.sum { Math.pow(it.zero - it.actualZero, 2) } / n : null]
                    }
                }
                contracts.findAll { (fold == 'POOLED' || it.draftClass == fold) &&
                        (cohort == 'ALL' || it.overallRank <= 50) }.groupBy { [it.horizon, it.model] }.each { key, rows ->
                    careers << [fold: fold, cohort: cohort, horizon: key[0], model: key[1]] + RookieQbDiagnostics.summarize(rows)
                }
            }
        }
        [availability: availability, careers: careers]
    }

    static void main(String[] args) {
        if (args.size() > 1) throw new IllegalArgumentException('Usage: ./research/run.sh availability-study [output-directory]')
        def result = new RookieAvailabilityStudy(B.observations()).run { System.err.println(it) }
        File directory = new File(args ? args[0] : 'reports/fuad/availability-study')
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create $directory")
        COLUMNS.each { name, columns ->
            new File(directory, "${name}.tsv").withWriter('UTF-8') { writer ->
                writer.println(columns.join('\t'))
                result[name].each { row -> writer.println(columns.collect { column ->
                    def value = row[column]
                    value == null ? '' : value instanceof Number && !(value instanceof Integer) ?
                            String.format(Locale.ROOT, '%.6f', value.toDouble()) : value.toString().replaceAll('[\t\r\n]', ' ')
                }.join('\t')) }
            }
        }
        println "Availability study written to ${directory.absolutePath}"
        result.availability.findAll { it.fold == 'POOLED' && it.cohort == 'TOP50' && it.band == 'ALL' }.each { println it }
        result.careers.findAll { it.fold == 'POOLED' && it.cohort == 'TOP50' && it.horizon == 5 }.each { println it }
    }
}
