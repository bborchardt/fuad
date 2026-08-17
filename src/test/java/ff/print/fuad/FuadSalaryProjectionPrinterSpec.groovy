package ff.print.fuad

import ff.data.Player
import ff.data.PlayerValuation
import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.mfl.MflData
import ff.data.mfl.MflFranchise
import spock.lang.Specification

/**
 * The auction board's shape, which is a contract rather than a layout.
 *
 * This sheet is the whole permitted input to a draft plan, and `check_strategy.sh` verifies a plan's tables
 * against it <b>by heading</b>: it reads the first line for column names and every line after it
 * positionally. So a value written under the wrong heading is not a cosmetic fault. It is a figure a plan
 * cites, a check that confirms it, and both of them wrong together, with nothing left to notice.
 *
 * Two of these columns make that easy to do. `TAG` is the franchise tag <b>price</b> and `FRANCHISED` is the
 * marker saying whether the tag was used, which is the opposite of what the names suggest, and they sit
 * beside each other. `COST` and `TAG` then hold the same number for every tagged player, so a fixture where
 * they agree cannot tell them apart. The fixtures below are built so that no two columns share a value
 * unless the model requires it. See docs/STRATEGY.md.
 */
class FuadSalaryProjectionPrinterSpec extends Specification {

    /**
     * The columns, in order.
     *
     * Written out rather than derived from the printer, since deriving it from the thing under test would
     * assert only that the printer agrees with itself. A plan written last week names these.
     */
    private static final List<String> COLUMNS = [
            'POS', 'TIER', 'RANK', 'DYNRANK', 'PLAYER', 'TEAM', 'HOLDER', 'BYE', 'PTS', 'PPG', 'G',
            'PTSLOW', 'PTSHIGH', 'VOR', 'VALUE', 'PRICE', 'COST', 'ACQUIRE', 'EDGE', 'BAND', 'RFRCOST',
            'AVAIL', 'TAG', 'FRANCHISED']

    /**
     * One priced player, every field given a value nothing else on the row carries.
     *
     * Distinct on purpose. A row of round numbers and repeated zeros will pass a column-order assertion that
     * a transposition would sail through, so each figure here is chosen to be unmistakable for any other.
     */
    private static PlayerValuation valuation(Map overrides = [:]) {
        new PlayerValuation([
                playerId          : 'p1',
                playerName        : 'Lamar Jackson',
                position          : 'QB',
                positionRank      : 2,
                dynastyRank       : 5,
                tier              : 3,
                points            : 246.4,
                pointsPerGame     : 21.44,
                expectedGames     : 11.49,
                pointsLow         : 88.7,
                pointsHigh        : 370.2,
                bye               : 13,
                valueOverReplacement: 104.6,
                value             : 67,
                marketSalary      : 83,
                salary            : 83,
                acquisitionSalary : 91,
                availability      : 0.26,
                franchiseSalary   : 66,
                franchiseId       : '0001',
                franchiseTagged   : false,
        ] + overrides)
    }

    private static FuadData dataFor(String team = 'BAL') {
        new FuadData(
                mflData: new MflData(franchiseByIdMap: ['0001': new MflFranchise(
                        id: '0001', name: 'Test Team', ownerName: 'Brett Someone', players: [])]),
                playerByNameMap: ['Lamar Jackson': new FuadPlayer(
                        player: new Player(name: 'Lamar Jackson', position: 'QB', team: team),
                        mflId: 'p1')],
                qbRanks: [], rbRanks: [], wrRanks: [], teRanks: [], pkRanks: [])
    }

    private static List<String> lines(List<PlayerValuation> valuations, FuadData data = dataFor()) {
        StringWriter out = new StringWriter()
        new FuadSalaryProjectionPrinter(data, valuations).print(new PrintWriter(out))
        out.toString().readLines()
    }

    /** One row read the way check_strategy.sh reads it: headings from line one, values by position. */
    private static Map<String, String> row(List<PlayerValuation> valuations, FuadData data = dataFor()) {
        List<String> printed = lines(valuations, data)
        List<String> headings = printed[0].split('\t', -1) as List
        List<String> cells = printed[1].split('\t', -1) as List
        headings.withIndex().collectEntries { String heading, int i ->
            [(heading): i < cells.size() ? cells[i] : null]
        }
    }

    def "carries the columns a plan is allowed to name, in the order it reads them"() {
        expect:
        lines([valuation()])[0].split('\t', -1) as List == COLUMNS
    }

