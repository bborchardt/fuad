package ff.projection.fuad

import ff.data.PlayerValuation
import spock.lang.Specification
import ff.projection.ByeWeeks
import ff.projection.PointsCurve
import ff.projection.StarterRequirements
import ff.projection.TestSeasons

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

    /**
     * A team saving the same on two players tags the more valuable of them.
     *
     * <b>This is what the loop turns on, not merely a tidier answer.</b> Surpluses are whole dollars off
     * levels carrying a standard error of seven points, so ties happen — and while the winner of one fell
     * out of the order the pool happened to iterate in, the loop could flip between them forever. Choosing
     * the same way every time is what lets it settle.
     *
     * Asserted directly on {@link AuctionValuation#predictTags}, since a board built to produce an exact
     * tie and nothing else would be a fixture arranged to have one answer.
     */
    def "a team saving the same on two players tags the one worth more"() {
        given: 'one franchise, two expiring players, an identical saving and different worth'
        List<PlayerValuation> board = [
                tie('cheap', 'f1', 40, 20, 55),
                tie('dear', 'f1', 40, 20, 70),
        ]

        expect:
        AuctionValuation.predictTags(board) == ['dear'] as Set

        and: 'and the order they arrive in decides nothing'
        AuctionValuation.predictTags(board.reverse()) == ['dear'] as Set
    }

    def "a larger saving still wins, value breaking ties and never overriding one"() {
        given: 'the less valuable player saves a dollar more'
        List<PlayerValuation> board = [
                tie('saves-more', 'f1', 41, 20, 55),
                tie('worth-more', 'f1', 40, 20, 70),
        ]

        expect: 'the tag is what it always was, on the bigger saving'
        AuctionValuation.predictTags(board) == ['saves-more'] as Set
    }

    def "a tie on saving and on worth alike still predicts one tag, and the same one twice"() {
        given:
        List<PlayerValuation> board = [tie('a', 'f1', 40, 20, 60), tie('b', 'f1', 40, 20, 60)]

        expect: 'arbitrary, but fixed: the same board cannot predict two different tags on two runs'
        AuctionValuation.predictTags(board).size() == 1
        AuctionValuation.predictTags(board) == AuctionValuation.predictTags(board.reverse())
    }

    /** A held player whose market price, tag price and worth are all stated outright. */
    private static PlayerValuation tie(String id, String franchise, int market, int tagPrice, int worth) {
        new PlayerValuation(playerId: id, playerName: id, position: 'QB', positionRank: 1,
                marketSalary: market, franchiseSalary: tagPrice, value: worth, salary: market,
                acquisitionSalary: market, franchiseId: franchise, points: 0.0, pointsPerGame: 0.0,
                expectedGames: 0.0, pointsLow: 0.0, pointsHigh: 0.0, valueOverReplacement: 0.0,
                availability: 1.0)
    }
}
