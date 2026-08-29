package ff.load.fuad

import ff.data.fantasypros.FpRankedPlayer
import ff.data.fuad.RookiePick
import ff.load.fantasypros.FantasyProsLoader
import ff.load.util.LoadUtils

/**
 * When a rookie actually comes off the board, which is not the same as where the consensus ranks him.
 *
 * <b>Value says who to take; this says who will still be there.</b> A plan that takes the best rookie
 * available at every pick is only a plan if the board waits, and nine of this league's own drafts are a far
 * better witness to that than the consensus order is. They agree closely at the top — the first pick has
 * been the consensus first or second rookie in six of nine years — and diverge steadily after it, which is
 * exactly the region a middle pick has to plan around.
 *
 * Measured the way {@link ff.load.greenfield.PositionDemand} measures it, and for the same reason: a rank
 * with two observations across nine drafts is not evidence, so ranks are pooled with their neighbours and
 * anything still short of a handful of sightings is reported as unknown rather than guessed.
 *
 * <b>Drafts are not the same length, so the deep picks rest on fewer of them.</b> Four rounds through 2020
 * and five since, against eight to eleven teams; 2017 and 2024 carry expansion picks on top. A pick past 40
 * exists in five drafts and a pick past 50 in one, which is why {@link #MINIMUM_OBSERVATIONS} is enforced
 * per pick rather than per draft.
 */
class RookieDemand {

    /** Ranks either side of one that are pooled in, to get a usable sample out of nine drafts. */
    private static final int SMOOTHING_RADIUS = 2

    /** Below this many sightings a rank or a pick is left unanswered rather than answered badly. */
    private static final int MINIMUM_OBSERVATIONS = 3

    /** Prefix lengths tried when a drafted name has no exact match in that year's rookie ranking. */
    private static final List<Integer> MATCH_LENGTHS = [10, 5, 3].asImmutable()

    private Map<Integer, Integer> expectedPick
    private Map<String, Map<Integer, Integer>> bestAvailable
    private List<List<Integer>> ranked

    /**
     * The overall pick each rookie rank has gone at, as a median over the drafts that took one there.
     *
     * Keyed by the rank rather than by the pick because it answers a question about a player: the consensus
     * fourteenth rookie is expected to go here, so at your pick he is a reach or a bargain by this much.
     */
    Map<Integer, Integer> expectedPickByRank() {
        expectedPick ?: (expectedPick = medianByRank())
    }

    /**
     * The best rookie still on the board at each pick, by position, as a median over the drafts.
     *
     * Positional rather than overall because it feeds a plan across a team's own picks, and a plan chooses
     * between positions: what matters at pick 23 is that the best receiver left is typically the eighth
     * rather than that the best rookie left is the nineteenth.
     */
    Map<String, Map<Integer, Integer>> bestAvailableByPick() {
        bestAvailable ?: (bestAvailable = medianBestAvailable())
    }

    /** Every drafted pick that could be joined to a rookie rank, as [overall pick, overall rookie rank]. */
    private List<List<Integer>> rankedPicks() {
        ranked ?: (ranked = RookieDraftHistory.SEASONS.collectMany { String season ->
            Map<String, FpRankedPlayer> rookies = rookieRanking(season)
            RookieDraftHistory.picks(season).collect { RookiePick pick ->
                FpRankedPlayer matched = match(rookies, pick.playerName)
                matched ? [pick.overall, matched.rank.overallRank] : null
            }.findAll()
        })
    }

    private Map<Integer, Integer> medianByRank() {
        Map<Integer, List<Integer>> picksByRank = [:].withDefault { [] }
        rankedPicks().each { List<Integer> pick -> picksByRank[pick[1]] << pick[0] }

        Map<Integer, Integer> median = [:]
        picksByRank.keySet().sort().each { int rank ->
            List<Integer> pooled = ((rank - SMOOTHING_RADIUS)..(rank + SMOOTHING_RADIUS))
                    .collectMany { picksByRank[it] ?: [] }
            if (pooled.size() >= MINIMUM_OBSERVATIONS) {
                median[rank] = medianOf(pooled)
            }
        }
        median
    }

