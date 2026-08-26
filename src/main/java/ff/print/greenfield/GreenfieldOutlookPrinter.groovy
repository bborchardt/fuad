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
 * Everything here is a median over nine drafts, not a promise. Half the time the rank named is already gone.
 */
class GreenfieldOutlookPrinter {

    private final GreenfieldBoard board
    private final int slot
    private final int teams
    private final int rounds
    private final Set<Integer> forfeited
    private final List<String> positions

    GreenfieldOutlookPrinter(GreenfieldBoard board, int slot, int teams, int rounds,
                             Set<Integer> forfeited, List<String> positions) {
        this.board = board
        this.slot = slot
        this.teams = teams
        this.rounds = rounds
        this.forfeited = forfeited
        this.positions = positions
    }

    void print(PrintWriter out) {
        out.println(['ROUND', 'PICK', 'POS', 'BESTRANK', 'VOR', 'NEXTPICK', 'NEXTRANK', 'NEXTVOR',
                     'DECAY'].join('\t'))
        List<Integer> mine = (1..rounds).collect { SnakeDraft.overallPick(it, slot, teams) }
                .findAll { !forfeited.contains(it) }
        mine.eachWithIndex { int pick, int i ->
            Integer next = i + 1 < mine.size() ? mine[i + 1] : null
            positions.collect { String position ->
                Integer rank = board.positionalRankAt(position, pick)
                BigDecimal value = board.positionalValueAt(position, pick)
                Integer nextRank = next ? board.positionalRankAt(position, next) : null
                BigDecimal nextValue = next ? board.positionalValueAt(position, next) : null
                BigDecimal decay = value != null && nextValue != null ? value - nextValue : null
                [position, rank, value, next, nextRank, nextValue, decay]
            }.sort { a, b ->
                // The position that cannot wait goes first, and one with nothing to say goes last.
                //
                // Explicitly against null rather than with an Elvis, because a decay of exactly zero is
                // falsy in Groovy and would sort as though it were missing. It is not missing: it says the
                // position does not move before this slot picks again, which is a finding. Decay can also be
                // negative, the curve not being perfectly monotone, and a negative sorted above a zero is
                // how that first showed up.
                unknownLast(b[6] as BigDecimal) <=> unknownLast(a[6] as BigDecimal)
            }.each { List row ->
                out.println([SnakeDraft.roundOf(pick, teams), pick, row[0],
                             row[1] ?: '', one(row[2] as BigDecimal), row[3] ?: '', row[4] ?: '',
                             one(row[5] as BigDecimal), one(row[6] as BigDecimal)].join('\t'))
            }
        }
    }

    /** A decay for sorting, with "no next pick to compare against" ordered below every real answer. */
    private static BigDecimal unknownLast(BigDecimal decay) {
        decay == null ? new BigDecimal('-9999') : decay
    }

    private static String one(BigDecimal value) {
        value == null ? '' : value.setScale(1, RoundingMode.HALF_UP) as String
    }
}
