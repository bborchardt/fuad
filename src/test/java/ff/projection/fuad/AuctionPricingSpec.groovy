package ff.projection.fuad

import ff.data.PlayerValuation
import spock.lang.Specification
import spock.lang.Unroll
import ff.projection.ByeWeeks
import ff.projection.PointsCurve
import ff.projection.StarterRequirements
import ff.projection.TestSeasons

/**
 * What the chain from points to dollars promises, asserted on boards small enough to check by hand.
 *
 * {@link AuctionValuationSpec} checks that the constants dividing the money are the measurements they claim
 * to be. This checks what happens to the money once they have divided it, which had nothing on it at all:
 * the positional totals surviving the steepening, the prices summing to the pot, the world a tag decision
 * is measured in, and the two columns the right of first refusal produces.
 *
 * Every board here is synthetic. These are properties the arithmetic has to hold for any curve, so pinning
 * them to a season's figures would only be the drift problem with a spec around it — see
 * {@link ff.print.figures.fuad.ModelFiguresPrinterSpec}.
 */
class AuctionPricingSpec extends Specification {

    private static final ByeWeeks NO_BYES = new ByeWeeks([:], 14)

    /** A position declining smoothly with rank, with enough scatter that outcomes exist to average over. */
    private static Map<Integer, List<BigDecimal>> declining(int depth) {
        (1..depth).collectEntries { int rank ->
            BigDecimal expected = (300 - rank * 4) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5] * 3]
        }
    }

    private static PointsCurve curveFor(List<String> positions) {
        PointsCurve.of(positions.collectEntries { [(it): TestSeasons.byRank(declining(50))] })
    }

    /** Ranks 1..depth at each position, held by the franchise the closure names, or by nobody. */
    private static Map<String, List> poolOf(List<String> positions, int depth,
                                            Closure<String> holder = { int rank -> null }) {
        positions.collectMany { String position ->
            (1..depth).collect { int rank ->
                ["${position}${rank}".toString(), ["$position $rank".toString(), position, rank,
                                                   holder(rank)]]
            }
        }.collectEntries { it }
    }

    private static PlayerValuation find(List<PlayerValuation> board, String id) {
        board.find { it.playerId == id }
    }

    private static BigDecimal moneyAt(List<PlayerValuation> board, String position) {
        (board.findAll { it.position == position }.collect { it.marketSalary }.sum() ?: 0) as BigDecimal
    }

    /**
     * The pot the auction actually divides, which is not the free cap.
     *
     * Teams spend a share of what they have, and the rookie draft's contracts are committed before any
     * bidding, so both come off before a price is set. Recomputed here from the same constants rather than
     * taken from a number typed in, since the point of the assertions below is the arithmetic between them.
     */
    private static BigDecimal potOf(BigDecimal freeCap) {
        freeCap * AuctionValuation.SPEND_RATE * (1.0 - AuctionValuation.ROOKIE_BUDGET_SHARE)
    }

    /**
     * Steepening moves money inside a position and never between them.
     *
     * The documentation leans on this hard: the calibration hits each position's target share exactly, and
     * bending the curve to how steeply the league bids within a position is said to leave that total alone.
     * If it did not, {@code PRICE_STEEPNESS} would be a second, silent calibration — a description of
     * behaviour inside a position quietly deciding how much of the pot the position gets.
     *
     * Asserted on two positions given <b>the same curve</b>, so every difference between them is the
     * steepness, quarterback being much the steeper of the two — the fitted figures are GAMMA on
     * docs/figures/fuad/&lt;year&gt;/positions.tsv. Their money then has to divide as their target shares do and
     * in no other proportion.
     */
    def "each position keeps the share the calibration gave it, however steeply it is bid"() {
        given: 'two positions with identical curves and identical pools'
        List<PlayerValuation> board = AuctionValuation.value(curveFor(['QB', 'WR']),
                new StarterRequirements([QB: 2, WR: 2], [QB: 2, WR: 2], 4, 10),
                poolOf(['QB', 'WR'], 40), [QB: 500, WR: 500], 10000.0, 80, NO_BYES)

        when:
        BigDecimal total = moneyAt(board, 'QB') + moneyAt(board, 'WR')
        BigDecimal targets = AuctionValuation.MARKET_SHARE.QB + AuctionValuation.MARKET_SHARE.WR

        then: 'the split is the ratio of their targets, not of their steepnesses'
        (moneyAt(board, 'QB') / total - AuctionValuation.MARKET_SHARE.QB / targets).abs() < 0.005
        (moneyAt(board, 'WR') / total - AuctionValuation.MARKET_SHARE.WR / targets).abs() < 0.005

        and: 'while inside each position the steeper one really is steeper, so this had something to catch'
        topShare(board, 'QB') > topShare(board, 'WR') + 0.02
    }

    /** What the best five at a position hold of that position's money, which is what steepness moves. */
    private static BigDecimal topShare(List<PlayerValuation> board, String position) {
        List<Integer> prices = board.findAll { it.position == position }
                .collect { it.marketSalary }.sort().reverse()
        (prices.take(5).sum() as BigDecimal) / (prices.sum() as BigDecimal)
    }

    /**
     * Prices sum to the money that exists, which is the thing no curve fitted player by player will do.
     *
     * Exactly, and the exact statement is worth having rather than a tolerance. Every price is
     * {@code 1 + rate * share} truncated to a whole dollar, the rate being what is left of the pot once
     * every roster spot still to be filled has been reserved its minimum bid. So the money on the board is
     * the pot, less a dollar for each slot, plus a dollar back for each player who is actually on it, less
     * whatever the truncation shaves — under a dollar a player.
     *
     * Which makes the shortfall two named things and not slack: the reserve held for slots no listed player
     * fills, and rounding. A board listing as many players as there are slots to fill therefore comes back
     * within a dollar a player of the whole pot.
     *
     * <b>The last row is what separates a reserve per slot from a reserve per player.</b> They are the same
     * arithmetic whenever the two counts agree, so a board of forty against ninety-two slots is the case
     * that can tell them apart — and it has to be told, since charging every name on the board a dollar
     * rather than every spot that gets filled would take hundreds off a real pot for players nobody signs.
     */
    @Unroll
    def "prices sum to the pot, short only the reserve for #slots slots and the rounding"() {
        given: 'nobody holds anybody, so no tag takes money out of the bidding'
        List<PlayerValuation> board = AuctionValuation.value(curveFor(['QB', 'WR']),
                new StarterRequirements([QB: 2, WR: 2], [QB: 2, WR: 2], 4, 10),
                poolOf(['QB', 'WR'], depth), [QB: 500, WR: 500], freeCap, slots, NO_BYES)

        when:
        BigDecimal pot = potOf(freeCap)
        BigDecimal spent = board.collect { it.marketSalary }.sum() as BigDecimal

        then: 'every dollar of the pot is on the board bar the reserve and at most a dollar a player'
        board.size() == depth * 2
        spent > pot - slots
        spent <= pot - slots + board.size()

        where:
        freeCap | depth | slots
        10000.0 | 40    | 80     // as many slots as players: the board holds essentially the whole pot
        2438.0  | 40    | 92     // twelve reserved dollars sit on no listed player
        2438.0  | 20    | 92     // and here fifty-two do, which per-player reserving could not produce
    }

    /**
     * A tagged player's market price is what he would have fetched had his own team not tagged him.
     *
     * It has to be a counterfactual, because his actual cost is the tag price and comparing that against
     * itself makes every tag look pointless — which is what sends the settlement loop round forever rather
     * than to a fixed point. So the price is taken with him back in the bidding and with the money his team
     * would have spent tagging him back in the pot.
     *
     * Asserted by building the same board twice, once where the tag is worth using and once where it is
     * priced out of reach so nobody uses one. With a single tag in play the two have to agree exactly: that
     * is the whole content of the claim. Pricing him at the rate the untagged are charged, against a pot
     * his tag had already left, is what used to overstate the top of the board by a quarter.
     */
    def "a tagged player is priced at what the auction would have paid for him"() {
        given: 'one team holds the best expiring player and everyone else is unrostered'
        Map<String, List> pool = poolOf(['QB'], 40) { int rank -> rank == 1 ? 'f1' : null }
        def boardWith = { int tagPrice ->
            AuctionValuation.value(curveFor(['QB']), new StarterRequirements([QB: 2], [QB: 2], 2, 10),
                    pool, [QB: tagPrice], 2438.0, 60, NO_BYES)
        }

        when: 'a tag worth using, against a tag nobody would ever use'
        List<PlayerValuation> tagged = boardWith(40)
        List<PlayerValuation> untagged = boardWith(100000)

        then: 'the tag was used, and only the once'
        find(tagged, 'QB1').franchiseTagged
        tagged.count { it.franchiseTagged } == 1
        !untagged.any { it.franchiseTagged }

        and: 'and his market price is the price of the board where nobody tagged him, to the dollar'
        find(tagged, 'QB1').marketSalary == find(untagged, 'QB1').marketSalary

        and: 'while what his team pays is the tag, which is the saving the tag exists to make'
        find(tagged, 'QB1').salary == 40
        find(tagged, 'QB1').marketSalary > 40
        find(tagged, 'QB1').tagSurplus == find(tagged, 'QB1').marketSalary - 40
    }

    /**
     * Everything a team's tag decision compares comes from one world.
     *
     * A tag is a choice between two boards: the one where the team uses it, and the one where it uses none
     * and the player is back in the bidding with his tag price back in the pot. What a tag saves is the
     * difference between them, so the market half of it has to be read on the second — and so does every
     * rival candidate on the same roster, or the team is not comparing like with like.
     *
     * Priced player by player it was not. The player actually tagged was measured in the world where his
     * tag is lifted while his own team-mates were measured in the world where it still stands, against a
     * smaller pool and a smaller pot. That discounts the incumbent and inflates the challenger every time,
     * which is a bias rather than noise, and it is the mechanism the settlement loop used to cycle on: see
     * {@link FranchiseTagSettlementSpec}.
     *
     * <b>One team holds the whole board here, so the world where that team tags nobody is exactly the board
     * priced with the tag out of reach</b> — which makes the claim checkable to the dollar rather than by
     * argument.
     */
    def "every one of a team's expiring players is measured in the world where it tags nobody"() {
        given: 'one team holding the board, priced once with a tag worth using and once with none'
        Map<String, List> pool = poolOf(['QB'], 30) { int rank -> 'f1' }
        def boardWith = { int tagPrice ->
            AuctionValuation.value(curveFor(['QB']), new StarterRequirements([QB: 2], [QB: 2], 2, 10),
                    pool, [QB: tagPrice], 2438.0, 60, NO_BYES)
        }

        when:
        List<PlayerValuation> tagged = boardWith(40)
        List<PlayerValuation> none = boardWith(100000)

        then: 'one tag was used, so there is one world for the whole roster to be measured in'
        tagged.count { it.franchiseTagged } == 1
        !none.any { it.franchiseTagged }

        and: 'and every player the team holds is measured there, the tagged one and his rivals alike'
        tagged.every { PlayerValuation player ->
            player.untaggedSalary == find(none, player.playerId).marketSalary
        }

        and: 'while what the auction pays for the ones it can still bid on is the board rate, which is higher'
        tagged.findAll { !it.franchiseTagged }.every { it.marketSalary >= it.untaggedSalary }
        tagged.any { !it.franchiseTagged && it.marketSalary > it.untaggedSalary }
    }

    /**
     * A player nobody holds has no tag to lift, so the two prices are one price.
     *
     * Worth stating outright because {@code untaggedSalary} is the basis of a saving that only a holder can
     * make, and a board of free agents must not quietly carry a second number that differs from the first.
     */
    def "a player nobody holds is priced once, on the board's own rate"() {
        given: 'one team holds the best player, and nobody holds the rest'
        List<PlayerValuation> board = AuctionValuation.value(curveFor(['QB']),
                new StarterRequirements([QB: 2], [QB: 2], 2, 10),
                poolOf(['QB'], 40) { int rank -> rank == 1 ? 'f1' : null },
                [QB: 40], 2438.0, 60, NO_BYES)

        expect: 'a tag really was used, or there was no second world for anyone to be measured in'
        board.count { it.franchiseTagged } == 1

        and: 'and the unheld are on one price, however the tagged one is measured'
        board.findAll { !it.franchiseId }.every { it.untaggedSalary == it.marketSalary }
    }

    /**
     * The world a tag is measured in has to be a board the model could itself have priced.
     *
     * Its roster spots are counted by the same rule as the board's own rather than by adding one to it. The
     * two agree wherever spots outnumber tags and part company exactly where they do not: the reserve is
     * floored at one spot, so a board with more tags than spots left has one either way, and adding one
     * invents a spot that world does not have, reserves a dollar against it and reports the saving a dollar
     * short. A dollar is enough to flip a team sitting on the margin, and flip it back next round.
     *
     * The board below is that case and nothing else — every player held by his own team, and four spots to
     * fill between the six teams that want to tag. Counting the world's spots by adding one instead, this
     * board never settles inside the loop's rounds; boards of the same kind traced out past them are found
     * to cycle rather than to be converging slowly, one team flipping in and out without end.
     */
    def "a board with more tags than roster spots left still settles"() {
        given:
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        PrintStream original = System.err
        System.err = new PrintStream(captured)

        when: 'every player held by his own team, and fewer spots to fill than teams that would tag'
        List<PlayerValuation> board
        try {
            board = AuctionValuation.value(curveFor(['QB']),
                    new StarterRequirements([QB: 2], [QB: 2], 2, 10),
                    poolOf(['QB'], 25) { int rank -> "f$rank".toString() },
                    [QB: 6], 120.0, 4, NO_BYES)
        } finally {
            System.err = original
        }

        then: 'more tags than spots, or the floor is not in play and this fixture is not the one'
        board.count { it.franchiseTagged } > 4

        and: 'and the tags settle rather than flipping a marginal team in and out for ever'
        !captured.toString().contains('did not settle')
    }

    /**
     * How often a player of this rank reaches another team at all, which is a fact about the rule and not
     * about him.
     *
     * An expiring contract is restricted, so the friction the right of first refusal creates shows up as
     * stickiness rather than as price — and it lands almost entirely on the players worth wanting. The
     * bands are read at their own boundaries here, since an off-by-one in a lookup table of four rows is
     * both easy to write and invisible on a board.
     */
    @Unroll
    def "a held player at rank #rank reaches another team #availability of the time"() {
        given:
        List<PlayerValuation> board = AuctionValuation.value(curveFor(['QB']),
                new StarterRequirements([QB: 2], [QB: 2], 2, 10),
                poolOf(['QB'], 45) { int r -> "f$r".toString() },
                [QB: 100000], 2438.0, 60, NO_BYES)

        expect:
        find(board, "QB$rank").availability == availability

        where:
        rank | availability
        1    | 0.30
        12   | 0.30   // the band is inclusive at its own rank
        13   | 0.47
        24   | 0.47
        25   | 0.58
        40   | 0.58
        41   | 0.46   // past the table, where the deep band is mostly never re-signed at all
        45   | 0.46
    }

    def "a player nobody holds is always available, and a tagged one almost never"() {
        given: 'one team holds the best player; the rest are unrostered'
        List<PlayerValuation> board = AuctionValuation.value(curveFor(['QB']),
                new StarterRequirements([QB: 2], [QB: 2], 2, 10),
                poolOf(['QB'], 40) { int rank -> rank == 1 ? 'f1' : null },
                [QB: 40], 2438.0, 60, NO_BYES)

        expect: 'nobody can match on a free agent, so he goes to the highest bid'
        find(board, 'QB2').availability == 1.0

        and: 'and a tag is worth a first round pick to break, which has happened six times in nine seasons'
        find(board, 'QB1').franchiseTagged
        find(board, 'QB1').availability == AuctionValuation.TAGGED_AVAILABILITY
    }

    /**
     * What it takes to prise a restricted free agent loose, which is not what the market settles at.
     *
     * The team holding him may match, so a bid has to clear what he is worth to <b>them</b> rather than
     * what he would otherwise go for. That is the whole reason positive edge on somebody else's restricted
     * free agent is arithmetically unavailable, and it is one line of the pricing that a board full of
     * players nobody holds would never exercise.
     */
    def "acquiring a held player costs the greater of his price and his worth"() {
        given: 'every player held, and a franchise salary above every price here, so nothing is bounded by it'
        List<PlayerValuation> held = AuctionValuation.value(curveFor(['QB']),
                new StarterRequirements([QB: 2], [QB: 2], 2, 10),
                poolOf(['QB'], 45) { int rank -> "f$rank".toString() },
                [QB: 100000], 2438.0, 60, NO_BYES)

        expect:
        held.every { it.acquisitionSalary == Math.max(it.marketSalary, it.value + 1) }

        and: 'and somewhere on this board the worth really is the binding one, or nothing was tested'
        held.any { it.acquisitionSalary > it.marketSalary }

        and: 'so what the right of first refusal costs an outside bidder is never negative'
        held.every { it.restrictionPremium >= 0 }
    }

    /** A franchise salary low enough to bind on the deep ranks, where worth runs above the market price. */
    private static final int TOP_OF_POSITION = 6

    /**
     * The bound on the premium, which is what keeps a restricted price inside what the league pays.
     *
     * Worth can run far above the market price, and where it does the rule that an outside bid must clear
     * the incumbent's valuation produces a number nobody would ever pay. The franchise salary — the average
     * of the top five at that position last season — is what the board already knows about the top of a
     * position, and it is what the right of first refusal is allowed to add and no more.
     *
     * The board is held by one team so that a franchise salary this cheap tags one player rather than the
     * whole pool. That team then holds two tag candidates, which is the case {@code warnUnsettled} exists
     * for, so the warning this prints on stderr is the expected one.
     */
    def "the right to match can add no more than the top of a position"() {
        given: 'the whole board held by one team, which has one tag, so a cheap tag cannot empty the auction'
        List<PlayerValuation> held = AuctionValuation.value(curveFor(['QB']),
                new StarterRequirements([QB: 2], [QB: 2], 2, 10),
                poolOf(['QB'], 45) { int rank -> 'f1' },
                [QB: TOP_OF_POSITION], 2438.0, 60, NO_BYES)

        expect: 'the right to match adds something, but never more than the position is worth at its top'
        held.every { it.restrictionPremium >= 0 && it.restrictionPremium <= TOP_OF_POSITION }

        and: 'the allowance really bound somewhere, or the board was priced under it all along'
        held.any { it.value + 1 - it.marketSalary > TOP_OF_POSITION }

        and: 'it only ever takes back premium, never the market price, and never adds any'
        held.every { it.acquisitionSalary >= it.marketSalary }
        held.every { it.acquisitionSalary <= Math.max(it.marketSalary, it.value + 1) }
    }

    /**
     * The property the premium bound exists for, and the one a cap on the price cannot hold.
     *
     * Capping the price is clipped by the market floor wherever the market has already cleared above the
     * position's top, which deletes the premium on the best players and leaves a step in the middle: a
     * player clearing at the allowance would pay no premium while one clearing a dollar more would pay his
     * full worth. Bounding the premium instead is monotonic in rank all the way down the board.
     *
     * Tagged players sit outside this ordering for a reason of their own, and it is a reason rather than an
     * exemption: a tagged row is priced in the world where his team tagged nobody, so it is not on the same
     * scale as rows priced in the world where it did. The ordering is not given up, it is asserted where it
     * means something — the row below checks it over the whole board in the one world that holds all of
     * them, and {@code every one of a team's expiring players is measured in the world where it tags nobody}
     * pins each tagged row to its price there.
     */
    def "a better player never costs less to prise loose than a worse one"() {
        given:
        List<PlayerValuation> held = AuctionValuation.value(curveFor(['QB']),
                new StarterRequirements([QB: 2], [QB: 2], 2, 10),
                poolOf(['QB'], 45) { int rank -> 'f1' },
                [QB: TOP_OF_POSITION], 2438.0, 60, NO_BYES)

        when: 'read down the board in rank order, over the players actually being bid on'
        List<Integer> byRank = held.findAll { !it.franchiseTagged }
                .sort { it.positionRank }.collect { it.acquisitionSalary }

        then: 'the acquisition price never rises as the players get worse'
        byRank == byRank.sort(false).reverse()
    }

    /**
     * And the same ordering over every player, on the one board where they are all in one world.
     *
     * The row above has to skip the tagged, which leaves the property untested exactly where the tag
     * mechanism could break it. Pricing the same board with the tag out of reach puts every player back on
     * one rate, and the ordering then has to hold across all of them — including the player who would
     * otherwise have been tagged, whose reported price is this board's price for him.
     */
    def "and never on the board where nobody is tagged, over every player on it"() {
        given: 'the same board, with a tag nobody would ever use'
        List<PlayerValuation> none = AuctionValuation.value(curveFor(['QB']),
                new StarterRequirements([QB: 2], [QB: 2], 2, 10),
                poolOf(['QB'], 45) { int rank -> 'f1' },
                [QB: 100000], 2438.0, 60, NO_BYES)

        expect: 'nobody is tagged, so there is no second world on this board'
        !none.any { it.franchiseTagged }

        and: 'and the ordering holds over the whole of it, with nobody left out'
        List<Integer> byRank = none.sort { it.positionRank }.collect { it.acquisitionSalary }
        byRank == byRank.sort(false).reverse()
    }

    /**
     * A position the previous season priced nothing at has no top to bound against, and guessing one from
     * an empty list would be worse than leaving the rule as it was.
     */
    def "a position with no franchise salary is left on the unbounded rule"() {
        given: 'no franchise salary at all, as happens where the prior season rostered nobody there'
        List<PlayerValuation> held = AuctionValuation.value(curveFor(['QB']),
                new StarterRequirements([QB: 2], [QB: 2], 2, 10),
                poolOf(['QB'], 45) { int rank -> "f$rank".toString() },
                [:], 2438.0, 60, NO_BYES)

        expect:
        held.every { it.acquisitionSalary == Math.max(it.marketSalary, it.value + 1) }
    }

    def "a player nobody holds costs what the market settles at and nothing over"() {
        given:
        List<PlayerValuation> free = AuctionValuation.value(curveFor(['QB']),
                new StarterRequirements([QB: 2], [QB: 2], 2, 10),
                poolOf(['QB'], 40), [QB: 100000], 2438.0, 60, NO_BYES)

        expect: 'there is nobody to match, so there is nothing to clear beyond the price'
        free.every { it.acquisitionSalary == it.marketSalary }
        free.every { it.restrictionPremium == 0 }
    }
}
