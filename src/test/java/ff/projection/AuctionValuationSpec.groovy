package ff.projection

import ff.data.PlayerValuation
import spock.lang.Specification

/**
 * The constants this model divides money by are measurements, not choices, so they are checked against the
 * seasons they were measured from rather than left to drift.
 *
 * <b>The measuring itself is no longer done here.</b> It was, and that was the wrong place for it: the
 * figures the documentation quotes about what the league pays were computed by test code, which meant they
 * could not be generated, could not be cited, and could not be checked. {@link AuctionSpend} owns them now,
 * so the constants below and the tables in docs/figures come from one implementation reading one set of
 * committed seasons. See docs/PROJECTION.md.
 */
class AuctionValuationSpec extends Specification {

    private static List<AuctionSpend.Season> calibrated() {
        AuctionSpend.CALIBRATED_SEASONS.collect { AuctionSpend.of(it) }
    }

    def "the spend rate is what the superflex seasons actually spent"() {
        given:
        List<AuctionSpend.Season> seasons = calibrated()

        when:
        BigDecimal measured = seasons.collect { it.spendRate }.sum() / seasons.size()

        then:
        (measured - AuctionValuation.SPEND_RATE).abs() < 0.01
    }

    def "no season spent outside the range the model claims"() {
        expect:
        calibrated().every { it.spendRate > 0.65 && it.spendRate < 0.90 }
    }

    def "five rounds times teams is what the rookie draft actually puts on rosters"() {
        expect: 'within a couple of picks every season, since rookies are almost always kept'
        calibrated().every {
            Math.abs(it.rookiesRostered - AuctionValuation.ROOKIE_ROUNDS * it.teams) <= 3
        }
    }

    def "rookies cost about the share of the pot the model reserves for them"() {
        given:
        List<BigDecimal> shares = calibrated().collect { it.rookieShare }

        expect:
        (shares.sum() / shares.size() - AuctionValuation.ROOKIE_BUDGET_SHARE).abs() < 0.01
    }

    /**
     * Every position is calibrated on its share of the whole auction, kickers in the denominator with
     * everyone else.
     *
     * <b>One basis, and it has to be one.</b> The four scoring positions used to carry shares of what those
     * four took, alongside a kicker entry on the whole-pot basis, and the map summed to 1.009. That cost
     * nothing while it lasted: a kicker had no curve and so no value over replacement, which kept him out
     * of the pool {@link AuctionValuation#calibrate} normalises over, and his entry was never read at all.
     * Levelling kickers put them in that pool and made the mixed basis a real error.
     */
    def "the market shares are what the superflex seasons actually paid each position"() {
        given:
        Map<String, BigDecimal> paid = AuctionSpend.shareByPosition(calibrated())

        expect:
        AuctionSpend.POSITIONS.every {
            (paid[it] - AuctionValuation.MARKET_SHARE[it]).abs() < 0.005
        }
    }

    def "the shares are shares, so they sum to one"() {
        expect: 'which the mixed basis did not, and could not have been noticed from the map alone'
        ((AuctionValuation.MARKET_SHARE.values().sum() as BigDecimal) - 1.0).abs() < 0.002
    }

    /**
     * The finding that made levelling kickers worth doing.
     *
     * Every other position is bought at roughly what it is worth — the league's spending and the model's
     * value over replacement agree to within a fifth either way. Kicker is off by a factor of six. That is
     * either the one standing inefficiency in this league's market or a limit of what value over
     * replacement can say about a position whose starters can be replaced from the waiver wire in a week,
     * and the board reports it as {@code EDGE} rather than acting on it. See docs/PROJECTION.md.
     */
    def "kicker is the one position whose price and value disagree by an order of magnitude"() {
        given:
        Map<String, BigDecimal> paid = AuctionSpend.shareByPosition(calibrated())

        expect: 'the four scoring positions are paid within a fifth of their share of the pot'
        AuctionSpend.EXCLUDING_KICKERS.every {
            paid[it] > AuctionValuation.MARKET_SHARE[it] * 0.8 &&
                    paid[it] < AuctionValuation.MARKET_SHARE[it] * 1.2
        }

        and: 'and kickers take well under a fifth of what the curve says they are worth'
        AuctionValuation.MARKET_SHARE.PK < 0.02
    }

