package ff.load.nflverse

import spock.lang.Specification
import spock.lang.Unroll

/**
 * The league scores a field goal by how far it was kicked, and from 2026 it keeps going by the decade.
 *
 * Kicking was not scored at all until kickers were levelled, on the belief that nflverse does not carry
 * it — which was true of this project's extract and of nothing else. See docs/LEAGUE_RULES.md.
 */
class ScoringRulesSpec extends Specification {

    @Unroll
    def "a #yards yard field goal is worth #points under the current rules"() {
        expect:
        ScoringRules.FUAD_2026.fieldGoal(yards) == points

        where:
        yards | points
        20    | 3.0      // anything inside forty is three
        39    | 3.0
        40    | 4.0      // then a point a decade
        49    | 4.0
        50    | 5.0
        59    | 5.0
        60    | 6.0      // 2026: the tiers no longer stop at fifty
        69    | 6.0
        70    | 7.0
        80    | 8.0
        90    | 9.0
        99    | 9.0
    }

    /**
     * Through 2025 the tiers stopped at fifty yards, everything beyond scoring five.
     *
     * Which is what {@code longestFieldGoalTier} is for: a season has to be restatable under either rule
     * set, since the whole point of scoring from raw statistics is that one season can be compared to
     * another.
     */
    def "the tiers stopped at five before 2026, however long the kick"() {
        given:
        ScoringRules through2025 = new ScoringRules(
                passingTouchdown: 4.5, passingYardsPerPoint: 30, interception: -1.0,
                receptionsByPosition: [:].withDefault { 0.5 as BigDecimal },
                extraPoint: 1.0, longestFieldGoalTier: 5)

        expect:
        through2025.fieldGoal(55) == 5.0
        through2025.fieldGoal(65) == 5.0
        through2025.fieldGoal(99) == 5.0
    }

    def "scores every field goal in a week from its own distance, not from a bucket"() {
        given: 'three kicks either side of the tier boundaries, and two extra points'
        Map<String, String> week = [position: 'PK', fg_made_list: '25;43;62', pat_made: '2']

        expect: '3 for the 25, 4 for the 43, 6 for the 62, and a point each for the extras'
        ScoringRules.FUAD_2026.score(week) == 15.0
    }

    def "a kicker who attempted nothing scores nothing, rather than failing on an empty list"() {
        expect:
        ScoringRules.FUAD_2026.score([position: 'PK', fg_made_list: '', pat_made: '']) == 0.0
        ScoringRules.FUAD_2026.score([position: 'PK']) == 0.0
    }

    def "leaves the scoring positions exactly as they were, kicking being empty for them"() {
        given: 'a receiving line with no kicking columns at all'
        Map<String, String> week = [position: 'WR', receiving_yards: '100', receiving_tds: '1', receptions: '6']

        expect: '10 yards, 6 for the score, half a point a catch'
        ScoringRules.FUAD_2026.score(week) == 19.0
    }

    def "gives the tight end a whole point a reception from 2026 and everyone else a half"() {
        expect:
        ScoringRules.FUAD_2026.score([position: 'TE', receptions: '6']) == 6.0
        ScoringRules.FUAD_2026.score([position: 'WR', receptions: '6']) == 3.0
    }
}
