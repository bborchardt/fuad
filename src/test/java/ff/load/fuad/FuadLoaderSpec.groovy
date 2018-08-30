package ff.load.fuad

import ff.data.Contract
import ff.data.Draft
import ff.data.Player
import ff.data.Rank
import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.mfl.MflPlayer
import spock.lang.Specification

class FuadLoaderSpec extends Specification {

    def "2017 fuad loader"() {
        when:
        FuadData data = new FuadLoader().loadData('2017')
        def mflPlayer = data.mflData.playerByNameMap['Dez Bryant']
        def fuadPlayer = data.playerByNameMap['Dez Bryant']

        then:
        data.mflData.franchiseByIdMap.size() == 10
        mflPlayer == new MflPlayer(
                new Player('Dez Bryant', 'DAL', 'WR'),
                new Contract(2, 50),
                '9823',
                false,
                new Draft(1, 24)
        )
        fuadPlayer == new FuadPlayer(
                new Player('Dez Bryant', 'DAL', 'WR'),
                new Rank(19, 13),
                new Rank(16, 8),
                null,
                new Contract(2, 50),
                '9823',
                false,
                '6',
                new Draft(1, 24)
        )
        data.rookieRanks[0].player.name == 'Corey Davis'
        data.rookieRanks[0].draft == new Draft(1, 5)
    }

    def "2018 fuad loader"() {
        when:
        FuadData data = new FuadLoader().loadData('2018')
        def mflPlayer = data.mflData.playerByNameMap['Todd Gurley']
        def fuadPlayer = data.playerByNameMap['Todd Gurley']

        then:
        data.mflData.franchiseByIdMap.size() == 10
        mflPlayer == new MflPlayer(
                new Player('Todd Gurley', 'LAR', 'RB'),
                new Contract(2, 8),
                '12150',
                false,
                new Draft(1, 10)
        )
        fuadPlayer == new FuadPlayer(
                new Player('Todd Gurley', 'LAR', 'RB'),
                new Rank(2, 1),
                new Rank(1, 1),
                null,
                new Contract(2, 8),
                '12150',
                false,
                '12',
                new Draft(1, 10)
        )
        data.rookieRanks[0].player.name == 'Saquon Barkley'
        data.rookieRanks[0].draft == new Draft(1, 2)
    }
}
