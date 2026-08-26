package ff.load.greenfield

import ff.data.fantasypros.FpRankedPlayer
import ff.league.League
import ff.load.fantasypros.FantasyProsLoader
import ff.load.util.LoadUtils
import ff.projection.greenfield.SnakeDraft

/**
 * When each position actually comes off the board in this league, which is not when it is worth taking.
 *
 * <b>Value says who to draft; demand says when he will be gone.</b> Taking the highest value over
 * replacement at every pick is only correct if the board waits for you, and it does not. A position the room
 * empties early has to be reached for or done without, and one it leaves alone can be had later at no cost —
 * so the two readings together are the strategy and either alone is half of it.
 *
 * Measured the same way {@link DraftHistory} measures pick value: nine of this league's own drafts, each
 * pick joined to the consensus rank the player held that preseason. What comes out is how many of each
 * position have gone by the end of each round, and the pick at which a given positional rank typically goes.
 *
 * <b>Team defences are in the residual.</b> The rankings name them by city and nickname — "Denver Broncos",
 * or "Chicago (CHI)" in the older exports, which carries no nickname at all — where the draft export names
 * them by nickname alone. They cannot be joined without a team map, and nothing here prices a defence
 * anyway, so they fall into {@link #UNRANKED} along with the deep fliers no ranking carried. That bucket is
 * reported rather than hidden, because a reader counting positions would otherwise find the rounds do not
 * add up.
 */
class PositionDemand {

    /** Picks that matched no ranked player: team defences, and late fliers nobody ranked. */
    static final String UNRANKED = 'UNRANKED'

    /** Ranks either side of one that are pooled to give a positional rank enough drafts to speak from. */
    private static final int SMOOTHING_RADIUS = 2

    /** Below this many observations a rank is not given an average draft position at all. */
    private static final int MINIMUM_OBSERVATIONS = 3

    private final League league

    private List<Map<String, Object>> picks
    private Map<Integer, Map<String, Integer>> takenByRound
    private Map<String, Map<Integer, Integer>> averageDraftPosition

    PositionDemand(League league) {
        this.league = league
    }

    /** The positions reported, in board order, with the residual last. */
    List<String> positions() { league.scoredPositions + UNRANKED }

    /**
     * How many players at each position have gone by the end of each round, as a median over the seasons.
     *
     * The number to read it against is what the league starts: once the count passes that, everyone drafting
     * after is choosing from below replacement.
     */
    Map<Integer, Map<String, Integer>> takenByRound() {
        if (takenByRound != null) {
            return takenByRound
        }
        Map<Integer, Map<String, List<Integer>>> samples = [:].withDefault { [:].withDefault { [] } }
        league.seasons.each { String season ->
            Map<String, Integer> running = [:].withDefault { 0 }
            Map<Integer, Map<String, Integer>> atRound = [:]
            picks(season).each { Map pick ->
                running[pick.position as String] = running[pick.position as String] + 1
                atRound[pick.round as int] = positions().collectEntries { [(it): running[it]] }
            }
            atRound.each { int round, Map<String, Integer> counts ->
                counts.each { String position, Integer count -> samples[round][position] << count }
            }
        }
        takenByRound = samples.collectEntries { int round, Map<String, List<Integer>> counts ->
            [(round): counts.collectEntries { String position, List<Integer> values ->
                [(position): median(values)]
            }]
        }.sort { it.key } as Map<Integer, Map<String, Integer>>
    }

    /**
     * The pick a positional rank has typically gone at, pooled over ranks either side of it.
     *
     * Pooled because one rank across nine drafts is nine observations of nine different players, and the
     * question is what the position does rather than what one man did. A rank the drafts have too little to
     * say about is left out rather than reported from two samples.
     */
    Map<String, Map<Integer, Integer>> averageDraftPosition() {
        if (averageDraftPosition != null) {
            return averageDraftPosition
        }
        Map<String, Map<Integer, List<Integer>>> samples = [:].withDefault { [:].withDefault { [] } }
        league.seasons.each { String season ->
            picks(season).each { Map pick ->
                if (pick.positionRank) {
                    samples[pick.position as String][pick.positionRank as int] << (pick.overall as int)
                }
            }
        }
        averageDraftPosition = samples.collectEntries { String position, Map<Integer, List<Integer>> byRank ->
            [(position): byRank.keySet().sort().collectEntries { int rank ->
                List<Integer> pooled = ((rank - SMOOTHING_RADIUS)..(rank + SMOOTHING_RADIUS))
                        .collectMany { byRank[it] ?: [] }
                pooled.size() >= MINIMUM_OBSERVATIONS ? [(rank): median(pooled)] : [:]
            }]
        }
        averageDraftPosition
    }

    /**
     * The round by which the league's last starter at a position is normally gone.
     *
     * The single number a plan reaches for: past it, every remaining player at that position is below the
     * replacement the board prices everyone against. Null where the position still has starters left when
     * the draft ends.
     */
    Map<String, Integer> starterExhaustedByRound(Map<String, Integer> starters) {
        league.scoredPositions.collectEntries { String position ->
            Integer started = starters[position]
            Integer round = started == null ? null : takenByRound()
                    .findAll { int r, Map<String, Integer> counts -> (counts[position] ?: 0) >= started }
                    .keySet().min()
            [(position): round]
        }
    }

    /** Every pick of a season, with the position and positional rank the consensus gave the player. */
    private List<Map<String, Object>> picks(String season) {
        Map<String, FpRankedPlayer> ranked = new FantasyProsLoader().loadRedraftRankedPlayers(season)
                .values().collectEntries { [(key(it.player.name)): it] }
        List<List<String>> rows = LoadUtils.loadCsvResource("/ff/greenfield/data/$season/draft.tsv")
                .drop(1).collect { it.split('\t') as List<String> }
        rows.withIndex().collect { List<String> row, int i ->
            FpRankedPlayer player = ranked[key(row[2])]
            String position = player && league.scoredPositions.contains(player.player.position)
                    ? player.player.position : UNRANKED
            [overall     : i + 1,
             round       : SnakeDraft.roundOf(i + 1, league.teams),
             position    : position,
             positionRank: position == UNRANKED ? null : player.rank.positionRank]
        }
    }

    private static int median(List<Integer> values) {
        values.sort()[(values.size() / 2) as int]
    }

    /** As {@code DraftHistory}: aliased, then stripped of a suffix, because both sources drift. */
    private static String key(String name) {
        LoadUtils.aliasedName(name)?.toLowerCase()
                ?.replaceAll(/\b(jr|sr|ii|iii|iv|v)\b/, '')?.replaceAll(/[^a-z]/, '')
    }
}
