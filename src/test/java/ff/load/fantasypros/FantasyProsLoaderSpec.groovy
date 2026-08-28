package ff.load.fantasypros

import ff.data.Player
import ff.data.Rank
import ff.data.fantasypros.FpRankedPlayer
import ff.load.util.LoadUtils
import spock.lang.Specification
import spock.lang.Unroll

class FantasyProsLoaderSpec extends Specification {

    @Unroll
    def "#year dynasty PPR loading"() {
        when:
        Map<String, FpRankedPlayer> players = new FantasyProsLoader().loadRankedPlayers(
                LoadUtils.fpDynastyRankingsPprResourcePath(year))

        then:
        expected.each { name, player ->
            assert players[name] == player
        }

        where:
        year << [LoadUtils.YEARS.last()]
        expected << [
                ["Ja'Marr Chase"   : new FpRankedPlayer(new Player("Ja'Marr Chase", 'CIN', 'WR'),
                        new Rank(5, 1), '0'),
                 'Justin Jefferson': new FpRankedPlayer(new Player('Justin Jefferson', 'MIN', 'WR'),
                         new Rank(17, 5), '0')]
        ]
    }

    @Unroll
    def "#year redraft half PPR loading"() {
        when:
        Map<String, FpRankedPlayer> players = new FantasyProsLoader().loadRankedPlayers(
                LoadUtils.fpRedraftRankingsHalfPprResourcePath(year))

        then:
        expected.each { name, player ->
            assert players[name] == player
        }

        where:
        year << [LoadUtils.YEARS.last()]
        expected << [
                ["Ja'Marr Chase"   : new FpRankedPlayer(new Player("Ja'Marr Chase", 'CIN', 'WR'),
                        new Rank(9, 1), '6'),
                 'Justin Jefferson': new FpRankedPlayer(new Player('Justin Jefferson', 'MIN', 'WR'),
                         new Rank(22, 6), '6')]
        ]
    }

    @Unroll
    def "#year rookie loading"() {
        when:
        Map<String, FpRankedPlayer> players = new FantasyProsLoader().loadRankedPlayers(
                LoadUtils.fpRookieRankingsPprResourcePath(year))

        then:
        expected.each { name, player ->
            assert players[name] == player
        }

        where:
        year << [LoadUtils.YEARS.last()]
        expected << [
                ['Jeremiyah Love': new FpRankedPlayer(new Player('Jeremiyah Love', 'ARI', 'RB'),
                        new Rank(1, 1), '0'),
                 'Carnell Tate'  : new FpRankedPlayer(new Player('Carnell Tate', 'TEN', 'WR'),
                         new Rank(3, 1), '0')]
        ]
    }

    def "quoted comma separated loading"() {
        when:
        Map<String, FpRankedPlayer> players = new FantasyProsLoader().loadRankedPlayers([
                '"RK",TIERS,"PLAYER NAME",TEAM,"POS","BYE WEEK","ECR VS. ADP"',
                '"1",1,"Josh Allen",BUF,"QB1","7","0"',
                '"2",1,"Brown, Marvin",CIN,"WR1","6","+3"',
                '"3",2,"Hollywood Brown",PHI,"WR2","10","-1"'
        ])

        then:
        players['Josh Allen'] == new FpRankedPlayer(new Player('Josh Allen', 'BUF', 'QB'), new Rank(1, 1), '7')
        players['Marvin Brown'] == new FpRankedPlayer(new Player('Marvin Brown', 'CIN', 'WR'), new Rank(2, 1), '6')
        players['Marquise Brown'] == new FpRankedPlayer(new Player('Marquise Brown', 'PHI', 'WR'), new Rank(3, 2), '10')
    }
}
