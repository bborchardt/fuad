package ff.projection

import ff.data.RealisedSeason

/**
 * What a player at a given consensus rank is worth, and how widely that turns out.
 *
 * <b>Order comes from the FantasyPros consensus, level from history.</b> The player ranked WR1 is valued at
 * what preseason WR1s have actually scored, restated under the rules being priced. No projection of any
 * particular player enters anywhere: a ranking is a judgement about who is better, which is what consensus
 * is for, and history says what that judgement has been worth.
 *
 * This is deliberately not built from projections. Doing so made the level of every rank somebody's opinion
 * of this year's specific players, so that a source rating one quarterback far above consensus dragged the
 * whole rank with him.
 *
 * <b>A season is a rate multiplied by an availability, and the two are levelled separately.</b> How good a
 * player is when he plays and how much football he plays are differently caused and differently variable —
 * for ranked quarterbacks the rate scatters with a coefficient of variation of 0.25 and games played with
 * 0.25, so about half the variation in a season total is availability rather than production. Levelling the
 * product directly lets one unlucky year of injuries at one rank masquerade as a judgement about talent.
 *
 * Multiplying two separately averaged halves is also the better estimator, and the curve measures by how
 * much: {@link Census#backward} against {@link Census#backwardOfTotals} is how far each shape travels
 * backwards, and the split wins at every position. The figures are in docs/figures/&lt;year&gt;/positions.tsv
 * as BACKWARD against BACKWARDTOTALS, rather than quoted here where nothing would notice them going stale.
 *
 * Nine seasons are pooled flat. Restating 2017-19 and 2022-24 under one rule set brings them to within a
 * few per cent at every position, so there is no era effect left to weight against. That gives about 45
 * observations a rank against 15 from three seasons, which is what the smoothing has to work with.
 *
 * Players ranked before a season who never played are in the sample as {@code games = 0}. They carry no
 * rate, being no evidence about one, and count in the availability half. Dropping them would bias the curve
 * upward and flatten the left tail a bench is priced against.
 *
 * See docs/PROJECTION.md.
 */
class PointsCurve {

    /** Ranks either side of each one that are averaged in, to get a usable sample out of nine seasons. */
    private static final int SMOOTHING_RADIUS = 2

    /**
     * Availability is smoothed five times harder than the rate, because it has far less to say by rank.
     *
     * How much football a player misses is nearly unrelated to where he was ranked — the correlation
     * between rank and games played is -0.04 at running back, -0.09 at receiver, -0.14 at tight end — so a
     * narrow window fits noise and multiplies it back into the level this split exists to take it out of.
     * Smoothed at the rate's own radius, the curve came out less monotone than the season totals it
     * replaced.
     *
     * <b>Smoothed, though, and not flattened.</b> Holding it constant across the ranks that carry money was
     * the first attempt and it went wrong at quarterback, where availability is not flat at all: there are
     * 32 starting jobs in the league, so a quarterback ranked past about 26 is a backup who plays when
     * somebody gets hurt. Availability there falls away sharply over the back of the priced range, and a flat
     * figure overstated that end by half while understating the elite. It also left a cliff wherever the flat
     * region ended. The figures are in docs/figures/&lt;year&gt;/curve.tsv as G.
     *
     * A wide window keeps what flattening was for and gives all of that back. It is less monotone at no
     * position and more monotone at every one.
     */
    private static final int AVAILABILITY_SMOOTHING_RADIUS = 10

    /**
     * Ranks levelled below this share of the position's best do not get a say in the outcome spread.
     *
     * Not because they are uninteresting but because a ratio taken against a very small number is not a
     * ratio of the same thing. The consensus ranks receivers a hundred and forty deep, and by the bottom of
     * that list a rank is worth three or four points a season, so one player who turns out to be a starter
     * comes back as sixteen times expectation. Those are not seasons that beat their projection, they are
     * seasons the consensus was not really making a claim about, and letting them in invents exactly the
     * multipliers above three that this distribution is kept empirical to avoid.
     */
    private static final double RELEVANT_FRACTION = 0.25d

