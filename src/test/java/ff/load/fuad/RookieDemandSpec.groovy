package ff.load.fuad

import spock.lang.Shared
import spock.lang.Specification

/**
 * When a rookie rank actually comes off the board, measured over the league's own nine drafts.
 *
 * The measurement is a median over few observations, so what can be asserted is its shape rather than its
 * cells: that it runs the right way, that it agrees with consensus at the top where the two should agree,
 * and that it declines to answer where the drafts have not been.
 *
 * Measured over the superflex drafts alone, which is the correction this class most needed. See
 * {@link RookieDemand#SEASONS}.
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

        expect: 'one draft of the four has ever made a fifty first pick'
        best.WR[51] == null
        best.WR[40] != null
    }

    def "is measured over the superflex drafts and no others"() {
        expect:
        RookieDemand.SEASONS == ['2022', '2023', '2024', '2025']
    }

    /**
     * The room adjusted to superflex, and pooling the drafts either side of it said the opposite.
     *
     * Across all nine drafts the best rookie quarterback appeared to sit on the board until pick 15, which
     * reads as a standing inefficiency worth exploiting. It was five pre-superflex drafts averaged into four
     * superflex ones: before 2022 he did last that long, and since 2022 he is gone by pick 8. Measured over
     * the era being drafted in, quarterbacks go faster than receivers, not slower.
     *
     * Asserted because the wrong version of it was in the documentation, in a figure, and in this spec, and
     * because it is the one number here that would change what a plan does at a pick.
     */
    def "the best rookie quarterback is gone by the middle of the first round"() {
        given:
        Map<String, Map<Integer, Integer>> best = demand.bestAvailableByPick()

        expect: 'still there in the first handful of picks'
        best.QB[5] == 1

        and: 'and gone by the eighth, where a pooled measurement had him lasting to fifteen'
        best.QB[10] > 1
        best.QB[15] > 3
    }
}
