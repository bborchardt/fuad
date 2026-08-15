package ff.load.util

import groovy.json.JsonSlurper

class LoadUtils {

    public static final List<String> YEARS = ['2017', '2018', '2019', '2020', '2021', '2022', '2023', '2024', '2025', '2026']

    static String mflOwnersResourcePath(String year) { "/ff/mfl/data/$year/owners.json" }
    static String mflLeagueResourcePath(String year) { "/ff/mfl/data/$year/league.json" }
    static String mflPlayersResourcePath(String year) { "/ff/mfl/data/$year/players.json" }
    static String mflRostersResourcePath(String year) { "/ff/mfl/data/$year/rosters.json" }
    /** Rosters as of week 1, holding the contracts signed in that year's auction rather than wiped ones. */
    static String mflPostDraftRostersResourcePath(String year) { "/ff/mfl/data/$year/rosters_post_draft.json" }
    /** Rosters at the close of the season, which the next year's pre draft rosters are rolled over from. */
    static String mflEndOfYearRostersResourcePath(String year) { "/ff/mfl/data/$year/rosters_end_of_year.json" }
    static String mflDraftResourcePath(String year) { "/ff/mfl/data/$year/draft.json" }
    /** Every move made that season, including the pick compensation that marks a contested franchise tag. */
    static String mflTransactionsResourcePath(String year) { "/ff/mfl/data/$year/transactions.json" }
    /** That season's scoring rules, which changed in 2021, 2023 and 2026. See docs/LEAGUE_RULES.md. */
    static String mflRulesResourcePath(String year) { "/ff/mfl/data/$year/rules.json" }
    /** Weekly projections under league scoring, collected before the season and never after it. */
    static String mflProjectedScoresResourcePath(String year) { "/ff/mfl/data/$year/projected_scores.json" }
    /** Weekly scoring a finished season actually produced, under that season's rules. */
    static String mflPlayerScoresResourcePath(String year) { "/ff/mfl/data/$year/player_scores.json" }
    /** Raw weekly statistics, so any season can be restated under any rules. See docs/PROJECTION.md. */
    static String nflverseStatsResourcePath(String year) { "/ff/nflverse/data/$year/player_stats.tsv" }
    static String fpDynastyRankingsPprResourcePath(String year) { "/ff/fantasypros/data/$year/dynasty_rankings_ppr.csv" }
    static String fpRookieRankingsPprResourcePath(String year) { "/ff/fantasypros/data/$year/rookie_rankings_ppr.csv" }
    static String fpRedraftRankingsHalfPprResourcePath(String year) { "/ff/fantasypros/data/$year/redraft_rankings_half_ppr.csv" }

    /**
     * Players who show up under a nickname sharing no prefix with their given name, which no amount of
     * fuzzy matching pairs up. Applied to both sources, since it is not only fantasypros that uses the
     * nickname: MFL called Marquise Brown Hollywood in 2024 and Marquise in every other year.
     */
    private static final Map<String, String> NAME_ALIASES = [
            'Hollywood Brown': 'Marquise Brown',
            'Dee Eskridge'   : "D'Wayne Eskridge"
    ]

    static String aliasedName(String name) { NAME_ALIASES[name] ?: name }

    private static final JsonSlurper jsonSlurper = new JsonSlurper()

    static String loadTextResource(String resourcePath) {
        LoadUtils.class.getResourceAsStream(resourcePath).getText('UTF-8')
    }

    static Object loadJsonResource(String resourcePath) {
        def stream = LoadUtils.class.getResourceAsStream(resourcePath)
        jsonSlurper.parse(stream)
    }

    static List<String> loadCsvResource(String resourcePath) {
        def stream = LoadUtils.class.getResourceAsStream(resourcePath)
        stream.readLines()
    }

    static String nameFirstThenLast(String name) {
        List<String> firstLast = tokenizeFirstLast(name)
        firstLast[0] + ' ' + firstLast[1]
    }

    static String nameLastThenFirst(String name) {
        List<String> firstLast = tokenizeFirstLast(name)
        firstLast[1] + ', ' + firstLast[0]
    }

    static List<String> tokenizeFirstLast(String fullName) {
        String[] commaSplit = fullName.split(',')
        if(commaSplit.length == 2) {
            return [normalizeName(commaSplit[1].trim()), commaSplit[0].trim()]
        } else if(commaSplit.length == 1) {
            int firstSpace = fullName.indexOf(' ')
            if(firstSpace == -1) {
                throw new IllegalArgumentException("No space in name $fullName")
            } else {
                return [normalizeName(fullName.substring(0, firstSpace).trim()), fullName.substring(firstSpace + 1).trim()]
            }
        } else {
            throw new IllegalArgumentException("Unexpected number of commas in name $fullName")
        }
    }

    static boolean isNameMatch(String n1, String n2, int numChars) {
        List<String> firstLast1 = tokenizeFirstLast(n1)
        List<String> firstLast2 = tokenizeFirstLast(n2)
        return (firstLast1[0] == firstLast2[0] && firstLast1[1] == firstLast2[1]) ||
                (startsWith(firstLast1[0], firstLast2[0], numChars) && startsWith(firstLast1[1], firstLast2[1], numChars))
    }

    static boolean startsWith(String s1, String s2, int numChars) {
        int minLength = [s1.length(), s2.length(), numChars].min()
        s1.substring(0, minLength) == s2.substring(0, minLength)
    }

    static String normalizeName(String name) {
        name.replaceAll(/\./, '')
    }
}