    /**
     * Positions {@link #RELEVANT_FRACTION} cannot bound, and how deep they price instead.
     *
     * <b>The floor never fires at kicker.</b> The curve there is nearly flat — the 42nd ranked kicker still
     * levels around three fifths of the first — so a test written as a share of the position's best carries
     * the entire ranked pool. Flat is not the same as valuable: only ten kickers start, so everything past
     * about the eleventh is below replacement and worth nothing to anybody, and the curve saying a deep
     * kicker scores points does not give the lineup a slot to score them in.
     *
     * So kicker is bounded by what the league actually rosters. The deepest kicker ever signed at auction
     * ranks 25th, and 25 covers 95% of the kickers on a week 1 roster. Half the league carries a second for
     * bye cover, so about fifteen are rostered rather than ten, and rank predicts little enough at the
     * position that teams do not sign them in order — the median kicker signed ranks 6th, the 90th
     * percentile 16th.
     *
     * <b>It is a cap on the priced depth itself, not a filter over the board.</b> It was the second of
     * those, applied where the auction pool was assembled while the curve went on computing to 42, so
     * kicker had two depths: the board priced 25 ranks and the spread, the outcomes, the census and the
     * anchor behind those prices were all taken over 42. Every other position has one number doing both
     * jobs, and the seventeen ranks between them are where the position's lost seasons are concentrated, so
     * the distribution the board priced kickers against was largely made of ranks the board would not
     * carry.
     */
    private static final Map<String, Integer> DEPTH_CAP = [PK: 25].asImmutable() as Map<String, Integer>

    private static final int MINIMUM_OBSERVATIONS = 6

    /**
     * The rank window {@link Census#backward} is measured over, common to every position.
     *
     * It has to be common. Backward movement accumulates over the ranks it is summed across while the range
     * it is taken against does not, so a position priced a hundred ranks deep scores worse than one priced
     * thirty-six deep for no reason except its depth — and the whole use of the measure is comparing
     * positions. Thirty-six is the shallowest of the four scoring positions, so it is the deepest window
     * all of them actually make a claim across.
     *
     * <b>Kicker prices shallower than the window and is measured across its own depth instead.</b> Its
     * depth is set by what the league rosters rather than by the relevance floor — see {@link #DEPTH_CAP} —
     * and no window can be both common to the other four and inside a position that stops at 25. So the
     * kicker figure answers the same question over a shorter run and is not comparable with the rest; a
     * change to the smoothing is still read against kicker's own previous figure, which is what the measure
     * is for.
     */
    private static final int MONOTONICITY_WINDOW = 36

    /** One season as the spread sees it: how the player scored when he played, and how often he played. */
    static class Outcome {
        final double rateMultiplier
        final int games

        Outcome(double rateMultiplier, int games) {
            this.rateMultiplier = rateMultiplier
            this.games = games
        }
    }

    /**
     * What a position's curve was built from, and how well it came out.
     *
     * Carried so that the figures the documentation quotes about the curve come from the curve rather than
     * from somebody's notes on a run of it. See docs/figures.
     */
    static class Census {
        /** Seasons behind the ranks that carry money, which is the sample every level is a mean of. */
        final int seasons
        /** How many of those never happened, being the left tail a bench is priced against. */
        final int lost
        /**
         * How far the curve travels <b>backwards</b>, as a share of its range.
         *
         * A curve levelled on evidence is not obliged to be monotone and a good one is nearly so: where
         * rank r+1 levels above rank r, that step is noise the smoothing failed to remove. Summed over the
         * priced ranks and taken against the curve's whole range, it is the one number that says whether a
         * change to the smoothing helped.
         */
        final BigDecimal backward
        /** The same measure for levelling the season totals directly, which is what the split replaced. */
        final BigDecimal backwardOfTotals
        /**
         * What {@link #anchorTo} had to scale this position's shape by to put its level back.
         *
         * Above one at every position, because averaging the rate and the availability apart drops the
         * covariance between them and the product of the means lands under the mean of the products. Carried
         * because it differs <b>by position</b>, which is the whole reason it is applied: a single factor
         * inside a position would cancel out of every comparison taken there, and this one does not cancel
         * out of the sum across positions that divides the pot.
         */
        final BigDecimal anchor

