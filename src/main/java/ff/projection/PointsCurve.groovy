package ff.projection

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
 * Nine seasons are pooled flat. Restating 2017-19 and 2022-24 under one rule set brings them to within a
 * few per cent at every position, so there is no era effect left to weight against. That gives about 45
 * observations a rank against 15 from three seasons, which is what the smoothing has to work with.
 *
 * Players ranked before a season who never played are in the sample as zeros. They are the seasons that
 * busted hardest and dropping them biases the curve upward and flattens its left tail.
 *
 * See docs/PROJECTION.md.
 */
class PointsCurve {

    /** Ranks either side of each one that are averaged in, to get a usable sample out of nine seasons. */
    private static final int SMOOTHING_RADIUS = 2

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

    private static final int MINIMUM_OBSERVATIONS = 6

    private final Map<String, Map<Integer, BigDecimal>> levelByPosition
    private final Map<String, Map<Integer, BigDecimal>> errorByPosition
    private final Map<String, Map<Integer, Integer>> tierByPosition
    private final Map<String, List<Double>> multipliersByPosition
    private final Map<String, Integer> depthByPosition

    private PointsCurve(Map<String, Map<Integer, BigDecimal>> levelByPosition,
                        Map<String, Map<Integer, BigDecimal>> errorByPosition,
                        Map<String, Map<Integer, Integer>> tierByPosition,
                        Map<String, List<Double>> multipliersByPosition,
                        Map<String, Integer> depthByPosition) {
        this.levelByPosition = levelByPosition
        this.errorByPosition = errorByPosition
        this.tierByPosition = tierByPosition
        this.multipliersByPosition = multipliersByPosition
        this.depthByPosition = depthByPosition
    }

    /**
     * @param realised per position, the points scored by players holding each preseason rank, over as many
     *                 seasons as are available, all restated under one set of rules
     */
    static PointsCurve of(Map<String, Map<Integer, List<BigDecimal>>> realised) {
        Map<String, Map<Integer, BigDecimal>> levels = [:]
        Map<String, Map<Integer, BigDecimal>> errors = [:]
        Map<String, Map<Integer, Integer>> tiers = [:]
        Map<String, List<Double>> multipliers = [:]
        Map<String, Integer> depths = [:]

        realised.each { String position, Map<Integer, List<BigDecimal>> byRank ->
            int deepest = byRank.keySet() ? byRank.keySet().max() as int : 0
            Map<Integer, BigDecimal> level = [:]
            Map<Integer, BigDecimal> error = [:]
            (1..deepest).each { int rank ->
                List<BigDecimal> around = smoothed(byRank, rank)
                if (around.size() >= MINIMUM_OBSERVATIONS) {
                    level[rank] = (around.sum() as BigDecimal) / around.size()
                    error[rank] = standardErrorOf(around, level[rank])
                }
            }
            if (level) {
                levels[position] = level
                errors[position] = error
                tiers[position] = tiersOf(level, error)
                depths[position] = level.keySet().max() as int
                multipliers[position] = spread(byRank, level)
            }
        }
        new PointsCurve(levels, errors, tiers, multipliers, depths)
    }

    Set<String> positions() { levelByPosition.keySet().asImmutable() }

    int depth(String position) { depthByPosition[position] ?: 0 }

    /** Expected points over the season for the player holding this rank. */
    BigDecimal seasonPoints(String position, int rank) {
        levelByPosition[position]?.get(rank) ?: 0.0
    }

    /**
     * How precisely a rank is levelled: the standard error of the mean behind {@link #seasonPoints}.
     *
     * Roughly 10 to 12 points at quarterback, against a level near 190, because realised seasons at one
     * rank scatter enormously. That is the honest resolution of this curve and it is coarser than a dollar.
     */
    BigDecimal standardError(String position, int rank) {
        errorByPosition[position]?.get(rank) ?: 0.0
    }

