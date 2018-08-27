package ff.load.fantasypros

import ff.data.Player
import ff.data.Rank
import ff.data.fantasypros.FpRankedPlayer
import ff.load.util.LoadUtils

class FantasyProsLoader {

    public Map<String, FpRankedPlayer> loadRankedPlayers(String resource) {
        boolean started = false
        Map<String, FpRankedPlayer> rankedPlayers = [:];
        int overallRankOffset = 0
        int overallRankIndex = -1
        int playerNameIndex = -1
        int teamIndex = -1
        int positionAndRankIndex = -1
        int byeIndex = -1
        LoadUtils.loadCsvResource(resource).each { line ->
            if (!started) {
                List<String> headings = line.split('\t')
                overallRankIndex = headings.indexOf('Rank')
                playerNameIndex = [headings.indexOf('Player'), headings.indexOf('Overall'), headings.indexOf('Rookies')].max()
                teamIndex = headings.indexOf('Team')
                positionAndRankIndex = headings.indexOf('Pos')
                byeIndex = headings.indexOf('Bye')
                started = true
            } else {
                List<String> vals = line.split('\t')
                if(vals[playerNameIndex]) {
                    int overallRank = vals[overallRankIndex].trim().toInteger() - overallRankOffset
                    String playerName
                    String team
                    int offset = 0
//                    if(vals.size() == 8) {
//                        String playerAndTeam = vals[1]
//                        playerName = playerAndTeam.substring(0, playerAndTeam.lastIndexOf(' ')).trim()
//                        team = (playerAndTeam - playerName).trim()
//                        offset = -1
//                    } else {
                        playerName = LoadUtils.nameFirstThenLast(vals[playerNameIndex])
                        team = vals[teamIndex].trim()
//                    }
                    String positionAndRank = vals[positionAndRankIndex + offset].trim()
                    String position = positionAndRank.find(/^[A-Z]+/)
                    int positionRank = (positionAndRank - position).trim().toInteger()
                    if(position == 'K') {
                        position = 'PK'
                    }
                    String bye = vals[byeIndex + offset].trim()
                    rankedPlayers[playerName] = new FpRankedPlayer(new Player(playerName, team, position), new Rank(overallRank, positionRank), bye)
                } else {
                    overallRankOffset++
                }
            }
        }
        rankedPlayers
    }
}
