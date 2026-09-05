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
 * Three tables, because they answer different questions. The board is about players and is read best first;
 * the picks are read in draft order, and a pick has a price whoever is taken with it; the draft sheet is the
 * board cut to what a room can read at speed, with the draft order beside it. What they have to get right is
 * the arithmetic a reader will not redo: that surplus is value less salary over the years committed, that
 * the salary on a pick is the one bylaw 8.3 charges there, and that the draft sheet's two halves are two
 * tables sharing a row number rather than one table to be read across.
 */
class FuadRookieDraftPrinterSpec extends Specification {

    private static RookieValue rookie(Map overrides = [:]) {
        new RookieValue([
                playerId             : 'p1',
                playerName           : 'Rookie One',
                position             : 'WR',
                overallRank          : 1,
                positionRank         : 1,
                dynastyRank          : 23,
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
        printer(values, []).printBoard(new PrintWriter(out))
        out.toString().readLines().collect { it.split('\t', -1) as List<String> }
    }

    private static List<List<String>> picks(List<MflDraftPick> drafted, Map<String, Integer> baselines = null,
                                            Map<String, Map<Integer, Integer>> best = [:]) {
        StringWriter out = new StringWriter()
        printer([], drafted, baselines ?: [QB: 20, RB: 7, WR: 1, TE: 1, PK: 1], best)
                .printPicks(new PrintWriter(out))
        out.toString().readLines().collect { it.split('\t', -1) as List<String> }
    }

    private static List<List<String>> draft(List<RookieValue> values, List<MflDraftPick> drafted) {
        StringWriter out = new StringWriter()
        printer(values, drafted).printDraft(new PrintWriter(out))
        out.toString().readLines().collect { it.split('\t', -1) as List<String> }
    }

    def "every row carries the same columns as the header"() {
        given:
        List<List<String>> rows = board([rookie(), rookie(bye: null, expectedPick: null)])

        expect:
        rows*.size().unique().size() == 1
        rows.first().first() == 'PLAYER'
    }

    def "sorts by value, best first, so the sheet needs no sorting to read"() {
        given:
        List<List<String>> rows = board([
                rookie(playerName: 'Middling', valueByYear: [10, 10, 10, 10, 10]),
                rookie(playerName: 'Best', valueByYear: [40, 40, 40, 40, 40]),
                rookie(playerName: 'Worst', valueByYear: [1, 1, 1, 1, 1])])
        int name = rows.first().indexOf('PLAYER')

        expect:
        rows.tail()*.getAt(name) == ['Best', 'Middling', 'Worst']
    }

    /**
     * The dynasty rank is carried as position and rank together, and blank where the ranking has no view.
     *
     * A blank is not missing data. Not being ranked among a few hundred dynasty assets is a fact about a
     * deep rookie, and it is also why he carries no adjustment to his level.
     */
    /**
     * The two rankings side by side, both as a position and a rank, and the dynasty one blank where absent.
     *
     * They are read together or not at all. What moves a rookie's level is where the dynasty ranking puts
     * him <b>against where rookies of his standing usually sit</b>, so the dynasty column means nothing
     * without the rookie one beside it: WR3 at WR23 is a claim, WR2 at WR25 is not, and that is the whole of
     * why Lemon prices above Tyson when the rookie ranking prefers Tyson.
     *
     * A blank dynasty rank is not missing data. Not being ranked among a few hundred dynasty assets is a
     * fact about a deep rookie, and it is also why he carries no adjustment at all.
     */
    def "carries both rankings as a position and a rank, the dynasty one blank where absent"() {
        given:
        List<String> header = board([rookie()]).first()

        expect: 'positional, so neither needs the POS column to be read'
        board([rookie()])[1][header.indexOf('FP_ROOKIE')] == 'WR1'
        board([rookie()])[1][header.indexOf('FP_DYNASTY')] == 'WR23'

        and: 'the overall rank kept apart, being what DEMAND is keyed on'
        board([rookie()])[1][header.indexOf('FP_OVERALL')] == '1'

        and: 'and nothing where the dynasty ranking does not carry him'
        board([rookie(dynastyRank: null)])[1][header.indexOf('FP_DYNASTY')] == ''
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
        header.indexOf('VAL_LOW') < header.indexOf('VALUE')
        header.indexOf('VALUE') < header.indexOf('VAL_HIGH')
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
        'expectedPick' | 'DEMAND'
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

    def "heads the draft sheet's two halves and separates them with an unnamed column"() {
        given:
        List<String> heading = draft([rookie()], [pick(1, 1, 'Martin Someone')]).first()

        expect: 'what the room reads, then a gap, then what it writes down'
        heading == ['Player', 'Bye', 'NFL', 'Pos', 'VL', 'V', 'VH', 'FP', 'D', '',
                    'Pick', 'Owner', 'Player']
    }

    def "ranks a position on the draft sheet by value, not by where the consensus put him"() {
        given: 'the consensus prefers the receiver it ranks WR1, and the model does not'
        List<RookieValue> values = [
                rookie(playerName: 'Preferred', positionRank: 1, overallRank: 1, valueByYear: [10]),
                rookie(playerName: 'Better', positionRank: 4, overallRank: 9, valueByYear: [90]),
                rookie(playerName: 'A Back', position: 'RB', positionRank: 1, overallRank: 3,
                        valueByYear: [50]),
        ]

        when:
        List<List<String>> rows = draft(values, [])

        then: 'the column says who to take, so the best receiver is WR1 whatever he is ranked'
        rows[1][0] == 'Better' && rows[1][3] == 'WR1'
        rows[2][0] == 'A Back' && rows[2][3] == 'RB1'
        rows[3][0] == 'Preferred' && rows[3][3] == 'WR2'
    }

    def "writes the team and bye as one column, and says so where there is neither"() {
        given: 'a drafted rookie, and one nobody took'
        List<RookieValue> values = [rookie(nflTeam: 'CIN', bye: 10, valueByYear: [90]),
                                    rookie(playerName: 'Undrafted', nflTeam: null, bye: null,
                                           valueByYear: [10])]

        when:
        List<List<String>> rows = draft(values, [])

        then:
        rows[1][1] == 'CIN/10'

        and: 'a question mark rather than a blank, which reads as a column that failed to fill'
        rows[2][1] == '?/?'
    }

    def "numbers a pick by round and slot, which is how the room calls it"() {
        given:
        List<MflDraftPick> drafted = [pick(1, 1, 'Martin Someone'), pick(1, 10, 'Jeff Other'),
                                      pick(2, 1, 'Martin Someone')]

        when:
        List<List<String>> rows = draft([rookie()], drafted)

        then:
        rows[1][10..12] == ['1.1', 'Martin', '']
        rows[2][10..12] == ['1.10', 'Jeff', '']
        rows[3][10..12] == ['2.1', 'Martin', '']
    }

    def "pads whichever half runs out first, so neither can slide up against the other"() {
        given: 'more rookies than picks, which is every draft'
        List<RookieValue> values = [rookie(playerName: 'First', valueByYear: [90]),
                                    rookie(playerName: 'Second', valueByYear: [10])]

        when:
        List<List<String>> rows = draft(values, [pick(1, 1, 'Martin Someone')])

        then: 'the row past the last pick still carries its rookie'
        rows.size() == 3
        rows[2][0] == 'Second'
        rows[2][10..12] == ['', '', '']

        and: 'every row is the same width, so an import does not shift the halves against each other'
        rows*.size().unique() == [13]
    }

    def "leaves the player column empty for the room to fill in"() {
        expect: 'nothing this model produces belongs there: it is a record of what happened'
        draft([rookie()], [pick(1, 1, 'Martin Someone')])[1][12] == ''
    }
}
