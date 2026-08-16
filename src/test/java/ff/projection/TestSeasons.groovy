package ff.projection

import ff.data.RealisedSeason

/**
 * Turns plain season totals into the rate-and-availability seasons the curve is now built from.
 *
 * A season with points in it is a full slate of games unless a fixture says otherwise, and a zero is a
 * season that never happened — no points and no games — which is what the curve treats a lost year as.
 *
 * Holding games constant leaves the level exactly where the old totals put it: averaging the rate and the
 * availability apart and multiplying gives back the mean of the products when one of them never varies. So
 * a fixture built this way asserts the same numbers it always did, and only the fixtures that vary games
 * exercise the split.
 */
class TestSeasons {

    /** A full season, the fourteenth week being the bye. */
    static final int FULL = 13

    static List<RealisedSeason> of(List<BigDecimal> points, int games = FULL) {
        points.collect { BigDecimal scored ->
            new RealisedSeason(points: scored, games: scored > 0 ? games : 0)
        }
    }

    static Map<Integer, List<RealisedSeason>> byRank(Map<Integer, List<BigDecimal>> points, int games = FULL) {
        points.collectEntries { int rank, List<BigDecimal> scored -> [(rank): of(scored, games)] }
    }
}
