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
import spock.lang.Specification

/**
 * The franchise sheet quotes the auction board and never a price of its own.
 *
 * It used to project expiring contracts from a curve fitted straight from positional rank to dollars, so a
 * roster could be totalled here at one figure and on the salaries board at another. The column is now
 * {@code COST} — what the holding team actually pays, tag included. See docs/PROJECTION.md.
 */
class FuadFranchiseDraftPrinterSpec extends Specification {

    private static MflPlayer held(String position, String name, String mflId, Contract contract = null) {
        new MflPlayer(player: new Player(name: name, position: position), contract: contract, id: mflId)
    }

    private static PlayerValuation priced(String mflId, String position, int rank, int market, int cost) {
        new PlayerValuation(playerId: mflId, playerName: 'ignored', position: position, positionRank: rank,
                marketSalary: market, salary: cost, value: market, acquisitionSalary: market,
                points: 0.0, pointsPerGame: 0.0, expectedGames: 0.0, pointsLow: 0.0, pointsHigh: 0.0,
                valueOverReplacement: 0.0, availability: 1.0)
    }

    private static FuadData dataFor(List<MflPlayer> players) {
        Map<String, FuadPlayer> byName = players.collectEntries { MflPlayer p ->
            [(p.player.name): new FuadPlayer(player: p.player, mflId: p.id,
                    redraftRank: new Rank(overallRank: 2, positionRank: 2))]
        }
        new FuadData(mflData: new MflData(franchiseByIdMap:
                ['0001': new MflFranchise(id: '0001', name: 'Test', ownerName: 'Brett', players: players)]),
                playerByNameMap: byName)
    }

    /** The first quarterback row: name, position and rank, salary, years. */
    private static List<String> firstPlayerRow(List<MflPlayer> players, List<PlayerValuation> valuations,
                                               boolean projections) {
        StringWriter out = new StringWriter()
        new FuadFranchiseDraftPrinter(dataFor(players), valuations, projections).print(new PrintWriter(out))
        // Line 0 is the franchise header; line 1 is the first quarterback slot.
        out.toString().readLines()[1].split('\t', -1) as List
    }

    def "fills an expiring contract in at what the board says his team will pay"() {
        given: 'no contract, so he is expiring, and the board expects his team to tag him at 66'
        List<MflPlayer> players = [held('QB', 'Lamar Jackson', 'p1')]

        when:
        List<String> row = firstPlayerRow(players, [priced('p1', 'QB', 2, 83, 66)], true)

        then: 'the tag price, not the 83 open bidding would have gone to'
        row[0] == 'Lamar Jackson'
        row[2] == '66'
    }

    def "leaves a contract already running exactly as it stands"() {
        given: 'a signed player, whom no projection has any business overwriting'
        List<MflPlayer> players = [held('QB', 'Josh Allen', 'p2', new Contract(salary: 55, years: 1))]

        when:
        List<String> row = firstPlayerRow(players, [priced('p2', 'QB', 2, 90, 90)], true)

        then:
        row[2] == '55'
        row[3] == '1'
    }

    def "leaves an expiring player the board does not price blank rather than at a number"() {
        given: 'expiring, and too deep for the curve to make a claim about, so the board omits him'
        List<MflPlayer> players = [held('QB', 'Deep Reserve', 'p9')]

        expect:
        firstPlayerRow(players, [], true)[2] == ''
    }

    def "projects nothing at all without being asked to"() {
        given: 'the plain franchises sheet, which lists rosters and prices nothing'
        List<MflPlayer> players = [held('QB', 'Lamar Jackson', 'p1')]

        expect: 'no dollars, whatever the board would have said'
        firstPlayerRow(players, [priced('p1', 'QB', 2, 83, 66)], false)[2] == ''
    }
}
