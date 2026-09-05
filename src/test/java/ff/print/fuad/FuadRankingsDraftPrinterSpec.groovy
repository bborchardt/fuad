package ff.print.fuad

import ff.data.Player
import ff.data.PlayerValuation
import ff.data.Rank
import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.mfl.MflData
import ff.data.mfl.MflFranchise
import spock.lang.Specification

/**
 * The rankings sheet quotes the auction board and never a price of its own.
 *
 * It used to carry a second model — a curve fitted straight from positional rank to dollars — so the same
 * player could be worth one number here and another on the salaries board, with nothing to say which was
 * meant. See docs/fuad/PROJECTION.md.
 */
class FuadRankingsDraftPrinterSpec extends Specification {

    private static FuadPlayer ranked(String position, int rank, String name, String mflId) {
        new FuadPlayer(player: new Player(name: name, position: position, team: 'BAL'),
                redraftRank: new Rank(overallRank: rank, positionRank: rank),
                dynastyRank: new Rank(overallRank: rank, positionRank: rank),
                mflId: mflId, bye: '7')
    }

    private static PlayerValuation priced(String mflId, String position, int rank, int market, int cost,
                                         int worth = market) {
        new PlayerValuation(playerId: mflId, playerName: 'ignored', position: position, positionRank: rank,
                marketSalary: market, salary: cost, value: worth, acquisitionSalary: market,
                points: 0.0, pointsPerGame: 0.0, expectedGames: 0.0, pointsLow: 0.0, pointsHigh: 0.0,
                valueOverReplacement: 0.0, availability: 1.0)
    }

    private static FuadData dataFor(List<FuadPlayer> qbs) {
        new FuadData(mflData: new MflData(franchiseByIdMap:
                ['0001': new MflFranchise(id: '0001', name: 'Test', ownerName: 'Brett', players: [])]),
                qbRanks: qbs, rbRanks: [], wrRanks: [], teRanks: [], pkRanks: [])
    }

    /** Every line of the sheet, the headings first. */
    private static List<String> lines(List<FuadPlayer> qbs, List<PlayerValuation> valuations) {
        StringWriter out = new StringWriter()
        new FuadRankingsDraftPrinter(dataFor(qbs), valuations).print(new PrintWriter(out))
        out.toString().readLines()
    }

    /** The columns of the first player of the first list, which is the quarterbacks. */
    private static List<String> firstRow(List<FuadPlayer> qbs, List<PlayerValuation> valuations) {
        lines(qbs, valuations)[1].split('\t', -1) as List
    }

    def "quotes the price the auction board settled on"() {
        given: 'a player the board prices at 83, whose team is expected to tag him at 66'
        List<FuadPlayer> qbs = [ranked('QB', 2, 'Lamar Jackson', 'p1')]

        when:
        List<String> row = firstRow(qbs, [priced('p1', 'QB', 2, 83, 66)])

        then: 'the sheet a bid is made from carries what open bidding is expected to pay'
        row[6] == '83'
    }

    def "leaves a player the board does not price blank rather than at a number"() {
        given: 'ranked deeper than the curve still makes a claim at, so no valuation exists for him'
        List<FuadPlayer> qbs = [ranked('QB', 140, 'Deep Reserve', 'p9')]

        expect: 'blank, not zero: the board declines to price him rather than valuing him at nothing'
        firstRow(qbs, [])[6] == ''
    }

    def "prices nothing off a player the league data does not carry"() {
        given: 'a ranked player with no mfl id, so nothing can be joined to him'
        FuadPlayer unmatched = new FuadPlayer(
                player: new Player(name: 'No Id', position: 'QB', team: 'BAL'),
                redraftRank: new Rank(overallRank: 4, positionRank: 4), bye: '7')

        expect: 'blank, and no accidental join onto whatever a null key would find'
        firstRow([unmatched], [priced('p1', 'QB', 2, 83, 66)])[6] == ''
    }

    def "quotes the board's value beside the price, so the two can be read against each other"() {
        given: 'a player worth 91 whom open bidding is expected to settle at 83'
        List<FuadPlayer> qbs = [ranked('QB', 2, 'Lamar Jackson', 'p1')]

        when:
        List<String> row = firstRow(qbs, [priced('p1', 'QB', 2, 83, 66, 91)])

        then: 'value sits immediately left of the price it is judged against'
        row[5] == '91'
        row[6] == '83'
    }

    def "leaves value blank, not zero, where the board does not carry the player"() {
        given: 'ranked deeper than the curve still makes a claim at'
        List<FuadPlayer> qbs = [ranked('QB', 140, 'Deep Reserve', 'p9')]

        expect: 'the board declines to say what he is worth rather than saying nothing'
        firstRow(qbs, [])[5] == ''
    }

    def "keeps the sheet's shape: rank, rank, name, team/bye, holder, value, price, actual"() {
        given:
        List<FuadPlayer> qbs = [ranked('QB', 2, 'Lamar Jackson', 'p1')]

        when:
        List<String> row = firstRow(qbs, [priced('p1', 'QB', 2, 83, 66, 91)])

        then:
        row[0] == '2'
        row[1] == '2'
        row[2] == 'Lamar Jackson'
        row[3] == 'BAL/7'
        row[4] == 'UFA'
        row[5] == '91'
        row[6] == '83'

        and: 'the actual price is left for the reader to type on draft night'
        row[7] == ''
    }

    def "heads every column a reader has to name, and only those"() {
        when:
        List<String> heading = lines([ranked('QB', 2, 'Lamar Jackson', 'p1')], [])[0].split('\t', -1) as List

        then: 'one block a position, the last of them the column the actual price is typed into'
        heading[0..7] == ['D', 'R', 'Player', 'Bye', 'Owner', 'V', 'P', 'A']

        and: 'the column separating one position from the next is a gap and is not named'
        heading[8] == ''
        heading[9] == 'D'

        and: 'five positions, so five blocks, and the headings line up with the rows beneath them'
        heading.size() == 5 * 8 + 4
        heading.size() == firstRow([ranked('QB', 2, 'Lamar Jackson', 'p1')], []).size()
    }
}
