package ff.fetch.mfl

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Move a season's expansion draft selections onto the expansion franchise in that season's pre draft
 * rosters.
 *
 * An expansion draft takes players off existing rosters, so it moves contracts rather than creating them.
 * Whether a season's rosters.json shows it depends only on when that snapshot happened to be taken: 2024's
 * was taken afterwards and holds it, 2023's was taken before and shows the new franchise sitting on an
 * empty roster while its players still count against the teams that lost them. Applying it makes the two
 * seasons mean the same thing.
 *
 * The selections come from the commissioner's roster load in the transaction log rather than from the week
 * 1 rosters, because a selection the expansion franchise later released never reaches week 1 at all.
 * Contracts are carried across untouched, including the ones already wiped, which the expansion franchise
 * inherits as its own expiring players to re-sign or lose in the auction.
 */
class MflExpansionDraftBuilder {

    private static final String ROSTER_LOAD = 'LOAD_ROSTERS'

    static String withExpansionDraftJson(String preDraftJson, String transactionsJson, String expansionFranchiseId) {
        Map preDraft = new JsonSlurper().parseText(preDraftJson) as Map
        Map transactions = new JsonSlurper().parseText(transactionsJson) as Map
        new JsonOutput().prettyPrint(JsonOutput.toJson(
                withExpansionDraft(preDraft, transactions, expansionFranchiseId)))
    }

    static Map withExpansionDraft(Map preDraftRosters, Map transactions, String expansionFranchiseId) {
        Set<String> selected = selections(transactions, expansionFranchiseId)
        List<Map> franchises = preDraftRosters.rosters.franchise as List<Map>

        List<Map> moved = []
        List<Map> remaining = franchises.collect { franchise ->
            def rostered = franchise.player ?: []
            List<Map> players = (rostered instanceof List ? rostered : [rostered]) as List<Map>
            if (franchise.id == expansionFranchiseId) {
                return franchise
            }
            moved.addAll(players.findAll { selected.contains(it.id as String) })
            franchise + [player: players.findAll { !selected.contains(it.id as String) }]
        }

        Set<String> unaccounted = selected - (moved*.id as Set)
        if (unaccounted) {
            throw new IllegalStateException("Expansion selections $unaccounted are not on any pre draft " +
                    'roster, so the transaction log and the rosters disagree about that season.')
        }

        List<Map> withExpansion = remaining.collect { franchise ->
            franchise.id == expansionFranchiseId ?
                    franchise + [player: ((franchise.player ?: []) as List<Map>) + moved] :
                    franchise
        }
        preDraftRosters + [rosters: [franchise: withExpansion]]
    }

    /**
     * The selections are the players the commissioner loaded onto the expansion franchise. Every other
     * franchise has a roster load of its own from the same reconciliation, listing players it keeps, so
     * only the expansion franchise's is a record of players changing hands.
     */
    static Set<String> selections(Map transactions, String expansionFranchiseId) {
        (transactions.transactions.transaction as List<Map>)
                .findAll { it.type == ROSTER_LOAD && it.franchise == expansionFranchiseId }
                .collectMany { Map load ->
                    // Loads read as the players added, a pipe, then the players dropped. Either side of it
                    // can be empty, which is why the pipe is found rather than split on.
                    String moves = (load.transaction ?: '') as String
                    String added = moves.contains('|') ? moves.substring(0, moves.indexOf('|')) : moves
                    added.split(',').toList()
                }
                .findAll { it } as Set
    }
}
