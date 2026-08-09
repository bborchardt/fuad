package ff.fetch.mfl

import ff.fetch.FetchUtils
import groovy.json.JsonOutput

/**
 * Fetch a season's scoring rules from myfantasyleague.com.
 *
 * The league has rescored the passing game twice and the receiving game once, so a player's fantasy points
 * are not comparable across seasons without knowing which rules were in force. Unlike rosters this is kept
 * per season by the league site and can be refetched for any year. See docs/LEAGUE_RULES.md.
 */
class MflRulesRefresh implements Runnable {

    private final int year
    private final int leagueId
    private final String host

    MflRulesRefresh(int year, int leagueId, String host) {
        this.year = year
        this.leagueId = leagueId
        this.host = host
    }

    @Override
    void run() {
        String url = "https://$host/$year/export?JSON=1&TYPE=rules&L=$leagueId"
        String resourcePath = "$FetchUtils.baseResourceFilePath/ff/mfl/data/$year"

        println url

        String json = FetchUtils.fetchText(url)
        new File(resourcePath).mkdirs()
        new File("$resourcePath/rules.json").text = new JsonOutput().prettyPrint(json)
    }
}
