package ff.print.fuad

import ff.data.fuad.FuadData
import ff.data.fuad.RookieValue
import ff.data.mfl.MflDraftPick
import ff.load.fuad.RookieSeasons
import ff.projection.fuad.RookieSalary

/**
 * The rookie draft: what each rookie is worth over the contract he comes with, and what he will cost.
 *
 * <b>Three sheets off one evaluation, because they are read at three different moments.</b>
 * {@code rookie_board} is everything below, for the planning done before the room sits down.
 * {@code rookies} is the same board cut to what can be read at speed, with the draft order beside it and a
 * column to write who went where, which is the sheet the draft is actually run from. {@code rookie_picks}
 * prices the picks themselves, which is what a trade is argued over. Splitting them is not cosmetic: a
 * sheet carrying every column is unreadable at the pace a draft moves, and one carrying only the fast
 * columns cannot answer why a rookie is where he is.
 *
 * <b>{@code VALUE} is the column to read, and the sheet sorts on it.</b> It is what a rookie is worth over
 * the five years a contract can run, and it is deliberately the only thing here that is not mixed with a
 * price: what he costs is a fact about the pick he goes at rather than about him, and it lives on the pick
 * sheet beside the pick. Y1 to Y5 are the same figure year by year, kept because the shape of a career is a
 * real distinction — a back worth most immediately and a quarterback worth most in his third season are not
 * the same asset — and kept to the right, because nobody chooses on them.
 *
 * <b>{@code TIER} exists to stop the rest being over-read, and {@code VALLOW} to {@code VALHIGH} does the
 * same job across positions.</b> Rookies sharing a tier are ties: the levels behind these dollars are means
 * of a few dozen seasons, and any ordering inside their error is noise the price column dresses up. A tier
 * is only comparable within a position, so the bounds are there for the choice a rookie draft actually
 * poses — a back against a tight end against a receiver — where two overlapping ranges are a tie whatever
 * their midpoints say.
 *
 * The bounds are on the <b>estimate</b> and not on the outcome: they say how well nine rookie classes pin
 * down what a rank is worth, not how widely one career might run. They are asymmetric because value over
 * replacement is convex, which is why they are two columns and not a single band.
 *
 * <b>Sorted by value, best first.</b> It was in consensus order, on the reasoning that a draft is read that
 * way and that sorting by worth hides who is actually left — but a reader with the board in front of him can
 * see who is left, and what he cannot see is which of them is worth most. Consensus order is still on the
 * sheet as {@code FP_ROOKIE} for anyone who wants to read down it.
 *
 * <b>The three consensus columns are the working behind the value, and they are read together.</b>
 * {@code FP_ROOKIE} is where the rookie ranking puts him at his position and {@code FP_DYNASTY} where the
 * dynasty ranking does, both as a position and a rank so neither needs the {@code POS} column to be
 * understood. The second is what moves his level, and it only means anything against the first: a class's
 * third receiver usually sits around dynasty WR31, so {@code WR3} at {@code WR23} is being told something
 * that {@code WR2} at {@code WR25} is not — which is the whole of why Makai Lemon prices above Jordyn Tyson
 * despite the rookie ranking preferring Tyson. {@code FP_OVERALL} is the same rookie ranking read across
 * positions, and is what {@code DEMAND} is keyed on.
 *
 * {@code DEMAND} is where the league's own drafts say a rookie of that <b>overall consensus rank</b> goes,
 * so the distance between it and your pick is the reach or the wait. It is keyed on the rank rather than on
 * the player: it says what has happened to rookies ranked here, not what this room will do with this man.
 *
 * See docs/fuad/PROJECTION.md.
 */
class FuadRookieDraftPrinter {

    /** What the room reads off the board, left of the spacer. */
    private static final List<String> BOARD_COLUMNS =
            ['Player', 'Bye', 'NFL', 'Pos', 'VL', 'V', 'VH', 'FP', 'D']

    /** What the room writes down, right of it. */
    private static final List<String> LIVE_COLUMNS = ['Pick', 'Owner', 'Player']

    private final FuadData fuadData
    private final List<RookieValue> values
    private final Map<String, Integer> baselines
    private final Map<String, Map<Integer, Integer>> bestAvailable

    FuadRookieDraftPrinter(FuadData fuadData, List<RookieValue> values, Map<String, Integer> baselines,
                           Map<String, Map<Integer, Integer>> bestAvailable) {
        this.fuadData = fuadData
        this.values = values
        this.baselines = baselines
        this.bestAvailable = bestAvailable
    }

    /** One ranked rookie a row, best first. Written to {@code rookie_board}. */
    void printBoard(PrintWriter out) {
        List<String> years = (1..RookieSeasons.CONTRACT_YEARS).collect { "Y$it" as String }
        out.println((['PLAYER', 'POS', 'TEAM', 'BYE', 'VAL_LOW', 'VALUE', 'VAL_HIGH', 'TIER',
                      'FP_ROOKIE', 'FP_OVERALL', 'FP_DYNASTY', 'NFL', 'DEMAND'] + years).join('\t'))
        byValue().each { RookieValue value ->
            out.println(([
                    value.playerName,
                    value.position,
                    value.nflTeam ?: '',
                    value.bye ?: '',
                    value.valueLow,
                    value.contractValue,
                    value.valueHigh,
                    value.tier,
                    "$value.position$value.positionRank",
                    value.overallRank,
                    value.dynastyRank ? "$value.position$value.dynastyRank" : '',
                    nflDraftOf(value),
                    value.expectedPick ?: '',
            ] + value.valueByYear).join('\t'))
        }
    }

