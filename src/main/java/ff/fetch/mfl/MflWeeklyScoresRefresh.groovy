package ff.fetch.mfl

import ff.fetch.FetchUtils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Fetch what a finished season's players actually scored, week by week, under the league's own rules.
 *
 * Weekly rather than seasonal because a season's record is also its schedule: a week is the unit a lineup is
 * set in. This is the league's own view of its scoring, kept as the record of what happened.
 *
 * It used to fetch projections as well, and no longer does. The league site keeps one projection per week
 * and rewrites it as the season goes, so a week 8 projection pulled in December was made knowing who was
 * hurt in week 3 — summed over 2025 that lifted the correlation with actual scoring to 0.95, against 0.17
 * to 0.65 for the week 1 projections genuinely made in advance. Nothing is priced off projections now:
 * expected points come from what consensus ranks have historically been worth, restated from raw statistics
 * by {@link ff.fetch.nflverse.NflverseStatsRefresh}. Actual scores have never had that problem and are
 * refetchable at any time. See docs/PROJECTION.md.
 */
class MflWeeklyScoresRefresh implements Runnable {

    /** The league's regular season, which is the window a salary is paid for. */
    static final int LAST_REGULAR_SEASON_WEEK = 14

    private final int year
    private final int leagueId
    private final String host

    MflWeeklyScoresRefresh(int year, int leagueId, String host) {
        this.year = year
        this.leagueId = leagueId
        this.host = host
    }

    @Override
    void run() {
        String resourcePath = "$FetchUtils.baseResourceFilePath/ff/mfl/data/$year"

        Map<String, Map<String, String>> byWeek = (1..LAST_REGULAR_SEASON_WEEK).collectEntries { int week ->
            String url = "https://$host/$year/export?JSON=1&TYPE=playerScores&L=$leagueId&W=$week&COUNT=0"
            println url
            [(week as String): scoresByPlayer(FetchUtils.fetchText(url))]
        }

        new File(resourcePath).mkdirs()
        new File("$resourcePath/player_scores.json").text =
                new JsonOutput().prettyPrint(JsonOutput.toJson([year: year as String, week: byWeek]))
    }

    private static Map<String, String> scoresByPlayer(String json) {
        def scores = (new JsonSlurper().parseText(json) as Map).playerScores?.playerScore ?: []
        ((scores instanceof List ? scores : [scores]) as List<Map>)
                .findAll { it.score && it.score != '-' }
                .collectEntries { [(it.id as String): it.score as String] }
    }
}
