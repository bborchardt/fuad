package ff.print.figures.fuad

import ff.data.PlayerValuation
import ff.projection.fuad.AuctionSpend
import ff.projection.fuad.AuctionValuation
import ff.projection.ByeWeeks
import ff.projection.PointsCurve
import ff.projection.StarterRequirements
import ff.projection.fuad.TagHistory
import ff.projection.TestSeasons
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The figures the documentation cites have to be the model's, and have to hold together.
 *
 * These assert the properties a reader relies on when they read a row rather than the values of any one
 * season, which would only be the drift problem again with a spec around it. See docs/fuad/PROJECTION.md.
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

    /** The same, carrying value over replacement, for the columns that report worth rather than price. */
    private static PlayerValuation worth(String position, int rank, int price, BigDecimal vor) {
        valued(position, rank, price, price, null).copyWith(valueOverReplacement: vor)
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

    /**
     * The account the documentation now gives of why SHARE and TARGETSHARE differ, asserted rather than
     * described.
     *
     * It used to claim they were equal, because {@code MARKET_WEIGHT} is 1.0 and the calibration does force
     * the shares of value exactly. What moves them afterwards is the dollar reserved for every roster spot,
     * handed out by headcount rather than by worth. If that ever stops explaining the gap, this fails.
     */
    def "the reserved minimum bids are what separate the board's shares from the calibration's"() {
        given: 'a board of three, one of them a position holding a single cheap player'
        List<PlayerValuation> valuations = [valued('WR', 1, 90, 90, null), valued('WR', 2, 9, 9, null),
                                            valued('PK', 1, 1, 1, null)]
        List<Map<String, String>> rows = rows(printer(valuations).&printPositions)

        expect: 'RESERVE is a headcount as a share of the money, not of the value'
        rows.find { it.POS == 'WR' }.RESERVE == '2.0'
        rows.find { it.POS == 'PK' }.RESERVE == '1.0'

        and: 'and the kicker takes a share of the board far above anything the calibration gave him'
        new BigDecimal(rows.find { it.POS == 'PK' }.SHARE) ==
                new BigDecimal(rows.find { it.POS == 'PK' }.RESERVE)
    }

    /**
     * VORSHARE is worth against TARGETSHARE's money, which is the only column pair here comparing two
     * different things.
     *
     * SHARE and TARGETSHARE both describe what the board charges and the calibration forces one onto the
     * other, so a reader learns nothing from their agreement. This is the board's value over replacement
     * before any calibration touches it, so it has to be computed off {@code valueOverReplacement} and not
     * off any price — which is exactly what a position holding value and charging almost nothing has to be
     * able to show.
     */
    def "reports what a position is worth apart from what it is charged"() {
        given: 'two positions charged alike, one of them carrying nearly all the value'
        List<PlayerValuation> valuations = [
                worth('WR', 1, 50, 30.0), worth('WR', 2, 50, 30.0), worth('PK', 1, 50, 0.0)]
        List<Map<String, String>> rows = rows(printer(valuations).&printPositions)

        expect: 'value is shared on value, so the receivers hold all of it and the kicker none'
        rows.find { it.POS == 'WR' }.VORSHARE == '100.0'
        rows.find { it.POS == 'PK' }.VORSHARE == '0.0'

        and: 'while the money says the opposite, a third each, which is the disagreement worth reporting'
        rows.find { it.POS == 'WR' }.SHARE == '66.7'
        rows.find { it.POS == 'PK' }.SHARE == '33.3'
    }

    def "takes replacement one past the last player the league starts"() {
        given:
        Map<String, String> receiver = rows(printer().&printPositions).find { it.POS == 'WR' }

        expect: 'a league starting 32 replaces from its 33rd, which is what sets the level'
        (receiver.REPLRANK as int) == (receiver.STARTED as int) + 1
    }

    /** board.tsv is one figure per row, so it reads as a lookup rather than as a single wide record. */
    private static Map<String, String> board(List<PlayerValuation> valuations = []) {
        rows(printer(valuations).&printBoard).collectEntries { [(it.FIGURE): it.VALUE] }
    }

    def "totals the board both ways, since the tag holds the best players below what they would fetch"() {
        given: 'a tagged player who would have gone for 90 and costs his team 61'
        List<PlayerValuation> valuations = [valued('WR', 1, 90, 61, '0001', true),
                                            valued('WR', 2, 40, 40, '0002'),
                                            valued('WR', 3, 1, 1, null)]

        when:
        Map<String, String> figures = board(valuations)

        then:
        figures.PLAYERS == '3'
        figures.TOTALPRICE == '131'
        figures.TOTALCOST == '102'
        figures.TOPPRICE == '90'
        figures.TOPCOST == '61'

        and: 'players above the minimum bid counted on what is actually paid'
        figures.PLAYERSABOVE1 == '2'

        and: 'and the tags counted by player and by team, which differ when one team holds two'
        figures.TAGS == '1'
        figures.TEAMSTAGGING == '1'
    }

    /**
     * The margin is the gap between a team's tag and its next best, not the size of the saving.
     *
     * Both read on that team's own no-tag money, which is what makes them subtractable at all. A team
     * holding one expiring player was never choosing, so it contributes no margin — and a board where
     * nobody was choosing reports none rather than reporting zero, which would read as a board the model
     * could not separate.
     */
    def "reports how narrow the closest tag decision was, over teams that had a choice"() {
        given: 'one team choosing between two players, and one team with nobody to choose against'
        List<PlayerValuation> valuations = [
                choosing('WR', 1, 40, 'f1', true), choosing('WR', 2, 37, 'f1'),
                choosing('RB', 1, 50, 'f2', true),
        ]

        expect: 'the narrowest gap between a tag and the runner up on the same roster'
        board(valuations).TAGMARGIN == '3'
    }

    @Unroll
    def "reports no margin where no team that tagged had anybody to weigh it against: #situation"() {
        expect: 'blank, since nothing was chosen between, rather than a margin of nought'
        board(valuations).TAGMARGIN == ''

        where:
        situation                     | valuations
        'each tagger holds one'       | [choosing('WR', 1, 40, 'f1', true), choosing('RB', 1, 50, 'f2', true)]
        'nobody tagged at all'        | [choosing('WR', 1, 40, 'f1'), choosing('WR', 2, 37, 'f1')]
    }

    /** A held player whose saving is stated outright: no tag price, so the surplus is the price itself. */
    private static PlayerValuation choosing(String position, int rank, int untagged, String franchise,
                                            boolean tagged = false) {
        valued(position, rank, untagged, untagged, franchise, tagged)
                .copyWith(untaggedSalary: untagged)
    }

    def "reports the pot the board was divided out of"() {
        given:
        Map<String, String> figures = board()

        expect: 'free cap, and the share of it the league actually spends'
        figures.FREECAP == '2438'
        figures.EXPECTEDSPEND == (2438 * AuctionValuation.SPEND_RATE)
                .setScale(0, java.math.RoundingMode.HALF_UP) as String
    }

    def "names every figure it writes, so a document can cite one by name"() {
        expect: 'a lookup, not a wide row: each line is a figure and its value'
        rows(printer().&printBoard).every { it.keySet() == ['FIGURE', 'VALUE'] as Set }
    }

    /**
     * The spend table reads the committed seasons rather than the fixture, being about the league and not
     * about a curve. So what is asserted is that the two bases hold together, which is the thing a reader
     * of the documentation relies on and the thing that was wrong in the constant.
     */
    def "reports what the league paid on both bases, each summing to its own whole"() {
        given:
        List<Map<String, String>> spend = rows(printer().&printSpend)

        expect: 'every season the league has played under superflex, and the pooled span besides'
        spend.collect { it.SEASON }.toSet() ==
                (AuctionSpend.SUPERFLEX_SEASONS + ['2023-2025']) as Set

        and: 'the share of every dollar sums to a hundred, kickers included'
        spend.groupBy { it.SEASON }.every { String season, List<Map<String, String>> rows ->
            (rows.collect { new BigDecimal(it.SHARE) }.sum() - 100.0).abs() < 0.2
        }

        and: 'and so does the share of the four priced positions, kickers left out'
        spend.groupBy { it.SEASON }.every { String season, List<Map<String, String>> rows ->
            (rows.findAll { it.SHAREXPK }.collect { new BigDecimal(it.SHAREXPK) }.sum() - 100.0).abs() < 0.2
        }
    }

    def "leaves the kicker out of the basis he is not in, rather than calling it nil"() {
        given:
        List<Map<String, String>> spend = rows(printer().&printSpend)

        expect: 'a kicker has no share of a total he was excluded from, which is not a share of zero'
        spend.findAll { it.POS == 'PK' }.every { it.SHAREXPK == '' && new BigDecimal(it.SHARE) > 0 }
    }

    /**
     * The tags are the one figure here that is inferred rather than computed, so what is asserted is that
     * the inference holds together — a confirmed tag is priced at the rate unless somebody paid picks for
     * him, and the one-per-team rule the identifier never enforces across teams comes out satisfied anyway.
     */
    def "writes every confirmed tag, priced at the rate unless it was bid away"() {
        given:
        List<Map<String, String>> tags = rows(printer().&printTags)

        expect: 'every season a tag can be recovered for, and nothing uncertain'
        tags.collect { it.SEASON }.toSet() == TagHistory.SEASONS as Set
        tags.every { it.STATUS == 'CONFIRMED' }

        and: 'an uncontested tag is paid exactly the rate, which is what makes it recognisable as one'
        tags.findAll { it.BASIS == 'exact' }.every { it.SALARY == it.RATE }

        and: 'and one bid away went above it, since nobody gives up a pick to pay the same money'
        tags.findAll { it.BASIS != 'exact' }.every {
            (it.SALARY as int) > (it.RATE as int) && it.BASIS.startsWith('bid away')
        }
    }

    def "the rate a tag was paid is the rate the calculator produces for that season"() {
        given:
        Map<String, String> rates = rows(printer().&printRates)
                .collectEntries { [("$it.SEASON $it.POS" as String): it.RATE] }

        expect: 'the two tables are written from one calculation, and say so'
        rows(printer().&printTags).every { rates["$it.SEASON $it.POS" as String] == it.RATE }
    }

    def "carries a rate for the season being priced, which has no auction and so no tags"() {
        given:
        List<Map<String, String>> rates = rows(printer().&printRates)

        expect: 'a rate is known from salaries already paid, where a tag needs an auction to have happened'
        rates.collect { it.SEASON }.toSet() == TagHistory.RATE_SEASONS as Set
        rows(printer().&printTags).collect { it.SEASON }.toSet() == TagHistory.SEASONS as Set
        !(TagHistory.SEASONS.contains(TagHistory.RATE_SEASONS.last()))
    }

    def "the pooled row is the calibration target, which is what the model actually uses"() {
        given:
        Map<String, Map<String, String>> pooled = rows(printer().&printSpend)
                .findAll { it.SEASON == '2023-2025' }.collectEntries { [(it.POS): it] }

        expect: 'the four priced positions calibrate to their share of the four-position pot'
        AuctionSpend.EXCLUDING_KICKERS.every {
            (new BigDecimal(pooled[it].SHAREXPK) / 100 - AuctionValuation.MARKET_SHARE[it]).abs() < 0.005
        }
    }
}
