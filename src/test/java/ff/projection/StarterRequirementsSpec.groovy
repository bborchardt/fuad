package ff.projection

import ff.load.util.LoadUtils
import spock.lang.Specification
import spock.lang.Unroll

/**
 * How many players at each position the league starts is not a setting, it is what happens when every team
 * fills its flex with the best player it has. See docs/fuad/PROJECTION.md.
 */
class StarterRequirementsSpec extends Specification {

    private static StarterRequirements forSeason(String season, int teams) {
        StarterRequirements.fromLeague(
                LoadUtils.loadJsonResource(LoadUtils.mflLeagueResourcePath(season)) as Map, teams)
    }

    /** Descending points at each position, steep enough that the ordering between them is unambiguous. */
    private static Map<String, List<BigDecimal>> pool(Map<String, Integer> tops) {
        tops.collectEntries { position, top ->
            [(position): (0..<80).collect { (top - it * 2) as BigDecimal }]
        }
    }

    def "reads the ranges out of a season's league settings"() {
        expect:
        forSeason('2026', 10).startersByPosition(pool([QB: 300, RB: 300, WR: 300, TE: 300, PK: 100]))
                .values().sum() == 100
    }

    def "gives every flex spot to the position scoring most, up to its cap"() {
        given: 'running backs outscore everyone and quarterbacks are next'
        Map<String, List<BigDecimal>> points = pool([RB: 400, QB: 300, WR: 200, TE: 100, PK: 50])

        when:
        Map<String, Integer> starters = forSeason('2026', 10).startersByPosition(points)

        then: 'both fill to their cap of 3 and 2 per team, and the rest sit at their minimum'
        starters.RB == 30
        starters.QB == 20
        starters.TE == 10
        starters.PK == 10
        starters.values().sum() == 100
    }

    def "starts twenty quarterbacks in superflex and ten before it"() {
        expect:
        forSeason('2026', 10).startersByPosition(pool([QB: 300, RB: 280, WR: 260, TE: 200, PK: 50])).QB == 20
        forSeason('2020', 10).startersByPosition(pool([QB: 300, RB: 280, WR: 260, TE: 200, PK: 50])).QB == 10
    }

    @Unroll
    def "#season starts #expected players in total across #teams teams"() {
        expect:
        forSeason(season, teams).startersByPosition(pool([QB: 300, RB: 290, WR: 280, TE: 200, PK: 50]))
                .values().sum() == expected

        where:
        season | teams | expected
        '2020' | 10    | 80
        '2021' | 8     | 64
        '2022' | 8     | 80
        '2023' | 9     | 90
        '2026' | 10    | 100
    }

    def "never starts more of a position than the lineup allows, however well it scores"() {
        given: 'tight ends outscore every other position by a distance'
        Map<String, List<BigDecimal>> points = pool([TE: 900, QB: 100, RB: 100, WR: 100, PK: 50])

        expect: 'still only three per team, the lineup maximum'
        forSeason('2026', 10).startersByPosition(points).TE == 30
    }
}