        Census(int seasons, int lost, BigDecimal backward, BigDecimal backwardOfTotals, BigDecimal anchor) {
            this.seasons = seasons
            this.lost = lost
            this.backward = backward
            this.backwardOfTotals = backwardOfTotals
            this.anchor = anchor
        }
    }

    private final Map<String, Map<Integer, BigDecimal>> rateByPosition
    private final Map<String, Map<Integer, BigDecimal>> gamesByPosition
    private final Map<String, Map<Integer, BigDecimal>> levelByPosition
    private final Map<String, Map<Integer, BigDecimal>> errorByPosition
    private final Map<String, Map<Integer, Integer>> tierByPosition
    private final Map<String, List<Double>> multipliersByPosition
    private final Map<String, List<Outcome>> outcomesByPosition
    private final Map<String, Integer> depthByPosition
    private final Map<String, Census> censusByPosition

    private PointsCurve(Map<String, Map<Integer, BigDecimal>> rateByPosition,
                        Map<String, Map<Integer, BigDecimal>> gamesByPosition,
                        Map<String, Map<Integer, BigDecimal>> levelByPosition,
                        Map<String, Map<Integer, BigDecimal>> errorByPosition,
                        Map<String, Map<Integer, Integer>> tierByPosition,
                        Map<String, List<Double>> multipliersByPosition,
                        Map<String, List<Outcome>> outcomesByPosition,
                        Map<String, Integer> depthByPosition,
                        Map<String, Census> censusByPosition) {
        this.rateByPosition = rateByPosition
        this.gamesByPosition = gamesByPosition
        this.levelByPosition = levelByPosition
        this.errorByPosition = errorByPosition
        this.tierByPosition = tierByPosition
        this.multipliersByPosition = multipliersByPosition
        this.outcomesByPosition = outcomesByPosition
        this.depthByPosition = depthByPosition
        this.censusByPosition = censusByPosition
    }

    /** What this position's curve was built from, and how monotone it came out. */
    Census census(String position) {
        censusByPosition[position] ?: new Census(0, 0, 0.0, 0.0, 1.0)
    }

