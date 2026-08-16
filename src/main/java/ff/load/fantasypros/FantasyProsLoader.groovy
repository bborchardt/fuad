package ff.load.fantasypros

import ff.data.Player
import ff.data.Rank
import ff.data.fantasypros.FpRankedPlayer
import ff.load.util.LoadUtils

class FantasyProsLoader {

    Map<String, FpRankedPlayer> loadRankedPlayers(String resource) {
        loadRankedPlayers(LoadUtils.loadCsvResource(resource))
    }

    /**
     * A season's redraft ranking, with kickers merged in from their own export where it has one.
     *
     * Fantasypros omits kickers from superflex rankings, which is the format this league needs, so from
     * 2026 they come as a separate single position export. Merging them here rather than at each call site
     * is what keeps "the redraft ranking" meaning the same thing everywhere — the byes, the historical
     * levelling and the auction pool all read it, and a set that quietly lacks a position is invisible
     * until somebody needs that position. See docs/DATA.md.
     */
    Map<String, FpRankedPlayer> loadRedraftRankedPlayers(String year) {
        Map<String, FpRankedPlayer> ranked =
                loadRankedPlayers(LoadUtils.fpRedraftRankingsHalfPprResourcePath(year))
        List<String> kickers = LoadUtils.loadCsvResourceIfPresent(LoadUtils.fpKickerRankingsResourcePath(year))
        if (!kickers) {
            return ranked
        }
        // Their own file numbers from one, so the overall rank is offset to sit after the main set. Nothing
        // prices off it, but leaving it would put kickers among the best players in the ranking.
        int offset = ranked.size()
        loadRankedPlayers(kickers, 'PK').each { String name, FpRankedPlayer kicker ->
            ranked[name] = new FpRankedPlayer(kicker.player,
                    new Rank(kicker.rank.overallRank + offset, kicker.rank.positionRank), kicker.bye)
        }
        ranked
    }

    Map<String, FpRankedPlayer> loadRankedPlayers(List<String> lines) {
        loadRankedPlayers(lines, null)
    }

    /**
     * @param impliedPosition the position every row belongs to, for a single position export that carries
     *                        no POS column. The positional rank is then the row's own rank.
     */
    Map<String, FpRankedPlayer> loadRankedPlayers(List<String> lines, String impliedPosition) {
        boolean started = false
        boolean tabSeparated = lines && lines.first().contains('\t')
        Map<String, FpRankedPlayer> rankedPlayers = [:]
        int overallRankIndex = -1
        int playerNameIndex = -1
        int teamIndex = -1
        int positionAndRankIndex = -1
        int byeIndex = -1
        lines.each { line ->
            try {
                if (!started) {
                    List<String> headings = splitLine(line, tabSeparated)
                    overallRankIndex = [headings.indexOf('Rank'), headings.indexOf('RK')].max()
                    playerNameIndex = [headings.indexOf('Player'), headings.indexOf('Overall'), headings.indexOf('Rookies'), headings.indexOf('PLAYER NAME')].max()
                    teamIndex = [headings.indexOf('Team'), headings.indexOf('TEAM')].max()
                    positionAndRankIndex = [headings.indexOf('Pos'), headings.indexOf('POS')].max()
                    byeIndex = [headings.indexOf('Bye'), headings.indexOf('BYE'), headings.indexOf('BYE WEEK')].max()
                    started = true
                } else {
                    List<String> vals = splitLine(line, tabSeparated)
                    // The site's export puts an empty row between tiers. Its rank column skips those rows
                    // rather than counting them, so they are passed over and the rank is taken as written:
                    // renumbering around them collided two players onto one rank in every 2026 set, and in
                    // the kicker export, where the rank is also the positional rank, that is a real loss.
                    if (vals.size() > playerNameIndex && vals[playerNameIndex]) {
                        int overallRank = vals[overallRankIndex].trim().toInteger()
                        String playerName
                        String team
                        int offset = 0
                        playerName = LoadUtils.aliasedName(LoadUtils.nameFirstThenLast(vals[playerNameIndex]))
                        team = vals[teamIndex].trim()
                        String position
                        int positionRank
                        if (positionAndRankIndex < 0 && impliedPosition) {
                            position = impliedPosition
                            positionRank = overallRank
                        } else {
                            String positionAndRank = vals[positionAndRankIndex + offset].trim()
                            position = positionAndRank.find(/^[A-Z]+/)
                            positionRank = (positionAndRank - position).trim().toInteger()
                        }
                        if (position == 'K') {
                            position = 'PK'
                        }
                        String bye = '0'
                        if(byeIndex >= 0) {
                            bye = vals[byeIndex + offset].trim()
                        }
                        rankedPlayers[playerName] = new FpRankedPlayer(new Player(playerName, team, position), new Rank(overallRank, positionRank), bye)
                    }
                }
            } catch (RuntimeException e) {
                throw new RuntimeException("Error parsing line: $line", e)
            }
        }
        rankedPlayers
    }

    /**
     * The hand downloaded exports are tab separated with unquoted values, while the ones exported from the
     * fantasypros site are comma separated with values that may be double quoted (and may themselves contain
     * commas, as in "Ross, Jr."). Split on the delimiter the file actually uses, honoring quoting.
     */
    private static List<String> splitLine(String line, boolean tabSeparated) {
        if (tabSeparated) {
            return line.split('\t').toList()
        }
        List<String> vals = []
        StringBuilder val = new StringBuilder()
        boolean quoted = false
        line.each { String c ->
            if (c == '"') {
                quoted = !quoted
            } else if (c == ',' && !quoted) {
                vals << val.toString()
                val = new StringBuilder()
            } else {
                val.append(c)
            }
        }
        vals << val.toString()
        vals*.trim()
    }
}