    /**
     * The property that makes the scale a curve is measured on somebody else's business.
     *
     * Value over replacement is computed inside a position and then summed across all of them to divide the
     * pot, so any factor applied to one position's whole curve is a thumb on that sum. It ought not to be:
     * {@link AuctionValuation#calibrate} forces each position's share of the money to what the league
     * actually pays, and a share is a ratio, so multiplying a position's points by anything must come out
     * the other side unchanged.
     *
     * <b>That is what was quietly not true.</b> Rate and availability are averaged apart and multiplied,
     * which loses their covariance, so each position's level is anchored back to the season it really had —
     * by a different factor at each position, 1.032 at receiver against 1.067 at kicker. Value over
     * replacement was taken on the unanchored rate and {@code PTS} on the anchored one, so the two were on
     * different scales and {@code VALUE} carried the difference between positions. Prices never did, which
     * is why it went unseen until the kicker market turned on {@code VALUE}.
     *
     * Asserted with the flex pinned shut, since allocating it compares positions against each other and
     * would legitimately move if one of them doubled.
     */
    def "doubling a position's points changes no price, a share being a ratio"() {
        given: 'two positions, and the same two with every receiver season worth twice as much'
        Map<Integer, List<BigDecimal>> shape = (1..12).collectEntries { int rank ->
            BigDecimal expected = (240 - rank * 12) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5] * 3]
        }
        Map<Integer, List<BigDecimal>> doubled = shape.collectEntries { int rank, List<BigDecimal> seasons ->
            [(rank): seasons.collect { it * 2 }]
        }
        PointsCurve plain = PointsCurve.of([QB: TestSeasons.byRank(shape), WR: TestSeasons.byRank(shape)])
        PointsCurve scaled = PointsCurve.of([QB: TestSeasons.byRank(shape), WR: TestSeasons.byRank(doubled)])

        and: 'a lineup with no flex, so nothing reallocates a starting slot between the two'
        StarterRequirements fixed = new StarterRequirements([QB: 1, WR: 2], [QB: 1, WR: 2], 3, 10)
        Map<String, List> pool = (1..6).collectMany { int rank ->
            [["q$rank" as String, ["QB $rank" as String, 'QB', rank, null]],
             ["w$rank" as String, ["WR $rank" as String, 'WR', rank, null]]]
        }.collectEntries { [(it[0]): it[1]] }

        when:
        List<PlayerValuation> before = AuctionValuation.value(plain, fixed, pool, [QB: 20, WR: 20],
                400.0, 12, new ByeWeeks([:], 14))
        List<PlayerValuation> after = AuctionValuation.value(scaled, fixed, pool, [QB: 20, WR: 20],
                400.0, 12, new ByeWeeks([:], 14))

        then: 'the receivers really are worth twice as much, so there is something here that could leak'
        scaled.seasonPoints('WR', 3) > plain.seasonPoints('WR', 3) * 1.9

        and: 'and every price is the same to the dollar, at both positions'
        before.every { PlayerValuation player ->
            player.marketSalary == after.find { it.playerId == player.playerId }.marketSalary
        }
    }

    /** Measured and reported, never calibrated on: the case for dropping it has to be checkable. */
    def "2022 is the outlier the calibration excludes, and by a distance"() {
        given:
        Map<String, BigDecimal> transition =
                AuctionSpend.shareByPosition([AuctionSpend.of('2022')], AuctionSpend.EXCLUDING_KICKERS)
        List<Map<String, BigDecimal>> since = AuctionSpend.CALIBRATED_SEASONS.collect {
            AuctionSpend.shareByPosition([AuctionSpend.of(it)], AuctionSpend.EXCLUDING_KICKERS)
        }

        expect: 'wide receiver took more of that auction than of any season since, by a wide margin'
        transition.WR > since.collect { it.WR }.max() + 0.15

        and: 'and quarterback less than any of them'
        transition.QB < since.collect { it.QB }.min()
    }
}