    /**
     * Walk each draft pick by pick, recording the best rookie left at each position when the pick was made.
     *
     * The board is the consensus order and the walk removes players as they go, so this measures the same
     * thing at every pick of every draft: what the room had left to choose from.
     */
    private Map<String, Map<Integer, Integer>> medianBestAvailable() {
        Map<String, Map<Integer, List<Integer>>> seen = [:].withDefault { [:].withDefault { [] } }
        RookieDraftHistory.SEASONS.each { String season ->
            Map<String, FpRankedPlayer> rookies = rookieRanking(season)
            Map<String, List<FpRankedPlayer>> byPosition = rookies.values()
                    .groupBy { it.player.position }
                    .collectEntries { String position, List<FpRankedPlayer> players ->
                        [(position): players.sort { it.rank.positionRank }]
                    }
            Set<String> taken = [] as Set
            RookieDraftHistory.picks(season).each { RookiePick pick ->
                byPosition.each { String position, List<FpRankedPlayer> players ->
                    FpRankedPlayer best = players.find { !taken.contains(it.player.name) }
                    if (best) {
                        seen[position][pick.overall] << best.rank.positionRank
                    }
                }
                FpRankedPlayer matched = match(rookies, pick.playerName)
                if (matched) {
                    taken << matched.player.name
                }
            }
        }
        seen.collectEntries { String position, Map<Integer, List<Integer>> byPick ->
            [(position): monotone(byPick.findAll { it.value.size() >= MINIMUM_OBSERVATIONS }
                    .collectEntries { int pick, List<Integer> ranks -> [(pick): medianOf(ranks)] })]
        } as Map<String, Map<Integer, Integer>>
    }

    /**
     * Hold the board to only ever emptying, which a median over unequal drafts does not do on its own.
     *
     * A pick past 40 exists in five of the nine drafts and a pick past 50 in one, so the deep picks are
     * medians over a different, smaller set of seasons than the shallow ones — and a five round year that
     * happened to leave a good receiver on the board can put pick 41 ahead of pick 40. That is an artefact
     * of which drafts reached which pick, not a claim that waiting improves the board, and a plan that read
     * it would be told to wait for a player who has already gone.
     *
     * Carried forward rather than smoothed, so a pick can only ever report a board at least as picked over
     * as the pick before it. {@link ff.projection.PointsCurve} does the same to the levels for the same
     * reason.
     */
    private static Map<Integer, Integer> monotone(Map<Integer, Integer> byPick) {
        int worst = 0
        byPick.sort { it.key }.collectEntries { int pick, int rank ->
            worst = Math.max(worst, rank)
            [(pick): worst]
        } as Map<Integer, Integer>
    }

    private static Map<String, FpRankedPlayer> rookieRanking(String season) {
        new FantasyProsLoader().loadRankedPlayers(LoadUtils.fpRookieRankingsPprResourcePath(season))
    }

    /**
     * The ranked rookie a drafted name refers to, tried exactly and then by prefix.
     *
     * The league writes a name last first and the ranking writes it first last, so both go through
     * {@link LoadUtils#isNameMatch} rather than being compared as strings. Six of 398 picks match nothing:
     * they are the deep fliers no rookie ranking carried at all.
     */
    private static FpRankedPlayer match(Map<String, FpRankedPlayer> rookies, String draftedName) {
        if (!draftedName) {
            return null
        }
        String name = LoadUtils.aliasedName(LoadUtils.nameFirstThenLast(draftedName))
        FpRankedPlayer exact = rookies[name] ?: rookies.values().find { it.player.name == name }
        if (exact) {
            return exact
        }
        for (int length : MATCH_LENGTHS) {
            FpRankedPlayer matched = rookies.values().find {
                LoadUtils.isNameMatch(it.player.name, name, length)
            }
            if (matched) {
                return matched
            }
        }
        null
    }

    private static int medianOf(List<Integer> values) {
        List<Integer> sorted = values.sort(false)
        int middle = sorted.size().intdiv(2)
        sorted.size() % 2 == 1 ? sorted[middle] : ((sorted[middle - 1] + sorted[middle]) / 2).round() as int
    }
}