    /**
     * The picks themselves: who holds each one, what it costs at each position, and what is usually left.
     *
     * <b>A pick's price is a property of the pick and not of the player.</b> Bylaw 8.3 decays the baseline
     * by a fifth for every selection already made, so the same rookie costs a different salary at 1.02 and
     * at 2.02, and by the third round every position has decayed to the minimum dollar. That is the column
     * a trade is priced against, and it cannot be read off the player board at all.
     *
     * {@code BEST<POS>} is the best rookie at that position the league's own drafts have typically left at
     * that pick, as a positional rank. Where the drafts are too few to say — the deep picks, which only the
     * five round years reach — it is left empty rather than extrapolated.
     */
    void printPicks(PrintWriter out) {
        List<String> positions = RookieSalary.BASELINE_RANK.keySet().toList()
        out.println((['PICK', 'ROUND', 'SLOT', 'TEAM'] + positions.collect { "\$$it" as String } +
                positions.collect { "BEST$it" as String }).join('\t'))
        fuadData.mflData.draftPicks.eachWithIndex { MflDraftPick pick, int index ->
            int overall = index + 1
            out.println(([
                    overall,
                    pick.round,
                    pick.pick,
                    ownerOf(pick),
            ] + positions.collect { String position ->
                RookieSalary.salary(baselines[position] ?: RookieSalary.MINIMUM_SALARY, index)
            } + positions.collect { String position ->
                bestAvailable[position]?.get(overall) ?: ''
            }).join('\t'))
        }
    }


    /**
     * The sheet the draft is actually run from: who is worth taking, beside the picks as they come round.
     *
     * <b>Two tables side by side and not one.</b> The left is the board, cut to the columns a room can read
     * at speed — everything the board carries as working, the tiers and the year by year and both consensus
     * rankings, is on {@code rookie_board} for the planning that happens before the room sits down. The
     * right is the draft order with a column left empty to write who went where, which is a record of what
     * happened rather than anything this model produces. They share a row number and nothing else: the
     * rookie on row 12 has no relation to the pick on row 12, and reading across is a mistake.
     *
     * {@code POS} here is the rank <b>in value order</b>, so the third best back reads RB3 wherever the
     * consensus has him. That is a different claim from the board's {@code FP_ROOKIE}, which is where the
     * rookie ranking puts him, and the two disagreeing is the point: this column says who to take.
     */
    void printDraft(PrintWriter out) {
        List<RookieValue> board = byValue()
        List<String> positions = valueOrderPositions(board)
        List<MflDraftPick> picks = fuadData.mflData.draftPicks
        out.println((BOARD_COLUMNS + [''] + LIVE_COLUMNS).join('\t'))
        (0..<Math.max(board.size(), picks.size())).each { int row ->
            RookieValue value = row < board.size() ? board[row] : null
            MflDraftPick pick = row < picks.size() ? picks[row] : null
            out.println((boardCells(value, positions[row]) + [''] + liveCells(pick)).join('\t'))
        }
    }

    /** Best first, ties broken by the consensus, which is the order every rookie sheet is read in. */
    private List<RookieValue> byValue() {
        values.sort { RookieValue a, RookieValue b ->
            (b.contractValue <=> a.contractValue) ?: (a.overallRank <=> b.overallRank)
        }
    }

    /** {@code RB1}, {@code RB2} down the value order, which is the order the sheet is already in. */
    private static List<String> valueOrderPositions(List<RookieValue> board) {
        Map<String, Integer> taken = [:]
        board.collect { RookieValue value ->
            int rank = (taken[value.position] = (taken[value.position] ?: 0) + 1)
            "$value.position$rank" as String
        }
    }

    private static List<String> boardCells(RookieValue value, String position) {
        if (!value) {
            return BOARD_COLUMNS.collect { '' }
        }
        [
                value.playerName,
                "${value.nflTeam ?: '?'}/${value.bye ?: '?'}" as String,
                nflDraftOf(value),
                position,
                value.valueLow as String,
                value.contractValue as String,
                value.valueHigh as String,
                value.overallRank as String,
                value.expectedPick ? value.expectedPick as String : '',
        ]
    }

    /** The pick, who holds it, and a column left empty for who actually went there. */
    private static List<String> liveCells(MflDraftPick pick) {
        pick ? ["${pick.round}.${pick.pick}" as String, ownerOf(pick), ''] : LIVE_COLUMNS.collect { '' }
    }

    /** The name anybody uses at the draft, rather than the one the league site records. */
    private static String ownerOf(MflDraftPick pick) {
        String franchise = pick.franchise?.ownerName ?: pick.franchise?.name ?: ''
        franchise ? franchise.split(' ').first() : ''
    }

    /**
     * Where the NFL took him, as round and pick, or a question mark where it did not take him at all.
     *
     * A question mark rather than a blank, which would read as missing data. Going undrafted is a fact
     * about a player and one of the strongest the sheet carries.
     */
    private static String nflDraftOf(RookieValue value) {
        value.nflDraft ? "${value.nflDraft.round}.${value.nflDraft.pick}" : '?'
    }
}

