package ff.projection

/**
 * What a player is worth in points over replacement, before anything converts that into a price.
 *
 * This is the half of a valuation that belongs to the football rather than to the market. A rank's level
 * comes from {@link PointsCurve}, how many of them a league starts from {@link StarterRequirements}, and
 * what the player beside him is worth from the replacement those two imply. None of it knows what a league
 * uses for money.
 *
 * <b>That is the whole reason it is its own class.</b> An auction turns these points into dollars against a
 * cap; a snake draft turns them into picks against a board. Both ask the same question first — how much
 * better than replacement is this player, week by week, once byes and absence are allowed for — and only
 * then diverge. While the two lived together, the shared half could not be used without dragging in market
 * shares, franchise tags and a spend rate that describe one league's auction and no other league at all.
 *
 * The pricing that used to sit alongside this is in {@link AuctionValuation}, which reads from here.
 *
 * See docs/fuad/PROJECTION.md.
 */
class ExpectedValue {
    /**
     * The percentiles of realised outcomes the board reports a player's range at.
     *
     * Reported so that a plan can weigh a bad season against a good one without reaching into the curve for
     * the raw multipliers. Deliberately not the extremes: the left tail runs to zero at every position, so
     * a minimum is the same number for everyone and says only that seasons are sometimes lost entirely.
     */
    static final double LOW_OUTCOME = 0.10
    static final double HIGH_OUTCOME = 0.90
    /**
     * Value over replacement, averaged over how the season might actually go.
     *
     * A season is replayed against every one this stretch of the board has actually produced, and the value
     * over replacement averaged across them. Taking the expectation at the end rather than the start is the
     * whole point: a player projected level with replacement is worth nothing at his average and a good deal
     * in the seasons he beats it, and only the second reading gives a bench any value at all.
     *
     * <b>The seasons come from the rank's own neighbourhood, not from the whole position.</b> One pool a
     * position replayed the best quarterback on the board and the 34th through the same distribution, and
     * the record says a deep rank scatters two to three times as widely and plays four fewer games. Since
     * this reading is convex in the spread and linear in the games, pooling was two errors at once — and
     * they happened to be the same size at the top of the board, which is why it looked harmless. See
     * {@link PointsCurve#outcomeSeasons}.
     *
     * <b>Each replayed season is a rate and a number of games, not one blended number.</b> A player who
     * misses half a year was previously modelled as scoring half as much every week, which put him under
     * replacement in all of them and worth nothing at all — and a good many top-24 quarterback seasons are
     * that shape. Now he plays his own rate in the weeks he plays and contributes nothing in the rest, which
     * is what actually happened and is worth considerably more. What the old shape valued those seasons at
     * was recorded in a commit this repository no longer has, the history having been rewritten since, and is a
     * figure no model here can produce any more.
     *
     * Which weeks he misses is left as an expectation rather than drawn, since nothing here knows when an
     * injury lands: a season of {@code g} games out of {@code W} playable weeks earns the fraction
     * {@code g/W} of what a full season at that rate would have earned.
     *
     * Replacement itself is left at its expectation, and at its <b>rate</b>. It is the best of whoever is
     * left at a position rather than one player's season, and a replacement by definition turns up: the
     * waiver wire always has a healthy body, so discounting him by somebody else's injury risk would be
     * pricing against a player nobody has to accept.
     */
    static BigDecimal expectedValueOverReplacement(PointsCurve curve,
                                                   Map<String, Map<Integer, BigDecimal>> replacement,
                                                   String position, int rank, ByeWeeks byes) {
        Map<Integer, BigDecimal> weekly = curve.weeklyRate(position, rank, byes.of(position, rank), byes.lastWeek)
        Map<Integer, BigDecimal> against = replacement[position] ?: [:]
        if (!weekly) {
            return 0.0
        }
        int playable = PointsCurve.playableWeeks(byes.of(position, rank), byes.lastWeek).size()
        List<PointsCurve.Outcome> outcomes = curve.outcomeSeasons(position, rank)
        if (!outcomes || !playable) {
            return valueOverReplacement(weekly, against, 1.0d)
        }
        BigDecimal total = outcomes.collect { PointsCurve.Outcome outcome ->
            BigDecimal full = valueOverReplacement(weekly, against, outcome.rateMultiplier)
            full * Math.min(outcome.games, playable) / playable
        }.sum() as BigDecimal
        total / outcomes.size()
    }

