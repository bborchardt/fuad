package ff.load.nflverse

import ff.load.util.LoadUtils

/**
 * Read a season of weekly team defence statistics and score it under a given set of rules.
 *
 * The sibling of {@link NflverseStatsLoader}, and keyed by team abbreviation where that one is keyed by
 * player name — a defence is a franchise rather than a person, so none of the name matching applies and none
 * of its failure modes do either. A team is either in the file or it is not.
 */
class NflverseTeamStatsLoader {

    /** Team abbreviation to points over the regular season. */
    static Map<String, BigDecimal> seasonPoints(String year, DstScoringRules rules) {
        Map<String, BigDecimal> totals = [:].withDefault { 0.0 as BigDecimal }
        eachTeamWeek(year) { Map<String, String> line ->
            totals[line.team] = totals[line.team] + rules.score(line)
        }
        totals
    }

    /**
     * Team abbreviation to games played, counted as distinct weeks with a line.
     *
     * Every defence plays every week its team does, so this is thirteen for everyone bar a cancelled game.
     * It is counted rather than assumed because a season that lost a week to weather or to a pandemic is a
     * season a rate has to be taken over, and assuming would put the loss into the rate instead.
     */
    static Map<String, Integer> gamesPlayed(String year) {
        Map<String, Set<String>> weeks = [:].withDefault { [] as Set }
        eachTeamWeek(year) { Map<String, String> line -> weeks[line.team] << line.week }
        weeks.collectEntries { String team, Set<String> played -> [(team): played.size()] }
    }

    /** Every team with a line at all, which is every team that played. */
    static Set<String> played(String year) {
        Set<String> teams = []
        eachTeamWeek(year) { Map<String, String> line -> teams << line.team }
        teams
    }

    private static void eachTeamWeek(String year, Closure<Void> body) {
        List<String> lines = LoadUtils.loadCsvResource("/ff/nflverse/data/$year/team_stats.tsv")
        List<String> headings = lines.first().split('\t', -1) as List
        lines.drop(1).each { String line ->
            if (line.trim()) {
                List<String> values = line.split('\t', -1) as List
                body([headings, values].transpose().collectEntries { [(it[0]): it[1]] })
            }
        }
    }
}
