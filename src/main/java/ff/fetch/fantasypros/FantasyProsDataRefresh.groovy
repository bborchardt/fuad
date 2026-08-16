package ff.fetch.fantasypros

import ff.fetch.FetchUtils
import groovy.json.JsonSlurper

/**
 * Fetch consensus rankings from the fantasypros.com API and write them as tab separated files
 * in the same layout as the ones downloaded by hand, so that {@link ff.load.fantasypros.FantasyProsLoader}
 * can read them unchanged.
 *
 * All three sets are superflex (position OP). Dynasty and rookie rankings are only published as PPR;
 * the API ignores the scoring parameter for those types.
 */
class FantasyProsDataRefresh implements Runnable {

    private static final String HEADER = ['RK', 'TIERS', 'PLAYER NAME', 'TEAM', 'POS', 'BYE WEEK'].join('\t')

    private final int year
    private final String apiKey
    private final String host

    FantasyProsDataRefresh(int year, String apiKey, String host) {
        this.year = year
        this.apiKey = apiKey
        this.host = host
    }

    @Override
    void run() {
        String baseUrl = "https://$host/public/v2/json/nfl/$year/consensus-rankings"
        String resourcePath = "$FetchUtils.baseResourceFilePath/ff/fantasypros/data/$year"

        println baseUrl

        new File(resourcePath).mkdirs()

        new File("$resourcePath/dynasty_rankings_ppr.csv").text =
                toTabSeparated(fetch("$baseUrl?type=DYNASTY&position=OP"))
        // OP is every offensive player, which does not include kickers. They have to be asked for
        // separately or the redraft set comes back without a single one, and a team that needs a kicker
        // then sees none on the board. See docs/DATA.md.
        new File("$resourcePath/redraft_rankings_half_ppr.csv").text = toTabSeparated(
                fetch("$baseUrl?type=DRAFT&position=OP&scoring=HALF"),
                fetch("$baseUrl?type=DRAFT&position=K&scoring=HALF"))
        new File("$resourcePath/rookie_rankings_ppr.csv").text =
                toTabSeparated(fetch("$baseUrl?type=ROOKIES&position=OP"))
    }

    private Map fetch(String url) {
        println url
        Map rankings = new JsonSlurper().parse(new URL(url), [requestProperties: ['x-api-key': apiKey]]) as Map
        verifyComplete(url, rankings)
        rankings
    }

    /**
     * A free tier API key silently truncates every ranking set to the first handful of players, which would
     * otherwise overwrite good data with a near empty file. Refuse to write anything in that case.
     */
    static void verifyComplete(String url, Map rankings) {
        int available = rankings.count as int
        int returned = (rankings.players as List)?.size() ?: 0
        if (returned < available) {
            throw new IllegalStateException("$url returned only $returned of $available players. " +
                    'The fantasypros API key is rate limited to a subset of each ranking set ' +
                    '(tier=free). Request an upgraded key at https://secure.fantasypros.com/api-keys/request/.')
        }
    }

    /**
     * Write one or more ranking sets as a single file.
     *
     * Each set numbers its own overall ranks from one, so a later set is offset to sit after the ones
     * before it. Nothing prices off the overall rank — the model reads the positional rank out of the POS
     * column — but leaving a second set numbered from one would put kickers among the best players in the
     * file, which is misleading to anyone reading it and to the unmatched-player diagnostic.
     */
    static String toTabSeparated(Map... rankings) {
        StringBuilder out = new StringBuilder(HEADER).append('\n')
        int offset = 0
        rankings.each { Map set ->
            List<Map> players = (set.players as List<Map>) ?: []
            players.each { player ->
                out.append([(player.rank_ecr as String as int) + offset, player.tier, player.player_name,
                            player.player_team_id, player.pos_rank, player.player_bye_week]
                        .join('\t')).append('\n')
            }
            offset += players.size()
        }
        out.toString()
    }
}