    /**
     * The same for a rank whose rate and outcome spread are supplied rather than read from one curve.
     *
     * <b>Why a rookie cannot simply be handed a curve.</b> His rate comes from two places — what rookies at
     * his rank have scored, and what the consensus says his dynasty rank is worth — and his spread comes
     * from the rookie population rather than from the veterans his price is quoted against. Neither is a
     * curve lookup, so the primitive takes the numbers instead of the object holding them.
     *
     * <b>Availability lives in the outcomes and nowhere else.</b> Each outcome is one realised season: how
     * the player scored when he played, and how many games he played. Multiplying a rate by a multiplier and
     * <b>then</b> scaling by a separate expectation of games is how availability gets counted twice, which
     * is mild at a veteran rank and severe at a rookie one — it is what once priced rookie quarterbacks at
     * $151 against a board whose most expensive player was $89. Here the games come from the same season the
     * multiplier does, and a season nobody played contributes nothing rather than a discounted something.
     *
     * That makes this correct only for multipliers normalised on <b>rate</b>. See
     * {@link ff.projection.fuad.RookieOutcomes} and {@link PointsCurve#outcomeSeasons}, which are the two
     * things that build them that way; the curve's own {@link PointsCurve#outcomeMultipliers} are ratios of
     * season totals and are a description of a position rather than something to price with.
     */
    static BigDecimal expectedValueOverReplacement(BigDecimal rate, List<PointsCurve.Outcome> outcomes,
                                                   Map<Integer, BigDecimal> against,
                                                   Integer byeWeek, int lastWeek) {
        if (!rate || rate <= 0) {
            return 0.0
        }
        List<Integer> playableWeeks = PointsCurve.playableWeeks(byeWeek, lastWeek)
        int playable = playableWeeks.size()
        if (!playable) {
            return 0.0
        }
        Map<Integer, BigDecimal> weekly = (1..lastWeek)
                .collectEntries { [(it): it == byeWeek ? 0.0 as BigDecimal : rate] }
        if (!outcomes) {
            return valueOverReplacement(weekly, against, 1.0d)
        }
        BigDecimal total = outcomes.collect { PointsCurve.Outcome outcome ->
            BigDecimal full = valueOverReplacement(weekly, against, outcome.rateMultiplier)
            full * Math.min(outcome.games, playable) / playable
        }.sum() as BigDecimal
        total / outcomes.size()
    }

    /**
     * The same thing without the outcome spread: what the rank is worth if it simply has its average year.
     *
     * This is {@code max(0, E[X] - r)} against {@link #expectedValueOverReplacement}'s
     * {@code E[max(0, X - r)]}, and the gap between them is what a roster spot is worth over and above what
     * the player is expected to score — a player level with replacement is worth nothing at his average and
     * a good deal in the weeks he beats it.
     *
     * Availability is treated identically in both, so the difference isolates the spread and nothing else.
     * Reported rather than priced: nothing in the chain uses this, and it exists so the comparison the
     * documentation makes can be recomputed instead of remembered. See docs/figures.
     */
    static BigDecimal valueOverReplacementAtExpectation(PointsCurve curve,
                                                        Map<String, Map<Integer, BigDecimal>> replacement,
                                                        String position, int rank, ByeWeeks byes) {
        Map<Integer, BigDecimal> weekly = curve.weeklyRate(position, rank, byes.of(position, rank), byes.lastWeek)
        if (!weekly) {
            return 0.0
        }
        int playable = PointsCurve.playableWeeks(byes.of(position, rank), byes.lastWeek).size()
        BigDecimal full = valueOverReplacement(weekly, replacement[position] ?: [:], 1.0d)
        BigDecimal games = curve.expectedGames(position, rank)
        playable > 0 ? full * (games < playable ? games : playable) / playable : full
    }

