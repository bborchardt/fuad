package ff.load.nflverse

/**
 * How a league scores a team defence, applied to one team's week.
 *
 * Its own class rather than more fields on {@link ScoringRules}, because a defence is scored on a different
 * kind of thing. Every term there is something a player did; the largest term here is something the other
 * team failed to do, and it comes from the scoreboard rather than from a stat line.
 *
 * <b>Points allowed is the term that dominates.</b> It runs from ten for a shutout to minus four for
 * thirty five conceded — a fourteen point swing on one week, against a defence's whole weekly total of about
 * seven. Nothing else here is close.
 */
class DstScoringRules {

    /**
     * The Greenfield league, unchanged in all ten seasons collected.
     *
     * A sack, a takeaway, a safety, a blocked kick and a returned conversion, plus the points allowed tiers.
     */
    static final DstScoringRules GREENFIELD = new DstScoringRules(
            sack: 1.0,
            interception: 2.0,
            fumbleRecovery: 2.0,
            touchdown: 6.0,
            safety: 2.0,
            blockedKick: 2.0,
            extraPointReturned: 2.0,
            pointsAllowed: [[0, 10.0], [6, 7.0], [13, 4.0], [20, 1.0], [27, 0.0], [34, -1.0],
                            [Integer.MAX_VALUE, -4.0]])

    BigDecimal sack
    BigDecimal interception
    BigDecimal fumbleRecovery
    BigDecimal touchdown
    BigDecimal safety
    BigDecimal blockedKick
    BigDecimal extraPointReturned

    /** Upper bound of each band and what conceding inside it is worth, in ascending order. */
    List<List> pointsAllowed

    /** What conceding this many points is worth. */
    BigDecimal pointsAllowedValue(int conceded) {
        (pointsAllowed.find { conceded <= (it[0] as int) }?.getAt(1) ?: 0.0) as BigDecimal
    }

    /**
     * One team's week.
     *
     * <b>A defensive touchdown is {@code def_tds} plus a fumble return, and the two do not overlap.</b> Of
     * 220 team weeks with a fumble recovery touchdown, 197 carry {@code def_tds} at zero, so the release
     * counts interception returns in one column and fumble returns in the other.
     *
     * <b>A fumble return only counts where an opponent's fumble was recovered.</b> A team recovering its own
     * in the end zone has scored on offence, and the release does not say which happened. Where it recovered
     * no opponent fumble that week it cannot have been the defence, which rules out 11 of the 220; the
     * remaining ambiguity is a week carrying both kinds, which no column here separates.
     */
    BigDecimal score(Map<String, String> line) {
        BigDecimal recoveries = num(line.fumble_recovery_opp)
        BigDecimal fumbleReturns = recoveries > 0 ? num(line.fumble_recovery_tds) : 0.0
        (sack * num(line.def_sacks)
                + interception * num(line.def_interceptions)
                + fumbleRecovery * recoveries
                + touchdown * (num(line.def_tds) + num(line.special_teams_tds) + fumbleReturns)
                + safety * num(line.def_safeties)
                + blockedKick * (num(line.def_punt_blocks) + num(line.def_pat_blocks)
                + num(line.def_fg_blocks))
                + extraPointReturned * num(line.def_2pt_made)
                + pointsAllowedValue(num(line.points_allowed).intValue())) as BigDecimal
    }

    private static BigDecimal num(String value) {
        value?.trim() ? new BigDecimal(value.trim()) : 0.0
    }
}
