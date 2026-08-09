package ff.projection

import ff.data.FranchiseTag
import ff.load.util.LoadUtils
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The franchise tag is recorded nowhere, so what this asserts is the inference rather than the fact. The
 * one season with independent ground truth is 2025, which the commissioner confirmed: Lamar Jackson, Jalen
 * Hurts, Joe Burrow, Patrick Mahomes and CeeDee Lamb were tagged, plus Jahmyr Gibbs and Saquon Barkley,
 * whose omission from that list was recall rather than absence. Every one of the seven is recovered here.
 *
 * See docs/LEAGUE_RULES.md.
 */
class FranchiseTagIdentifierSpec extends Specification {

    private static List<FranchiseTag> tagsFor(String season) {
        String priorSeason = (season as int) - 1 as String
        Map<String, Integer> franchiseSalaries = FranchiseSalaryCalculator.franchiseSalaries(
                LoadUtils.loadJsonResource(LoadUtils.mflEndOfYearRostersResourcePath(priorSeason)) as Map,
                LoadUtils.loadJsonResource(LoadUtils.mflPlayersResourcePath(priorSeason)) as Map)
        FranchiseTagIdentifier.tags(
                LoadUtils.loadJsonResource(LoadUtils.mflRostersResourcePath(season)) as Map,
                LoadUtils.loadJsonResource(LoadUtils.mflPostDraftRostersResourcePath(season)) as Map,
                LoadUtils.loadJsonResource(LoadUtils.mflTransactionsResourcePath(season)) as Map,
                LoadUtils.loadJsonResource(LoadUtils.mflPlayersResourcePath(season)) as Map,
                franchiseSalaries)
    }

    private static List<String> named(List<FranchiseTag> tags, FranchiseTag.Status status) {
        tags.findAll { it.status == status }.collect { it.playerName }.sort()
    }

    def "recovers every confirmed 2025 tag, the season the league confirmed"() {
        expect:
        named(tagsFor('2025'), FranchiseTag.Status.CONFIRMED) == [
                'Barkley, Saquon', 'Burrow, Joe', 'Gibbs, Jahmyr', 'Hurts, Jalen',
                'Jackson, Lamar', 'Lamb, CeeDee', 'Mahomes, Patrick']
    }

    def "reads Lamar Jackson as tagged by the team that lost him, not the one that paid"() {
        given:
        FranchiseTag jackson = tagsFor('2025').find { it.playerName == 'Jackson, Lamar' }

        expect:
        jackson.basis == FranchiseTag.Basis.PICK_COMPENSATED
        jackson.bidAway
        jackson.taggingFranchiseId == '0006'
        jackson.signingFranchiseId == '0001'
        jackson.salary == 100
        jackson.franchiseSalary == 47
    }

    @Unroll
    def "a contested tag keeps the winning team's contract terms: #player, #years years"() {
        expect:
        tagsFor(season).find { it.playerName == player }.with {
            it.basis == FranchiseTag.Basis.PICK_COMPENSATED && it.contractYears == years
        }

        where:
        season | player              | years
        '2018' | 'Bell, Le\'Veon'    | 1
        '2019' | 'Bell, Le\'Veon'    | 2
        '2020' | 'Elliott, Ezekiel'  | 3
        '2021' | 'Prescott, Dak'     | 5
        '2022' | 'Mixon, Joe'        | 1
        '2025' | 'Jackson, Lamar'    | 1
    }

    @Unroll
    def "#season has #confirmed confirmed, #uncertain uncertain and #candidate candidate tags"() {
        given:
        List<FranchiseTag> tags = tagsFor(season)

        expect:
        tags.count { it.status == FranchiseTag.Status.CONFIRMED } == confirmed
        tags.count { it.status == FranchiseTag.Status.UNCERTAIN } == uncertain
        tags.count { it.status == FranchiseTag.Status.CANDIDATE } == candidate

        where:
        season | confirmed | uncertain | candidate
        '2018' | 9         | 0         | 0
        '2019' | 3         | 0         | 3
        '2020' | 3         | 2         | 1
        '2021' | 5         | 0         | 2
        '2022' | 6         | 0         | 1
        '2023' | 7         | 0         | 3
        '2024' | 6         | 0         | 0
        '2025' | 7         | 0         | 3
    }

    @Unroll
    def "no team uses more than its one tag in #season"() {
        given:
        List<FranchiseTag> tags = tagsFor(season)
                .findAll { it.status != FranchiseTag.Status.CANDIDATE }

        expect:
        tags.groupBy { it.taggingFranchiseId }.every { franchise, held ->
            held.count { it.status == FranchiseTag.Status.CONFIRMED } <= 1
        }

        where:
        season << ['2018', '2019', '2020', '2021', '2022', '2023', '2024', '2025']
    }

    def "the only team that cannot be told apart is 2020's, holding two signings at their rate"() {
        given:
        List<FranchiseTag> uncertain = tagsFor('2020')
                .findAll { it.status == FranchiseTag.Status.UNCERTAIN }

        expect:
        uncertain*.playerName.sort() == ['Ekeler, Austin', 'Wilson, Russell']
        uncertain*.taggingFranchiseId.unique() == ['0010']
    }
}
