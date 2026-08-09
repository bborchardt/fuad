package ff.projection

import java.math.RoundingMode

/**
 * What it costs a franchise to tag one of its own expiring players in a given season.
 *
 * The league lets each team franchise one free agent, keeping them off the auction block at the average of
 * the top five salaries at that player's position the previous season. Another team may bid on a franchised
 * player, but only by giving up rookie draft picks as compensation, which is enough friction that it has
 * never happened in the collected data: every tag observed is a team re-signing its own player.
 *
 * This is the one auction price in the data that is set by rule rather than by bidding, so a salary model
 * fitted over signings should treat tagged players as fixed points rather than as evidence of what the
 * market pays. See docs/LEAGUE_RULES.md.
 */
class FranchiseSalaryCalculator {

    /** How many of the previous season's salaries at a position the tag averages. */
    private static final int TOP_SALARIES = 5

    static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE', 'PK'].asImmutable()

    /**
     * The franchise salary at each position for the season following the one these rosters are from.
     *
     * Rosters are the previous season's, and players that season's player list, since a player's position
     * has to be read from the year the salary was paid in: reading it from a later year silently drops
     * everyone who has since retired, and the top five is exactly where the long contracts of retiring
     * players sit.
     */
    static Map<String, Integer> franchiseSalaries(Map priorSeasonRosters, Map priorSeasonPlayers) {
        Map<String, String> positionById = (priorSeasonPlayers.players.player as List<Map>)
                .collectEntries { [(it.id as String): it.position as String] }

        Map<String, List<BigDecimal>> salariesByPosition = (priorSeasonRosters.rosters.franchise as List<Map>)
                .collectMany { Map franchise ->
                    def rostered = franchise.player ?: []
                    (rostered instanceof List ? rostered : [rostered]) as List<Map>
                }
                .findAll { POSITIONS.contains(positionById[it.id as String]) }
                .groupBy { positionById[it.id as String] }
                .collectEntries { position, players ->
                    [(position): players.collect { new BigDecimal(it.salary as String) }]
                }

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
