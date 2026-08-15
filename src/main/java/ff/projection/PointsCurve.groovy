package ff.projection

/**
 * Expected weekly points for the player consensus ranks k-th at a position.
 *
 * Two sources, each used only for what it is good at. <b>FantasyPros consensus supplies the order</b>, since
 * a ranking is a judgement about who is better and that is what expert consensus is for. <b>The league
 * site's projections supply the curve</b>: the gaps between one rank and the next, which a ranking cannot
 * express and which is where the league's own scoring lives. So the player ranked WR1 is valued at whatever
 * the best projected wide receiver is worth, whether or not the projection agrees that they are the same
 * player.
 *
 * Projections are then corrected for what a rank actually delivers. Comparing three finished seasons of
 * scoring, indexed by the consensus rank the player held before the season, against the projection curve
 * shows projections running well above realised scoring, because a projection quietly assumes a full
 * healthy season and ranks do not survive contact with one. The correction is fitted per position on log
 * scales, `actual = e^a * projected^b`, so it takes out both the optimism and the fact that the projected
 * curve is steeper than reality at running back and wide receiver and flatter at quarterback and tight end.
 *
 * Two parameters per position, fitted over every rank, is about as much as three seasons will carry. It is
 * deliberately not fitted per rank: at that resolution the data is three observations and it shows, with
 * the fifth ranked quarterback averaging 108 points and the eighth 237.
 *
 * See docs/PROJECTION.md.
 */
class PointsCurve {

    /** Ranks projected below this share of the position's best are not worth fitting against. */
    private static final double RELEVANT_FRACTION = 0.25d

    /** Ranks either side of each one that are averaged in, to get a usable sample out of three seasons. */
    private static final int SMOOTHING_RADIUS = 2

    private final Map<String, List<Map<Integer, BigDecimal>>> slotsByPosition
    private final Map<String, List<Double>> fitByPosition
    private final Map<String, List<Double>> multipliersByPosition

    private PointsCurve(Map<String, List<Map<Integer, BigDecimal>>> slotsByPosition,
                        Map<String, List<Double>> fitByPosition,
                        Map<String, List<Double>> multipliersByPosition) {
        this.slotsByPosition = slotsByPosition
        this.fitByPosition = fitByPosition
        this.multipliersByPosition = multipliersByPosition
    }

    /**
     * @param projected      week to player id to projected points, for the season being priced
     * @param positionById   player id to position
     * @param realised       per position, the points players actually scored indexed by the consensus rank
     *                       they held before that season, over as many finished seasons as are available
     */
    static PointsCurve of(Map<Integer, Map<String, BigDecimal>> projected,
                          Map<String, String> positionById,
                          Map<String, Map<Integer, List<BigDecimal>>> realised) {
        Map<String, List<Map<Integer, BigDecimal>>> slots = [:]
        weeklyByPlayer(projected).groupBy { id, weeks -> positionById[id] }
                .findAll { position, byPlayer -> position }
                .each { String position, Map byPlayer ->
                    slots[position] = byPlayer.values()
                            .sort { a, b -> total(b as Map) <=> total(a as Map) } as List<Map<Integer, BigDecimal>>
                }
        Map<String, List<Double>> fits = slots.keySet().collectEntries { String position ->
            [(position): fit(slots[position], realised[position] ?: [:])]
        }
        Map<String, List<Double>> multipliers = slots.keySet().collectEntries { String position ->
            [(position): multipliers(slots[position], realised[position] ?: [:], fits[position])]
        }
        new PointsCurve(slots, fits, multipliers)
    }

    /** The positions the curve covers. */
    Set<String> positions() { slotsByPosition.keySet().asImmutable() }

    /** How many ranks the curve covers at a position. */
    int depth(String position) { slotsByPosition[position]?.size() ?: 0 }

    /** Expected points week by week for the player ranked {@code rank} at this position. */
    Map<Integer, BigDecimal> weeklyPoints(String position, int rank) {
        List<Map<Integer, BigDecimal>> slots = slotsByPosition[position]
        if (!slots || rank < 1 || rank > slots.size()) {
            return [:]
        }
        Map<Integer, BigDecimal> slot = slots[rank - 1]
        BigDecimal projectedTotal = total(slot)
        if (projectedTotal <= 0) {
            return slot
        }
        if (!fitByPosition[position]) {
            return slot
        }
        BigDecimal scale = realisedFor(position, projectedTotal) / projectedTotal
        slot.collectEntries { week, points -> [(week): points * scale] }
    }

    /** The fitted realisation of a projected season total, `e^a * projected^b`, uncorrected if unfitted. */
    BigDecimal realisedFor(String position, BigDecimal projectedTotal) {
        List<Double> fit = fitByPosition[position]
        if (!fit || projectedTotal <= 0) {
            return projectedTotal
        }
        Math.exp(fit[0] + fit[1] * Math.log(projectedTotal.toDouble())) as BigDecimal
    }

