package ff.fetch.mfl

import ff.fetch.FetchUtils
import groovy.json.JsonOutput

/**
 * Fetch a season's salary adjustments from myfantasyleague.com.
 *
 * This is the record of what releasing a player actually cost. Every adjustment in the league's history is
 * a cut penalty, charged to the franchise that made the cut and carrying the contract it was released from
 * in its description: {@code Treylon Burks (2yrs@1)}, with the dollars charged alongside.
 *
 * It is the only place the cut penalty is visible at all. The rule is a bylaw, absent from both
 * {@code league.json} and {@code rules.json}, and before this was collected it was the least verified rule
 * in the league — stated by the commissioner and checkable against nothing. See docs/LEAGUE_RULES.md.
 */
class MflSalaryAdjustmentsRefresh implements Runnable {

    private final int year
    private final int leagueId
    private final String host

    MflSalaryAdjustmentsRefresh(int year, int leagueId, String host) {
        this.year = year
        this.leagueId = leagueId
        this.host = host
    }

    @Override
    void run() {
        String url = "https://$host/$year/export?JSON=1&TYPE=salaryAdjustments&L=$leagueId"
        String resourcePath = "$FetchUtils.baseResourceFilePath/ff/mfl/data/$year"

        println url

        String json = FetchUtils.fetchText(url)
        new File(resourcePath).mkdirs()
        new File("$resourcePath/salary_adjustments.json").text = new JsonOutput().prettyPrint(json)
    }
}
