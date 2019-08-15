package ff.load.util

import groovy.json.JsonSlurper

class LoadUtils {

    public static final List<String> YEARS = ['2017', '2018', '2019']

    static String mflOwnersResourcePath(String year) { "/ff/mfl/data/$year/owners.json" }
    static String mflLeagueResourcePath(String year) { "/ff/mfl/data/$year/league.json" }
    static String mflPlayersResourcePath(String year) { "/ff/mfl/data/$year/players.json" }
    static String mflRostersResourcePath(String year) { "/ff/mfl/data/$year/rosters.json" }
    static String mflDraftResourcePath(String year) { "/ff/mfl/data/$year/draft.json" }
    static String fpDynastyRankingsPprResourcePath(String year) { "/ff/fantasypros/data/$year/dynasty_rankings_ppr.csv" }
    static String fpRookieRankingsPprResourcePath(String year) { "/ff/fantasypros/data/$year/rookie_rankings_ppr.csv" }
    static String fpRedraftRankingsPprResourcePath(String year) { "/ff/fantasypros/data/$year/redraft_rankings_ppr.csv" }
    static String fpRedraftRankingsHalfPprResourcePath(String year) { "/ff/fantasypros/data/$year/redraft_rankings_half_ppr.csv" }

    private static final JsonSlurper jsonSlurper = new JsonSlurper()

    static Object loadJsonResource(String resourcePath) {
        def stream = getClass().getResourceAsStream(resourcePath)
        jsonSlurper.parse(stream)
    }

    static List<String> loadCsvResource(String resourcePath) {
        def stream = getClass().getResourceAsStream(resourcePath)
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