    /**
     * Every ratio of realised to expected scoring seen at this position, scaled to average one.
     *
     * This is how a season might actually turn out, and it is the reason a bench is worth anything: a
     * player projected level with replacement is not worth nothing, he is worth the share of seasons he
     * comes in above it.
     *
     * The shape is kept as observed rather than fitted to a distribution, because it is badly lopsided and
     * fitting it goes wrong in an expensive direction. Nearly all the variance is a left tail of seasons
     * lost to injury, down to zero at quarterback, while the upside stops around 1.6 to 1.9 times
     * expectation. A lognormal matched to that variance mirrors the left tail into a right one and invents
     * multipliers over three, which prices a bench as if every deep player might turn into a star.
     *
     * It bundles genuine variance with the consensus simply having been wrong about someone. For pricing
     * that is the right total, but it cannot tell the two apart.
     */
    List<Double> outcomeMultipliers(String position) { multipliersByPosition[position] ?: [] }

    /** The fitted [intercept, exponent] per position, null where there was too little to fit. */
    Map<String, List<Double>> getFits() { fitByPosition.asImmutable() }

    /**
     * Least squares of log realised against log projected, rank by rank. Ranks with no realised scoring
     * behind them are skipped, and a position with too little to fit is left uncorrected.
     */
    private static List<Double> fit(List<Map<Integer, BigDecimal>> slots, Map<Integer, List<BigDecimal>> realised) {
        // Only the ranks worth money. Below a quarter of the best projection at the position everyone is a
        // dollar anyway, and the deep ranks are noisy enough to drag the fit flat if they are let in.
        double best = slots ? total(slots[0]).toDouble() : 0.0d
        double floor = best * RELEVANT_FRACTION

        List<List<Double>> points = (1..(slots?.size() ?: 0)).findResults { int rank ->
            double projected = total(slots[rank - 1]).toDouble()
            // Three seasons is one observation per rank, so smooth over the neighbours before fitting.
            List<BigDecimal> scored = ((rank - SMOOTHING_RADIUS)..(rank + SMOOTHING_RADIUS))
                    .collectMany { (realised[it] ?: []) as List<BigDecimal> }
            if (projected < floor || scored.size() < 3) {
                return null
            }
            double actual = scored.sum() / scored.size() as double
            projected > 0 && actual > 0 ? [Math.log(projected), Math.log(actual)] : null
        }
        if (points.size() < 8) {
            // Too little realised scoring to say anything about this position; leave the projection alone.
            return null
        }
        double n = points.size()
        double mx = points.sum { it[0] } / n
        double my = points.sum { it[1] } / n
        double sxy = points.sum { (it[0] - mx) * (it[1] - my) } as double
        double sxx = points.sum { (it[0] - mx) * (it[0] - mx) } as double
        double b = sxx ? sxy / sxx : 1.0d
        [my - b * mx, b]
    }

    /**
     * Realised over expected for every individual season on record at this position, rescaled to average
     * one so that carrying the spread moves no expected points around.
     *
     * Measured against individual seasons rather than the smoothed rank means the curve was fitted through,
     * since what a bench spot is worth depends on how much one player's season can differ from expectation,
     * not on how well the averages behave.
     */
    private static List<Double> multipliers(List<Map<Integer, BigDecimal>> slots,
                                            Map<Integer, List<BigDecimal>> realised, List<Double> fit) {
        if (!fit || !slots) {
            return []
        }
        double floor = total(slots[0]).toDouble() * RELEVANT_FRACTION
        List<Double> ratios = realised.collectMany { int rank, List<BigDecimal> scored ->
            if (rank < 1 || rank > slots.size()) {
                return []
            }
            double projected = total(slots[rank - 1]).toDouble()
            if (projected < floor) {
                return []
            }
            double expected = Math.exp(fit[0] + fit[1] * Math.log(projected))
            expected > 0 ? scored.collect { it.toDouble() / expected } : []
        }
        if (ratios.size() < 20) {
            return []
        }
        double mean = ratios.sum() / ratios.size()
        mean > 0 ? ratios.collect { it / mean } : []
    }

    private static Map<String, Map<Integer, BigDecimal>> weeklyByPlayer(Map<Integer, Map<String, BigDecimal>> projected) {
        Map<String, Map<Integer, BigDecimal>> byPlayer = [:].withDefault { [:] }
        projected.each { int week, Map<String, BigDecimal> scores ->
            scores.each { id, points -> byPlayer[id][week] = points }
        }
        byPlayer
    }

    private static BigDecimal total(Map<Integer, BigDecimal> weeks) {
        (weeks.values().sum() ?: 0.0) as BigDecimal
    }
}
