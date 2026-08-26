package ff.print.greenfield

import ff.load.greenfield.PositionDemand

/**
 * How many players at each position have gone by the end of each round, against how many the league starts.
 *
 * <b>The column to read against is STARTED.</b> Once a position's count passes it, everyone drafting after
 * is choosing from below the replacement the whole board is priced against — so this is the sheet that says
 * when a position has to be taken rather than which player is worth taking.
 *
 * {@code UNRANKED} is picks that matched no ranked player. It is mostly team defences, which the two sources
 * name differently and this project does not price, plus the deep fliers no ranking carried. Reported rather
 * than hidden, so that a reader adding up a round finds it adds up.
 */
class GreenfieldDemandPrinter {

    private final PositionDemand demand
    private final Map<String, Integer> starters

    GreenfieldDemandPrinter(PositionDemand demand, Map<String, Integer> starters) {
        this.demand = demand
        this.starters = starters
    }

    void print(PrintWriter out) {
        List<String> positions = demand.positions()
        out.println((['ROUND'] + positions).join('\t'))
        Map<Integer, Map<String, Integer>> taken = demand.takenByRound()
        taken.keySet().sort().each { int round ->
            out.println(([round] + positions.collect { taken[round][it] ?: 0 }).join('\t'))
        }
        // The line every other line is read against, on the sheet rather than in a reader's head.
        out.println((['STARTED'] + positions.collect { starters[it] ?: '' }).join('\t'))
    }
}
