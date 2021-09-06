package ff.load.fantasypros

import ff.data.Player
import ff.data.Rank
import ff.data.fantasypros.FpRankedPlayer
import ff.load.util.LoadUtils

class FantasyProsLoader {

    Map<String, FpRankedPlayer> loadRankedPlayers(String resource) {
        boolean started = false
        Map<String, FpRankedPlayer> rankedPlayers = [:]
        int overallRankOffset = 0
        int overallRankIndex = -1
        int playerNameIndex = -1
        int teamIndex = -1
        int positionAndRankIndex = -1
        int byeIndex = -1
        LoadUtils.loadCsvResource(resource).each { line ->
            try {
                if (!started) {
                    List<String> headings = line.split('\t')
                    overallRankIndex = [headings.indexOf('Rank'), headings.indexOf('RK')].max()
                    playerNameIndex = [headings.indexOf('Player'), headings.indexOf('Overall'), headings.indexOf('Rookies'), headings.indexOf('PLAYER NAME')].max()
                    teamIndex = [headings.indexOf('Team'), headings.indexOf('TEAM')].max()
                    positionAndRankIndex = [headings.indexOf('Pos'), headings.indexOf('POS')].max()
                    byeIndex = [headings.indexOf('Bye'), headings.indexOf('BYE')].max()
                    started = true
                } else {
                    List<String> vals = line.split('\t')
                    if (vals[playerNameIndex]) {
                        int overallRank = vals[overallRankIndex].trim().toInteger() - overallRankOffset
                        String playerName
                        String team
                        int offset = 0
                        playerName = LoadUtils.nameFirstThenLast(vals[playerNameIndex])
                        team = vals[teamIndex].trim()
                        String positionAndRank = vals[positionAndRankIndex + offset].trim()
                        String position = positionAndRank.find(/^[A-Z]+/)
                        int positionRank = (positionAndRank - position).trim().toInteger()
                        if (position == 'K') {
                            position = 'PK'
                        }
                        String bye = vals[byeIndex + offset].trim()
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
}
