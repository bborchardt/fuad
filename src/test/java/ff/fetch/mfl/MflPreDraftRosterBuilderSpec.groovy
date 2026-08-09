package ff.fetch.mfl

import groovy.json.JsonSlurper
import spock.lang.Specification

class MflPreDraftRosterBuilderSpec extends Specification {

    private static Map endOfYear(String... franchiseJson) {
        new JsonSlurper().parseText("""{
            "encoding": "utf-8", "version": "1.0",
            "rosters": {"franchise": [${franchiseJson.join(',')}]}
        }""") as Map
    }

    private static String franchise(String id, String... playerJson) {
        """{"id": "$id", "week": "17", "player": [${playerJson.join(',')}]}"""
    }

    private static String player(String id, String salary, String contractYear) {
        """{"id": "$id", "salary": "$salary", "contractYear": "$contractYear", "status": "ROSTER"}"""
    }

    private static Map players(Map rosters, String franchiseId) {
        Map franchise = (rosters.rosters.franchise as List<Map>).find { it.id == franchiseId }
        (franchise.player as List<Map>).collectEntries { [(it.id): it] }
    }

    def "decrements a contract with years left and keeps its salary"() {
        when:
        Map rebuilt = MflPreDraftRosterBuilder.preDraftRosters(
                endOfYear(franchise('0001', player('14104', '35', '3'))))

        then:
        players(rebuilt, '0001')['14104'] == [id: '14104', salary: '35', contractYear: '2', status: 'ROSTER']
    }

    def "wipes the salary of a contract reaching its final year"() {
        when:
        Map rebuilt = MflPreDraftRosterBuilder.preDraftRosters(
                endOfYear(franchise('0001', player('14783', '47', '1'))))

        then:
        players(rebuilt, '0001')['14783'] == [id: '14783', salary: '0.01', contractYear: '0', status: 'ROSTER']
    }

    def "drops players signed in season, who are already at zero years"() {
        when:
        Map rebuilt = MflPreDraftRosterBuilder.preDraftRosters(endOfYear(
                franchise('0001', player('14104', '35', '3'), player('99999', '1', '0'))))

        then:
        players(rebuilt, '0001').keySet() == ['14104'] as Set
    }

    def "keeps a franchise that rolls over no one"() {
        when:
        Map rebuilt = MflPreDraftRosterBuilder.preDraftRosters(endOfYear(
                franchise('0001', player('14104', '35', '3')),
                franchise('0009', player('99999', '1', '0'))))

        then:
        (rebuilt.rosters.franchise as List<Map>)*.id == ['0001', '0009']
        players(rebuilt, '0009').isEmpty()
    }

    def "renders json the roster loader can read back"() {
        given:
        String json = MflPreDraftRosterBuilder.preDraftRostersJson("""{
            "encoding": "utf-8", "version": "1.0",
            "rosters": {"franchise": [${franchise('0001', player('14104', '35', '3'))}]}
        }""")

        when:
        Map reparsed = new JsonSlurper().parseText(json) as Map

        then:
        reparsed.encoding == 'utf-8'
        players(reparsed, '0001')['14104'].contractYear == '2'
    }
}
