package ff.run

import ff.fetch.FetchUtils
import ff.fetch.mfl.MflPreDraftRosterBuilder

/**
 * Rebuild a season's rosters.json from the prior season's end of year rosters, for a season whose pre draft
 * snapshot was pulled at the wrong moment. The league site cannot serve that snapshot after the fact, so
 * deriving it is the only repair available. See docs/DATA.md for which seasons this was used on and what
 * the derived rosters can and cannot tell you.
 *
 * Usage: PreDraftRebuild <year>
 */
class PreDraftRebuild {
    static void main(String[] args) {
        if (args.length != 1) {
            System.err.println 'Usage: PreDraftRebuild <year>'
            Runtime.getRuntime().exit(-1)
        }
        int year = Integer.parseInt(args[0])
        File endOfYear = new File("$FetchUtils.baseResourceFilePath/ff/mfl/data/${year - 1}/rosters_end_of_year.json")
        if (!endOfYear.exists()) {
            System.err.println "No end of year rosters for ${year - 1} at $endOfYear. " +
                    "Run season_history_refresh.sh ${year - 1} first."
            Runtime.getRuntime().exit(-1)
        }
        File preDraft = new File("$FetchUtils.baseResourceFilePath/ff/mfl/data/$year/rosters.json")
        preDraft.text = MflPreDraftRosterBuilder.preDraftRostersJson(endOfYear.text)
        println "Rebuilt $preDraft from $endOfYear"
    }
}
