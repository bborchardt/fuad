package ff.projection.fuad

/**
 * What every player on a set of rosters was being paid, gathered by position and ordered richest first.
 *
 * Two of the league's rules are set off this same list and read it at different depths: the franchise tag
 * averages the top five at a position, and a rookie salary starts from a single salary well down it — the
 * fifteenth quarterback, the twentieth running back, the thirty fifth receiver. Sharing the extraction is
 * what keeps them reading the same league.
 *
 * <b>Positions come from the season the salary was paid in.</b> Reading them from a later player list
 * silently drops everyone who has since retired, and at the top of the list that is exactly where the long
 * contracts sit. Both rules name the previous season, so both are given that season's players.
 */
class RosterSalaries {

    /** The positions the league prices, and the only ones either rule asks about. */
    static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE', 'PK'].asImmutable()

    /** Every salary at each position, highest first, from one roster snapshot. */
    static Map<String, List<BigDecimal>> byPosition(Map rosters, Map players) {
        Map<String, String> positionById = (players.players.player as List<Map>)
                .collectEntries { [(it.id as String): it.position as String] }

        (rosters.rosters.franchise as List<Map>)
                .collectMany { Map franchise ->
                    // A franchise sitting on an empty roster carries no player key at all.
                    def rostered = franchise.player ?: []
                    (rostered instanceof List ? rostered : [rostered]) as List<Map>
                }
                .findAll { POSITIONS.contains(positionById[it.id as String]) }
                .groupBy { positionById[it.id as String] }
                .collectEntries { position, rostered ->
                    [(position): rostered.collect { new BigDecimal(it.salary as String) }.sort().reverse()]
                }
    }
}
