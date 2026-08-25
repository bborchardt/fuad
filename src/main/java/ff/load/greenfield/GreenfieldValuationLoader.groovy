package ff.load.greenfield

import ff.data.RealisedSeason
import ff.data.fantasypros.FpRankedPlayer
import ff.league.League
import ff.load.RealisedSeasons
import ff.load.fantasypros.FantasyProsLoader
import ff.load.util.LoadUtils
import ff.projection.ByeWeeks
import ff.projection.ExpectedValue
import ff.projection.PointsCurve
import ff.projection.StarterRequirements

/**
 * What a consensus rank is worth in the Greenfield league, which is a snake draft and so has no dollars in it.
 *
 * The chain as far as points over replacement is the auction's chain exactly: order from the FantasyPros
 * consensus, level from what those ranks have historically scored, restated under the rules being priced, and
 * replacement taken one past what the league actually starts, week by week. That much is
 * {@link ExpectedValue} and is shared. What this league does with the answer is not shared, because a snake
 * draft's currency is picks.
 *
 * Three things differ from the auction and all three are consequences of the league rather than choices.
 *
 * <b>The lineup is fixed and the quarterback is not flexed.</b> Fourteen teams start one apiece and no flex
 * can reach the position, so replacement sits at rank 15 against the dynasty league's 21. That inverts what a
 * good quarterback is worth: superflex compresses the position against a very high replacement, and this does
 * the opposite.
 *
 * <b>The order comes from a full PPR ranking and the level from half PPR seasons.</b> No PPR export survives
 * for a finished preseason, so the historical order is the half PPR set. The two disagree by about a rank
 * within a position — inside the smoothing radius the curve already applies — so the stand-in is cheap. It is
 * a stand-in nonetheless and is written down as one. See
 * {@link LoadUtils#fpRedraftRankingsPprResourcePath}.
 *
 * <b>Team defences are started and not priced.</b> The league scores one, the statistics here are per player,
 * and a position with no curve is reported as having none rather than guessed at. See {@link League}.
 */
class GreenfieldValuationLoader {

    /**
     * The last week of the regular season.
     *
     * The playoffs run weeks 15 to 17 in every rules export collected, so the season a curve is built over is
     * weeks 1 to 14 — the same fourteen the dynasty league plays, which is coincidence rather than a shared
     * rule and is why it is read off the rules rather than assumed.
     */
    static final int LAST_REGULAR_SEASON_WEEK = 14

    private static final League LEAGUE = League.GREENFIELD

    /**
     * The curve, built once.
     *
     * Nine seasons of statistics are read and restated to make it, which is much the most expensive thing
     * here and is wanted identically by everything downstream.
     */
    private PointsCurve curve

    PointsCurve curve() {
        curve ?: (curve = PointsCurve.of(realisedByRank()))
    }

    /** What a team has to field each week, which is a property of this league rather than of its seasons. */
    StarterRequirements requirements() {
        LEAGUE.requirements()
    }

    /**
     * When each rank is off, over the whole ranked pool rather than only the players worth drafting.
     *
     * Replacement level is the best player a team would not otherwise start, so it needs the byes of the
     * players doing the replacing as much as of the players being valued.
     */
    ByeWeeks byes(String year) {
        Map<String, Map<Integer, Integer>> byes = [:].withDefault { [:] }
        ranked(year).each { FpRankedPlayer player ->
            if (LEAGUE.scoredPositions.contains(player.player.position) && player.bye?.isInteger()) {
                byes[player.player.position][player.rank.positionRank] = player.bye as int
            }
        }
        new ByeWeeks(byes, LAST_REGULAR_SEASON_WEEK)
    }

    /** For each week, what the best unstarted player at each position is worth. */
    Map<String, Map<Integer, BigDecimal>> replacement(String year) {
        ExpectedValue.replacementLevels(curve(), requirements(), byes(year))
    }

    /** How many at each position the league starts, which is what replacement is taken one past. */
    Map<String, Integer> starters() {
        ExpectedValue.startersOf(curve(), requirements())
    }

    /** The season being drafted, ranked in this league's own format. */
    Collection<FpRankedPlayer> ranked(String year) {
        new FantasyProsLoader().loadRankedPlayers(LoadUtils.fpRedraftRankingsPprResourcePath(year)).values()
    }

    /**
     * The seasons the curve is levelled from, ordered by the half PPR sets that are all that survive.
     *
     * Kept separate from {@link #ranked} so the stand-in is visible: what is being drafted is read in one
     * format and what it is levelled against in another, which is a compromise and should look like one.
     */
    private Collection<FpRankedPlayer> historicallyRanked(String year) {
        new FantasyProsLoader().loadRedraftRankedPlayers(year).values()
    }

    private Map<String, Map<Integer, List<RealisedSeason>>> realisedByRank() {
        RealisedSeasons.byRank(LEAGUE, this.&historicallyRanked)
    }
}