    def "writes a cell for every column, so nothing after a gap shifts left"() {
        given: 'a player missing the two figures that are legitimately absent'
        Map<String, String> written = row([valuation(dynastyRank: null, bye: null)])

        expect: 'the row is as wide as the header, the missing figures being empty rather than skipped'
        written.size() == COLUMNS.size()
        written.DYNRANK == ''
        written.BYE == ''

        and: 'and everything after them is still under its own heading'
        written.PLAYER == 'Lamar Jackson'
        written.PTS == '246'
    }

    /**
     * The assertion the rest of this file exists for: every figure under its own name.
     *
     * Read by heading rather than by index, which is how a plan and its check both read it, so this fails
     * for a column moved as well as for a value written wrongly.
     */
    def "writes every figure under the heading that names it"() {
        given:
        Map<String, String> written = row([valuation()])

        expect: 'what the player is'
        written.POS == 'QB'
        written.TIER == '3'
        written.RANK == '2'
        written.DYNRANK == '5'
        written.PLAYER == 'Lamar Jackson'
        written.TEAM == 'BAL'
        written.BYE == '13'

        and: 'what he is expected to score, the two halves rounded as the board prints them'
        written.PTS == '246'
        written.PPG == '21.4'
        written.G == '11.5'
        written.PTSLOW == '89'
        written.PTSHIGH == '370'
        written.VOR == '105'

        and: 'and what he costs, which is four different questions'
        written.VALUE == '67'
        written.PRICE == '83'
        written.COST == '83'
        written.ACQUIRE == '91'
        written.AVAIL == '0.26'
    }

    /**
     * TAG is a price and FRANCHISED is a marker, which their names do not say and their order does not help.
     *
     * Asserted on a player who is <b>not</b> tagged and whose tag price differs from everything else on his
     * row, since for a tagged player COST and TAG are the same number by construction and a fixture built
     * that way could not tell one from the other.
     */
    def "TAG is what the tag would cost, and FRANCHISED whether it was used"() {
        given: 'a player his team could tag for 66 but is expected to pay 83 for in the open'
        Map<String, String> written = row([valuation()])

        expect:
        written.TAG == '66'
        written.FRANCHISED == ''
        written.COST == '83'
    }

    def "a tagged player costs the tag, and says so"() {
        given: 'the same player, tagged: his team pays the rate and he never reaches the bidding'
        Map<String, String> written = row([valuation(franchiseTagged: true, salary: 66)])

        expect: 'COST is the tag while PRICE stays the counterfactual he would have fetched'
        written.FRANCHISED == 'TAG'
        written.COST == '66'
        written.TAG == '66'
        written.PRICE == '83'
    }

    /**
     * The three derived columns, which exist so a plan never computes them itself and gets them different.
     */
    def "derives edge, its band and the cost of the right to match from the columns beside them"() {
        given:
        Map<String, String> written = row([valuation()])

        expect: 'worth less price, banded rather than given to the dollar'
        written.EDGE == '-16'
        written.BAND == 'fair'

        and: 'and what the incumbent right to match costs an outside bidder, over the price'
        written.RFRCOST == '8'
    }

    def "bands an edge the board is confident about rather than calling it fair"() {
        expect: 'a quarter of the price, or five dollars, whichever is larger'
        row([valuation(value: 130)]).BAND == 'BARGAIN'
        row([valuation(value: 20)]).BAND == 'OVERPRICED'
    }

    def "names the owner holding an expiring contract, and UFA where nobody does"() {
        expect: 'the first name, which is how the league refers to itself'
        row([valuation()]).HOLDER == 'Brett Someone'.split(' ')[0]

        and: 'and a player nobody rosters is unrestricted, which is a different thing from unknown'
        row([valuation(franchiseId: null)]).HOLDER == 'UFA'
    }

    def "leaves the team blank for a player the league data does not carry"() {
        given: 'priced from his rank, but joined to no league player, so there is no team to print'
        Map<String, String> written = row([valuation(playerName: 'Absent Player')])

        expect: 'blank rather than a stray join onto whatever a missing name would find'
        written.TEAM == ''
        written.PLAYER == 'Absent Player'
    }

    def "prints one row per priced player and nothing for an empty board"() {
        expect:
        lines([valuation(), valuation(playerId: 'p2', playerName: 'Someone Else')]).size() == 3
        lines([]).size() == 1
    }
}
