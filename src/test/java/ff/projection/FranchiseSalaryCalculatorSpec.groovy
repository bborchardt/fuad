package ff.projection

import ff.data.FranchiseTag
import ff.load.util.LoadUtils
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The franchise tag is the one auction price the league sets by rule, so it can be checked rather than
 * inferred: every tag in the collected data should come out at exactly the average of the top five salaries
 * at that position the previous season. See docs/LEAGUE_RULES.md.
 */
class FranchiseSalaryCalculatorSpec extends Specification {

    private static Map<String, Integer> franchiseSalariesFor(String season) {
        TagHistory.franchiseSalaries(season)
    }

    def "averages the top five salaries and ignores the rest"() {
        expect:
        FranchiseSalaryCalculator.franchiseSalary([10, 50, 30, 1, 40, 20, 60].collect { it as BigDecimal }) == 40
    }

    def "averages everything at a position that has fewer than five salaries"() {
        expect:
        FranchiseSalaryCalculator.franchiseSalary([10, 20, 30].collect { it as BigDecimal }) == 20
    }

    @Unroll
    def "rounds an average of #salaries to the nearest dollar, #expected"() {
        expect:
        FranchiseSalaryCalculator.franchiseSalary(salaries.collect { it as BigDecimal }) == expected

        where:
        salaries              | expected
        [10, 10, 10, 10, 11]  | 10
        [10, 10, 10, 11, 11]  | 10
        [10, 10, 11, 11, 11]  | 11
        [10, 11, 11, 11, 11]  | 11
    }

    @Unroll
    def "#season franchise salaries are #expected"() {
        expect:
        franchiseSalariesFor(season) == expected

        where:
        season | expected
        '2021' | [QB: 23, RB: 40, WR: 92, TE: 23, PK: 2]
        '2022' | [QB: 33, RB: 43, WR: 94, TE: 21, PK: 2]
        '2023' | [QB: 36, RB: 52, WR: 82, TE: 21, PK: 3]
        '2024' | [QB: 45, RB: 61, WR: 71, TE: 38, PK: 4]
        '2025' | [QB: 47, RB: 64, WR: 61, TE: 32, PK: 3]
        '2026' | [QB: 66, RB: 60, WR: 61, TE: 24, PK: 3]
    }

    /**
     * What this test's name has always claimed, and did not check until now.
     *
     * The player column was decoration: the body asserted only that the rate for a season and position was
     * what the calculator says, which the test above already asserts for every position of every season. So
     * eleven rows collapsed to six assertions, all of them redundant, under a name promising that named
     * players were verified as having been tagged at the rate. Nothing was checked about any player.
     *
     * It now asserts the thing itself — that the player is recovered as a confirmed tag, and that what he
     * was paid is the rate this calculator produced. That is the join the two classes exist either side of,
     * and it is what makes the rate a price somebody actually paid rather than an arithmetic exercise.
     */
    @Unroll
    def "#season #position #player was tagged at the franchise salary"() {
        given:
        FranchiseTag tag = TagHistory.tags(season).find { TagHistory.readableName(it) == player }

        expect: 'the rate the calculator produces for that position'
        franchiseSalariesFor(season)[position] == salary

        and: 'and a tag recovered for that player, priced at it'
        tag != null
        tag.status == FranchiseTag.Status.CONFIRMED
        tag.position == position
        tag.franchiseSalary == salary
        tag.salary == salary

        where:
        season | position | player            | salary
        '2021' | 'TE'     | 'Travis Kelce'    | 23
        '2021' | 'WR'     | 'Tyreek Hill'     | 92
        '2022' | 'RB'     | 'Derrick Henry'   | 43
        '2023' | 'WR'     | 'Davante Adams'   | 82
        '2023' | 'WR'     | 'Stefon Diggs'    | 82
        '2023' | 'WR'     | 'Cooper Kupp'     | 82
        '2024' | 'QB'     | 'Lamar Jackson'   | 45
        '2024' | 'QB'     | 'Patrick Mahomes' | 45
        '2025' | 'QB'     | 'Joe Burrow'      | 47
        '2025' | 'QB'     | 'Jalen Hurts'     | 47
        '2025' | 'QB'     | 'Patrick Mahomes' | 47
    }

    /**
     * The rule says the previous season, and both of that season's roster snapshots hold the same contract
     * salaries, so which one is meant only ever mattered once: 2019 running backs, where end of year gives
     * 30 and week 1 gives 27. Both 2020 running back tags came in at 30.
     */
    def "takes the average over the prior season's end of year salaries, not its week 1 salaries"() {
        given:
        Map<String, Integer> endOfYear = franchiseSalariesFor('2020')
        Map<String, Integer> postDraft = FranchiseSalaryCalculator.franchiseSalaries(
                LoadUtils.loadJsonResource(LoadUtils.mflPostDraftRostersResourcePath('2019')) as Map,
                LoadUtils.loadJsonResource(LoadUtils.mflPlayersResourcePath('2019')) as Map)

        expect:
        endOfYear.RB == 30
        postDraft.RB == 27
    }
}
