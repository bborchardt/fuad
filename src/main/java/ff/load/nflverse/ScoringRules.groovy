package ff.load.nflverse

/**
 * A set of league scoring rules, applied to one weekly stat line.
 *
 * Rules are a parameter so that every season can be restated under whichever set is being priced. That is
 * what makes nine seasons comparable: the league has scored four different ways since 2017, and a season
 * scored under its own rules says nothing about what a rank is worth today.
 *
 * Only the offensive rules the league has varied are modelled. Return and recovery touchdowns score six
 * throughout and are too rare to matter; kicking is not carried in this data, and kickers take under one
 * per cent of the auction. See docs/LEAGUE_RULES.md.
 */
class ScoringRules {

    /** 2026: 4.5 point passing touchdowns, a point per completed thirty passing yards, tight end premium. */
    static final ScoringRules CURRENT = new ScoringRules(
            passingTouchdown: 4.5,
            passingYardsPerPoint: 30,
            interception: -1.0,
            receptionsByPosition: [TE: 1.0 as BigDecimal].withDefault { 0.5 as BigDecimal })

    BigDecimal passingTouchdown
    int passingYardsPerPoint
    BigDecimal interception
    Map<String, BigDecimal> receptionsByPosition

    /** Scored a week at a time, since a point per thirty yards truncates and rounding once is not the same. */
    BigDecimal score(Map<String, String> line) {
        int passingPoints = num(line.passing_yards).toInteger().intdiv(passingYardsPerPoint)
        (passingTouchdown * num(line.passing_tds)
                + passingPoints
                + interception * num(line.passing_interceptions)
                + 6.0 * num(line.rushing_tds) + 0.1 * num(line.rushing_yards)
                + 6.0 * num(line.receiving_tds) + 0.1 * num(line.receiving_yards)
                + receptionsByPosition[line.position] * num(line.receptions)
                + 2.0 * (num(line.passing_2pt_conversions) + num(line.rushing_2pt_conversions)
                + num(line.receiving_2pt_conversions))
                - 2.0 * (num(line.sack_fumbles_lost) + num(line.rushing_fumbles_lost)
                + num(line.receiving_fumbles_lost))) as BigDecimal
    }

    private static BigDecimal num(String value) {
        value?.trim() ? new BigDecimal(value.trim()) : 0.0
    }
}
