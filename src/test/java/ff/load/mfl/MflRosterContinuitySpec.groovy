package ff.load.mfl

import ff.load.util.LoadUtils
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Each season's pre draft rosters are the prior season's end of year rosters rolled over: contracts with a
 * year left carry over decremented, contracts reaching zero have their salary wiped, and players already at
 * zero are dropped. Franchises may be renumbered and players may be released between the two, so who is
 * rostered and where is allowed to move; what may never happen is a contract appearing from nowhere or
 * rolling over to the wrong number. See docs/DATA.md for the seasons that make this less obvious than it
 * sounds.
 */
class MflRosterContinuitySpec extends Specification {

    private static final String WIPED_SALARY = '0.01'

    private static Map<String, Map> contracts(String resourcePath) {
        List franchises = LoadUtils.loadJsonResource(resourcePath).rosters.franchise
        franchises.collectMany { franchise ->
            def rostered = franchise.player ?: []
            (rostered instanceof List ? rostered : [rostered]).collect { [franchise: franchise.id, player: it] }
        }.collectEntries { [(it.player.id): it] }
    }

    @Unroll
    def "#year pre draft rosters roll over #priorYear end of year contracts"() {
        given:
        Map<String, Map> priorContracts = contracts(LoadUtils.mflEndOfYearRostersResourcePath(priorYear))
        Map<String, Map> preDraft = contracts(LoadUtils.mflRostersResourcePath(year))

        when:
        Map<String, Map> rolledOver = priorContracts.findAll { id, held ->
            (held.player.contractYear as int) >= 1
        }
        List<String> appearedFromNowhere = (preDraft.keySet() - rolledOver.keySet()).toList()
        List<String> wrongYear = rolledOver.keySet().findAll { id ->
            preDraft[id] && (preDraft[id].player.contractYear as int) !=
                    (rolledOver[id].player.contractYear as int) - 1
        }.toList()
        List<String> wrongSalary = rolledOver.keySet().findAll { id ->
            if (!preDraft[id]) {
                return false
            }
            String expected = (preDraft[id].player.contractYear as int) ?
                    rolledOver[id].player.salary : WIPED_SALARY
            (preDraft[id].player.salary as double) != (expected as double)
        }.toList()

        then:
        appearedFromNowhere == []
        wrongYear == []
        wrongSalary == []

        where:
        // Every year but the first, which has no prior season to roll over from.
        priorYear << LoadUtils.YEARS[0..-2]
        year << LoadUtils.YEARS[1..-1]
    }
}
