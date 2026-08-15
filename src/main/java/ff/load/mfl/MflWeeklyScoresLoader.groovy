package ff.load.mfl

import ff.load.util.LoadUtils

/**
 * Read what a finished season actually scored, as written by {@link ff.fetch.mfl.MflWeeklyScoresRefresh}.
 *
 * This is the league's own arithmetic, under whichever rules it used that year. Nothing is priced off it —
 * the curve is restated from raw statistics so that seasons scored four different ways can be compared —
 * but it remains the record of what the league itself paid points for.
 */
class MflWeeklyScoresLoader {

    /** Week to player id to points. */
    static Map<Integer, Map<String, BigDecimal>> weeklyScores(String resourcePath) {
        Map scores = LoadUtils.loadJsonResource(resourcePath) as Map
        (scores.week as Map).collectEntries { week, byPlayer ->
            [(week as String as int): (byPlayer as Map<String, String>)
                    .collectEntries { id, points -> [(id as String): new BigDecimal(points as String)] }]
        }
    }

    /** Player id to points over the whole regular season. */
    static Map<String, BigDecimal> seasonTotals(Map<Integer, Map<String, BigDecimal>> weekly) {
        Map<String, BigDecimal> totals = [:].withDefault { 0.0 as BigDecimal }
        weekly.values().each { Map<String, BigDecimal> week ->
            week.each { id, points -> totals[id] = totals[id] + points }
        }
        totals
    }
}
