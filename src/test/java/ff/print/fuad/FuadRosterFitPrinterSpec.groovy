package ff.print.fuad

import ff.data.Contract
import ff.data.Player
import ff.data.PlayerValuation
import ff.data.Rank
import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.mfl.MflData
import ff.data.mfl.MflFranchise
import ff.data.mfl.MflPlayer
import ff.projection.ByeWeeks
import ff.projection.LineupValue
import ff.projection.PointsCurve
import ff.projection.StarterRequirements
import ff.projection.TestSeasons
import spock.lang.Specification

/**
 * The depth report answers whether a <b>second</b> player at a position is worth buying, which the
 * per-player marginals cannot: every one of those is the value of being the first signing there.
 *
 * It walked a list of four positions written into the printer, which stopped being every position the day
 * kickers were levelled — so the report that exists to say "one is enough" could not mention the position
 * where that advice matters most. See docs/STRATEGY.md.
 */
class FuadRosterFitPrinterSpec extends Specification {

    private static final int LAST_WEEK = 14

    /** Ranks levelled 210 down, realising at half, level and half again, so outcomes actually vary. */
    private static Map<Integer, List<BigDecimal>> uneven(int top) {
        (1..20).collectEntries { int rank ->
            BigDecimal expected = (top - rank * 6) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5] * 3]
        }
    }

    /**
     * A lineup starting one kicker and up to two quarterbacks.
     *
     * The kicker is the case worth testing: one slot and a cap of one, so a second can never be fielded
     * beside the first and is worth only the weeks the first is absent.
     */
    private static StarterRequirements lineup() {
        new StarterRequirements([QB: 1, PK: 1], [QB: 2, PK: 1], 3, 10)
    }

    /** Kickers play 11 of the 13 playable weeks, so there is real absence for a spare to cover. */
    private static LineupValue lineups() {
        PointsCurve curve = PointsCurve.of([QB: TestSeasons.byRank(uneven(210), 13),
                                            PK: TestSeasons.byRank(uneven(120), 11)])
        new LineupValue(curve, new ByeWeeks([:], LAST_WEEK), lineup())
    }

    private static PlayerValuation available(String position, int rank) {
        new PlayerValuation(playerId: "$position$rank", playerName: "$position $rank", position: position,
                positionRank: rank, points: (200 - rank * 6) as BigDecimal, pointsPerGame: 0.0,
                expectedGames: 0.0, pointsLow: 0.0, pointsHigh: 0.0, valueOverReplacement: 0.0,
                value: 1, marketSalary: 1, acquisitionSalary: 1, availability: 1.0, tier: 1)
    }

    /**
     * A team with nothing under contract, so both positions are walked from empty.
     *
     * Deliberately empty rather than part filled: with a quarterback already signed, the <i>second</i>
     * quarterback the depth report adds is the third on the roster and is worth cover alone — which is a
     * true figure and the wrong comparison for telling a one-slot position from a two-slot one.
     */
    private static FuadData dataFor() {
        new FuadData(
                mflData: new MflData(franchiseByIdMap: ['0001': new MflFranchise(
                        id: '0001', name: 'Test', ownerName: 'Brett', players: [])]),
                playerByNameMap: [:])
    }

    private static Map<String, List<BigDecimal>> depth() {
        List<PlayerValuation> valuations =
                (1..4).collectMany { [available('QB', it), available('PK', it)] }
        StringWriter out = new StringWriter()
        new FuadRosterFitPrinter(dataFor(), valuations, lineups(), '0001').printDepth(new PrintWriter(out))
        out.toString().readLines().drop(2).collectEntries { String line ->
            List<String> cells = line.split('\t') as List
            [(cells[0]): cells.drop(1).collect { new BigDecimal(it) }]
        }
    }

    def "walks every position the lineup fields, not a list written down in the printer"() {
        expect: 'the kicker among them, which the hand-kept list of four had no way of gaining'
        depth().keySet() == ['QB', 'PK'] as Set
    }

    /**
     * The advice the row exists to give, at the position where it is least obvious.
     *
     * A kicker priced far below what the curve says he is worth invites buying two. Only one can ever be
     * started, so the second is worth the weeks the first is absent and nothing else — real, and a small
     * fraction of the first.
     */
    def "a second player where only one starts is worth cover alone"() {
        given:
        List<BigDecimal> kicker = depth().PK

        expect: 'the first fills a slot standing empty and brings most of a season'
        kicker[0] > 60

        and: 'the second cannot be fielded beside him, so he is worth only the weeks the first misses'
        kicker[1] > 0
        kicker[1] < kicker[0] / 2

        and: 'and it keeps falling away, a fourth being worth next to nothing'
        kicker[3] < kicker[1]
    }

    /** Where two start, the second is a starter rather than cover, and the shape says so. */
    def "a second player where two start is worth far more than cover"() {
        given:
        Map<String, List<BigDecimal>> depth = depth()

        expect: 'the second quarterback holds a starting slot of his own'
        depth.QB[1] > depth.QB[0] / 2

        and: 'where the second kicker only ever covers, which is the distinction the report is drawing'
        (depth.PK[1] / depth.PK[0]) < (depth.QB[1] / depth.QB[0])
    }
}
