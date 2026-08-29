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
     * Column order is a decision, not a layout.
     *
     * A sheet read under time pressure is read left to right, so what a reader chooses on goes first and
     * what merely qualifies it follows. Salary went off the player sheet entirely: it is a fact about a
     * pick, it lives on the pick sheet beside the pick, and carrying it here at the pick a rookie is
     * *expected* to go at made every column downstream of it an assumption.
     */
    def "puts what a reader chooses on to the left of what he does not"() {
        given:
        List<String> header = board([rookie()]).first()

        expect: 'the value and its bounds before the year by year shape'
        header.indexOf('VALUE') < header.indexOf('Y1')
        header.indexOf('VALLOW') < header.indexOf('VALUE')
        header.indexOf('VALUE') < header.indexOf('VALHIGH')
        header.last() == 'Y5'
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

}