    /**
     * @param realised per position, the seasons had by players holding each preseason rank, over as many
     *                 seasons as are available, all restated under one set of rules
     */
    static PointsCurve of(Map<String, Map<Integer, List<RealisedSeason>>> realised) {
        Map<String, Map<Integer, BigDecimal>> rates = [:]
        Map<String, Map<Integer, BigDecimal>> games = [:]
        Map<String, Map<Integer, BigDecimal>> levels = [:]
        Map<String, Map<Integer, BigDecimal>> errors = [:]
        Map<String, Map<Integer, Integer>> tiers = [:]
        Map<String, List<Double>> multipliers = [:]
        Map<String, List<Outcome>> outcomes = [:]
        Map<String, Integer> depths = [:]
        Map<String, Census> census = [:]

        realised.each { String position, Map<Integer, List<RealisedSeason>> byRank ->
            int deepest = byRank.keySet() ? byRank.keySet().max() as int : 0
            Map<Integer, BigDecimal> rate = [:]
            Map<Integer, BigDecimal> played = [:]
            Map<Integer, BigDecimal> level = [:]
            Map<Integer, BigDecimal> error = [:]
            (1..deepest).each { int rank ->
                List<RealisedSeason> around = smoothed(byRank, rank, SMOOTHING_RADIUS)
                // Availability counts every ranked season, including the ones that never happened. Rate
                // counts only the seasons with football in them, a lost year being no evidence about form.
                List<BigDecimal> observedRates = around.findAll { it.games > 0 }.collect { it.rate }
                if (around.size() >= MINIMUM_OBSERVATIONS && observedRates.size() >= MINIMUM_OBSERVATIONS) {
                    List<RealisedSeason> wider = smoothed(byRank, rank, AVAILABILITY_SMOOTHING_RADIUS)
                    BigDecimal meanRate = mean(observedRates)
                    BigDecimal meanGames = mean(wider.collect { it.games as BigDecimal })
                    rate[rank] = meanRate
                    played[rank] = meanGames
                    level[rank] = meanRate * meanGames
                    error[rank] = levelErrorOf(observedRates, wider, meanRate, meanGames)
                }
            }
            if (level) {
                BigDecimal anchor = anchorTo(position, byRank, level, rate, played)
                Map<Integer, BigDecimal> settled = level.collectEntries { int rank, BigDecimal points ->
                    [(rank): rate[rank] * played[rank] * anchor]
                }
                Map<Integer, BigDecimal> settledError = error.collectEntries { int rank, BigDecimal e ->
                    [(rank): level[rank] > 0 ? e * settled[rank] / level[rank] : e]
                }
                rates[position] = rate
                games[position] = played
                levels[position] = settled
                errors[position] = settledError
                tiers[position] = tiersOf(settled, settledError)
                depths[position] = settled.keySet().max() as int
                multipliers[position] = spread(position, byRank, settled)
                outcomes[position] = outcomesOf(position, byRank, rate, settled)
                census[position] = censusOf(position, byRank, settled, anchor)
            }
        }
        new PointsCurve(rates, games, levels, errors, tiers, multipliers, outcomes, depths, census)
    }

    Set<String> positions() { levelByPosition.keySet().asImmutable() }

    int depth(String position) { depthByPosition[position] ?: 0 }

    /** Expected points over the season for the player holding this rank: his rate times his availability. */
    BigDecimal seasonPoints(String position, int rank) {
        levelByPosition[position]?.get(rank) ?: 0.0
    }

    /**
     * What this rank scores in a game he plays, which is the half of a season that is about ability.
     *
     * The raw mean of the seasons behind the rank, before {@link #anchorTo} puts the position's overall
     * level back where its seasons actually were.
     *
     * <b>Nothing prices off this, and it used to.</b> The argument was that the anchor is a single factor
     * across a position and cancels out of any comparison taken inside one, which is true and was not the
     * whole story: value over replacement is computed inside a position and then <b>summed across</b> all
     * of them to divide the pot, so a factor that differs by position does not cancel.
     *
     * {@link #weeklyRate} therefore takes {@link #levelledRate}. Kept public because the two are worth
     * telling apart, and because the difference between them is the anchor itself, which is in
     * docs/figures/&lt;year&gt;/positions.tsv as ANCHOR. An unanchored rate tilted {@code VALUE} between
     * positions by the spread of that column — invisible while {@code VALUE} was a secondary one, and load
     * bearing once the kicker market turned on it.
     */
    BigDecimal pointsPerGame(String position, int rank) {
        rateByPosition[position]?.get(rank) ?: 0.0
    }

    /**
     * The same rate, scaled so that rate times availability is exactly the season this rank is levelled at.
     *
     * The board carries the season, the rate and the games side by side, and a reader who multiplies two of
     * them has to land on the third. {@link #pointsPerGame} does not oblige: the level is anchored back to
     * the mean season the position actually had, about five per cent above the product of the two separate
     * means, so the raw rate times the raw games comes out short of the season by that much. A column that
     * quietly fails to multiply out is a trap on a board whose whole point is that a plan never has to
     * reach behind it.
     */
    BigDecimal levelledRate(String position, int rank) {
        BigDecimal games = expectedGames(position, rank)
        games > 0 ? seasonPoints(position, rank) / games : 0.0
    }

