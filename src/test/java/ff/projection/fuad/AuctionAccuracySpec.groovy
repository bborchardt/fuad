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
 * A number this cheap to compute and this easy to misread needs its denominator watched. The failure that
 * would matter is silent: the pool quietly ceasing to cover the players somebody bid on, leaving an ever more
 * flattering figure about an ever smaller slice of the auction. Nothing in the arithmetic would complain, so
 * the coverage is asserted here, along with the invariants of the signing list the whole measure is taken
 * over.
 */
class AuctionAccuracySpec extends Specification {

    /** Seasons the record carries both a board and a set of signings for. */
    @Shared
    List<String> seasons = AuctionAccuracy.MEASURED_SEASONS.findAll { AuctionSpend.isMeasurable(it) }

    @Shared
    FuadValuationLoader loader = new FuadValuationLoader()

    private List<PlayerValuation> board(String season) {
        loader.valuations(season, new FuadLoader().loadData(season))
    }

    /**
     * The invariants the extraction rests on, which is what there is to check here.
     *
     * Deliberately not "the signings sum to what {@code AuctionSpend.of} reports": {@code of} is now built
     * from this list, so that comparison passes by construction and would go on passing through any error
     * either could make. What can still break is the list itself — a player counted under both of the two
     * cases, a rookie let in among the veterans, or a contract read at its wiped price rather than its
     * signed one — so those are what is asserted.
     */
    def "finds each signing once, at what was paid, and lets no rookie in"() {
        expect:
        seasons.every { String season ->
            List<AuctionSpend.Signing> signings = AuctionSpend.signings(season)
            Set<String> ids = signings.collect { it.playerId } as Set

            // Nobody is both re-signed and arrived from outside, which is what counting a contract twice
            // would look like.
            ids.size() == signings.size() &&
                    // A signing is a real contract, never the 0.01 an expiring one is written down to.
                    signings.every { it.paid >= 1.0 } &&
                    // Both kinds are represented, so neither branch has quietly stopped matching.
                    signings.any { it.resigned } && signings.any { !it.resigned } &&
                    signings.every { AuctionSpend.POSITIONS.contains(it.position) }
        }
    }

    def "leaves rookies out, whose contracts are set by rule rather than bid for"() {
        given: 'the rookies on each post-draft roster, who were on no pre-draft roster either'
        expect:
        seasons.every { String season ->
            Set<String> rookies = (LoadUtils.loadJsonResource(
                    LoadUtils.mflPlayersResourcePath(season)).players.player as List<Map>)
                    .findAll { it.status == 'R' }.collect { it.id as String } as Set
            !AuctionSpend.signings(season).any { rookies.contains(it.playerId) }
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
