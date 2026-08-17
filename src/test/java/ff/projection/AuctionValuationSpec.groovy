package ff.projection

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
     * The four priced positions are calibrated on the share of what those four took, kickers left out of
     * the denominator. See {@link AuctionSpend#EXCLUDING_KICKERS} for why the two bases are kept apart.
     */
    def "the market shares are what the superflex seasons actually paid each position"() {
        given:
        Map<String, BigDecimal> paid = AuctionSpend.shareByPosition(calibrated(), AuctionSpend.EXCLUDING_KICKERS)

        expect:
        AuctionSpend.EXCLUDING_KICKERS.every {
            (paid[it] - AuctionValuation.MARKET_SHARE[it]).abs() < 0.005
        }
    }

    /**
     * The kicker is the one entry on a different basis, and pinning it is what makes that visible.
     *
     * {@code MARKET_SHARE} therefore does not sum to one — it sums to 1.009, being four shares of the
     * four-position pot plus one share of the whole pot. It costs nothing in dollars, because the four are
     * inflated together and {@code clearingRate} renormalises anything uniform away, and because kickers
     * have no curve and price at the minimum bid whatever share they are given. It is asserted rather than
     * quietly corrected: repricing the board is a decision, not a tidy-up.
     */
    def "the kicker share is on the other basis, which is why the shares do not sum to one"() {
        given:
        Map<String, BigDecimal> wholePot = AuctionSpend.shareByPosition(calibrated())

        expect: 'PK is a share of every auction dollar, where the other four are not'
        (wholePot.PK - AuctionValuation.MARKET_SHARE.PK).abs() < 0.005

        and: 'and the four are each above their whole-pot share, by the kicker slice they leave out'
        AuctionSpend.EXCLUDING_KICKERS.every {
            AuctionValuation.MARKET_SHARE[it] > wholePot[it]
        }

        and: 'so the map overstates by almost exactly that slice'
        ((AuctionValuation.MARKET_SHARE.values().sum() as BigDecimal) - 1.0 - wholePot.PK).abs() < 0.002
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
