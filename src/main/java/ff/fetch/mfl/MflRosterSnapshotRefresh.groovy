package ff.fetch.mfl

import ff.fetch.FetchUtils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Fetch a {@link RosterSnapshot} for a completed season from myfantasyleague.com.
 *
 * The rosters {@link MflDataRefresh} pulls for a season are the ones in place before that season's auction,
 * so every expiring contract has already been wiped to the 0.01 the league site stores in place of a zero.
 * A season's own league keeps those contracts intact once its auction has run, so fetching it afterwards
 * recovers what each player was actually signed for.
 */
class MflRosterSnapshotRefresh implements Runnable {

    private static final String WIPED_SALARY = '0.01'

    private final int year
    private final int leagueId
    private final String host
    private final RosterSnapshot snapshot

    MflRosterSnapshotRefresh(int year, int leagueId, String host, RosterSnapshot snapshot) {
        this.year = year
        this.leagueId = leagueId
        this.host = host
        this.snapshot = snapshot
    }

    @Override
    void run() {
        String url = "https://$host/$year/export?JSON=1&TYPE=rosters&L=$leagueId$snapshot.urlSuffix"
        String resourcePath = "$FetchUtils.baseResourceFilePath/ff/mfl/data/$year"

        println url

        String json = FetchUtils.fetchText(url)
        verifyPostAuction(url, new JsonSlurper().parseText(json) as Map)

        new File(resourcePath).mkdirs()
        new File("$resourcePath/$snapshot.fileName").text = new JsonOutput().prettyPrint(json)
    }

    /**
     * Before a season's auction its rosters still carry the wiped contracts, which is exactly the data these
     * snapshots exist to replace. Writing that would be worse than writing nothing, so refuse it.
     */
    static void verifyPostAuction(String url, Map rosters) {
        List<Map> players = (rosters.rosters.franchise as List<Map>).collectMany { franchise ->
            // A franchise sitting on an empty roster carries no player key at all.
            def rostered = franchise.player ?: []
            (rostered instanceof List ? rostered : [rostered]) as List<Map>
        }
        int wiped = players.count { it.salary == WIPED_SALARY }
        if (wiped) {
            throw new IllegalStateException("$url still holds $wiped of ${players.size()} contracts wiped to " +
                    "$WIPED_SALARY, so that season's auction has not been entered yet. Roster snapshots " +
                    'can only be collected once it has.')
        }
    }
}
