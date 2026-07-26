package ff.schedule.fuad

import ff.data.fuad.FuadMatchup
import ff.data.mfl.MflFranchise
import ff.load.mfl.MflLoader
import ff.load.util.LoadUtils
import spock.lang.Specification
import spock.lang.Unroll

class FuadScheduleGeneratorSpec extends Specification {

    def "even sized divisions (2 franchises each): division rivals play twice, cross rivals play at least once"() {
        given:
        List<MflFranchise> franchises = [
                new MflFranchise(id: 'A1', name: 'A1', ownerName: 'Owner', division: '00', players: []),
                new MflFranchise(id: 'A2', name: 'A2', ownerName: 'Owner', division: '00', players: []),
                new MflFranchise(id: 'B1', name: 'B1', ownerName: 'Owner', division: '01', players: []),
                new MflFranchise(id: 'B2', name: 'B2', ownerName: 'Owner', division: '01', players: []),
        ]

        when:
        List<FuadMatchup> matchups = new FuadScheduleGenerator(4, new Random(42)).generate(franchises)

        then:
        matchups == [
                new FuadMatchup(1, 'A1', 'B2'),
                new FuadMatchup(1, 'A2', 'B1'),
                new FuadMatchup(2, 'A1', 'A2'),
                new FuadMatchup(2, 'B2', 'B1'),
                new FuadMatchup(3, 'A1', 'B1'),
                new FuadMatchup(3, 'A2', 'B2'),
                new FuadMatchup(4, 'A1', 'A2'),
                new FuadMatchup(4, 'B2', 'B1'),
        ]
    }

    def "odd sized divisions (3 franchises each): division rivals play twice, cross rivals play at least once"() {
        given:
        List<MflFranchise> franchises = [
                new MflFranchise(id: 'A1', name: 'A1', ownerName: 'Owner', division: '00', players: []),
                new MflFranchise(id: 'A2', name: 'A2', ownerName: 'Owner', division: '00', players: []),
                new MflFranchise(id: 'A3', name: 'A3', ownerName: 'Owner', division: '00', players: []),
                new MflFranchise(id: 'B1', name: 'B1', ownerName: 'Owner', division: '01', players: []),
                new MflFranchise(id: 'B2', name: 'B2', ownerName: 'Owner', division: '01', players: []),
                new MflFranchise(id: 'B3', name: 'B3', ownerName: 'Owner', division: '01', players: []),
        ]

        when:
        List<FuadMatchup> matchups = new FuadScheduleGenerator(8, new Random(42)).generate(franchises)

        then:
        matchups == [
                new FuadMatchup(1, 'A2', 'B1'),
                new FuadMatchup(1, 'A1', 'B2'),
                new FuadMatchup(1, 'A3', 'B3'),
                new FuadMatchup(2, 'A1', 'A3'),
                new FuadMatchup(2, 'B3', 'B1'),
                new FuadMatchup(2, 'A2', 'B2'),
                new FuadMatchup(3, 'A2', 'A1'),
                new FuadMatchup(3, 'B2', 'B3'),
                new FuadMatchup(3, 'A3', 'B1'),
                new FuadMatchup(4, 'A1', 'A3'),
                new FuadMatchup(4, 'B3', 'B1'),
                new FuadMatchup(4, 'A2', 'B2'),
                new FuadMatchup(5, 'A2', 'A3'),
                new FuadMatchup(5, 'B2', 'B1'),
                new FuadMatchup(5, 'A1', 'B3'),
                new FuadMatchup(6, 'A2', 'A1'),
                new FuadMatchup(6, 'B2', 'B3'),
                new FuadMatchup(6, 'A3', 'B1'),
                new FuadMatchup(7, 'A2', 'A3'),
                new FuadMatchup(7, 'B2', 'B1'),
                new FuadMatchup(7, 'A1', 'B3'),
                new FuadMatchup(8, 'A2', 'B3'),
                new FuadMatchup(8, 'A1', 'B1'),
                new FuadMatchup(8, 'A3', 'B2'),
        ]
    }

    @Unroll
    def "#year real league produces #expectedTotalMatchups total matchups over 14 weeks"() {
        given:
        Collection<MflFranchise> franchises = new MflLoader().loadData(
                LoadUtils.mflPlayersResourcePath(year),
                LoadUtils.mflOwnersResourcePath(year),
                LoadUtils.mflLeagueResourcePath(year),
                LoadUtils.mflRostersResourcePath(year),
                LoadUtils.mflDraftResourcePath(year)
        ).franchiseByIdMap.values()

        when:
        List<FuadMatchup> matchups = new FuadScheduleGenerator().generate(franchises)

        then:
        matchups.size() == expectedTotalMatchups

        where:
        year   | expectedTotalMatchups
        '2025' | 70
        '2022' | 56
    }

    def "schedules are randomized between runs"() {
        given:
        List<MflFranchise> franchises = [
                new MflFranchise(id: 'A1', name: 'A1', ownerName: 'Owner', division: '00', players: []),
                new MflFranchise(id: 'A2', name: 'A2', ownerName: 'Owner', division: '00', players: []),
                new MflFranchise(id: 'B1', name: 'B1', ownerName: 'Owner', division: '01', players: []),
                new MflFranchise(id: 'B2', name: 'B2', ownerName: 'Owner', division: '01', players: []),
        ]

        when:
        List<FuadMatchup> first = new FuadScheduleGenerator().generate(franchises)
        List<FuadMatchup> second = new FuadScheduleGenerator().generate(franchises)

        then:
        first != second
    }

    def "requires exactly 2 divisions"() {
        given:
        List<MflFranchise> franchises = [
                new MflFranchise(id: '0001', name: 'F1', ownerName: 'Owner', division: '00', players: []),
                new MflFranchise(id: '0002', name: 'F2', ownerName: 'Owner', division: '01', players: []),
                new MflFranchise(id: '0003', name: 'F3', ownerName: 'Owner', division: '02', players: []),
                new MflFranchise(id: '0004', name: 'F4', ownerName: 'Owner', division: '02', players: []),
        ]

        when:
        new FuadScheduleGenerator().generate(franchises)

        then:
        thrown(IllegalArgumentException)
    }

    def "rejects unequal sized divisions using real, uneven league data"() {
        given:
        Collection<MflFranchise> franchises = new MflLoader().loadData(
                LoadUtils.mflPlayersResourcePath('2023'),
                LoadUtils.mflOwnersResourcePath('2023'),
                LoadUtils.mflLeagueResourcePath('2023'),
                LoadUtils.mflRostersResourcePath('2023'),
                LoadUtils.mflDraftResourcePath('2023')
        ).franchiseByIdMap.values()

        when:
        new FuadScheduleGenerator().generate(franchises)

        then:
        thrown(IllegalArgumentException)
    }

    def "rejects division sizes too large to fill 14 byeless weeks"() {
        given:
        List<MflFranchise> franchises = (1..6).collect {
            new MflFranchise(id: "A$it", name: "A$it", ownerName: 'Owner', division: '00', players: [])
        } + (1..6).collect {
            new MflFranchise(id: "B$it", name: "B$it", ownerName: 'Owner', division: '01', players: [])
        }

        when:
        new FuadScheduleGenerator().generate(franchises)

        then:
        thrown(IllegalArgumentException)
    }
}
