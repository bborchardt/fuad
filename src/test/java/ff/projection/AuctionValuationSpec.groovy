package ff.projection

import ff.load.util.LoadUtils
import spock.lang.Specification

/**
 * The constants this model divides money by are measurements, not choices, so they are checked against the
 * seasons they were measured from rather than left to drift. See docs/PROJECTION.md.
 */
class AuctionValuationSpec extends Specification {

    private static final List<String> SUPERFLEX_SEASONS = ['2022', '2023', '2024', '2025']

    private static final String WIPED_SALARY = '0.01'

    /**
     * Player id to [franchise, salary], keeping the first row for each player.
     *
     * Deliberately by player rather than by row: the week 1 snapshots repeat a few roster rows verbatim,
     * and counting those contracts twice overstates what the league spent.
     */
    private static Map<String, List> byPlayer(String resourcePath) {
        Map<String, List> held = [:]
        (LoadUtils.loadJsonResource(resourcePath).rosters.franchise as List).each { franchise ->
            def rostered = franchise.player ?: []
            (rostered instanceof List ? rostered : [rostered]).each { player ->
                held.putIfAbsent(player.id as String, [franchise.id as String, player.salary as BigDecimal])
            }
        }
        held
    }

    private static BigDecimal spendRate(String season) {
        Map<String, List> preDraft = byPlayer(LoadUtils.mflRostersResourcePath(season))
        Map<String, List> postDraft = byPlayer(LoadUtils.mflPostDraftRostersResourcePath(season))
        Map league = LoadUtils.loadJsonResource(LoadUtils.mflLeagueResourcePath(season)) as Map

        int teams = (league.league.franchises.franchise as List).size()
        BigDecimal cap = (league.league.salaryCapAmount as String as BigDecimal) * teams
        BigDecimal committed = preDraft.values().findAll { it[1] != new BigDecimal(WIPED_SALARY) }
                .collect { it[1] as BigDecimal }.sum() ?: 0.0
        BigDecimal spent = preDraft.findAll { id, held ->
            held[1] == new BigDecimal(WIPED_SALARY) && postDraft.containsKey(id)
        }.collect { id, held -> postDraft[id][1] as BigDecimal }.sum() ?: 0.0

        spent / (cap - committed)
    }

    def "the spend rate is what the superflex seasons actually spent"() {
        when:
        BigDecimal measured = SUPERFLEX_SEASONS.collect { spendRate(it) }.sum() / SUPERFLEX_SEASONS.size()

        then:
        (measured - AuctionValuation.SPEND_RATE).abs() < 0.01
    }

    def "no season spent outside the range the model claims"() {
        expect:
        SUPERFLEX_SEASONS.every { spendRate(it) > 0.65 && spendRate(it) < 0.90 }
    }

    def "the market shares are what the superflex seasons actually paid each position"() {
        given:
        Map<String, BigDecimal> paid = [:].withDefault { 0.0 as BigDecimal }
        SUPERFLEX_SEASONS.each { String season ->
            Map<String, List> preDraft = byPlayer(LoadUtils.mflRostersResourcePath(season))
            Map<String, List> postDraft = byPlayer(LoadUtils.mflPostDraftRostersResourcePath(season))
            Map<String, String> position = (LoadUtils.loadJsonResource(
                    LoadUtils.mflPlayersResourcePath(season)).players.player as List)
                    .collectEntries { [(it.id as String): it.position as String] }
            preDraft.each { id, held ->
                if (held[1] == new BigDecimal(WIPED_SALARY) && postDraft.containsKey(id) && position[id]) {
                    paid[position[id]] += postDraft[id][1] as BigDecimal
                }
            }
        }
        BigDecimal total = paid.values().sum() as BigDecimal

        expect:
        AuctionValuation.MARKET_SHARE.every { String pos, BigDecimal share ->
            (paid[pos] / total - share).abs() < 0.005
        }
    }
}
