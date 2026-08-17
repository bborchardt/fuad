package ff.projection

import ff.data.PlayerValuation
import spock.lang.Specification

/**
 * The tags a board reports have to be the tags it was priced with.
 *
 * Tagging is iterated because it is circular: who gets tagged depends on prices, and prices depend on who
 * is tagged. Usually the set stops changing after a round or two. Sometimes it never does — two expiring
 * players on one roster can each be the better tag once the other is tagged, because taking one out of the
 * bidding puts his tag price back in the pot and lifts what the other would fetch.
 *
 * When that happens the loop runs out of rounds part way round the cycle, and the danger is reporting the
 * round it did not price. That put a team's tag on one player while every price on the board — his own
 * included — assumed it had tagged another, which is not a board a plan can be held to.
 *
 * So the contract is internal consistency rather than convergence: whatever set the loop stops on, the
 * salaries, the availabilities and the flags all describe that one set.
 */
class FranchiseTagSettlementSpec extends Specification {

    private static final int LAST_WEEK = 14

    /** Two positions levelled identically, so nothing but the tag price separates the two held players. */
    private static PointsCurve curve() {
        Map<Integer, List<BigDecimal>> shape = (1..8).collectEntries { int rank ->
            BigDecimal expected = (220 - rank * 10) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5] * 3]
        }
        PointsCurve.of([QB: TestSeasons.byRank(shape), WR: TestSeasons.byRank(shape)])
    }

    /**
     * One franchise holding the best quarterback and the best receiver.
     *
     * The pool is deliberately tiny. A held player has to be a large enough share of the bidding, and his
     * tag a large enough share of the pot, that removing him moves what the other one would fetch — which
     * is the whole mechanism the cycle runs on.
     */
    private static Map<String, List> pool() {
        (1..4).collectMany { int rank ->
            [["q$rank" as String, ["QB $rank" as String, 'QB', rank, rank == 1 ? 'F' : null]],
             ["w$rank" as String, ["WR $rank" as String, 'WR', rank, rank == 1 ? 'F' : null]]]
        }.collectEntries { [(it[0]): it[1]] }
    }

    /** Prices the unsettling fixture, handing back the board and whatever it wrote to standard error. */
    private static List run() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        PrintStream original = System.err
        System.err = new PrintStream(captured)
        try {
            List<PlayerValuation> board = AuctionValuation.value(curve(),
                    new StarterRequirements([QB: 1, WR: 1], [QB: 2, WR: 2], 3, 4),
                    pool(), [QB: 3, WR: 6], 80.0, 4, new ByeWeeks([:], LAST_WEEK))
            [board, captured.toString()]
        } finally {
            System.err = original
        }
    }

    def "prices and flags describe one set of tags, even when the tags never settle"() {
        given:
        List<PlayerValuation> board = run()[0] as List<PlayerValuation>

        expect: 'a tagged player is charged his tag, and an untagged one what the market settles at'
        board.every { PlayerValuation player ->
            player.franchiseTagged ? player.salary == player.franchiseSalary
                    : player.salary == player.marketSalary
        }

        and: 'and the chance of prising him away is the tagged one exactly when the flag says he is tagged'
        board.every { PlayerValuation player ->
            (player.availability == AuctionValuation.TAGGED_AVAILABILITY) == player.franchiseTagged
        }
    }

    def "says so when the tags do not settle, rather than letting a half-turn read as an answer"() {
        given:
        String warnings = run()[1] as String

        expect: 'the fixture is one that cycles, and the model admits it'
        warnings.contains('did not settle')

        and: 'naming the players it cannot choose between, so a reader knows which team is undecided'
        warnings.contains('QB 1') || warnings.contains('WR 1')
    }

    def "settles silently when there is nothing to cycle between"() {
        given: 'the same league, with the franchise holding only one expiring player'
        Map<String, List> single = pool().findAll { it.key != 'w1' }

        when:
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        PrintStream original = System.err
        System.err = new PrintStream(captured)
        List<PlayerValuation> board
        try {
            board = AuctionValuation.value(curve(),
                    new StarterRequirements([QB: 1, WR: 1], [QB: 2, WR: 2], 3, 4),
                    single, [QB: 3, WR: 6], 80.0, 4, new ByeWeeks([:], LAST_WEEK))
        } finally {
            System.err = original
        }

        then: 'no warning, and the same consistency holds'
        !captured.toString().contains('did not settle')
        board.every { PlayerValuation player ->
            player.franchiseTagged ? player.salary == player.franchiseSalary
                    : player.salary == player.marketSalary
        }
    }
}
