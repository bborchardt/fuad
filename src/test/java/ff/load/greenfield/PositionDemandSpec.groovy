package ff.load.greenfield

import ff.league.League
import spock.lang.Shared
import spock.lang.Specification

/**
 * When each position comes off the board, measured against the nine drafts it is measured from.
 *
 * The shape is asserted rather than the figures, which move with the curve and belong in docs/figures. What
 * matters here is that the measurement is of a real draft: counts only rise, every position is accounted
 * for, and the residual is reported rather than swallowed.
 */
class PositionDemandSpec extends Specification {

    @Shared
    PositionDemand demand = new PositionDemand(League.GREENFIELD)

    @Shared
    Map<String, Integer> starters = new GreenfieldValuationLoader().starters()

    def "a position is only ever taken, never given back"() {
        given:
        Map<Integer, Map<String, Integer>> taken = demand.takenByRound()

        expect:
        demand.positions().every { String position ->
            (2..15).every { int round -> taken[round][position] >= taken[round - 1][position] }
        }
    }

    def "every pick of every draft lands in some position, the residual included"() {
        given: 'fifteen rounds of fourteen, less the keepers that leave the board before it starts'
        Map<String, Integer> last = demand.takenByRound()[15]

        expect:
        last.values().sum() > 190
        last.values().sum() <= 15 * 14

        and: 'team defences cannot be joined by name, so they are in the residual and not lost'
        last[PositionDemand.UNRANKED] > 0
    }

    def "running back runs out before receiver, and receiver before quarterback"() {
        given:
        Map<String, Integer> exhausted = demand.starterExhaustedByRound(starters)

        expect: 'the whole point: RB has to be taken early because the room empties it early'
        exhausted.RB < exhausted.WR
        exhausted.WR < exhausted.QB

        and: 'and quarterback starters survive well past the round the backs are gone'
        exhausted.QB >= 9
    }

    def "kickers are never exhausted, the room not bothering to fill the slot it must start"() {
        expect: 'null is "still available when the draft ends", which is a different claim from a late round'
        demand.starterExhaustedByRound(starters).PK == null
    }

    def "a positional rank goes later than the rank above it"() {
        given:
        Map<String, Map<Integer, Integer>> adp = demand.averageDraftPosition()

        expect: 'not strictly, since consensus and this room disagree, but overwhelmingly'
        ['QB', 'RB', 'WR', 'TE'].every { String position ->
            List<Integer> ranks = adp[position].keySet().sort()
            int rising = ranks.drop(1).count { adp[position][it] >= adp[position][it - 1] }
            rising > ranks.size() * 0.8
        }
    }

    def "a rank too few drafts reached is left out rather than reported from nothing"() {
        given:
        Map<String, Map<Integer, Integer>> adp = demand.averageDraftPosition()

        expect: 'the deep ranks of every position stop somewhere short of the ranking itself'
        adp.QB.keySet().max() < 71
        adp.every { String position, Map<Integer, Integer> byRank -> byRank.keySet().min() == 1 }
    }
}
