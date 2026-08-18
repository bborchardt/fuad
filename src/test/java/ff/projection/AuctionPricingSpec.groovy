package ff.projection

import ff.data.PlayerValuation
import spock.lang.Specification
import spock.lang.Unroll

/**
 * What the chain from points to dollars promises, asserted on boards small enough to check by hand.
 *
 * {@link AuctionValuationSpec} checks that the constants dividing the money are the measurements they claim
 * to be. This checks what happens to the money once they have divided it, which had nothing on it at all:
 * the positional totals surviving the steepening, the prices summing to the pot, the counterfactual a
 * tagged player is priced at, and the two columns the right of first refusal produces.
 *
 * Every board here is synthetic. These are properties the arithmetic has to hold for any curve, so pinning
 * them to a season's figures would only be the drift problem with a spec around it — see
 * {@link ff.print.figures.ModelFiguresPrinterSpec}.
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
     * docs/figures/&lt;year&gt;/positions.tsv. Their money then has to divide as their target shares do and
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
        given: 'every player held by somebody, so the right to match applies to all of them'
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
