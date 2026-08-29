package ff.load.fuad

import spock.lang.Shared
import spock.lang.Specification

/**
 * When a rookie rank actually comes off the board, measured over the league's own nine drafts.
 *
 * The measurement is a median over few observations, so what can be asserted is its shape rather than its
 * cells: that it runs the right way, that it agrees with consensus at the top where the two should agree,
 * and that it declines to answer where the drafts have not been.
 */
class RookieDemandSpec extends Specification {

    @Shared
    RookieDemand demand = new RookieDemand()

    def "the best rookies go first, and later ranks go later"() {
        given:
        Map<Integer, Integer> byRank = demand.expectedPickByRank()

        expect: 'the consensus first rookie goes in the first handful of picks'
        byRank[1] <= 5

        and: 'and the order is broadly the order, over ranks far enough apart to be distinguishable'
        byRank[5] < byRank[15]
        byRank[15] < byRank[30]
    }

    /**
     * The board empties as the draft runs, at every position and never backwards.
     *
     * This is the property a plan across picks depends on: if the best receiver left at pick 20 were better
     * than the best at pick 10, waiting would be free and there would be nothing to plan.
     */
    def "the best available at a position never improves as the draft runs"() {
        given:
        Map<String, Map<Integer, Integer>> best = demand.bestAvailableByPick()

        expect:
        ['QB', 'RB', 'WR', 'TE'].every { String position ->
            List<Integer> byPick = best[position].sort { it.key }.values().toList()
            (1..<byPick.size()).every { byPick[it] >= byPick[it - 1] }
        }
    }

    def "answers nothing for a pick too few drafts have reached"() {
        given:
        Map<String, Map<Integer, Integer>> best = demand.bestAvailableByPick()

        expect: 'one draft in nine has ever made a fifty first pick'
        best.WR[51] == null
        best.WR[40] != null
    }

    /**
     * Quarterbacks last, which is the standing anomaly in this league's rookie drafting.
     *
     * The lineup has started two quarterbacks since 2022 and the rookie board still leaves the best one on
     * the table into the second round. It is reported rather than corrected: the model says what the room
     * does, and what to do about it is the plan's business.
     */
    def "the best rookie quarterback survives longer than the best rookie at any other position"() {
        given:
        Map<String, Map<Integer, Integer>> best = demand.bestAvailableByPick()

        expect:
        best.QB[10] == 1
        best.RB[10] > 1
        best.WR[10] > 1
    }
}
