package ff.projection.fuad

import java.math.RoundingMode

/**
 * What it costs a franchise to tag one of its own expiring players in a given season.
 *
 * The league lets each team franchise one free agent, keeping them off the auction block at the average of
 * the top five salaries at that player's position the previous season. Another team may bid on a franchised
 * player, but only by giving up rookie draft picks as compensation, which is enough friction to make it
 * uncommon rather than unheard of: six of the 46 confirmed tags were bid away, and the other forty are a
 * team re-signing its own player at exactly this rate. See {@link FranchiseTagIdentifier}.
 *
 * This is the one auction price in the data that is set by rule rather than by bidding, so a salary model
 * fitted over signings should treat tagged players as fixed points rather than as evidence of what the
 * market pays. See docs/fuad/LEAGUE_RULES.md.
 */
class FranchiseSalaryCalculator {

    /** How many of the previous season's salaries at a position the tag averages. */
    private static final int TOP_SALARIES = 5

    static final List<String> POSITIONS = RosterSalaries.POSITIONS

    /**
     * The franchise salary at each position for the season following the one these rosters are from.
     *
     * Rosters are the previous season's, and players that season's player list. See {@link RosterSalaries},
     * which the rookie salary rule reads the same snapshot through at a different depth.
     */
    static Map<String, Integer> franchiseSalaries(Map priorSeasonRosters, Map priorSeasonPlayers) {
        Map<String, List<BigDecimal>> salariesByPosition =
                RosterSalaries.byPosition(priorSeasonRosters, priorSeasonPlayers)

        POSITIONS.collectEntries { position ->
            List<BigDecimal> salaries = salariesByPosition[position]
            salaries ? [(position): franchiseSalary(salaries) as Integer] : [:]
        }
    }

    /**
     * The average of the top five, rounded to the nearest dollar.
     *
     * An average of five salaries lands on a fifth of a dollar, never on a half, so the rounding mode never
     * comes into it.
     */
    static int franchiseSalary(List<BigDecimal> salaries) {
        List<BigDecimal> top = salaries.sort(false).reverse().take(TOP_SALARIES)
        ((top.sum() as BigDecimal) / top.size()).setScale(0, RoundingMode.HALF_UP).intValueExact()
    }
}
