package ff.fetch.mfl

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Rebuild a season's pre draft rosters from the prior season's end of year rosters.
 *
 * Pre draft rosters are the one snapshot the league site cannot serve after the fact, so a season whose
 * rosters.json was pulled at the wrong moment cannot be refetched. It can be derived instead, because the
 * rollover between seasons is mechanical: every contract with a year left carries over with its year
 * decremented, a contract reaching zero has its salary wiped to the 0.01 the site stores in place of a
 * zero, and players already at zero, who were signed in season for the rest of that year, are dropped.
 *
 * What it cannot reproduce is anything that happened between the two snapshots: offseason releases,
 * retirements, trades, and expansion drafts. Every derived season is therefore a roster of exactly who was
 * under contract, on the team that held that contract in December.
 */
class MflPreDraftRosterBuilder {

    private static final String WIPED_SALARY = '0.01'

    static String preDraftRostersJson(String endOfYearJson) {
        Map rosters = new JsonSlurper().parseText(endOfYearJson) as Map
        new JsonOutput().prettyPrint(JsonOutput.toJson(preDraftRosters(rosters)))
    }

    static Map preDraftRosters(Map endOfYearRosters) {
        List<Map> franchises = (endOfYearRosters.rosters.franchise as List<Map>).collect { franchise ->
            def rostered = franchise.player ?: []
            List<Map> players = ((rostered instanceof List ? rostered : [rostered]) as List<Map>)
                    .findAll { (it.contractYear as int) >= 1 }
                    .collect { rollOver(it) }
            // Week is a property of the snapshot it came from, not of the rosters themselves.
            [id: franchise.id, player: players]
        }
        [encoding: endOfYearRosters.encoding, version: endOfYearRosters.version, rosters: [franchise: franchises]]
    }

    private static Map rollOver(Map player) {
        int contractYear = (player.contractYear as int) - 1
        [id           : player.id,
         salary       : contractYear ? player.salary : WIPED_SALARY,
         contractYear : contractYear as String,
         status       : player.status ?: 'ROSTER']
    }
}
