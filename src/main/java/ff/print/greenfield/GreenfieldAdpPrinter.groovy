package ff.print.greenfield

import ff.load.greenfield.PositionDemand

/**
 * The pick each positional rank has typically gone at in this league.
 *
 * <b>Value says who to draft, this says when he will be gone.</b> A rank whose pick here is far later than
 * its worth on the board is one to wait on; one that goes earlier than it is worth has to be reached for or
 * done without.
 *
 * A rank the drafts have too little to say about is blank rather than guessed at. Ranks are pooled with the
 * two either side, since nine drafts of one rank are nine observations of nine different players and the
 * question is what the position does rather than what one man did.
 */
class GreenfieldAdpPrinter {

    private final PositionDemand demand
    private final List<String> positions

    GreenfieldAdpPrinter(PositionDemand demand, List<String> positions) {
        this.demand = demand
        this.positions = positions
    }

    void print(PrintWriter out) {
        Map<String, Map<Integer, Integer>> adp = demand.averageDraftPosition()
        out.println((['RANK'] + positions).join('\t'))
        int deepest = positions.collect { adp[it]?.keySet()?.max() ?: 0 }.max()
        (1..deepest).each { int rank ->
            List<String> row = positions.collect { (adp[it]?.get(rank) ?: '') as String }
            if (row.any { it }) {
                out.println(([rank] + row).join('\t'))
            }
        }
    }
}
