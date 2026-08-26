package ff.load.nflverse

import spock.lang.Specification
import spock.lang.Unroll

/**
 * How a defence scores, and the two columns that do not mean what their names suggest.
 */
class DstScoringRulesSpec extends Specification {

    private static final DstScoringRules RULES = DstScoringRules.GREENFIELD

    @Unroll
    def "conceding #conceded points is worth #points"() {
        expect:
        RULES.pointsAllowedValue(conceded) == points

        where:
        conceded || points
        0        || 10.0
        6        || 7.0
        7        || 4.0
        13       || 4.0
        14       || 1.0
        20       || 1.0
        21       || 0.0
        27       || 0.0
        28       || -1.0
        34       || -1.0
        35       || -4.0
        70       || -4.0
    }

    def "points allowed is the largest term, and the only one that can go negative"() {
        expect: 'a shutout against thirty five conceded is fourteen points, on a weekly total of about seven'
        RULES.pointsAllowedValue(0) - RULES.pointsAllowedValue(35) == 14.0
    }

    def "an interception return and a fumble return are both touchdowns, and do not overlap"() {
        given: 'the release counts interception returns in def_tds and fumble returns separately'
        Map<String, String> week = [def_tds: '1', fumble_recovery_tds: '1', fumble_recovery_opp: '2',
                                    points_allowed: '21']

        expect: 'two touchdowns at six, two recoveries at two, and nothing for conceding 21'
        RULES.score(week) == 6.0 * 2 + 2.0 * 2
    }

    def "a fumble returned for a touchdown only counts where an opponent's fumble was recovered"() {
        given: 'a team that recovered only its own fumble and scored on it -- an offensive touchdown'
        Map<String, String> own = [fumble_recovery_tds: '1', fumble_recovery_opp: '0', points_allowed: '21']

        and: 'the same week with an opponent fumble recovered, which the defence could have returned'
        Map<String, String> opponents = [fumble_recovery_tds: '1', fumble_recovery_opp: '1',
                                         points_allowed: '21']

        expect: 'the first scores nothing at all, the second a touchdown and a recovery'
        RULES.score(own) == 0.0
        RULES.score(opponents) == 6.0 + 2.0
    }

    def "return touchdowns score for the defence, the unit being defence and special teams"() {
        expect:
        RULES.score([special_teams_tds: '1', points_allowed: '21']) == 6.0
    }

    def "every kind of blocked kick is worth the same"() {
        expect:
        RULES.score([def_punt_blocks: '1', points_allowed: '21']) == 2.0
        RULES.score([def_pat_blocks: '1', points_allowed: '21']) == 2.0
        RULES.score([def_fg_blocks: '1', points_allowed: '21']) == 2.0
    }

    def "a whole week scores as the league would score it"() {
        given: 'three sacks, a pick, a forced fumble recovered, a safety, and thirteen conceded'
        Map<String, String> week = [def_sacks: '3', def_interceptions: '1', fumble_recovery_opp: '1',
                                    def_safeties: '1', points_allowed: '13']

        expect:
        RULES.score(week) == 3 * 1.0 + 2.0 + 2.0 + 2.0 + 4.0
    }

    def "a missing column is nothing rather than an error, a quiet week having no rows to speak of"() {
        expect:
        RULES.score([points_allowed: '0']) == 10.0
    }
}
