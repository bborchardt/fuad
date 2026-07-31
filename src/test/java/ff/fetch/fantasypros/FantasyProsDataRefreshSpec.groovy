package ff.fetch.fantasypros

import ff.data.Player
import ff.data.Rank
import ff.data.fantasypros.FpRankedPlayer
import ff.load.fantasypros.FantasyProsLoader
import groovy.json.JsonSlurper
import spock.lang.Specification

class FantasyProsDataRefreshSpec extends Specification {

    private static Map rankings(String... playerJson) {
        new JsonSlurper().parseText("""{
            "sport": "NFL", "year": "2026", "count": ${playerJson.length},
            "players": [${playerJson.join(',')}]
        }""") as Map
    }

    private static String player(int rank, String name, String team, String posRank, int tier, String bye) {
        """{"rank_ecr": $rank, "tier": $tier, "player_name": "$name", "player_team_id": "$team",
            "pos_rank": "$posRank", "player_bye_week": "$bye"}"""
    }

    def "rejects a response the api truncated to a subset of the ranking set"() {
        given:
        Map truncated = rankings(player(1, 'Josh Allen', 'BUF', 'QB1', 1, '7'))
        truncated.count = 540

        when:
        FantasyProsDataRefresh.verifyComplete('http://example.com/rankings', truncated)

        then:
        IllegalStateException e = thrown()
        e.message.contains('only 1 of 540 players')
    }

    def "accepts a response holding the whole ranking set"() {
        when:
        FantasyProsDataRefresh.verifyComplete('http://example.com/rankings',
                rankings(player(1, 'Josh Allen', 'BUF', 'QB1', 1, '7')))

        then:
        noExceptionThrown()
    }

    def "converts a ranking response to the tab separated layout"() {
        when:
        String tsv = FantasyProsDataRefresh.toTabSeparated(rankings(
                player(1, 'Josh Allen', 'BUF', 'QB1', 1, '7'),
                player(2, 'Ja\'Marr Chase', 'CIN', 'WR1', 1, '10')))

        then:
        tsv == 'RK\tTIERS\tPLAYER NAME\tTEAM\tPOS\tBYE WEEK\n' +
                '1\t1\tJosh Allen\tBUF\tQB1\t7\n' +
                '2\t1\tJa\'Marr Chase\tCIN\tWR1\t10\n'
    }

    def "the converted output is readable by the rankings loader"() {
        given:
        String tsv = FantasyProsDataRefresh.toTabSeparated(rankings(
                player(1, 'Josh Allen', 'BUF', 'QB1', 1, '7'),
                player(2, 'Justin Jefferson', 'MIN', 'WR1', 1, '6'),
                player(3, 'Cameron Dicker', 'LAC', 'K1', 12, '12')))

        when:
        Map<String, FpRankedPlayer> players = new FantasyProsLoader().loadRankedPlayers(tsv.readLines())

        then:
        players['Josh Allen'] == new FpRankedPlayer(new Player('Josh Allen', 'BUF', 'QB'), new Rank(1, 1), '7')
        players['Justin Jefferson'] == new FpRankedPlayer(new Player('Justin Jefferson', 'MIN', 'WR'), new Rank(2, 1), '6')
        players['Cameron Dicker'] == new FpRankedPlayer(new Player('Cameron Dicker', 'LAC', 'PK'), new Rank(3, 1), '12')
    }
}
