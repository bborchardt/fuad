package ff.projection.fuad

import spock.lang.Specification

class AuctionRankPricesSpec extends Specification {

    def "levels a rank on the true local median above the reserved dollar"() {
        when:
        Map<String, Map<Integer, BigDecimal>> curve = AuctionRankPrices.ofObservations([
                ['WR', 1, 11.0], ['WR', 3, 5.0],
                ['PK', 4, 1.0],
        ])

        then: 'the middle pair is averaged, then the dollar every roster spot already owns is removed'
        curve.WR[2] == 7.0

        and: 'a minimum-bid history supplies no share above that reserve'
        curve.PK[4] == 0.0

        and: 'past the observed range the nearest rank is used rather than the whole position'
        curve.WR[250] == 4.0
    }
}
