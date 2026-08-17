package ff.fetch.nflverse

import ff.fetch.FetchUtils

/**
 * Fetch a season's weekly player statistics from nflverse and keep the columns this league scores.
 *
 * Raw statistics rather than fantasy points, because the league's scoring has changed four times and one
 * season is only comparable to another once both are restated under a single rule set. Points computed by
 * anyone else are points under somebody else's rules.
 *
 * The published file carries 150 columns and runs to eight megabytes a season. Only the scoring inputs are
 * kept, for the regular season weeks the league plays, at the positions it rosters.
 *
 * <b>Kicking is among them.</b> It was left out on the belief that nflverse does not carry it, which is
 * true of nothing but this extract: the release publishes made field goals with their distances and extra
 * points, and the league scores both. Leaving them out meant no kicker could be levelled and every one
 * priced at the minimum bid.
 *
 * Source: https://github.com/nflverse/nflverse-data, release `stats_player`, which covers 1999 onwards
 * under one schema. The older `player_stats` release stops at 2024 and names interceptions differently; it
 * is deliberately not what this reads.
 */
class NflverseStatsRefresh implements Runnable {

    private static final String RELEASE =
            'https://github.com/nflverse/nflverse-data/releases/download/stats_player/stats_player_week_'

    /** The league's regular season. A salary buys these weeks and no others. */
    static final int LAST_REGULAR_SEASON_WEEK = 14

    /** nflverse names the position K; the league and every other file here call it PK. */
    private static final String NFLVERSE_KICKER = 'K'
    private static final String KICKER = 'PK'

    private static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE', NFLVERSE_KICKER].asImmutable()

    /** Everything the league's scoring rules read, in the order written. */
    static final List<String> COLUMNS = [
            'player_display_name', 'position', 'week',
            'passing_yards', 'passing_tds', 'passing_interceptions',
            'rushing_yards', 'rushing_tds',
            'receiving_yards', 'receiving_tds', 'receptions',
            'sack_fumbles_lost', 'rushing_fumbles_lost', 'receiving_fumbles_lost',
            'passing_2pt_conversions', 'rushing_2pt_conversions', 'receiving_2pt_conversions',
            // Kicking. The distances rather than the buckets, because the league scores by decade from 2026
            // and a 60-plus bucket cannot tell a 62 yard kick from a 71 yard one.
            'fg_made_list', 'pat_made'].asImmutable()

    private final int year

    NflverseStatsRefresh(int year) {
        this.year = year
    }

    @Override
    void run() {
        String url = "$RELEASE${year}.csv"
        println url

        List<String> lines = FetchUtils.fetchText(url).split('\n').toList()
        List<String> header = split(lines.first())
        Map<String, Integer> at = COLUMNS.collectEntries { [(it): header.indexOf(it)] }
        List<String> missing = COLUMNS.findAll { at[it] < 0 }
        if (missing) {
            throw new IllegalStateException("$url is missing $missing, so nflverse has changed its schema.")
        }
        int seasonType = header.indexOf('season_type')

        List<String> kept = lines.tail().findResults { String line ->
            if (!line.trim()) {
                return null
            }
            List<String> values = split(line)
            if (values[seasonType] != 'REG' || !POSITIONS.contains(values[at['position']])) {
                return null
            }
            int week = ((values[at['week']] ?: '0') as BigDecimal).intValue()
            if (week < 1 || week > LAST_REGULAR_SEASON_WEEK) {
                return null
            }
            COLUMNS.collect {
                String value = values[at[it]] ?: ''
                // Written in the league's vocabulary, so one position means one thing across every file here.
                'position' == it && NFLVERSE_KICKER == value ? KICKER : value
            }.join('\t')
        }

        String resourcePath = "$FetchUtils.baseResourceFilePath/ff/nflverse/data/$year"
        new File(resourcePath).mkdirs()
        new File("$resourcePath/player_stats.tsv").text = ([COLUMNS.join('\t')] + kept).join('\n') + '\n'
        println "  kept ${kept.size()} player weeks"
    }

    /** The published file is comma separated with quoted values that may contain commas. */
    private static List<String> split(String line) {
        List<String> out = []
        StringBuilder current = new StringBuilder()
        boolean quoted = false
        line.each { String c ->
            if (c == '"') {
                quoted = !quoted
            } else if (c == ',' && !quoted) {
                out << current.toString(); current = new StringBuilder()
            } else {
                current.append(c)
            }
        }
        out << current.toString().trim()
    }
}