    /** How many games this rank has historically played, out of the thirteen a season holds. */
    BigDecimal expectedGames(String position, int rank) {
        gamesByPosition[position]?.get(rank) ?: 0.0
    }

    /**
     * How precisely a rank is levelled: the standard error of the mean behind {@link #seasonPoints}.
     *
     * Combined from the two halves, since the level is their product. That it is smaller than the error on
     * the season totals themselves is the point of splitting them: averaging rate and availability apart
     * and multiplying is a lower variance estimator than averaging the product.
     */
    BigDecimal standardError(String position, int rank) {
        errorByPosition[position]?.get(rank) ?: 0.0
    }

    /**
     * Which band of indistinguishable ranks this one falls in, 1 being the best.
     *
     * <b>The curve resolves QB2 from QB17 and cannot resolve QB10 from QB14.</b> Levels are a mean of about
     * 45 realised seasons and carry a standard error to match, so across the flat middle of a position the
     * rank-to-rank ordering is noise. Reporting those as different prices claims a resolution the evidence
     * does not have.
     *
     * A tier holds every rank levelling within one standard error of the best rank in it. Ranks in the same
     * tier at the same position should be read as ties and chosen between on price, bye or roster fit —
     * never on the order they happen to fall in. Tiers are per position: a QB tier 3 and an RB tier 3 have
     * nothing to do with each other.
     */
    int tier(String position, int rank) {
        tierByPosition[position]?.get(rank) ?: 0
    }

    /**
     * What this rank scores in each week he plays, which is not the same as each week of the season.
     *
     * Evenly across the weeks he is not on bye, because there is nothing here to say otherwise: a matchup
     * by matchup shape would have to come from a projection, which is the thing this curve exists to avoid.
     *
     * <b>This is a rate, not an expectation.</b> It says what a week looks like when he plays, and says
     * nothing about how many such weeks there are — that is {@link #expectedGames}, and the two are kept
     * apart so that a season lost to injury is modelled as absence rather than as thirteen bad weeks.
     */
    Map<Integer, BigDecimal> weeklyRate(String position, int rank, Integer byeWeek, int lastWeek) {
        BigDecimal rate = levelledRate(position, rank)
        if (!rate || !playableWeeks(byeWeek, lastWeek)) {
            return [:]
        }
        (1..lastWeek).collectEntries { [(it): it == byeWeek ? 0.0 as BigDecimal : rate] }
    }

    /** The weeks of the season this rank could play, the bye excepted. */
    static List<Integer> playableWeeks(Integer byeWeek, int lastWeek) {
        (1..lastWeek).findAll { it != byeWeek }
    }

    /**
     * Every ratio of a realised season to what the rank predicted, scaled to average one.
     *
     * The whole season, rate and availability together, which is what the board reports a range from. Kept
     * as observed rather than fitted to a distribution, because it is badly lopsided: nearly all the
     * variance is a left tail of seasons lost to injury, reaching zero, while the upside stops not far
     * above one and a half times expectation. A lognormal matched to that variance mirrors the left tail
     * into a right one and invents multipliers over three.
     */
    List<Double> outcomeMultipliers(String position) { multipliersByPosition[position] ?: [] }

    /**
     * The same seasons, but split into how he played and how often, and kept paired.
     *
     * Paired deliberately: a season's rate and its games came from one player having one year, and pricing
     * them as independent draws would lose whatever relation they have. This is what value over replacement
     * is averaged across, and it is why an injury-shortened season is now worth something — a starter who
     * played six games at his own rate cleared replacement in six weeks, where smearing the same total over
     * thirteen made him look like a player who never cleared it at all.
     */
    List<Outcome> outcomeSeasons(String position) { outcomesByPosition[position] ?: [] }

