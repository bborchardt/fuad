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
 * is tagged. The contract is internal consistency rather than convergence: whatever set the loop stops on,
 * the salaries, the availabilities and the flags all describe that one set. Reporting a set it did not
 * price put a team's tag on one player while every price on the board — his own included — assumed it had
 * tagged another, which is not a board a plan can be held to.
 *
 * <b>It used not to converge, and the reason was a comparison across two worlds rather than anything about
 * the football.</b> Two expiring players on one roster could each be the better tag once the other was
 * tagged: the tagged one was priced in the world where his tag is lifted, his team-mate in the world where
 * it still stands, so whichever was tagged was measured against the larger pool and the fuller pot and came
 * off worse for it. Tagging one flipped the saving to the other, for ever. Pricing every one of a team's
 * candidates in that team's own no-tag world takes the asymmetry out — see
 * {@link AuctionPricingSpec}, which pins each of them to that world to the dollar — and the fixture below,
 * built to cycle and kept unchanged for it, now settles.
 *
 * The bounded loop and its warning stay all the same. Neither this nor the roster spots the counterfactual
 * counts is a proof that no board cycles: prices are whole dollars, and a dollar of truncation is not
 * something the argument above rules out.
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
     * is what the cycle used to run on, and so is what a fixture asserting the cycle is gone has to keep.
     */
    private static Map<String, List> pool() {
        (1..4).collectMany { int rank ->
            [["q$rank" as String, ["QB $rank" as String, 'QB', rank, rank == 1 ? 'F' : null]],
             ["w$rank" as String, ["WR $rank" as String, 'WR', rank, rank == 1 ? 'F' : null]]]
        }.collectEntries { [(it[0]): it[1]] }
    }

    /** Prices the fixture, handing back the board and whatever it wrote to standard error. */
    private static List run() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        PrintStream original = System.err
        System.err = new PrintStream(captured)
        try {
            List<PlayerValuation> board = AuctionValuation.value(curve(),
                    new StarterRequirements([QB: 1, WR: 1], [QB: 2, WR: 2], 3, 4),
                    pool(), [QB: 3, WR: 7], 80.0, 4, new ByeWeeks([:], LAST_WEEK))
            [board, captured.toString()]
        } finally {
            System.err = original
        }
    }

    def "prices and flags describe one set of tags"() {
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

    /**
     * The board that used to flip for ever between one team's two players.
     *
     * What makes it interesting is unchanged: one franchise holding two players each big enough that taking
     * either out of the bidding moves what the other would fetch. That is still true, and it no longer
     * decides anything, because both are now read off the same rate.
     *
     * The receiver's tag price moved by a dollar when {@link AuctionValuation#PRICE_STEEPNESS} was refitted
     * from the record. The board it makes is the same board — two candidates on one roster, one of them
     * tagged, the other dearer to buy than to tag — and the dollar is what keeps that last gap open rather
     * than exactly closed, which is the thing the choice used to turn on.
     */
    def "the two players a team is choosing between no longer flip it round for ever"() {
        given:
        List result = run()
        List<PlayerValuation> board = result[0] as List<PlayerValuation>
        String warnings = result[1] as String

        expect: 'it settles, where it used to run out of rounds mid-cycle'
        !warnings.contains('did not settle')

        and: 'on one tag for the one franchise that holds anybody'
        board.count { it.franchiseTagged } == 1
        board.find { it.franchiseTagged }.franchiseId == 'F'
    }

    /**
     * And the saving that decided it was read on one basis, which is what stopped the flipping.
     *
     * This is the fixture stated in numbers. Both of the franchise's players are worth 5 to tag once both
     * are read in the world where it tags nobody, so the choice between them is a tie the model settles on
     * worth. Read across two worlds it was not a tie at all: the untagged quarterback was priced at 9
     * rather than 8, because his rival's tag was still standing and had taken a share out of the bidding
     * and a price out of the pot. That extra dollar made him look the better tag, and tagging him handed
     * the same extra dollar to the receiver, for ever.
     *
     * Asserted against the board priced with the tag out of reach, since this franchise holds everybody
     * expiring and so its no-tag world is exactly that board.
     */
    def "a team's two candidates are compared on one world's money"() {
        given:
        List<PlayerValuation> board = run()[0] as List<PlayerValuation>
        List<PlayerValuation> held = board.findAll { it.franchiseId == 'F' }

        and: 'the same league with a tag nobody would use, which is that franchise tagging nobody'
        List<PlayerValuation> none = AuctionValuation.value(curve(),
                new StarterRequirements([QB: 1, WR: 1], [QB: 2, WR: 2], 3, 4),
                pool(), [QB: 100000, WR: 100000], 80.0, 4, new ByeWeeks([:], LAST_WEEK))

        expect: 'the franchise really is choosing between two players, and tagged one of them'
        held.size() == 2
        held.count { it.franchiseTagged } == 1

        and: 'both are measured in the world where it tagged neither, to the dollar'
        held.every { PlayerValuation player ->
            player.untaggedSalary == none.find { it.playerId == player.playerId }.marketSalary
        }

        and: 'and the tag went to a saving no smaller than the other, ties going to worth'
        held.every { it.tagSurplus <= held.find { it.franchiseTagged }.tagSurplus }

        and: 'while the one still bid on is dearer to buy than to tag, the gap that used to flip the choice'
        held.find { !it.franchiseTagged }.marketSalary > held.find { !it.franchiseTagged }.untaggedSalary
    }

    /**
     * The slowest cascade a league this size was found to produce, well inside the budget.
     *
     * The companion to the board below, and the reason that one has to be forty teams. A budget at or under
     * the longest run that would have settled turns the warning into a sentence meaning either "this cycles"
     * or "this was merely slow", so what matters is the headroom, and the headroom is what drifts silently.
     *
     * This is the slowest of six thousand synthetic ten-franchise boards: eighty expiring contracts spread
     * round the teams, nine of which end up tagging, settling in five rounds against a budget of ten. Ten
     * teams do not take ten rounds because the queue advances in blocks rather than a team at a time.
     */
    def "a board the size of this league settles well inside the rounds it is given"() {
        given: 'ten franchises holding eight expiring players each, dealt round the board'
        Map<String, List> pool = (1..115).collectEntries { int rank ->
            [("q$rank" as String): ["QB $rank" as String, 'QB', rank,
                                    rank <= 80 ? "f${(rank - 1) % 10 + 1}" as String : null]]
        }
        Map<Integer, List<BigDecimal>> shape = (1..115).collectEntries { int rank ->
            BigDecimal expected = (379 - rank * 8) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5] * 3]
        }

        when:
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        PrintStream original = System.err
        System.err = new PrintStream(captured)
        List<PlayerValuation> board
        try {
            board = AuctionValuation.value(PointsCurve.of([QB: TestSeasons.byRank(shape)]),
                    new StarterRequirements([QB: 2], [QB: 2], 2, 10), pool, [QB: 5], 204.0, 76,
                    new ByeWeeks([:], LAST_WEEK))
        } finally {
            System.err = original
        }

        then: 'it settles, with rounds to spare'
        !captured.toString().contains('did not settle')

        and: 'having actually made a cascade of it, or the fixture is not the one this is about'
        // Nearly every team, rather than a count to the team: a tag price a dollar either side of this one
        // takes the cascade from all ten to four, so pinning the number would be pinning the constant that
        // moved it. What the fixture has to produce is a long cascade, and that is what is asserted.
        board.findAll { it.franchiseTagged }.collect { it.franchiseId }.toSet().size() >= 8
    }

    /**
     * A cascade of teams tagging one after another, longer than the loop has rounds for.
     *
     * This is what the warning is left guarding, now that a team's candidates are all read off one rate.
     * Tagging is self-reinforcing: a tag takes a player's share out of the bidding and returns less to the
     * pot than his share was earning, so every tag lifts the rate for everyone still bidding, and lifting
     * the rate pulls the next team over the line. Where the teams are finely enough separated they come in
     * one at a time, and the set is still growing when the rounds run out.
     *
     * <b>It settles eventually — it is a queue, not a cycle.</b> Given more rounds this board reaches a
     * fixed point at fourteen, against a budget of ten. What the model must not do is present round ten as
     * an answer, and forty teams is what it takes to build a queue that long; the 2026 board settles in
     * three. Found by search over synthetic boards rather than constructed, which is why the numbers are
     * arbitrary.
     */
    def "says so when the cascade outruns the rounds, rather than letting a half-turn read as an answer"() {
        given: 'forty teams, one expiring player each, and a tag cheap enough to be worth using widely'
        Map<String, List> pool = (1..40).collectEntries { int rank ->
            [("q$rank" as String): ["QB $rank" as String, 'QB', rank, "f$rank" as String]]
        }
        Map<Integer, List<BigDecimal>> shape = (1..40).collectEntries { int rank ->
            BigDecimal expected = (440 - rank * 8) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5] * 3]
        }

        when:
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        PrintStream original = System.err
        System.err = new PrintStream(captured)
        List<PlayerValuation> board
        try {
            // The one call here that names its settings. What is under test is the cascade's reporting
            // when the rounds run out, which is the same loop whatever shapes the prices — but the board
            // that produces a fourteen-deep queue was found by search under the VOR endpoint, and the
            // same forty teams settle in three under the production signal. Pinning keeps the fixture the
            // thing it was searched for; the rest of this spec runs the shipped path.
            board = AuctionValuation.value(PointsCurve.of([QB: TestSeasons.byRank(shape)]),
                    new StarterRequirements([QB: 2], [QB: 2], 2, 12), pool, [QB: 2], 120.0, 60,
                    new ByeWeeks([:], LAST_WEEK), AuctionValuation.DEFAULT_SETTINGS)
        } finally {
            System.err = original
        }

        then: 'the rounds ran out, and the model says so rather than reporting the round it stopped on'
        captured.toString().contains('did not settle')

        and: 'naming the team it was still deciding about, so a reader knows which one is unfinished'
        captured.toString() ==~ /(?s).*QB \d+ \(f\d+\).*/

        and: 'the cascade really had got a long way, or the fixture is not the one this is about'
        board.count { it.franchiseTagged } > 20

        and: 'and the board it hands back still describes one set of tags throughout'
        board.every { PlayerValuation player ->
            player.franchiseTagged ? player.salary == player.franchiseSalary
                    : player.salary == player.marketSalary
        }
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

    /**
     * A held player whose market price, tag price and worth are all stated outright.
     *
     * The tag price is measured against {@code untaggedSalary}, so the fixture states both. They agree
     * here because no other tag is standing to separate them, which is the case the settlement rule is
     * being asserted on.
     */
    private static PlayerValuation tie(String id, String franchise, int market, int tagPrice, int worth) {
        new PlayerValuation(playerId: id, playerName: id, position: 'QB', positionRank: 1,
                marketSalary: market, untaggedSalary: market, franchiseSalary: tagPrice, value: worth,
                salary: market,
                acquisitionSalary: market, franchiseId: franchise, points: 0.0, pointsPerGame: 0.0,
                expectedGames: 0.0, pointsLow: 0.0, pointsHigh: 0.0, valueOverReplacement: 0.0,
                availability: 1.0)
    }
}
