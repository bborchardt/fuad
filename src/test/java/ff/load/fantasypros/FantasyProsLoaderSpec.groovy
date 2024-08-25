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
                ['Justin Jefferson': new FpRankedPlayer(new Player('Justin Jefferson', 'MIN', 'WR'), new Rank(1, 1), '6'),
                 'CeeDee Lamb'     : new FpRankedPlayer(new Player('CeeDee Lamb', 'DAL', 'WR'), new Rank(2, 2), '7')]
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
                ['Justin Jefferson': new FpRankedPlayer(new Player('Justin Jefferson', 'MIN', 'WR'), new Rank(8, 5), '6'),
                 'CeeDee Lamb'     : new FpRankedPlayer(new Player('CeeDee Lamb', 'DAL', 'WR'), new Rank(2, 1), '7')]
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
                ['Malik Nabers'      : new FpRankedPlayer(new Player('Malik Nabers', 'NYG', 'WR'), new Rank(2, 2), '0'),
                 'Xavier Worthy': new FpRankedPlayer(new Player('Xavier Worthy', 'KC', 'WR'), new Rank(5, 4), '0')]
        ]
    }
}