    /**
     * The multiplier a given share of this position's seasons came in under.
     *
     * <b>It is a property of the position, not of the player.</b> The ratios are pooled across every rank
     * that carries enough money to be a ratio of the same thing, so two players at the same position get
     * the same spread around their own different levels. That is deliberate — realised variation cannot
     * tell an erratic player from one the consensus misjudged — and it is why nothing here should be read
     * as this player being the risky one.
     */
    BigDecimal outcomePercentile(String position, double percentile) {
        List<Double> sorted = (multipliersByPosition[position] ?: []).sort()
        if (!sorted) {
            return 1.0
        }
        int index = (Math.ceil(percentile * sorted.size()) as int) - 1
        sorted[Math.min(sorted.size() - 1, Math.max(0, index))] as BigDecimal
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        (values.sum() as BigDecimal) / values.size()
    }

    /**
     * Put the position's overall level back where its seasons actually were.
     *
     * A season total is a rate times an availability, and averaging the two apart drops the covariance
     * between them: within a rank the years a player misses games are also years he plays less well, so the
     * product of the means comes in under the mean of the products at every position.
     *
     * That is deliberate for the <b>shape</b> — the rank-to-rank wobble it removes is the noise this whole
     * split exists to take out — but it is wrong for the <b>level</b>, and unevenly so. How unevenly is in
     * docs/figures/&lt;year&gt;/positions.tsv as ANCHOR, rather than quoted here where nothing would notice
     * it going stale, which is what happened to the two figures that used to stand in this paragraph.
     * {@link StarterRequirements} compares positions against each other to allocate the flex, so a
     * differential of that size is enough to move a starting slot between them.
     *
     * So the smoothed shape is scaled back to the mean season the position actually had. Prices normalise
     * to the pot and would not notice a uniform factor; the flex allocation would.
     */
    private static BigDecimal anchorTo(String position,
                                       Map<Integer, List<RealisedSeason>> byRank,
                                       Map<Integer, BigDecimal> level,
                                       Map<Integer, BigDecimal> rate,
                                       Map<Integer, BigDecimal> played) {
        int deepest = pricedDepthOf(position, level)
        List<Integer> priced = level.keySet().findAll { it <= deepest }.toList()
        if (!priced) {
            return 1.0
        }
        List<BigDecimal> observed = priced.collectMany { int rank -> (byRank[rank] ?: []).collect { it.points } }
        List<BigDecimal> modelled = priced.collect { int rank -> rate[rank] * played[rank] }
        if (!observed || !modelled) {
            return 1.0
        }
        BigDecimal target = mean(observed)
        BigDecimal built = mean(modelled)
        built > 0 ? target / built : 1.0
    }


    /**
     * The standard error of a level, propagated from the two halves it is a product of.
     *
     * Rate and availability are averaged over different samples — a lost season is in one and not the
     * other — so their errors are combined rather than taken from the season totals directly.
     */
    private static BigDecimal levelErrorOf(List<BigDecimal> rates, List<RealisedSeason> around,
                                           BigDecimal meanRate, BigDecimal meanGames) {
        double rateError = standardErrorOf(rates, meanRate).toDouble()
        double gamesError = standardErrorOf(around.collect { it.games as BigDecimal }, meanGames).toDouble()
        double level = (meanRate * meanGames).toDouble()
        if (level == 0.0d) {
            return 0.0
        }
        double relative = Math.sqrt(
                Math.pow(rateError / meanRate.toDouble(), 2) + Math.pow(gamesError / meanGames.toDouble(), 2))
        (level * relative) as BigDecimal
    }

    /** The standard error of a mean, over the sample it was taken from. */
    private static BigDecimal standardErrorOf(List<BigDecimal> values, BigDecimal mean) {
        if (values.size() < 2) {
            return 0.0
        }
        double variance = values.collect { double d = (it - mean).toDouble(); d * d }.sum() / (values.size() - 1)
        Math.sqrt(variance / values.size()) as BigDecimal
    }

