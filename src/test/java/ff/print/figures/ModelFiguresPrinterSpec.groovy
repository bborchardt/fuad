package ff.print.figures

import ff.data.PlayerValuation
import ff.projection.AuctionValuation
import ff.projection.ByeWeeks
import ff.projection.PointsCurve
import ff.projection.StarterRequirements
import ff.projection.TestSeasons
import spock.lang.Specification

/**
 * The figures the documentation cites have to be the model's, and have to hold together.
 *
 * These assert the properties a reader relies on when they read a row rather than the values of any one
 * season, which would only be the drift problem again with a spec around it. See docs/PROJECTION.md.
 */
class ModelFiguresPrinterSpec extends Specification {

    private static final int LAST_WEEK = 14

    private static Map<Integer, List<BigDecimal>> uneven() {
        (1..30).collectEntries { int rank ->
            BigDecimal expected = (210 - rank * 6) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5] * 3]
        }
    }

    private static ModelFiguresPrinter printer(List<PlayerValuation> valuations = []) {
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(uneven())])
        StarterRequirements requirements = new StarterRequirements(
                [WR: 2, PK: 1], [WR: 4, PK: 1], 4, 10)
        new ModelFiguresPrinter(curve, requirements, new ByeWeeks([:], LAST_WEEK), valuations, 2438.0)
    }

    private static List<Map<String, String>> rows(Closure<Void> print) {
        StringWriter out = new StringWriter()
        print(new PrintWriter(out))
        List<String> lines = out.toString().readLines()
        List<String> headings = lines[0].split('\t', -1) as List
        lines.drop(1).collect { String line ->
            List<String> values = line.split('\t', -1) as List
            headings.withIndex().collectEntries { String heading, int i -> [(heading): values[i]] }
        }
    }

    private static PlayerValuation valued(String position, int rank, int price, int cost, String franchise,
                                          boolean tagged = false) {
        new PlayerValuation(playerId: "p$rank$position", playerName: "Player $rank", position: position,
                positionRank: rank, marketSalary: price, salary: cost, value: price,
                acquisitionSalary: price, franchiseId: franchise, franchiseTagged: tagged,
                points: 0.0, pointsPerGame: 0.0, expectedGames: 0.0, pointsLow: 0.0, pointsHigh: 0.0,
                valueOverReplacement: 0.0, availability: 1.0)
    }

    /**
     * The invariant the board also holds itself to: a reader who multiplies two columns lands on the third.
     *
     * It costs something to arrange — the level is anchored back to the mean season the position actually
     * had — so it is worth asserting rather than assuming.
     */
    def "the rate and the availability multiply out to the season beside them"() {
        expect:
        rows(printer().&printCurve).every { Map<String, String> row ->
            BigDecimal product = new BigDecimal(row.PPG) * new BigDecimal(row.G)
            (product - new BigDecimal(row.PTS)).abs() < new BigDecimal(row.PTS) * 0.01
        }
    }

    def "stops at the depth the curve still makes a claim at"() {
        given:
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(uneven())])

        expect: 'a rank the curve has given up on is not a figure anything should cite'
        rows(printer().&printCurve).size() == curve.pricedDepth('WR')
        rows(printer().&printCurve).every { (it.RANK as int) <= curve.pricedDepth('WR') }
    }

    def "carries both readings of value over replacement, the priced one never the smaller"() {
        expect:
        rows(printer().&printCurve).every { Map<String, String> row ->
            new BigDecimal(row.VOR) >= new BigDecimal(row.VOREXP)
        }
    }

    def "reports a position with no curve as having none, rather than leaving it out"() {
        given: 'kickers, whom the statistics carry nothing for'
        Map<String, String> kicker = rows(printer().&printPositions).find { it.POS == 'PK' }

        expect: 'no depth, no level, no sample'
        kicker.PRICEDDEPTH == ''
        kicker.SEASONS == ''
        kicker.BACKWARD == ''

        and: 'but the lineup still requires one, which is the whole reason to print the row'
        kicker.STARTED == '10'
    }

    def "takes replacement one past the last player the league starts"() {
        given:
        Map<String, String> receiver = rows(printer().&printPositions).find { it.POS == 'WR' }

        expect: 'a league starting 32 replaces from its 33rd, which is what sets the level'
        (receiver.REPLRANK as int) == (receiver.STARTED as int) + 1
    }

    def "totals the board both ways, since the tag holds the best players below what they would fetch"() {
        given: 'a tagged player who would have gone for 90 and costs his team 61'
        List<PlayerValuation> board = [valued('WR', 1, 90, 61, '0001', true),
                                       valued('WR', 2, 40, 40, '0002'),
                                       valued('WR', 3, 1, 1, null)]

        when:
        Map<String, String> row = rows(printer(board).&printBoard).first()

        then:
        row.PLAYERS == '3'
        row.TOTALPRICE == '131'
        row.TOTALCOST == '102'
        row.TOPPRICE == '90'
        row.TOPCOST == '61'

        and: 'players above the minimum bid counted on what is actually paid'
        row.ABOVEMIN == '2'

        and: 'and the tags counted by player and by team, which differ when one team holds two'
        row.TAGS == '1'
        row.TAGTEAMS == '1'
    }

    def "reports the pot the board was divided out of"() {
        given:
        Map<String, String> row = rows(printer().&printBoard).first()

        expect: 'free cap, and the share of it the league actually spends'
        row.FREECAP == '2438'
        row.EXPECTEDSPEND == (2438 * AuctionValuation.SPEND_RATE)
                .setScale(0, java.math.RoundingMode.HALF_UP) as String
    }
}
