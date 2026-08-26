package ff.fetch.nflverse

import ff.fetch.FetchUtils
import ff.load.util.NflTeams

/**
 * Fetch a season's weekly team defence statistics and keep the columns a league scores a defence on.
 *
 * The sibling of {@link NflverseStatsRefresh} and deliberately its shape: raw counts rather than fantasy
 * points, for the regular season weeks the league plays, so that any season can be restated under whichever
 * rules are being priced.
 *
 * <b>Points allowed is folded in here, from a different source.</b> The team release carries every defensive
 * count a league scores but no scores at all, and points allowed is the largest single term in Yahoo's
 * defence scoring — ten points for a shutout down to minus four for thirty five conceded. It comes from
 * nflverse's {@code games.csv}, which is one file for every season rather than one per season, joined on the
 * game id the team release already carries. Folding it in at collection means the committed file answers on
 * its own, at the cost of a column whose provenance differs from its neighbours' — which is what this
 * paragraph is for.
 *
 * <b>Two columns need care, and neither says so in its name.</b>
 *
 * {@code def_tds} does not include fumble returns: of 220 team weeks with a fumble recovery touchdown,
 * 197 have {@code def_tds} at zero. So a defensive touchdown is {@code def_tds} plus the fumble recovery,
 * and adding them is not double counting.
 *
 * {@code fumble_recovery_tds} does not say whose fumble it was, and a team recovering its own in the end
 * zone has scored on offence rather than on defence. Where a team recovered no opponent fumble that week the
 * touchdown cannot have been defensive, and {@code fumble_recovery_opp} is kept so a loader can tell: it
 * rules out 11 of the 220 outright. The remaining ambiguity is a week in which a team recovered both kinds,
 * which these columns cannot separate — 102 weeks over nine seasons, and overwhelmingly defensive.
 *
 * <b>The two sources disagree about what a relocated franchise is called, and in opposite directions.</b>
 * The team release writes today's abbreviation into every season a franchise ever played, so the Raiders are
 * LV in 2017; the scores write the abbreviation of the day, OAK. Joined as written, thirteen team weeks of
 * 2017 have no score — which is the same forward-dating nflverse does to player names, done to teams. Both
 * sides go through {@link NflTeams}, which knows that OAK and LV are one franchise and SD and LAC another.
 *
 * Source: https://github.com/nflverse/nflverse-data, release `stats_team`, and
 * https://github.com/nflverse/nfldata for the scores.
 */
class NflverseTeamStatsRefresh implements Runnable {

    private static final String RELEASE =
            'https://github.com/nflverse/nflverse-data/releases/download/stats_team/stats_team_week_'

    /** One file for every season, so it is fetched per run rather than kept per year. */
    private static final String GAMES = 'https://raw.githubusercontent.com/nflverse/nfldata/master/data/games.csv'

    static final int LAST_REGULAR_SEASON_WEEK = NflverseStatsRefresh.LAST_REGULAR_SEASON_WEEK

    /** What the release carries that a defence is scored on. */
    static final List<String> COLUMNS = [
            'team', 'week',
            'def_sacks', 'def_interceptions', 'def_safeties', 'def_tds',
            'fumble_recovery_opp', 'fumble_recovery_tds',
            'special_teams_tds',
            'def_punt_blocks', 'def_pat_blocks', 'def_fg_blocks',
            'def_2pt_made'].asImmutable()

    /** Written alongside them, from the scores rather than from the team release. */
    static final String POINTS_ALLOWED = 'points_allowed'

    private final int year

    NflverseTeamStatsRefresh(int year) {
        this.year = year
    }

    @Override
    void run() {
        Map<List<String>, Integer> allowed = pointsAllowed()

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
        int gameId = header.indexOf('game_id')

        List<String> unscored = []
        List<String> kept = lines.tail().findResults { String line ->
            if (!line.trim()) {
                return null
            }
            List<String> values = split(line)
            if (values[seasonType] != 'REG') {
                return null
            }
            int week = ((values[at['week']] ?: '0') as BigDecimal).intValue()
            if (week < 1 || week > LAST_REGULAR_SEASON_WEEK) {
                return null
            }
            String team = canonical(values[at['team']])
            Integer conceded = allowed[[values[gameId], team]]
            if (conceded == null) {
                // A team week with no score is a team week that cannot be scored, and dropping it silently
                // would show up as a defence that played fewer games rather than as missing data.
                unscored << "${values[gameId]} ${values[at['team']]}".toString()
                return null
            }
            (COLUMNS.collect { values[at[it]] ?: '' } + [conceded as String]).join('\t')
        }
        if (unscored) {
            throw new IllegalStateException(
                    "$url has ${unscored.size()} team weeks with no score in games.csv, " +
                            "the first being ${unscored.first()}")
        }

        String resourcePath = "$FetchUtils.baseResourceFilePath/ff/nflverse/data/$year"
        new File(resourcePath).mkdirs()
        new File("$resourcePath/team_stats.tsv").text =
                ([(COLUMNS + [POINTS_ALLOWED]).join('\t')] + kept).join('\n') + '\n'
        println "  kept ${kept.size()} team weeks"
    }

    /** What each team conceded in each game, being the other team's score. */
    private static Map<List<String>, Integer> pointsAllowed() {
        List<String> lines = FetchUtils.fetchText(GAMES).split('\n').toList()
        List<String> header = split(lines.first())
        int id = header.indexOf('game_id'), home = header.indexOf('home_team')
        int away = header.indexOf('away_team'), homeScore = header.indexOf('home_score')
        int awayScore = header.indexOf('away_score')
        Map<List<String>, Integer> allowed = [:]
        lines.tail().each { String line ->
            if (!line.trim()) {
                return
            }
            List<String> values = split(line)
            if (!values[homeScore]?.trim() || !values[awayScore]?.trim()) {
                return
            }
            allowed[[values[id], canonical(values[home])]] = (values[awayScore] as BigDecimal).intValue()
            allowed[[values[id], canonical(values[away])]] = (values[homeScore] as BigDecimal).intValue()
        }
        allowed
    }

    /** One franchise, one abbreviation, whichever era the file was written in. */
    private static String canonical(String abbreviation) {
        NflTeams.abbreviationOf(abbreviation) ?: abbreviation
    }

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
