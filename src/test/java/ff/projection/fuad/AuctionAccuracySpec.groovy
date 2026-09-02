package ff.projection.fuad

import ff.data.PlayerValuation
import ff.load.fuad.FuadLoader
import ff.load.fuad.FuadValuationLoader
import ff.load.util.LoadUtils
import spock.lang.Shared
import spock.lang.Specification

/**
 * The board held to what the league actually paid, and the things that would make that measurement lie.
 *
 * A number this cheap to compute and this easy to misread needs its denominator watched. Two failures would
 * leave it reporting an ever more flattering figure about an ever smaller slice of the auction: the pool
 * quietly ceasing to cover the players somebody bid on, and the signings themselves ceasing to be the same
 * event {@link AuctionSpend} counts when it produces {@code MARKET_SHARE}. Both are checked here.
 */
class AuctionAccuracySpec extends Specification {

    /** Seasons the record can speak to and the data is present for. */
    @Shared
    List<String> seasons = AuctionAccuracy.MEASURED_SEASONS.findAll { LoadUtils.YEARS.contains(it) }

    @Shared
    FuadValuationLoader loader = new FuadValuationLoader()

    private List<PlayerValuation> board(String season) {
        loader.valuations(season, new FuadLoader().loadData(season))
    }

    def "counts the same signings the spend figures are built from"() {
        expect: 'per position, to the dollar, or MARKET_SHARE and this measure are about different auctions'
        seasons.every { String season ->
            AuctionSpend.Season spend = AuctionSpend.of(season)
            Map<String, BigDecimal> fromSignings = AuctionSpend.signings(season)
                    .groupBy { it.position }
                    .collectEntries { String position, List<AuctionSpend.Signing> at ->
                        [(position): at.collect { it.paid }.sum() as BigDecimal]
                    }
            AuctionSpend.POSITIONS.every {
                ((fromSignings[it] ?: 0.0) - (spend.dollars[it] ?: 0.0)).abs() < 0.005
            }
        }
    }

    def "prices nearly every player somebody actually bid on"() {
        given: 'the join is on the MFL id, so a miss is the pool not covering the auction'
        List<AuctionAccuracy.Fit> all = seasons.collectMany { AuctionAccuracy.of(it, board(it)) }
        List<AuctionAccuracy.Fit> whole = all.findAll { it.position == AuctionAccuracy.ALL }

        expect: 'every season is measured over the great majority of its own signings'
        whole.every { it.priced >= it.signings * 0.85 }

        and: 'and the sample as a whole is nearly complete, which is what the dollar figures rest on'
        whole.collect { it.priced }.sum() >= whole.collect { it.signings }.sum() * 0.9

        and: 'over enough signings to mean anything'
        whole.collect { it.priced }.sum() >= 200
    }

    def "reports each season entire as well as position by position"() {
        given:
        String season = seasons.last()
        List<AuctionAccuracy.Fit> fits = AuctionAccuracy.of(season, board(season))
        AuctionAccuracy.Fit whole = fits.find { it.position == AuctionAccuracy.ALL }

        expect: 'the whole-season row covers exactly the positions beside it'
        whole.signings == fits.findAll { it.position != AuctionAccuracy.ALL }.collect { it.signings }.sum()
        whole.priced == fits.findAll { it.position != AuctionAccuracy.ALL }.collect { it.priced }.sum()

        and: 'and its dollars are theirs'
        (whole.paid - fits.findAll { it.position != AuctionAccuracy.ALL }
                .collect { it.paid }.sum()).abs() < 0.005
    }

    def "scores a board that is exactly right as exactly right"() {
        given: 'a season, and a board rewritten to have said what the league went on to pay'
        String season = seasons.last()
        Map<String, BigDecimal> paid = AuctionSpend.signings(season)
                .collectEntries { [(it.playerId): it.paid] }
        List<PlayerValuation> perfect = board(season).collect { PlayerValuation v ->
            paid.containsKey(v.playerId) ? new PlayerValuation(playerId: v.playerId, position: v.position,
                    salary: paid[v.playerId] as int) : v
        }

        when:
        AuctionAccuracy.Fit whole = AuctionAccuracy.of(season, perfect)
                .find { it.position == AuctionAccuracy.ALL }

        then: 'no error at all, and the ordering perfectly correlated'
        whole.meanAbsolute == 0.0
        whole.bias == 0.0
        whole.correlation > 0.999
    }

    def "counts a season it cannot price as unmeasured rather than as a miss"() {
        when: 'no board at all, which is what a season with no prior roster snapshot would give'
        List<AuctionAccuracy.Fit> fits = AuctionAccuracy.of(seasons.last(), [])
        AuctionAccuracy.Fit whole = fits.find { it.position == AuctionAccuracy.ALL }

        then: 'the signings are still counted, and nothing is scored against them'
        whole.signings > 0
        whole.priced == 0
        whole.meanAbsolute == 0.0
    }
}
