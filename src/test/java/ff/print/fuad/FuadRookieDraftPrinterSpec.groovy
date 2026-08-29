package ff.print.fuad

import ff.data.fuad.FuadData
import ff.data.fuad.RookieValue
import ff.data.mfl.MflData
import ff.data.mfl.MflDraftPick
import ff.data.mfl.MflFranchise
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The rookie sheet: what a pick is worth over the contract it comes with, and what the pick itself costs.
 *
 * Two tables, because they answer different questions. The first is about players and is read in consensus
 * order; the second is about picks and is read in draft order, and a pick has a price whoever is taken with
 * it. What both have to get right is the arithmetic a reader will not redo: that surplus is value less
 * salary over the years committed, and that the salary on a pick is the one bylaw 8.3 charges there.
 */
class FuadRookieDraftPrinterSpec extends Specification {

    private static RookieValue rookie(Map overrides = [:]) {
        new RookieValue([
                playerId             : 'p1',
                playerName           : 'Rookie One',
                position             : 'WR',
                overallRank          : 1,
                positionRank         : 1,
                nflTeam              : 'CIN',
                bye                  : 10,
                pointsOverReplacement: [20.4g, 30.0g, 30.0g, 10.0g, 5.0g],
                valueByYear          : [20, 30, 30, 10, 5],
                expectedPick         : 2,
                salary               : 4,
                contractLength       : 5,
                surplus              : 75,
                valueLow             : 70,
                valueHigh            : 125,
                tier                 : 2,
        ] + overrides)
    }

    private static MflDraftPick pick(int round, int slot, String owner, String name = 'Some Team',
                                     String id = '0001') {
        new MflDraftPick(round: round, pick: slot,
                franchise: new MflFranchise(id: id, name: name, ownerName: owner, players: []))
    }

    private static FuadRookieDraftPrinter printer(List<RookieValue> values, List<MflDraftPick> picks,
                                                  Map<String, Integer> baselines = [QB: 20, RB: 7, WR: 1, TE: 1, PK: 1],
                                                  Map<String, Map<Integer, Integer>> best = [:]) {
        new FuadRookieDraftPrinter(new FuadData(rookieRanks: [],
                mflData: new MflData(draftPicks: picks, franchiseByIdMap: [:], playerByNameMap: [:])),
                values, baselines, best)
    }

    private static List<List<String>> board(List<RookieValue> values) {
        StringWriter out = new StringWriter()
        printer(values, []).print(new PrintWriter(out))
        out.toString().readLines().collect { it.split('\t', -1) as List<String> }
    }

    private static List<List<String>> picks(List<MflDraftPick> drafted, Map<String, Integer> baselines = null,
                                            Map<String, Map<Integer, Integer>> best = [:]) {
        StringWriter out = new StringWriter()
        printer([], drafted, baselines ?: [QB: 20, RB: 7, WR: 1, TE: 1, PK: 1], best)
                .printPicks(new PrintWriter(out))
        out.toString().readLines().collect { it.split('\t', -1) as List<String> }
    }

    def "every row carries the same columns as the header"() {
        given:
        List<List<String>> rows = board([rookie(), rookie(bye: null, expectedPick: null)])

        expect:
        rows*.size().unique().size() == 1
        rows.first().first() == 'OVR'
    }

    def "a year of the contract is a column, so the shape of a career is visible rather than summed away"() {
        given:
        List<String> header = board([rookie()]).first()
        List<String> row = board([rookie()])[1]

        expect: 'five years, first year first'
        header[(header.indexOf('Y1'))..(header.indexOf('Y5'))] == ['Y1', 'Y2', 'Y3', 'Y4', 'Y5']
        row[(header.indexOf('Y1'))..(header.indexOf('Y5'))] == ['20', '30', '30', '10', '5']
    }

    /**
     * The column the whole sheet exists for, and the one nobody can check by eye.
     *
     * A rookie worth $20 in his first year against a $4 salary has $16 of surplus this season and $59 after
     * it. Reading only the first would price him as an auction player, which is exactly the mistake the
     * board is here to stop.
     */
    def "separates the surplus that falls after this season from the whole of it"() {
        given:
        List<String> header = board([rookie()]).first()
        List<String> row = board([rookie()])[1]

        expect:
        row[header.indexOf('SURPLUS')] == '75'
        row[header.indexOf('DEFER')] == '59'
    }

