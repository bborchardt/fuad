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

    /**
     * A player at a rank, at a price, held by somebody or by nobody.
     *
     * <b>Callers price the better ranks higher</b>, which is not decoration. The ladder's shortlist keeps a
     * player only where nothing cheaper adds more, so a fixture whose cheapest player is also its best
     * leaves one candidate standing and a frontier of one row — a true answer to a board no auction has
     * ever produced.
     */
    private static PlayerValuation available(String position, int rank, int price = 1, String holder = null) {
        new PlayerValuation(playerId: "$position$rank", playerName: "$position $rank", position: position,
                positionRank: rank, points: (200 - rank * 6) as BigDecimal, pointsPerGame: 0.0,
                expectedGames: 0.0, pointsLow: 0.0, pointsHigh: 0.0, valueOverReplacement: 0.0,
                value: 1, marketSalary: price, acquisitionSalary: price + 10, availability: 1.0, tier: 1,
                franchiseId: holder)
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

    /** The ladder, parsed as rows of cost against points added, by position. */
    private static Map<String, List<List<BigDecimal>>> ladder(List<PlayerValuation> valuations) {
        StringWriter out = new StringWriter()
        new FuadRosterFitPrinter(dataFor(), valuations, lineups(), '0001').printLadder(new PrintWriter(out))
        Map<String, List<List<BigDecimal>>> rows = [:].withDefault { [] }
        out.toString().readLines().drop(2).each { String line ->
            List<String> cells = line.split('\t') as List
            rows[cells[0]] << [new BigDecimal(cells[1]), new BigDecimal(cells[2])]
        }
        rows
    }

    /**
     * The frontier is the point of the report: more money has to buy more points, or the row is noise.
     *
     * Dominated bundles used to reach it — a combination costing more than another and adding no more is
     * not a choice a reader has to weigh, and printing one asks them to do the filtering the report exists
     * to do.
     */
    def "a position's rows cost more and add more as they go down"() {
        given:
        List<List<BigDecimal>> rows = ladder((1..4).collectMany {
            [available('QB', it, (5 - it) * 10), available('PK', it, (5 - it) * 10)]
        }).QB

        expect: 'something to say at all'
        rows.size() > 1

        and: 'and every row strictly dearer and strictly better than the one above it'
        (1..<rows.size()).every { rows[it][0] > rows[it - 1][0] && rows[it][1] > rows[it - 1][1] }
    }

    /**
     * A position is never offered a bundle deeper than its lineup can field.
     *
     * Three kickers were a frontier point before the bound existed: cheapest way to a number, and a choice
     * nobody is weighing. One kicker starts, so two is the most that can be worth anything and the second is
     * cover.
     */
    def "bundles are bounded by what the lineup can start"() {
        given:
        StringWriter out = new StringWriter()
        List<PlayerValuation> valuations = (1..4).collectMany {
            [available('QB', it, (5 - it) * 10), available('PK', it, (5 - it) * 10)]
        }
        new FuadRosterFitPrinter(dataFor(), valuations, lineups(), '0001').printLadder(new PrintWriter(out))

        when: 'the kicker rows, which start one and so may hold at most two'
        List<Integer> kickers = out.toString().readLines().drop(2)
                .findAll { it.startsWith('PK\t') }
                .collect { (it.split('\t')[4] as String).split(', ').size() }

        then:
        kickers.every { it <= 2 }
    }

    /**
     * A team pays the market price for its own expiring player, never the outside bidder's.
     *
     * `ACQUIRE` exists because the team holding a player may match, which is what makes him dear to
     * everybody else. Charging a team that premium for keeping its own player prices it out of its own
     * roster, and the ladder is the one report where the distinction reaches a number.
     */
    def "a team's own player costs it the market price rather than the acquisition price"() {
        given: 'the same player, held by this team and by another'
        List<List<BigDecimal>> mine = ladder([available('PK', 1, 20, '0001')]).PK
        List<List<BigDecimal>> theirs = ladder([available('PK', 1, 20, '0002')]).PK

        expect: 'ours costs the market price and theirs the acquisition price, which is ten dearer'
        mine[0][0] == 20.0
        theirs[0][0] == 30.0

        and: 'for the identical player, so only the price differs'
        mine[0][1] == theirs[0][1]
    }
}
