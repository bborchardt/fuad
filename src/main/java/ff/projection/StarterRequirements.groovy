package ff.projection

/**
 * How many players at each position the league starts in total, which is what sets replacement level.
 *
 * The starting requirements are ranges rather than counts: from 2022 a team starts ten of QB 1-2, RB 1-3,
 * WR 2-5, TE 1-3 and PK 1. Six of those slots are fixed by the minimums and the remaining four are flex,
 * filled by whoever scores most. So the number of quarterbacks the league starts is not a setting to read
 * off, it is the outcome of every team filling its flex with the best player available.
 *
 * Allocating the flex greedily across the whole league gives, for 2026: 20 QB, 26 RB, 34 WR, 10 TE, 10 PK.
 *
 * The quarterback figure is decisive: superflex doubles the position's starters and sets a very high
 * replacement. The last few flex spots are not. They turn on whether a league's 12th tight end outscores
 * its 33rd receiver, and the curve has those two within three points of each other against a standard error
 * near ten, so which position they land on is inside the noise. See docs/PROJECTION.md.
 */
class StarterRequirements {

    private final Map<String, Integer> minimums
    private final Map<String, Integer> maximums
    private final int startersPerTeam
    private final int teams

    StarterRequirements(Map<String, Integer> minimums, Map<String, Integer> maximums, int startersPerTeam,
                        int teams) {
        this.minimums = minimums
        this.maximums = maximums
        this.startersPerTeam = startersPerTeam
        this.teams = teams
    }

    /** How many at each position a single team must start, before any flex is allocated. */
    Map<String, Integer> perTeamMinimums() { new LinkedHashMap<>(minimums) }

    /** The most a single team may start at each position, which is what caps its flex. */
    Map<String, Integer> perTeamMaximums() { new LinkedHashMap<>(maximums) }

    /** Slots a single team fields each week. */
    int perTeamStarters() { startersPerTeam }

    /** Read the ranges out of a season's league.json, which holds them as "1" or "1-2". */
    static StarterRequirements fromLeague(Map league, int teams) {
        Map starters = league.league.starters as Map
        List<Map> positions = starters.position as List<Map>
        Map<String, Integer> minimums = positions.collectEntries {
            [(it.name as String): ((it.limit as String).split('-')[0]) as int]
        }
        Map<String, Integer> maximums = positions.collectEntries {
            String limit = it.limit as String
            [(it.name as String): (limit.contains('-') ? limit.split('-')[1] : limit) as int]
        }
        new StarterRequirements(minimums, maximums, starters.count as String as int, teams)
    }

    /**
     * Players started league wide at each position, given what everyone is projected to score.
     *
     * Fixed slots go first, then the flex spots go to the highest projected players still under their
     * position's cap.
     */
    Map<String, Integer> startersByPosition(Map<String, List<BigDecimal>> descendingPointsByPosition) {
        Map<String, Integer> started = minimums.collectEntries { position, minimum ->
            [(position): minimum * teams]
        }
        Map<String, Integer> caps = maximums.collectEntries { position, maximum ->
            [(position): maximum * teams]
        }
        int total = startersPerTeam * teams

        List<List> candidates = descendingPointsByPosition.collectMany { String position, List<BigDecimal> points ->
            int from = started[position] ?: 0
            int to = Math.min(caps[position] ?: 0, points.size())
            (from..<to).collect { [points[it], position] }
        }.sort { a, b -> (b[0] as BigDecimal) <=> (a[0] as BigDecimal) }

        candidates.each { List candidate ->
            String position = candidate[1] as String
            if (started.values().sum() < total && started[position] < caps[position]) {
                started[position] = started[position] + 1
            }
        }
        started
    }
}
