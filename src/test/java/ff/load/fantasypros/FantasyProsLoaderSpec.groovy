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
        year << LoadUtils.YEARS
        expected << [
                ['Odell Beckham Jr.': new FpRankedPlayer(new Player('Odell Beckham Jr.', 'NYG', 'WR'), new Rank(1, 1), '8'),
                 'David Johnson'    : new FpRankedPlayer(new Player('David Johnson', 'ARI', 'RB'), new Rank(4, 1), '8')],
                ['Odell Beckham Jr.': new FpRankedPlayer(new Player('Odell Beckham Jr.', 'NYG', 'WR'), new Rank(3, 2), '9'),
                 'David Johnson'    : new FpRankedPlayer(new Player('David Johnson', 'ARI', 'RB'), new Rank(8, 5), '9')],
                ['Odell Beckham Jr.': new FpRankedPlayer(new Player('Odell Beckham Jr.', 'CLE', 'WR'), new Rank(5, 2), '7'),
                 'David Johnson'    : new FpRankedPlayer(new Player('David Johnson', 'ARI', 'RB'), new Rank(13, 7), '12')],
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
        year << LoadUtils.YEARS
        expected << [
                ['Odell Beckham Jr.': new FpRankedPlayer(new Player('Odell Beckham Jr.', 'NYG', 'WR'), new Rank(5, 3), '8'),
                 'David Johnson'    : new FpRankedPlayer(new Player('David Johnson', 'ARI', 'RB'), new Rank(1, 1), '8')],
                ['Odell Beckham Jr.': new FpRankedPlayer(new Player('Odell Beckham Jr.', 'NYG', 'WR'), new Rank(8, 3), '9'),
                 'David Johnson'    : new FpRankedPlayer(new Player('David Johnson', 'ARI', 'RB'), new Rank(3, 3), '9')],
                ['Odell Beckham Jr.': new FpRankedPlayer(new Player('Odell Beckham Jr.', 'CLE', 'WR'), new Rank(11, 4), '7'),
                 'David Johnson'    : new FpRankedPlayer(new Player('David Johnson', 'ARI', 'RB'), new Rank(5, 5), '12')],
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
        year << LoadUtils.YEARS
        expected << [
                ['Corey Davis'      : new FpRankedPlayer(new Player('Corey Davis', 'TEN', 'WR'), new Rank(1, 1), '8'),
                 'Leonard Fournette': new FpRankedPlayer(new Player('Leonard Fournette', 'JAC', 'RB'), new Rank(2, 1), '8')],
                ['Saquon Barkley': new FpRankedPlayer(new Player('Saquon Barkley', 'NYG', 'RB'), new Rank(1, 1), '9'),
                 'DJ Moore'    : new FpRankedPlayer(new Player('DJ Moore', 'CAR', 'WR'), new Rank(6, 1), '4')],
                ['Josh Jacobs': new FpRankedPlayer(new Player('Josh Jacobs', 'OAK', 'RB'), new Rank(1, 1), '6'),
                 'Justice Hill'    : new FpRankedPlayer(new Player('Justice Hill', 'BAL', 'RB'), new Rank(12, 5), '8')],
        ]
    }
}
