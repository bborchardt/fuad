package ff.load.nflverse

import ff.load.util.LoadUtils

/** Read a season of weekly statistics and score it under a given set of rules. */
class NflverseStatsLoader {

    /** Player name to points over the regular season. */
    static Map<String, BigDecimal> seasonPoints(String year, ScoringRules rules) {
        Map<String, BigDecimal> totals = [:].withDefault { 0.0 as BigDecimal }
        eachStatLine(year) { Map<String, String> line ->
            totals[line.player_display_name] = totals[line.player_display_name] + rules.score(line)
        }
        totals
    }

    /**
     * Everyone with a stat line at all.
     *
     * A player who was ranked before the season and never appears scored nothing, and has to be counted as
     * a zero rather than dropped: leaving him out biases every curve upward, since the seasons that vanish
     * are exactly the ones that busted. This set is how that is told apart from a name that failed to match.
     */
    static Set<String> played(String year) {
        Set<String> names = []
        eachStatLine(year) { Map<String, String> line -> names << line.player_display_name }
        names
    }

    /**
     * Player name to games played over the regular season, counted as distinct weeks with a stat line.
     *
     * Availability is half of what a fantasy season is, and it is separately caused from the rate: a back
     * who scores 16 points a game in ten games and one who scores 14 in twelve are not the same player
     * having the same year. Thirteen is a full season here, the fourteenth week being everybody's bye.
     */
    static Map<String, Integer> gamesPlayed(String year) {
        Map<String, Set<String>> weeks = [:].withDefault { [] as Set }
        eachStatLine(year) { Map<String, String> line ->
            weeks[line.player_display_name] << line.week
        }
        weeks.collectEntries { String name, Set<String> played -> [(name): played.size()] }
    }

    private static void eachStatLine(String year, Closure<?> handle) {
        List<String> lines = LoadUtils.loadTextResource(LoadUtils.nflverseStatsResourcePath(year)).readLines()
        List<String> header = lines.first().split('\t').toList()
        lines.tail().findAll { it.trim() }.each { String line ->
            List<String> values = line.split('\t', -1).toList()
            handle([header, values].transpose().collectEntries { it as List })
        }
    }
}
