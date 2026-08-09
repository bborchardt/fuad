package ff.fetch.mfl

import groovy.json.JsonSlurper
import spock.lang.Specification

class MflRosterSnapshotRefreshSpec extends Specification {

    private static Map rosters(String... franchiseJson) {
        new JsonSlurper().parseText("""{
            "encoding": "utf-8", "version": "1.0",
            "rosters": {"franchise": [${franchiseJson.join(',')}]}
        }""") as Map
    }

    private static String franchise(String id, String... playerJson) {
        """{"id": "$id", "player": [${playerJson.join(',')}]}"""
    }

    private static String player(String id, String salary, String contractYear) {
        """{"id": "$id", "salary": "$salary", "contractYear": "$contractYear", "status": "ROSTER"}"""
    }

    def "rejects rosters whose expiring contracts are still wiped"() {
        given:
        Map preDraft = rosters(
                franchise('0001', player('14104', '35', '1'), player('14783', '0.01', '0')),
                franchise('0002', player('15256', '0.01', '0')))

        when:
        MflRosterSnapshotRefresh.verifyPostAuction('http://example.com/rosters', preDraft)

        then:
        IllegalStateException e = thrown()
        e.message.contains('2 of 3 contracts wiped to 0.01')
    }

    def "accepts rosters holding the contracts signed in the auction"() {
        when:
        MflRosterSnapshotRefresh.verifyPostAuction('http://example.com/rosters', rosters(
                franchise('0001', player('14104', '35', '2'), player('14783', '47', '1')),
                franchise('0002', player('15256', '2', '1'))))

        then:
        noExceptionThrown()
    }

    def "reads a franchise carrying a single player"() {
        when:
        MflRosterSnapshotRefresh.verifyPostAuction('http://example.com/rosters',
                new JsonSlurper().parseText("""{
                    "rosters": {"franchise": [{"id": "0001", "player": ${player('14104', '0.01', '0')}}]}
                }""") as Map)

        then:
        IllegalStateException e = thrown()
        e.message.contains('1 of 1 contracts wiped to 0.01')
    }

    def "reads a franchise sitting on an empty roster"() {
        when:
        MflRosterSnapshotRefresh.verifyPostAuction('http://example.com/rosters',
                new JsonSlurper().parseText('{"rosters": {"franchise": [{"id": "0009"}]}}') as Map)

        then:
        noExceptionThrown()
    }
}
