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
                LoadUtils.mflOwnersResourcePath(year),
                LoadUtils.mflLeagueResourcePath(year),
                LoadUtils.mflRostersResourcePath(year),
                LoadUtils.mflDraftResourcePath(year)
        )
        def mflPlayer = data.playerByNameMap[name]

        then:
        mflPlayer == player
        data.franchiseByIdMap.size() == numFranchises
        data.franchiseByIdMap['0001'].id == '0001'
        data.franchiseByIdMap['0001'].name == franchiseName
        data.franchiseByIdMap['0001'].ownerName == 'Brett'
        data.franchiseByIdMap['0001'].players.find { p -> p == player }
        data.draftPicks[1].round == 1
        data.draftPicks[1].pick == 2

        where:
        year << [LoadUtils.YEARS.last()]
        numFranchises << [10]
        name << ['Chris Olave']
        franchiseName << ['Zeke Squad']
        player << [
                new MflPlayer(new Player('Chris Olave', 'NOS', 'WR'), new Contract(3, 1), '15754', false, new Draft(1, 11))
        ]
    }
}