    /**
     * Which band of indistinguishable ranks this one falls in, 1 being the best.
     *
     * <b>The curve resolves QB2 from QB17 and cannot resolve QB10 from QB14.</b> Levels are a mean of about
     * 45 realised seasons and carry a standard error to match, so across the flat middle of a position the
     * rank-to-rank ordering is noise: QB14 lands a point above QB10 on estimates that are each plus or
     * minus eleven. Reporting those as different prices claims a resolution the evidence does not have.
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
     * Expected points week by week, spread evenly over the weeks the player is not on bye.
     *
     * Evenly because there is nothing here to say otherwise. A matchup by matchup shape would have to come
     * from a projection, which is the thing this curve exists to avoid; the bye is a fact of the schedule.
     */
    Map<Integer, BigDecimal> weeklyPoints(String position, int rank, Integer byeWeek, int lastWeek) {
        BigDecimal season = seasonPoints(position, rank)
        List<Integer> playing = (1..lastWeek).findAll { it != byeWeek }
        if (!season || !playing) {
            return [:]
        }
        BigDecimal perWeek = season / playing.size()
        (1..lastWeek).collectEntries { [(it): it == byeWeek ? 0.0 as BigDecimal : perWeek] }
    }

    /**
     * Every ratio of realised scoring to what the rank predicted, scaled to average one.
     *
     * Kept as observed rather than fitted to a distribution, because it is badly lopsided: nearly all the
     * variance is a left tail of seasons lost to injury, reaching zero, while the upside stops not far
     * above one and a half times expectation. A lognormal matched to that variance mirrors the left tail
     * into a right one and invents multipliers over three.
     */
    List<Double> outcomeMultipliers(String position) { multipliersByPosition[position] ?: [] }

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

    /** The standard error of a rank's mean, over the smoothed window it was taken from. */
    private static BigDecimal standardErrorOf(List<BigDecimal> around, BigDecimal mean) {
        if (around.size() < 2) {
            return 0.0
        }
        double variance = around.collect { double d = (it - mean).toDouble(); d * d }.sum() / (around.size() - 1)
        Math.sqrt(variance / around.size()) as BigDecimal
    }

    /**
     * Group the ranks into bands the evidence can actually tell apart.
     *
     * Walked in order of <b>level</b> rather than of rank, which matters because the curve is not monotone.
     * Sweeping by rank puts a tier boundary wherever the curve happens to dip and then recover, so QB10 and
     * QB14 — a single point apart on estimates carrying eleven points of error — come out in different
     * tiers because QB11 sags between them. Ordering by level, they land together, which is what the
     * evidence says.
     *
     * A rank joins the current tier while it levels within one standard error of the <b>best</b> rank in
     * that tier, and opens a new one when it falls further. Measuring against the tier's best rather than
     * its previous member is what stops a chain of individually-small steps from drifting a tier
     * arbitrarily wide.
     *
     * So a tier is a set of ranks, not a range of them, and it need not be contiguous. That is the point:
     * saying QB8 through QB15 are one tier while QB11 is not is a claim about QB11 that the curve has no
     * business making.
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

    private static List<BigDecimal> smoothed(Map<Integer, List<BigDecimal>> byRank, int rank) {
        ((rank - SMOOTHING_RADIUS)..(rank + SMOOTHING_RADIUS))
                .collectMany { (byRank[it] ?: []) as List<BigDecimal> }
    }

    private static List<Double> spread(Map<Integer, List<BigDecimal>> byRank, Map<Integer, BigDecimal> level) {
        BigDecimal floor = (level.values().max() ?: 0.0) * RELEVANT_FRACTION
        List<Double> ratios = byRank.collectMany { int rank, List<BigDecimal> scored ->
            BigDecimal expected = level[rank]
            expected > floor && expected > 0 ? scored.collect { (it / expected).toDouble() } : []
        }
        if (ratios.size() < 20) {
            return []
        }
        double mean = ratios.sum() / ratios.size()
        mean > 0 ? ratios.collect { it / mean } : []
    }
}
