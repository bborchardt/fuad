package ff.print.greenfield

import ff.load.greenfield.GreenfieldBoard
import ff.projection.greenfield.SnakeDraft

import java.math.RoundingMode

/**
 * For one draft slot: what is likely to be there at each of its picks, and what it loses by waiting.
 *
 * <b>{@code DECAY} is the column to draft from.</b> Value says which player is worth most now; decay says
 * which position will have fallen furthest by the time this slot picks again, and only the second can be
 * acted on. A back and a receiver worth the same today are not the same pick if the backs will be gone in
 * two rounds and the receivers will not.
 *
 * Rows are ordered by decay within each pick, so the position at the top is the one that cannot wait.
 *
 * <b>A forfeited pick makes the gap that follows it longer, and that is the whole cost of a keeper beyond
 * its price.</b> Giving up an eighth does not only cost the player it would have returned; it doubles the
 * wait around it, and a position that runs out inside the gap runs out without this team.
 *
 * <b>It is roster-aware, which is what makes it a plan rather than a list.</b> This league caps a team at
 * one quarterback, three backs, three receivers, two tight ends and one kicker, so a position already full
 * adds nothing to a starting lineup however well it grades — and a sheet that goes on recommending
 * quarterbacks to a team holding one is worse than no sheet, because it is confidently wrong. Each position
 * carries a status against what is already held, and only the ones that can still improve a lineup are
 * candidates.
 *
 * The row marked {@code TAKE} at each pick is the one to act on: the position that can still be used and
 * falls furthest before this slot picks again. Taking it is then assumed, so the pick after it is advised
 * against a roster that already holds it. Feed the {@code -r} flag what has really been taken as the draft
 * runs and the rest re-plans around it.
 *
 * <b>The defence is not in it.</b> The league starts one and this project prices none, so the plan covers
 * eight of the nine starting slots and the ninth has to be remembered. It goes late in any case — no defence
 * comes off the board here before round seven.
 *
 * <b>Where it stops advising is deliberate.</b> Once every starting slot the model prices is full, a further
 * player is bench, and what a bench is worth is bye cover, injury cover and optionality — which is
 * {@code LineupValue}'s question and not this sheet's. It says {@code BENCH} and stops choosing rather than
 * inventing a preference it cannot support.
 *
 * Everything here is a median over nine drafts, not a promise. Half the time the rank named is already gone.
 */
class GreenfieldOutlookPrinter {

    private final GreenfieldBoard board
    private final int slot
    private final int teams
    private final int rounds
    private final Set<Integer> forfeited
    private final List<String> positions
    private final Map<String, Integer> minimums
    private final Map<String, Integer> maximums
    private final Map<String, Integer> held

    GreenfieldOutlookPrinter(GreenfieldBoard board, int slot, int teams, int rounds,
                             Set<Integer> forfeited, List<String> positions,
                             Map<String, Integer> minimums, Map<String, Integer> maximums,
                             Map<String, Integer> held) {
        this.board = board
        this.slot = slot
        this.teams = teams
        this.rounds = rounds
        this.forfeited = forfeited
        this.positions = positions
        this.minimums = minimums
        this.maximums = maximums
        this.held = new LinkedHashMap<>(held)
    }

    void print(PrintWriter out) {
        out.println(['ROUND', 'PICK', 'POS', 'HELD', 'STATUS', 'BESTRANK', 'VOR', 'NEXTPICK', 'NEXTRANK',
                     'NEXTVOR', 'DECAY', 'TAKE'].join('\t'))
        Map<String, Integer> roster = new LinkedHashMap<>(held)
        List<Integer> mine = (1..rounds).collect { SnakeDraft.overallPick(it, slot, teams) }
                .findAll { !forfeited.contains(it) }
        mine.eachWithIndex { int pick, int i ->
            Integer next = i + 1 < mine.size() ? mine[i + 1] : null
            List<List> rows = positions.collect { String position ->
                Integer rank = board.positionalRankAt(position, pick)
                BigDecimal value = board.positionalValueAt(position, pick)
                Integer nextRank = next ? board.positionalRankAt(position, next) : null
                BigDecimal nextValue = next ? board.positionalValueAt(position, next) : null
                BigDecimal decay = value != null && nextValue != null ? value - nextValue : null
                [position, rank, value, next, nextRank, nextValue, decay, status(position, roster)]
            }.sort { a, b ->
                // A position that cannot improve the lineup is not a candidate however it grades, so it
                // sorts below every one that can before decay is looked at.
                int byUse = usable(b[7] as String) <=> usable(a[7] as String)
                byUse ?: unknownLast(b[6] as BigDecimal) <=> unknownLast(a[6] as BigDecimal)
            }
            // HELD and STATUS both describe the roster the decision is made from, so they agree with each
            // other: two backs held against a minimum of two is FLEX, and reporting the count after the
            // pick made it read as NEED beside a number that already met the need.
            Map<String, Integer> before = new LinkedHashMap<>(roster)
            List take = rows.find { usable(it[7] as String) == 1 && it[2] != null }
            if (take) {
                roster[take[0] as String] = (roster[take[0] as String] ?: 0) + 1
            }
            rows.each { List row ->
                out.println([SnakeDraft.roundOf(pick, teams), pick, row[0],
                             before[row[0] as String] ?: 0, row[7],
                             row[1] ?: '', one(row[2] as BigDecimal), row[3] ?: '', row[4] ?: '',
                             one(row[5] as BigDecimal), one(row[6] as BigDecimal),
                             row.is(take) ? 'TAKE' : ''].join('\t'))
            }
        }
    }

    /**
     * What a further player at this position would do for the lineup, given what is already held.
     *
     * NEED is a starting slot still unfilled, FLEX one that can still take a better player up to the
     * position's cap, and FULL a position that cannot start another however good he is.
     */
    private String status(String position, Map<String, Integer> roster) {
        int have = roster[position] ?: 0
        if (have < (minimums[position] ?: 0)) {
            return 'NEED'
        }
        have < (maximums[position] ?: 0) ? 'FLEX' : 'FULL'
    }

    /** Whether a status can still improve a starting lineup, which is what makes it a candidate. */
    private static int usable(String status) { status == 'FULL' ? 0 : 1 }

    /** A decay for sorting, with "no next pick to compare against" ordered below every real answer. */
    private static BigDecimal unknownLast(BigDecimal decay) {
        decay == null ? new BigDecimal('-9999') : decay
    }

    private static String one(BigDecimal value) {
        value == null ? '' : value.setScale(1, RoundingMode.HALF_UP) as String
    }
}
