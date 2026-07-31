package ff.load.fantasypros

import ff.data.Player
import ff.data.Rank
import ff.data.fantasypros.FpRankedPlayer
import ff.load.util.LoadUtils

class FantasyProsLoader {

    Map<String, FpRankedPlayer> loadRankedPlayers(String resource) {
        loadRankedPlayers(LoadUtils.loadCsvResource(resource))
    }

    Map<String, FpRankedPlayer> loadRankedPlayers(List<String> lines) {
        boolean started = false
        boolean tabSeparated = lines && lines.first().contains('\t')
        Map<String, FpRankedPlayer> rankedPlayers = [:]
        int overallRankOffset = 0
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
                    if (vals[playerNameIndex]) {
                        int overallRank = vals[overallRankIndex].trim().toInteger() - overallRankOffset
                        String playerName
                        String team
                        int offset = 0
                        playerName = LoadUtils.aliasedName(LoadUtils.nameFirstThenLast(vals[playerNameIndex]))
                        team = vals[teamIndex].trim()
                        String positionAndRank = vals[positionAndRankIndex + offset].trim()
                        String position = positionAndRank.find(/^[A-Z]+/)
                        int positionRank = (positionAndRank - position).trim().toInteger()
                        if (position == 'K') {
                            position = 'PK'
                        }
                        String bye = '0'
                        if(byeIndex >= 0) {
                            bye = vals[byeIndex + offset].trim()
                        }
                        rankedPlayers[playerName] = new FpRankedPlayer(new Player(playerName, team, position), new Rank(overallRank, positionRank), bye)
                    } else {
                        overallRankOffset++
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
