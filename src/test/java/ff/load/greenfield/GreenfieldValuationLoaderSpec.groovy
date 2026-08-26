package ff.load.greenfield

import ff.league.League
import ff.projection.PointsCurve
import spock.lang.Shared
import spock.lang.Specification

/**
 * The Greenfield curve, built from the committed record rather than from a fixture.
 *
 * Nine seasons are read and restated to make it, so it is built once and shared. What is asserted here is
 * the shape a fourteen team single quarterback lineup forces, because that shape is the whole reason this
 * league needs its own board and not the auction's.
 */
class GreenfieldValuationLoaderSpec extends Specification {

    @Shared
    GreenfieldValuationLoader loader = new GreenfieldValuationLoader()

    @Shared
    PointsCurve curve = loader.curve()

    def "every position the league scores is levelled, none left at a guess"() {
        expect:
        League.GREENFIELD.scoredPositions.every { curve.pricedDepth(it) > 0 }
    }

    def "the league starts nine a team, and the flex is the only thing free to move"() {
        given:
        Map<String, Integer> started = loader.starters()

        expect: 'fourteen teams starting nine apiece'
        started.values().sum() == 9 * 14

        and: 'quarterback, kicker and defence are capped at one a team, so none can take a flex'
        started.QB == 14
        started.PK == 14
        started.DST == 14

        and: 'the minimums are floors: nobody starts fewer than two backs or two receivers a team'
        started.RB >= 28
        started.WR >= 28
        started.TE >= 14
    }

    def "quarterback replacement is rank 15, against 21 in the superflex league"() {
        expect: 'the single largest difference between the two leagues, and it needs no curve to predict'
        loader.starters().QB + 1 == 15
    }

    def "the flex goes to receivers, which is what sets replacement at every other position"() {
        given:
        Map<String, Integer> started = loader.starters()

        expect: 'full PPR and fourteen teams make the third receiver the best flex available'
        started.WR == 42
        started.RB == 28
        started.TE == 14
    }

    def "replacement is a rate for every week of the regular season"() {
        given:
        Map<String, Map<Integer, BigDecimal>> replacement = loader.replacement('2026')

        expect:
        replacement.keySet().containsAll(League.GREENFIELD.scoredPositions)
        replacement.every { position, weekly ->
            weekly.keySet() == (1..GreenfieldValuationLoader.LAST_REGULAR_SEASON_WEEK) as Set
        }
    }

    def "byes are carried for the whole ranked pool, not only the players worth drafting"() {
        expect: 'replacement is whoever a team would start instead, so his bye matters as much as anyone\'s'
        League.GREENFIELD.scoredPositions.every { String position ->
            loader.byes('2026').of(position, curve.pricedDepth(position)) > 0
        }
    }
}
