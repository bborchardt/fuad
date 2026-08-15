package ff.fetch.mfl

import ff.fetch.FetchUtils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Fetch a season's weekly scores, projected or actual, for the league's own scoring rules.
 *
 * Both are needed to value players. The projections say what a player is expected to do; the actuals from
 * finished seasons say how much of a projection at a given rank the market really gets, which is how the
 * optimism in any projection is corrected. Weekly rather than seasonal because replacement level moves week
 * to week as byes take starters out of the pool.
 *
 * <b>Projections cannot be refetched once a season is under way.</b> The league site keeps one projection
 * per week and rewrites it as the season goes, so a week 8 projection pulled in December was made knowing
 * who was hurt in week 3. Summed over 2025 that lifts the correlation with actual scoring to 0.95, against
 * 0.17-0.65 for the week 1 projections that were genuinely made in advance. Pulled before the season, as
 * {@link ScoreKind#PROJECTED} is meant to be, every week is a real forecast. Pulled afterwards it is
 * hindsight wearing a forecast's clothes, so this refuses to overwrite projections for a season whose games
 * have started. Actuals have no such problem.
 */
class MflWeeklyScoresRefresh implements Runnable {

    /** The league's regular season, which is the window a salary is paid for. */
    static final int LAST_REGULAR_SEASON_WEEK = 14

    private final int year
    private final int leagueId
    private final String host
    private final ScoreKind kind

    MflWeeklyScoresRefresh(int year, int leagueId, String host, ScoreKind kind) {
        this.year = year
        this.leagueId = leagueId
        this.host = host
        this.kind = kind
    }

    @Override
    void run() {
        String resourcePath = "$FetchUtils.baseResourceFilePath/ff/mfl/data/$year"
        File target = new File("$resourcePath/$kind.fileName")

        Map<String, Map<String, String>> byWeek = (1..LAST_REGULAR_SEASON_WEEK).collectEntries { int week ->
            String url = "https://$host/$year/export?JSON=1&TYPE=$kind.exportType&L=$leagueId&W=$week&COUNT=0"
            println url
            [(week as String): scoresByPlayer(FetchUtils.fetchText(url), kind.jsonKey)]
        }

        if (kind == ScoreKind.PROJECTED) {
            verifyNotUnderWay(byWeek, target)
        }

        new File(resourcePath).mkdirs()
        target.text = new JsonOutput().prettyPrint(JsonOutput.toJson([year: year as String, week: byWeek]))
    }

    /**
     * A projection the league site has revised in season is not a forecast, and writing it over one that
     * was collected in advance would quietly destroy the only ex ante record there is.
     *
     * The tell is that revised projections stop looking alike from week to week: an injured player is
     * projected near zero for the rest of the season while a healthy one is not. Before the season, weeks
     * differ only by matchup, so the same players lead every week.
     */
    private static void verifyNotUnderWay(Map<String, Map<String, String>> byWeek, File target) {
        Set<String> first = leaders(byWeek['1'])
        Set<String> last = leaders(byWeek[LAST_REGULAR_SEASON_WEEK as String])
        int shared = (first.intersect(last)).size()
        if (first && last && shared < first.size() / 2) {
            throw new IllegalStateException("Week 1 and week $LAST_REGULAR_SEASON_WEEK projections share " +
                    "only $shared of their top ${first.size()} players, so this season's projections have " +
                    'already been revised in season and are no longer forecasts. Refusing to overwrite ' +
                    "$target.name; see docs/PROJECTION.md.")
        }
    }

    private static Set<String> leaders(Map<String, String> week) {
        (week ?: [:]).sort { -(it.value as BigDecimal) }.take(40).keySet()
    }

    private static Map<String, String> scoresByPlayer(String json, String jsonKey) {
        def scores = (new JsonSlurper().parseText(json) as Map)[jsonKey]?.playerScore ?: []
        ((scores instanceof List ? scores : [scores]) as List<Map>)
                .findAll { it.score && it.score != '-' }
                .collectEntries { [(it.id as String): it.score as String] }
    }
}
