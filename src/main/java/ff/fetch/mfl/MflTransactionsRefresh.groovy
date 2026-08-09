package ff.fetch.mfl

import ff.fetch.FetchUtils
import groovy.json.JsonOutput

/**
 * Fetch a season's transaction log from myfantasyleague.com.
 *
 * The rosters say who ended up where; the log says how, and is the only record of moves that later moves
 * undid. The expansion drafts are in it as commissioner roster loads, which is how the 2023 one was
 * reconstructed after the fact. See docs/DATA.md.
 */
class MflTransactionsRefresh implements Runnable {

    private final int year
    private final int leagueId
    private final String host

    MflTransactionsRefresh(int year, int leagueId, String host) {
        this.year = year
        this.leagueId = leagueId
        this.host = host
    }

    @Override
    void run() {
        String url = "https://$host/$year/export?JSON=1&TYPE=transactions&L=$leagueId"
        String resourcePath = "$FetchUtils.baseResourceFilePath/ff/mfl/data/$year"

        println url

        String json = FetchUtils.fetchText(url)
        new File(resourcePath).mkdirs()
        new File("$resourcePath/transactions.json").text = new JsonOutput().prettyPrint(json)
    }
}
