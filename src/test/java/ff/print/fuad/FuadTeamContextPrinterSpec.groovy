package ff.print.fuad

import ff.data.Contract
import ff.data.Player
import ff.data.PlayerValuation
import ff.data.fuad.FuadData
import ff.data.mfl.MflData
import ff.data.mfl.MflFranchise
import ff.data.mfl.MflPlayer
import ff.projection.StarterRequirements
import spock.lang.Specification

/**
 * A team's roster holes are reported rather than priced, which is what lets a plan see the one position the
 * model cannot value at all. See docs/STRATEGY.md.
 */
class FuadTeamContextPrinterSpec extends Specification {

    /** The 2026 lineup: ten of QB 1-2, RB 1-3, WR 2-5, TE 1-3, PK 1. */
    private static StarterRequirements lineup() {
        new StarterRequirements([QB: 1, RB: 1, WR: 2, TE: 1, PK: 1],
                [QB: 2, RB: 3, WR: 5, TE: 3, PK: 1], 10, 10)
    }

    private static MflPlayer signed(String position, String name) {
        new MflPlayer(player: new Player(name: name, position: position),
                contract: new Contract(salary: 1, years: 2), id: name)
    }

    private static FuadData dataFor(List<MflPlayer> players) {
        new FuadData(mflData: new MflData(franchiseByIdMap:
                ['0001': new MflFranchise(id: '0001', name: 'Test', ownerName: 'Brett', players: players)]))
    }

    private static Map<String, String> row(List<MflPlayer> players) {
        StringWriter out = new StringWriter()
        new FuadTeamContextPrinter(dataFor(players), [] as List<PlayerValuation>, 300, lineup())
                .print(new PrintWriter(out))
        List<String> lines = out.toString().readLines()
        [lines[0].split('\t') as List, lines[1].split('\t') as List].transpose()
                .collectEntries { List pair -> [(pair[0]): pair[1]] }
    }

    def "names a position the team cannot field a legal lineup at"() {
        given: 'a roster with no kicker, which no points curve can ever flag'
        List<MflPlayer> players = [signed('QB', 'A'), signed('RB', 'B'),
                                   signed('WR', 'C'), signed('WR', 'D'), signed('TE', 'E')]

        expect:
        row(players).NEEDS == 'PK:1'
    }

    def "counts how many short, where a position wants more than one"() {
        given: 'two receivers are required and none are held'
        List<MflPlayer> players = [signed('QB', 'A'), signed('RB', 'B'), signed('TE', 'C'), signed('PK', 'D')]

        expect:
        row(players).NEEDS == 'WR:2'
    }

    def "says nothing where a roster can field its lineup"() {
        given:
        List<MflPlayer> players = [signed('QB', 'A'), signed('RB', 'B'), signed('WR', 'C'),
                                   signed('WR', 'D'), signed('TE', 'E'), signed('PK', 'F')]

        expect:
        row(players).NEEDS == ''
    }

    def "counts a player the lineup cannot use against the cap and the roster all the same"() {
        given: 'a kicker, who scores nothing anywhere in this model'
        List<MflPlayer> players = [signed('QB', 'A'), signed('PK', 'B')]

        when:
        Map<String, String> row = row(players)

        then: 'his salary is committed and his roster spot is taken, whatever the lineup makes of him'
        row.SIGNED == '2'
        row.COMMITTED == '2'
        row.FREECAP == '298'
        row.PKSIGNED == '1'

        and: 'and the position is not reported short, because it is not'
        !row.NEEDS.contains('PK')
    }
}
