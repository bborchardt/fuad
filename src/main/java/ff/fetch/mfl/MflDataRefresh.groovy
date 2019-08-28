package ff.fetch.mfl

import ff.fetch.FetchUtils
import groovy.json.JsonOutput

/**
 * Fetch data from myfantasyleague.com and pull into projection.
 */
class MflDataRefresh implements Runnable {

    private final int year
    private final int leagueId
    private final String host

    MflDataRefresh(int year, int leagueId, String host) {
        this.year = year
        this.leagueId = leagueId
        this.host = host
    }

    @Override
    void run() {
        String baseUrl = "https://$host/$year/export?JSON=1"
        String resourcePath = "$FetchUtils.baseResourceFilePath/ff/mfl/data/$year"

        println baseUrl

        new File("$resourcePath/draft.json").text = new JsonOutput().prettyPrint(new URL("$baseUrl&TYPE=draftResults&L=$leagueId").text)
        new File("$resourcePath/league.json").text = new JsonOutput().prettyPrint(new URL("$baseUrl&TYPE=league&L=$leagueId").text)
        new File("$resourcePath/players.json").text = new JsonOutput().prettyPrint(new URL("$baseUrl&TYPE=players&DETAILS=1").text)
        new File("$resourcePath/rosters.json").text = new JsonOutput().prettyPrint(new URL("$baseUrl&TYPE=rosters&L=$leagueId").text)
    }
}
