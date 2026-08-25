package ff.load.greenfield

import ff.data.fantasypros.FpRankedPlayer
import ff.league.League
import ff.load.fantasypros.FantasyProsLoader
import ff.load.util.LoadUtils
import ff.projection.ByeWeeks
import ff.projection.ExpectedValue
import ff.projection.PointsCurve
import ff.projection.StarterRequirements

/**
 * What the best player still on the board has actually been worth, pick by pick, across nine real drafts.
 *
 * <b>This replaces an assumption with a measurement.</b> Valuing a forfeited pick needs an answer to "who
 * would have been there", and the obvious answer — the next player in consensus order — is one no draft has
 * ever obeyed. This league's own drafts are a far better witness: same fourteen teams, same scoring, same
 * keeper rule, nine times over.
 *
 * The method is to walk each draft in pick order against a board ordered by what the model says each player
 * is worth, and record at every pick what the best undrafted player was worth. Where the league drafts in
 * the model's order the two agree; where it does not, the gap is value left sitting there.
 *
 * <b>Keepers leave the board before the draft starts, not at the pick they cost.</b> The export records a
 * keeper at his second or eighth round slot, which is the price paid rather than the moment he became
 * unavailable. Walking the file as written leaves him looking like the best available for most of the draft:
 * 2019's James Conner sat at the top of the board for 84 picks that way, and every figure past pick 15 was
 * his.
 *
 * <b>Name matching decides the answer here more than anywhere else.</b> A player who fails to match is never
 * marked drafted, so he stays at the top of the board and understates how far it has been picked over for
 * the rest of the draft. Two rounds of this were exactly that: suffix drift between Yahoo and FantasyPros
 * pinned it at one board position, and the aliases pinned it at another until they were applied to both
 * sides. Robby Anderson is the case worth remembering — the Yahoo export backdates him to "Robbie Chosen"
 * just as nflverse does, so 2019 reported him as the best player available in round eight of a draft he was
 * taken in. See docs/DATA.md.
 */
class DraftHistory {

    /** Suffixes the two sources disagree about carrying, which is 81 of the unmatched picks on its own. */
    private static final String SUFFIXES = /\b(jr|sr|ii|iii|iv|v)\b/

    private final PointsCurve curve
    private final StarterRequirements requirements
    private final League league

    DraftHistory(PointsCurve curve, StarterRequirements requirements, League league) {
        this.curve = curve
        this.requirements = requirements
        this.league = league
    }

    /**
     * The median value of the best available player at each pick, over every season with a draft.
     *
     * Median rather than mean because a single season where a star slid carries a mean a long way, and the
     * question being asked is what a pick is normally worth rather than what it once was.
     */
    Map<Integer, BigDecimal> bestAvailableByPick() {
        Map<Integer, List<BigDecimal>> samples = [:].withDefault { [] }
        league.seasons.each { String season ->
            bestAvailable(season).each { int pick, BigDecimal value -> samples[pick] << value }
        }
        samples.collectEntries { int pick, List<BigDecimal> values ->
            [(pick): values.sort()[(values.size() / 2) as int]]
        }.sort { it.key } as Map<Integer, BigDecimal>
    }

    /** One season's walk: what the best undrafted player was worth as each pick came round. */
    Map<Integer, BigDecimal> bestAvailable(String season) {
        List<Map> board = board(season)
        Map<String, Integer> position = [:]
        board.eachWithIndex { Map player, int i -> position[player.key as String] = i }

        boolean[] gone = new boolean[board.size()]
        List<List<String>> rows = draft(season)

        // A keeper is off the board before anyone picks; the round he cost is a price, not a moment.
        rows.findAll { it.size() > 5 && it[5] == 'Y' }.each { List<String> row ->
            Integer kept = position[key(row[2])]
            if (kept != null) {
                gone[kept] = true
            }
        }

        Map<Integer, BigDecimal> best = [:]
        int cursor = 0
        rows.findAll { !(it.size() > 5 && it[5] == 'Y') }.eachWithIndex { List<String> row, int i ->
            while (cursor < gone.length && gone[cursor]) {
                cursor++
            }
            if (cursor < gone.length) {
                best[i + 1] = board[cursor].value as BigDecimal
            }
            Integer taken = position[key(row[2])]
            if (taken != null) {
                gone[taken] = true
            }
        }
        best
    }

    /** Everyone ranked that season, ordered by what the model says they are worth. */
    private List<Map> board(String season) {
        Collection<FpRankedPlayer> ranked = new FantasyProsLoader().loadRedraftRankedPlayers(season).values()
                .findAll { league.scoredPositions.contains(it.player.position) }
        Map<String, Map<Integer, Integer>> byeMap = [:].withDefault { [:] }
        ranked.each { FpRankedPlayer player ->
            if (player.bye?.isInteger()) {
                byeMap[player.player.position][player.rank.positionRank] = player.bye as int
            }
        }
        ByeWeeks byes = new ByeWeeks(byeMap, GreenfieldValuationLoader.LAST_REGULAR_SEASON_WEEK)
        Map<String, Map<Integer, BigDecimal>> replacement =
                ExpectedValue.replacementLevels(curve, requirements, byes)
        ranked.collect { FpRankedPlayer player ->
            [key  : key(player.player.name),
             name : player.player.name,
             value: ExpectedValue.expectedValueOverReplacement(
                     curve, replacement, player.player.position, player.rank.positionRank, byes)]
        }.sort { -(it.value as BigDecimal) }
    }

    private static List<List<String>> draft(String season) {
        LoadUtils.loadCsvResource("/ff/greenfield/data/$season/draft.tsv")
                .drop(1).collect { it.split('\t') as List<String> }
    }

    /**
     * A name reduced to what both sources agree on: aliased first, then stripped of a suffix and of case.
     *
     * The alias map is applied because it is not only FantasyPros that renames a player — the Yahoo export
     * backdates a current name over a whole career exactly as nflverse does.
     */
    private static String key(String name) {
        LoadUtils.aliasedName(name)?.toLowerCase()?.replaceAll(SUFFIXES, '')?.replaceAll(/[^a-z]/, '')
    }
}
