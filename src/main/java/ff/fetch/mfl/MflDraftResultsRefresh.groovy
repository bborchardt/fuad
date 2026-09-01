package ff.fetch.mfl

import ff.fetch.FetchUtils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Fetch a completed season's rookie draft from myfantasyleague.com.
 *
 * {@link MflDataRefresh} pulls this file too, but for the season being played and so before its draft has
 * run: every pick carries its round, its slot and the franchise that owns it, and an empty player. That is
 * the whole of what the board needs before a draft and none of what a curve needs after one, so the file
 * this project kept was the empty one for seven of the nine finished seasons.
 *
 * The league site fills the same export in once the draft is held and keeps it, so the record is recoverable
 * for every year. What it recovers is 348 picks with the player taken at each, which is the only source for
 * them: unlike a signing or a release, a draft pick is not written to the transaction log at all.
 */
class MflDraftResultsRefresh implements Runnable {

    private final int year
    private final int leagueId
    private final String host

    MflDraftResultsRefresh(int year, int leagueId, String host) {
        this.year = year
        this.leagueId = leagueId
        this.host = host
    }

    @Override
    void run() {
        String url = "https://$host/$year/export?JSON=1&TYPE=draftResults&L=$leagueId"
        String resourcePath = "$FetchUtils.baseResourceFilePath/ff/mfl/data/$year"

        println url

        String json = FetchUtils.fetchText(url)
        verifyDrafted(url, new JsonSlurper().parseText(json) as Map)

        new File(resourcePath).mkdirs()
        new File("$resourcePath/draft.json").text = new JsonOutput().prettyPrint(json)
    }

    /**
     * A draft that has not been held yet is an empty pick list wearing the right shape, and writing it would
     * overwrite the record of one that has. Refuse it, the way a pre auction roster snapshot is refused.
     *
     * The test is that <b>some</b> pick carries a player rather than that all of them do, since a draft in
     * progress is a real state and half a draft is still worth keeping.
     */
    static void verifyDrafted(String url, Map draft) {
        def unit = draft.draftResults?.draftUnit
        List<Map> picks = ((unit instanceof List ? unit.first() : unit)?.draftPick ?: []) as List<Map>
        if (!picks.any { it.player }) {
            throw new IllegalStateException("$url holds ${picks.size()} picks and not one of them names a " +
                    'player, so that season\'s rookie draft has not been held yet. Draft results can only ' +
                    'be collected once it has.')
        }
    }
}