    /**
     * Group the ranks into bands the evidence can actually tell apart.
     *
     * Walked in order of <b>level</b> rather than of rank, which matters because the curve is not monotone.
     * Sweeping by rank puts a tier boundary wherever the curve happens to dip and then recover, so two
     * ranks a point apart on estimates carrying ten points of error come out in different tiers because a
     * third rank sags between them. Ordering by level, they land together, which is what the evidence says.
     *
     * A rank joins the current tier while it levels within one standard error of the <b>best</b> rank in
     * that tier, and opens a new one when it falls further. Measuring against the tier's best rather than
     * its previous member is what stops a chain of individually-small steps from drifting a tier
     * arbitrarily wide.
     *
     * So a tier is a set of ranks, not a range of them, and it need not be contiguous.
     */
    private static Map<Integer, Integer> tiersOf(Map<Integer, BigDecimal> level, Map<Integer, BigDecimal> error) {
        List<Integer> byLevel = level.keySet().sort { a, b ->
            (level[b] <=> level[a]) ?: (a <=> b)
        }
        Map<Integer, Integer> tiers = [:]
        int tier = 0
        BigDecimal best = null
        byLevel.each { int rank ->
            BigDecimal points = level[rank]
            if (best == null || best - points > (error[rank] ?: 0.0)) {
                tier++
                best = points
            }
            tiers[rank] = tier
        }
        tiers
    }

    /**
     * Count what the curve was built from, and measure how monotone it came out.
     *
     * Both are taken over the priced ranks only, since a rank the curve has stopped making a claim about is
     * neither a season that carries money nor a step worth calling backwards.
     */
    private static Census censusOf(String position, Map<Integer, List<RealisedSeason>> byRank,
                                   Map<Integer, BigDecimal> level, BigDecimal anchor) {
        int deepest = pricedDepthOf(position, level)
        List<RealisedSeason> counted = byRank.findAll { int rank, List<RealisedSeason> seasons ->
            rank <= deepest
        }.collectMany { int rank, List<RealisedSeason> seasons -> seasons }
        int window = Math.min(MONOTONICITY_WINDOW, deepest)
        new Census(counted.size(), counted.count { it.games == 0 } as int,
                backwardShare(level, window), backwardShare(totalsLevel(byRank, deepest), window), anchor)
    }

    /**
     * The share of a curve's range that it spends travelling the wrong way.
     *
     * Every step where a worse rank levels above a better one is added up and taken against the whole drop
     * across the window. Zero is a curve that never goes backwards.
     */
    private static BigDecimal backwardShare(Map<Integer, BigDecimal> level, int window) {
        List<Integer> ranks = level.keySet().findAll { it <= window }.sort()
        if (ranks.size() < 2) {
            return 0.0
        }
        BigDecimal backward = 0.0
        ranks.eachWithIndex { int rank, int i ->
            if (i > 0) {
                BigDecimal step = level[rank] - level[ranks[i - 1]]
                if (step > 0) {
                    backward += step
                }
            }
        }
        BigDecimal range = level[ranks.first()] - level[ranks.last()]
        range > 0 ? backward / range : 0.0
    }

    /**
     * The curve the season totals give directly, which is what levelling rate and availability apart
     * replaced.
     *
     * Kept so the comparison between the two can be recomputed rather than remembered. Smoothed at the
     * rate's own radius, which is what the old shape used.
     */
    private static Map<Integer, BigDecimal> totalsLevel(Map<Integer, List<RealisedSeason>> byRank,
                                                        int deepest) {
        (1..Math.max(1, deepest)).collectEntries { int rank ->
            List<RealisedSeason> around = smoothed(byRank, rank, SMOOTHING_RADIUS)
            around.size() >= MINIMUM_OBSERVATIONS ?
                    [(rank): mean(around.collect { it.points })] : [:]
        } as Map<Integer, BigDecimal>
    }