    @Unroll
    def "a missing #field prints empty rather than a zero that reads as a measurement"() {
        given:
        List<String> header = board([rookie((field): null)]).first()
        List<String> row = board([rookie((field): null)])[1]

        expect:
        row[header.indexOf(column)] == ''

        where:
        field          | column
        'bye'          | 'BYE'
        'expectedPick' | 'PICK'
    }

    /**
     * The price ladder, which is the half of the sheet a trade is read off.
     *
     * Bylaw 8.3 decays the baseline by a fifth for every pick already made, so the same position costs less
     * at every subsequent pick regardless of who is taken. A quarterback baseline of 20 is 20 at the first
     * pick and 16 at the second.
     */
    def "prices every pick from its own position in the draft"() {
        given:
        List<List<String>> rows = picks([pick(1, 1, 'Brett'), pick(1, 2, 'Jeff')])
        List<String> header = rows.first()

        expect:
        rows[1][header.indexOf('$QB')] == '20'
        rows[2][header.indexOf('$QB')] == '16'

        and: 'a baseline already at the minimum cannot decay below it'
        rows[1][header.indexOf('$WR')] == '1'
        rows[2][header.indexOf('$WR')] == '1'
    }

    def "numbers picks across the whole draft, which is what the salary decays by"() {
        given:
        List<List<String>> rows = picks([pick(1, 9, 'Brett'), pick(1, 10, 'Jeff'), pick(2, 1, 'Chris')])
        List<String> header = rows.first()

        expect: 'the third row is the third pick, though it is the first of round two'
        rows[3][header.indexOf('PICK')] == '3'
        rows[3][header.indexOf('ROUND')] == '2'
        rows[3][header.indexOf('SLOT')] == '1'
    }

    def "shortens an owner to the name anybody uses at the draft"() {
        given:
        List<List<String>> rows = picks([pick(2, 4, 'Brett Borchardt')])

        expect:
        rows[1][rows.first().indexOf('TEAM')] == 'Brett'
    }

    def "falls back to the franchise name where no owner is recorded"() {
        given:
        List<List<String>> rows = picks([pick(2, 4, null, 'Wolfpack Reloaded')])

        expect:
        rows[1][rows.first().indexOf('TEAM')] == 'Wolfpack'
    }

    /**
     * A pick too deep for the drafts to speak about is left blank.
     *
     * Only five of the nine drafts reach a fiftieth pick, so an average taken there would be an average over
     * whichever years happened to run long. Blank says that; a number would not.
     */
    def "leaves the best available blank where the drafts have not been there often enough"() {
        given:
        List<List<String>> rows = picks([pick(1, 1, 'Brett'), pick(1, 2, 'Jeff')], null, [WR: [1: 3]])
        List<String> header = rows.first()

        expect:
        rows[1][header.indexOf('BESTWR')] == '3'
        rows[2][header.indexOf('BESTWR')] == ''
    }

    private static List<List<String>> outlook(List<RookieValue> values, List<MflDraftPick> drafted,
                                              Map<String, Map<Integer, Integer>> best = [:],
                                              String franchiseId = '0001') {
        StringWriter out = new StringWriter()
        printer(values, drafted, [QB: 20, RB: 7, WR: 1, TE: 1, PK: 1], best)
                .printOutlook(new PrintWriter(out), franchiseId)
        out.toString().readLines().collect { it.split('\t', -1) as List<String> }
    }

    def "an outlook covers only the team's own picks"() {
        given:
        List<List<String>> rows = outlook([rookie()],
                [pick(1, 1, 'Brett'), pick(1, 2, 'Jeff', 'Some Team', '0002'), pick(1, 3, 'Brett')])

        expect: 'the first and third picks, and not the one belonging to somebody else'
        rows.tail()*.getAt(0) == ['1', '3']
    }

