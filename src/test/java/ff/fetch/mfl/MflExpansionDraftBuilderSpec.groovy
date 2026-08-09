package ff.fetch.mfl

import groovy.json.JsonSlurper
import spock.lang.Specification

class MflExpansionDraftBuilderSpec extends Specification {

    private static Map preDraft(String... franchiseJson) {
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

    private static Map transactions(String... transactionJson) {
        new JsonSlurper().parseText("""{
            "transactions": {"transaction": [${transactionJson.join(',')}]}
        }""") as Map
    }

    private static String rosterLoad(String franchiseId, String players) {
        """{"type": "LOAD_ROSTERS", "franchise": "$franchiseId", "timestamp": "1693485240",
            "by_commish": "1", "transaction": "$players|"}"""
    }

    private static List<String> rostered(Map rosters, String franchiseId) {
        Map franchise = (rosters.rosters.franchise as List<Map>).find { it.id == franchiseId }
        (franchise.player as List<Map>)*.id
    }

    def "moves selections onto the expansion franchise and off the teams that lost them"() {
        given:
        Map rosters = preDraft(
                franchise('0001', player('100', '35', '2'), player('101', '5', '1')),
                franchise('0002', player('200', '12', '3')),
                franchise('0009'))

        when:
        Map applied = MflExpansionDraftBuilder.withExpansionDraft(
                rosters, transactions(rosterLoad('0009', '101,200,')), '0009')

        then:
        rostered(applied, '0001') == ['100']
        rostered(applied, '0002') == []
        rostered(applied, '0009') == ['101', '200']
    }

    def "carries contracts across untouched, including wiped ones"() {
        given:
        Map rosters = preDraft(
                franchise('0001', player('100', '35', '2'), player('101', '0.01', '0')),
                franchise('0009'))

        when:
        Map applied = MflExpansionDraftBuilder.withExpansionDraft(
                rosters, transactions(rosterLoad('0009', '100,101,')), '0009')
        Map moved = ((applied.rosters.franchise as List<Map>).find { it.id == '0009' }
                .player as List<Map>).collectEntries { [(it.id): it] }

        then:
        moved['100'] == [id: '100', salary: '35', contractYear: '2', status: 'ROSTER']
        moved['101'] == [id: '101', salary: '0.01', contractYear: '0', status: 'ROSTER']
    }

    def "ignores the roster loads of franchises that kept their players"() {
        given:
        Map rosters = preDraft(
                franchise('0001', player('100', '35', '2')),
                franchise('0009', player('900', '1', '4')))

        when:
        Map applied = MflExpansionDraftBuilder.withExpansionDraft(rosters,
                transactions(rosterLoad('0001', '100,'), rosterLoad('0009', '')), '0009')

        then:
        rostered(applied, '0001') == ['100']
        rostered(applied, '0009') == ['900']
    }

    def "refuses a selection that is on no pre draft roster"() {
        given:
        Map rosters = preDraft(franchise('0001', player('100', '35', '2')), franchise('0009'))

        when:
        MflExpansionDraftBuilder.withExpansionDraft(
                rosters, transactions(rosterLoad('0009', '100,999,')), '0009')

        then:
        IllegalStateException e = thrown()
        e.message.contains('999')
    }

    def "reads the selections out of the transaction log"() {
        when:
        Set<String> selected = MflExpansionDraftBuilder.selections(
                transactions(rosterLoad('0009', '13832,13133,'), rosterLoad('0009', '15756,')), '0009')

        then:
        selected == ['13832', '13133', '15756'] as Set
    }
}
