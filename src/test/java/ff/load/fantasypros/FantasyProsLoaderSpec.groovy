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
                ['Justin Jefferson' : new FpRankedPlayer(new Player('Justin Jefferson', 'MIN', 'WR'),
                        new Rank(2, 2), '0'),
                 'Amon-Ra St. Brown': new FpRankedPlayer(new Player('Amon-Ra St. Brown', 'DET', 'WR'),
                         new Rank(10, 7), '0')]
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
                ['Justin Jefferson': new FpRankedPlayer(new Player('Justin Jefferson', 'MIN', 'WR'),
                        new Rank(5, 2), '6'),
                 'CeeDee Lamb'     : new FpRankedPlayer(new Player('CeeDee Lamb', 'DAL', 'WR'),
                         new Rank(6, 3), '10')]
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
                ['Omarion Hampton': new FpRankedPlayer(new Player('Omarion Hampton', 'LAC', 'RB'),
                        new Rank(3, 2), '0'),
                 'Travis Hunter'  : new FpRankedPlayer(new Player('Travis Hunter', 'JAC', 'WR'),
                         new Rank(5, 2), '0')]
        ]
    }
}