    private static List<RealisedSeason> smoothed(Map<Integer, List<RealisedSeason>> byRank, int rank,
                                                 int radius) {
        ((rank - radius)..(rank + radius)).collectMany { (byRank[it] ?: []) as List<RealisedSeason> }
    }

    /** Which ranks carry enough money for a ratio against them to mean anything. */
    private static BigDecimal relevanceFloor(Map<Integer, BigDecimal> level) {
        (level.values().max() ?: 0.0) * RELEVANT_FRACTION
    }

    /**
     * The deepest rank at this position still carrying real money: the last one above the relevance floor.
     *
     * Taken as a single cutoff rather than testing each rank on its own, because the curve is not monotone
     * and a per-rank test comes out ragged at the boundary. At receiver the level dips under the floor at
     * rank 95, climbs back over it from 96 to 101, and drops away for good after that, which would leave a
     * handful of ranks treated as though they carried money while their neighbours did not.
     *
     * This is what bounds the auction pool as well. A rank the curve says is not really a claim is a rank
     * nobody will bid on, and that is as true of a player whose contract is expiring as of one nobody
     * holds — an expiring contract does not have to be re-signed, and if nobody bids he returns to the free
     * agent pool like anyone else. See docs/PROJECTION.md.
     */
    int pricedDepth(String position) {
        Map<Integer, BigDecimal> level = levelByPosition[position]
        if (!level) {
            return 0
        }
        pricedDepthOf(position, level)
    }

    /**
     * The last rank above the relevance floor, taken as one cutoff so the boundary is not ragged, and held
     * to {@link #DEPTH_CAP} where the floor has nothing to say.
     */
    private static int pricedDepthOf(String position, Map<Integer, BigDecimal> level) {
        BigDecimal floor = relevanceFloor(level)
        int deepest = (level.keySet().findAll { level[it] > floor }.max() ?: 0) as int
        Integer cap = DEPTH_CAP[position]
        cap == null ? deepest : Math.min(deepest, cap)
    }

    private static List<Double> spread(String position, Map<Integer, List<RealisedSeason>> byRank,
                                       Map<Integer, BigDecimal> level) {
        int deepest = pricedDepthOf(position, level)
        List<Double> ratios = byRank.collectMany { int rank, List<RealisedSeason> seasons ->
            BigDecimal expected = level[rank]
            rank <= deepest && expected > 0 ? seasons.collect { (it.points / expected).toDouble() } : []
        }
        if (ratios.size() < 20) {
            return []
        }
        double mean = ratios.sum() / ratios.size()
        mean > 0 ? ratios.collect { it / mean } : []
    }

    /**
     * The same seasons split in two, rate against the rank's rate and games as they were.
     *
     * Rate multipliers are scaled to average one so that carrying the spread moves no expected points. A
     * lost season keeps its zero games and carries whatever multiplier the scaling leaves it with, which is
     * read by nothing either way: with no games there is no week for a rate to apply to.
     */
    private static List<Outcome> outcomesOf(String position, Map<Integer, List<RealisedSeason>> byRank,
                                            Map<Integer, BigDecimal> rate,
                                            Map<Integer, BigDecimal> level) {
        int deepest = pricedDepthOf(position, level)
        List<Outcome> raw = byRank.collectMany { int rank, List<RealisedSeason> seasons ->
            BigDecimal expected = level[rank]
            BigDecimal expectedRate = rate[rank]
            if (!expected || rank > deepest || !expectedRate) {
                return [] as List<Outcome>
            }
            seasons.collect { RealisedSeason season ->
                new Outcome(season.games > 0 ? (season.rate / expectedRate).toDouble() : 1.0d, season.games)
            }
        }
        if (raw.size() < 20) {
            return []
        }
        List<Double> played = raw.findAll { it.games > 0 }.collect { it.rateMultiplier }
        if (!played) {
            return []
        }
        double mean = played.sum() / played.size()
        mean > 0 ? raw.collect { new Outcome(it.rateMultiplier / mean, it.games) } : []
    }
}
