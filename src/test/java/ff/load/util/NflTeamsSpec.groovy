package ff.load.util

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Teams named the several ways the sources name them.
 *
 * None of the name matching that works for players helps here: "Chicago (CHI)" and "Bears" share no prefix,
 * no suffix and no word.
 */
class NflTeamsSpec extends Specification {

    @Unroll
    def "#name is #abbreviation"() {
        expect:
        NflTeams.abbreviationOf(name) == abbreviation

        where:
        name                      || abbreviation
        'Bears'                   || 'CHI'        // the draft export: nickname alone
        'Chicago Bears'           || 'CHI'        // FantasyPros, most seasons
        'Chicago (CHI)'           || 'CHI'        // FantasyPros, 2018 to 2020
        'San Francisco 49ers'     || 'SF'         // a nickname that is a number
        '49ers'                   || 'SF'
        'Jacksonville (JAX)'      || 'JAC'        // an abbreviation the sources spell two ways
        'Los Angeles (LAR)'       || 'LAR'        // both LA teams share a city, so the city cannot decide
        'Los Angeles (LAC)'       || 'LAC'
        'Rams'                    || 'LAR'
        'Chargers'                || 'LAC'
        'Oakland Raiders'         || 'LV'         // moved, so the city and abbreviation changed
        'Las Vegas Raiders'       || 'LV'
        'San Diego Chargers'      || 'LAC'
    }

    @Unroll
    def "Washington is WAS as the #nickname"() {
        expect: 'renamed twice inside the collected seasons, and the old names are in the record'
        NflTeams.abbreviationOf(name) == 'WAS'

        where:
        nickname           | name
        'Redskins'         | 'Washington Redskins'
        'Football Team'    | 'Washington Football Team'
        'Commanders'       | 'Commanders'
        'abbreviated form' | 'Washington (WAS)'
    }

    @Unroll
    def "#name is not a team"() {
        expect: 'null is how a caller tells a defence from a player without knowing which it holds'
        NflTeams.abbreviationOf(name) == null

        where:
        name << ['Andrew Luck', 'Wil Lutz', 'Gabe Davis', 'Rob Kelley', 'Ben Watson',
                 'Chris Godwin Jr.', '', null]
    }

    def "every team is distinct, so no two resolve onto one another"() {
        given:
        List<String> nicknames = ['Bills', 'Dolphins', 'Patriots', 'Jets', 'Ravens', 'Bengals', 'Browns',
                                  'Steelers', 'Texans', 'Colts', 'Jaguars', 'Titans', 'Broncos', 'Chiefs',
                                  'Raiders', 'Chargers', 'Cowboys', 'Giants', 'Eagles', 'Commanders',
                                  'Bears', 'Lions', 'Packers', 'Vikings', 'Falcons', 'Panthers', 'Saints',
                                  'Buccaneers', 'Cardinals', 'Rams', '49ers', 'Seahawks']

        expect:
        nicknames.size() == 32
        nicknames.collect { NflTeams.abbreviationOf(it) }.unique().size() == 32
        nicknames.every { NflTeams.isTeam(it) }
    }
}
