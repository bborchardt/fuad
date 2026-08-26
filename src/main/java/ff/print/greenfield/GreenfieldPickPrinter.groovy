package ff.print.greenfield

import ff.projection.greenfield.SnakeDraft

import java.math.RoundingMode

/**
 * What a pick has actually been worth, round by round and pick by pick.
 *
 * Measured from this league's own drafts rather than assumed from consensus order: at every pick of every
 * collected season, what the best player still on the board was worth. See {@code DraftHistory}.
 *
 * <b>Read it for where the value falls away, not for a single figure.</b> The steps between rounds are what
 * a trade or a keeper is priced against, and the flat stretch through the middle rounds is the finding worth
 * having — this league leaves a startable quarterback on the board into round eight most years, so an eighth
 * costs far more than its number suggests.
 *
 * The deep rounds are the softest part of this. A pick spent on a player no ranking carried cannot be
 * matched, so he is never taken off the board and the best available past him is overstated; that is rare
 * early and common late.
 */
class GreenfieldPickPrinter {

    private final Map<Integer, BigDecimal> byPick
    private final int teams

    GreenfieldPickPrinter(Map<Integer, BigDecimal> byPick, int teams) {
        this.byPick = byPick
        this.teams = teams
    }

    void print(PrintWriter out) {
        out.println(['ROUND', 'FIRSTPICK', 'LASTPICK', 'BESTFIRST', 'BESTLAST', 'DROP'].join('\t'))
        byPick.keySet().groupBy { SnakeDraft.roundOf(it, teams) }.sort { it.key }.each { int round, List<Integer> inRound ->
            List<Integer> picks = inRound.sort()
            BigDecimal first = byPick[picks.first()]
            BigDecimal last = byPick[picks.last()]
            out.println([round, picks.first(), picks.last(),
                         one(first), one(last), one(first - last)].join('\t'))
        }
    }

    private static String one(BigDecimal value) {
        value == null ? '' : value.setScale(1, RoundingMode.HALF_UP) as String
    }
}
