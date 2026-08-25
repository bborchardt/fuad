package ff.load.nflverse

/**
 * A set of league scoring rules, applied to one weekly stat line.
 *
 * Rules are a parameter so that every season can be restated under whichever set is being priced. That is
 * what makes nine seasons comparable: the league has scored four different ways since 2017, and a season
 * scored under its own rules says nothing about what a rank is worth today.
 *
 * Only the rules the league has varied are modelled. Return and recovery touchdowns score six throughout
 * and are too rare to matter. See docs/LEAGUE_RULES.md.
 */
class ScoringRules {

    /**
     * The dynasty league, 2026: 4.5 point passing touchdowns, a point per completed thirty passing yards,
     * tight end premium, and field goals scored by the decade they were kicked from.
     *
     * Named for its league rather than called CURRENT, which read as a fact about time while there was only
     * one league and reads as a claim about which league matters now that there are two.
     */
    static final ScoringRules FUAD_2026 = new ScoringRules(
            passingTouchdown: 4.5,
            passingYardsPerPoint: 30,
            interception: -1.0,
            receptionsByPosition: [TE: 1.0 as BigDecimal].withDefault { 0.5 as BigDecimal },
            extraPoint: 1.0,
            longestFieldGoalTier: 9)

    /**
     * The shortest field goal's worth, and the distance the tiers start climbing from.
     *
     * Three points for anything under forty yards, four for the forties, five for the fifties, and from
     * 2026 a further point for every decade beyond — six for the sixties up to nine for the nineties.
     * Before 2026 it stopped at five, which {@link #longestFieldGoalTier} expresses.
     */
    /**
     * The Greenfield league, unchanged in all ten seasons collected.
     *
     * Six point passing touchdowns, a point per twenty five passing yards, and a full point per reception at
     * every position alike — no tight end premium. Field goals are the pre-2026 tiers, three up to forty
     * yards, four for the forties and five for anything longer, which is what {@link #longestFieldGoalTier}
     * already defaults to.
     *
     * Yahoo scores the yardage fractionally, which MFL does not. See {@link #fractionalPassingYards}.
     *
     * There is only one of these because the league has never repriced: across every rules export from 2017
     * to 2026 the only differences are two IR slots that came and went and a column of Yahoo's own defaults.
     * So unlike the dynasty league, no season here has to be restated to be compared with another.
     */
    static final ScoringRules GREENFIELD = new ScoringRules(
            passingTouchdown: 6.0,
            passingYardsPerPoint: 25,
            fractionalPassingYards: true,
            interception: -1.0,
            receptionsByPosition: [:].withDefault { 1.0 as BigDecimal },
            extraPoint: 1.0)

    private static final BigDecimal SHORT_FIELD_GOAL = 3.0
    private static final int FIRST_TIER_YARDS = 40

    BigDecimal passingTouchdown
    int passingYardsPerPoint
    /**
     * Whether a part of {@link #passingYardsPerPoint} scores, rather than being thrown away.
     *
     * MFL truncates: at a point per thirty yards, 299 yards and 270 yards both score nine. Yahoo, with
     * fractional scoring switched on, pays the remainder. It is a small difference on any one week and a
     * systematic one over a season, falling only on quarterbacks and always downward, so a league scored
     * under the wrong setting has its quarterback curve quietly shifted under it.
     */
    boolean fractionalPassingYards = false
    BigDecimal interception
    Map<String, BigDecimal> receptionsByPosition
    BigDecimal extraPoint = 0.0
    /** The most a field goal can be worth: 5 through 2025, when the tiers stopped at fifty yards, 9 from 2026. */
    int longestFieldGoalTier = 5

    /**
     * What one made field goal scores, from the distance it was kicked.
     *
     * Taken from the distances themselves rather than from nflverse's buckets, whose deepest is an open
     * ended "sixty or more" — which cannot tell a 62 yard kick worth six from a 71 yard one worth seven.
     */
    BigDecimal fieldGoal(int yards) {
        yards < FIRST_TIER_YARDS ? SHORT_FIELD_GOAL
                : [SHORT_FIELD_GOAL + yards.intdiv(10) - 3, longestFieldGoalTier as BigDecimal].min()
    }

    /** Scored a week at a time, since a truncating yardage rate rounds once a week and not once a season. */
    BigDecimal score(Map<String, String> line) {
        BigDecimal passing = num(line.passing_yards)
        BigDecimal passingPoints = fractionalPassingYards ? passing / passingYardsPerPoint
                : passing.toInteger().intdiv(passingYardsPerPoint) as BigDecimal
        (passingTouchdown * num(line.passing_tds)
                + passingPoints
                + interception * num(line.passing_interceptions)
                + 6.0 * num(line.rushing_tds) + 0.1 * num(line.rushing_yards)
                + 6.0 * num(line.receiving_tds) + 0.1 * num(line.receiving_yards)
                + receptionsByPosition[line.position] * num(line.receptions)
                + 2.0 * (num(line.passing_2pt_conversions) + num(line.rushing_2pt_conversions)
                + num(line.receiving_2pt_conversions))
                - 2.0 * (num(line.sack_fumbles_lost) + num(line.rushing_fumbles_lost)
                + num(line.receiving_fumbles_lost))
                + fieldGoals(line.fg_made_list)
                + extraPoint * num(line.pat_made)) as BigDecimal
    }

    /** Every field goal made that week, scored one at a time, the distances semicolon separated. */
    private BigDecimal fieldGoals(String made) {
        if (!made?.trim()) {
            return 0.0
        }
        made.split(';').findAll { it.trim() }
                .collect { fieldGoal(it.trim() as BigDecimal as int) }
                .sum() as BigDecimal ?: 0.0
    }

    private static BigDecimal num(String value) {
        value?.trim() ? new BigDecimal(value.trim()) : 0.0
    }
}
