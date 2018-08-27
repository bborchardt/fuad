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
        year << ['2017', '2018']
        expected << [
                ['Odell Beckham Jr.': new FpRankedPlayer(new Player('Odell Beckham Jr.', 'NYG', 'WR'), new Rank(1, 1), '8'),
                 'David Johnson'    : new FpRankedPlayer(new Player('David Johnson', 'ARI', 'RB'), new Rank(4, 1), '8')],
                ['Odell Beckham Jr.': new FpRankedPlayer(new Player('Odell Beckham Jr.', 'NYG', 'WR'), new Rank(3, 2), '9'),
                 'David Johnson'    : new FpRankedPlayer(new Player('David Johnson', 'ARI', 'RB'), new Rank(8, 5), '9')],
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
        year << ['2017', '2018']
        expected << [
                ['Odell Beckham Jr.': new FpRankedPlayer(new Player('Odell Beckham Jr.', 'NYG', 'WR'), new Rank(5, 3), '8'),
                 'David Johnson'    : new FpRankedPlayer(new Player('David Johnson', 'ARI', 'RB'), new Rank(1, 1), '8')],
                ['Odell Beckham Jr.': new FpRankedPlayer(new Player('Odell Beckham Jr.', 'NYG', 'WR'), new Rank(8, 3), '9'),
                 'David Johnson'    : new FpRankedPlayer(new Player('David Johnson', 'ARI', 'RB'), new Rank(3, 3), '9')],
        ]
    }

    @Unroll
    def "#year redraft PPR loading"() {
        when:
        Map<String, FpRankedPlayer> players = new FantasyProsLoader().loadRankedPlayers(
                LoadUtils.fpRedraftRankingsPprResourcePath(year))

        then:
        expected.each { name, player ->
            assert players[name] == player
        }

        where:
        year << ['2017', '2018']
        expected << [
                ['Odell Beckham Jr.': new FpRankedPlayer(new Player('Odell Beckham Jr.', 'NYG', 'WR'), new Rank(6, 3), '8'),
                 'David Johnson'    : new FpRankedPlayer(new Player('David Johnson', 'ARI', 'RB'), new Rank(1, 1), '8')],
                ['Odell Beckham Jr.': new FpRankedPlayer(new Player('Odell Beckham Jr.', 'NYG', 'WR'), new Rank(8, 3), '9'),
                 'David Johnson'    : new FpRankedPlayer(new Player('David Johnson', 'ARI', 'RB'), new Rank(3, 3), '9')],
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
        year << ['2017', '2018']
        expected << [
                ['Corey Davis'      : new FpRankedPlayer(new Player('Corey Davis', 'TEN', 'WR'), new Rank(1, 1), '8'),
                 'Leonard Fournette': new FpRankedPlayer(new Player('Leonard Fournette', 'JAC', 'RB'), new Rank(2, 1), '8')],
                ['Saquon Barkley': new FpRankedPlayer(new Player('Saquon Barkley', 'NYG', 'RB'), new Rank(1, 1), '9'),
                 'D.J. Moore'    : new FpRankedPlayer(new Player('D.J. Moore', 'CAR', 'WR'), new Rank(5, 1), '4')],
        ]
    }
}
