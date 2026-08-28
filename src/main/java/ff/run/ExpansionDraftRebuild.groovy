package ff.run

import ff.fetch.FetchUtils
import ff.fetch.mfl.MflExpansionDraftBuilder

/**
 * Apply a season's expansion draft to its pre draft rosters, for a season whose snapshot was taken before
 * the expansion draft rather than after it. See docs/fuad/DATA.md for which seasons this was used on.
 *
 * Usage: ExpansionDraftRebuild <year> <expansionFranchiseId>
 */
class ExpansionDraftRebuild {
    static void main(String[] args) {
        if (args.length != 2) {
            System.err.println 'Usage: ExpansionDraftRebuild <year> <expansionFranchiseId>'
            Runtime.getRuntime().exit(-1)
        }
        String year = args[0]
        String expansionFranchiseId = args[1]
        File resources = new File("$FetchUtils.baseResourceFilePath/ff/mfl/data/$year")
        File preDraft = new File(resources, 'rosters.json')
        File transactions = new File(resources, 'transactions.json')
        if (!transactions.exists()) {
            System.err.println "No transaction log for $year at $transactions. " +
                    "Run season_history_refresh.sh $year first."
            Runtime.getRuntime().exit(-1)
        }
        preDraft.text = MflExpansionDraftBuilder.withExpansionDraftJson(
                preDraft.text, transactions.text, expansionFranchiseId)
        println "Applied the $year expansion draft for franchise $expansionFranchiseId to $preDraft"
    }
}
