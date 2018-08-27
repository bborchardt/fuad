package ff.load.mfl

import ff.data.Contract
import ff.data.Draft
import ff.data.Player
import ff.data.mfl.MflData
import ff.data.mfl.MflPlayer
import ff.load.util.LoadUtils
import spock.lang.Specification
import spock.lang.Unroll

class MflLoaderSpec extends Specification {

    @Unroll
    def "#year mfl loading"() {
        when:
        MflData data = new MflLoader().loadData(
                LoadUtils.mflPlayersResourcePath(year),
                LoadUtils.mflLeadResourcePath(year),
                LoadUtils.mflRostersResourcePath(year),
                LoadUtils.mflDraftResourcePath(year)
        )
        def mflPlayer = data.playerByNameMap[name]

        then:
        mflPlayer == player
        data.franchiseByIdMap.size() == 10
        data.franchiseByIdMap['0001'].id == '0001'
        data.franchiseByIdMap['0001'].name == 'Brett Borchardt'
        data.franchiseByIdMap['0001'].players.find { p -> p == player }
        data.draftPicks[1].round == 1
        data.draftPicks[1].pick == 2

        where:
        year << ['2017', '2018']
        name << ['Dez Bryant', 'Dalvin Cook']
        player << [
                new MflPlayer(new Player('Dez Bryant', 'DAL', 'WR'), new Contract(2, 50), '9823', false, new Draft(1, 24)),
                new MflPlayer(new Player('Dalvin Cook', 'MIN', 'RB'), new Contract(4, 2), '13128', false, new Draft(2, 9))
        ]
    }
}