    /**
     * For each week, the points of the best player at a position who would not be started that week.
     *
     * Public because it is an input to both readings of value over replacement, and the figures the
     * documentation quotes have to be able to compute the one the model does not price with.
     */
    static Map<String, Map<Integer, BigDecimal>> replacementLevels(PointsCurve curve,
                                                                   StarterRequirements requirements,
                                                                   ByeWeeks byes) {
        replacementByPosition(curve, startersOf(curve, requirements), byes)
    }

    /**
     * How many at each position the league starts, which is what replacement is taken one past.
     *
     * <b>Allocated on season totals, though replacement is then taken at a weekly rate.</b> The two bases
     * disagree in principle: a lineup is filled every week, and a player missing four games does not cost
     * his position four slots spread thinly over the year, he vacates one entirely in four weeks and holds
     * it in the other ten. Deciding the count on totals discounts a position's claim on the flex by its
     * injury rate, which is a season-long smear over a decision taken fourteen times.
     *
     * <b>It binds nothing, which is why it stands.</b> Allocating on {@link PointsCurve#levelledRate}
     * instead returns the same count at every position and leaves every value over replacement on the board
     * unchanged. Two things keep it inert. Most of the allocation is not contested — quarterback fills to
     * its cap of two a team and kicker's minimum is its maximum, so only running back, receiver and tight
     * end compete, for thirty slots. And expected games are nearly flat across exactly the ranks that do
     * compete, a five per cent spread over RB 24-29, WR 29-34 and TE 11-16, so multiplying by them preserves
     * the ordering. Availability diverges sharply only at the back of quarterback, which the cap has already
     * settled.
     *
     * Recorded rather than repaired so the check is not redone. See docs/TODO.md.
     */
    static Map<String, Integer> startersOf(PointsCurve curve, StarterRequirements requirements) {
        requirements.startersByPosition(curve.positions().collectEntries { String position ->
            [(position): (1..curve.depth(position)).collect { curve.seasonPoints(position, it) }]
        })
    }

    private static BigDecimal valueOverReplacement(Map<Integer, BigDecimal> weekly,
                                                   Map<Integer, BigDecimal> against, double multiplier) {
        (weekly.collect { int week, BigDecimal points ->
            BigDecimal over = points * multiplier - (against[week] ?: 0.0)
            over > 0 ? over : 0.0
        }.sum() ?: 0.0) as BigDecimal
    }

    /**
     * For each week, the points of the best player at a position who would not be started that week.
     *
     * Taken at the replacement's <b>rate</b>, since a replacement is by definition someone available: the
     * player a team actually starts in an emergency is whoever is fit that week, not a rank discounted by
     * the chance that he too is hurt.
     */
    private static Map<String, Map<Integer, BigDecimal>> replacementByPosition(PointsCurve curve,
                                                                               Map<String, Integer> starters,
                                                                               ByeWeeks byes) {
        curve.positions().collectEntries { String position ->
            Map<Integer, List<BigDecimal>> weekly = [:].withDefault { [] }
            (1..curve.depth(position)).each { int rank ->
                curve.weeklyRate(position, rank, byes.of(position, rank), byes.lastWeek).each { int week, BigDecimal points ->
                    if (points > 0) {
                        weekly[week] << points
                    }
                }
            }
            int started = starters[position] ?: 0
            [(position): weekly.collectEntries { int week, List<BigDecimal> points ->
                List<BigDecimal> sorted = points.sort(false).reverse()
                [(week): sorted.size() > started ? sorted[started] : 0.0 as BigDecimal]
            }]
        }
    }
}