    /**
     * A rookie costs what the pick costs, so the same player is a different transaction at 1.02 and 2.02.
     *
     * This is the reason an outlook exists beside the board at all: the board reports him at the pick he is
     * expected to go at, which is the neutral reading and is not the pick being made.
     */
    def "prices a rookie at the pick being made rather than the one he is expected to go at"() {
        given: 'a quarterback, whose baseline of 20 has not yet decayed at the first pick'
        RookieValue quarterback = rookie(position: 'QB', valueByYear: [20, 30, 30, 10, 5], expectedPick: 30)
        List<List<String>> first = outlook([quarterback], [pick(1, 1, 'Brett')])
        List<List<String>> sixth = outlook([quarterback],
                (1..6).collect { pick(1, it, 'Brett') }).findAll { it.first() == '6' }
        List<String> header = first.first()

        expect: 'twenty dollars at the first pick, and a fifth off for each one made before the sixth'
        first[1][header.indexOf('SALARY')] == '20'
        sixth.first()[header.indexOf('SALARY')] == '7'

        and: 'so the surplus is smaller where the salary is larger'
        (first[1][header.indexOf('SURPLUS')] as int) < (sixth.first()[header.indexOf('SURPLUS')] as int)
    }

    /**
     * Ordered by what each is worth <b>here</b>, which is not the order the board is in.
     *
     * A quarterback taken before his baseline has decayed costs real money, and one taken twenty picks later
     * costs a dollar. Sorting on the board's surplus would rank these by a price nobody is paying.
     */
    def "orders candidates by their surplus at this pick"() {
        given: 'a quarterback worth more, and a receiver whose salary is already at the minimum'
        RookieValue quarterback = rookie(playerName: 'QB One', position: 'QB',
                valueByYear: [20, 20, 20, 20, 20])
        RookieValue receiver = rookie(playerName: 'WR One', position: 'WR',
                valueByYear: [18, 18, 18, 18, 18])
        List<List<String>> rows = outlook([quarterback, receiver], [pick(1, 1, 'Brett')])
        int name = rows.first().indexOf('PLAYER')

        expect: 'the receiver first, the quarterback costing 20 of his 20 a year at the opening pick'
        rows.tail()*.getAt(name) == ['WR One', 'QB One']
    }

    /**
     * Every rookie is priced at every pick, and availability is a flag rather than a filter.
     *
     * <b>The one thing a shortlist cannot survive is somebody falling.</b> A rookie the room was expected to
     * take at pick three is exactly who a reader most needs priced when he is still there at nine, and the
     * outlook used to leave him off. Before a draft the flag is what a plan reads; during one it is worth
     * nothing, the board being in view.
     */
    def "prices every rookie at the pick and marks who is expected to be there"() {
        given:
        RookieValue best = rookie(playerName: 'Gone', positionRank: 1)
        RookieValue later = rookie(playerName: 'There', positionRank: 4)
        List<List<String>> rows = outlook([best, later], [pick(1, 1, 'Brett')], [WR: [1: 3]])
        int name = rows.first().indexOf('PLAYER')
        int expected = rows.first().indexOf('EXP')

        expect: 'both are priced, with the third receiver typically the best still on the board'
        rows.tail()*.getAt(name).toSet() == ['Gone', 'There'].toSet()

        and: 'and the one the drafts do not expect to reach this pick is marked, not dropped'
        rows.tail().find { it[name] == 'There' }[expected] == 'Y'
        rows.tail().find { it[name] == 'Gone' }[expected] == ''

        and: 'with nothing measured for that pick, everybody is expected'
        outlook([best, later], [pick(1, 1, 'Brett')], [:]).tail().every { it[expected] == 'Y' }
    }

    def "carries what he is worth beside what the contract is worth"() {
        given:
        List<String> header = board([rookie()]).first()
        List<String> row = board([rookie()])[1]

        expect: 'ninety-five of value against a twenty dollar contract leaves seventy-five'
        row[header.indexOf('VALUE')] == '95'
        row[header.indexOf('SURPLUS')] == '75'
    }

    def "carries the bounds either side of the value, which are not symmetric"() {
        given:
        List<String> header = board([rookie()]).first()
        List<String> row = board([rookie()])[1]

        expect: 'twenty-five below and thirty above a value of ninety-five'
        row[header.indexOf('VALLOW')] == '70'
        row[header.indexOf('VALUE')] == '95'
        row[header.indexOf('VALHIGH')] == '125'
    }

    def "carries the tier, so a gap inside the evidence reads as one"() {
        given:
        List<String> header = board([rookie()]).first()
        List<String> row = board([rookie()])[1]

        expect:
        row[header.indexOf('TIER')] == '2'
    }
}
