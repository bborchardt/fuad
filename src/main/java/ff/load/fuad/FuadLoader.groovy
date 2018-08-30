package ff.load.fuad

import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.fantasypros.FpRankedPlayer
import ff.data.mfl.MflData
import ff.data.mfl.MflPlayer
import ff.load.fantasypros.FantasyProsLoader
import ff.load.mfl.MflLoader
import ff.load.util.LoadUtils

class FuadLoader {

    FuadData loadData(String year) {
        Map<String, FuadPlayer> playerMap = [:]
        MflData mflData = new MflLoader().loadData(
                LoadUtils.mflPlayersResourcePath(year),
                LoadUtils.mflOwnersResourcePath(year),
                LoadUtils.mflLeagueResourcePath(year),
                LoadUtils.mflRostersResourcePath(year),
                LoadUtils.mflDraftResourcePath(year)
        )
        Map<String, FpRankedPlayer> upperDynastyRankedPlayers = new FantasyProsLoader()
                .loadRankedPlayers(LoadUtils.fpDynastyRankingsPprResourcePath(year))
                .collectEntries { k, v -> [k.toUpperCase(), v] }
        Map<String, FpRankedPlayer> upperRedraftRankedPlayers = new FantasyProsLoader()
                .loadRankedPlayers(LoadUtils.fpRedraftRankingsHalfPprResourcePath(year))
                .collectEntries { k, v -> [k.toUpperCase(), v] }
        Map<String, FpRankedPlayer> upperRookieRankedPlayers = new FantasyProsLoader()
                .loadRankedPlayers(LoadUtils.fpRookieRankingsPprResourcePath(year))
                .collectEntries { k, v -> [k.toUpperCase(), v] }

        Map<String, MflPlayer> unmatchedPlayerMap = new HashMap(mflData.playerByNameMap)
        populatePlayerMap(mflData.playerByNameMap, unmatchedPlayerMap, playerMap,
                upperDynastyRankedPlayers, upperRedraftRankedPlayers, upperRookieRankedPlayers, 10)
        populatePlayerMap(mflData.playerByNameMap, unmatchedPlayerMap, playerMap,
                upperDynastyRankedPlayers, upperRedraftRankedPlayers, upperRookieRankedPlayers, 5)
        populatePlayerMap(mflData.playerByNameMap, unmatchedPlayerMap, playerMap,
                upperDynastyRankedPlayers, upperRedraftRankedPlayers, upperRookieRankedPlayers, 3)

        new FuadData(
                mflData,
                playerMap,
                rankedList(playerMap, 'QB'),
                rankedList(playerMap, 'RB'),
                rankedList(playerMap, 'WR'),
                rankedList(playerMap, 'TE'),
                rankedList(playerMap, 'PK'),
                rankedRookieList(playerMap)
        )
    }

    private Map<String, MflPlayer> populatePlayerMap(
            Map<String, MflPlayer> allPlayerMap,
            Map<String, MflPlayer> unmatchedPlayerMap,
            Map<String, FuadPlayer> mapToPopulate,
            Map<String, FpRankedPlayer> upperDynastyRankedPlayers,
            Map<String, FpRankedPlayer> upperRedraftRankedPlayers,
            Map<String, FpRankedPlayer> upperRookieRankedPlayers,
            int minMatchLength) {
        allPlayerMap.each { String name, MflPlayer mflPlayer ->
            if (unmatchedPlayerMap.containsKey(name)) {
                FpRankedPlayer dynastyRankedPlayer = findPlayer(upperDynastyRankedPlayers, mflPlayer, minMatchLength)
                FpRankedPlayer redraftRankedPlayer = findPlayer(upperRedraftRankedPlayers, mflPlayer, minMatchLength)
                FpRankedPlayer rookieRankedPlayer = findPlayer(upperRookieRankedPlayers, mflPlayer, minMatchLength)
                if (dynastyRankedPlayer || redraftRankedPlayer || rookieRankedPlayer) {
                    mapToPopulate[mflPlayer.player.name] = new FuadPlayer(
                            mflPlayer.player, dynastyRankedPlayer?.rank, redraftRankedPlayer?.rank, rookieRankedPlayer?.rank,
                            mflPlayer.contract, mflPlayer.id, mflPlayer.rookie, dynastyRankedPlayer?.bye ?: '?', mflPlayer.draft)
                    unmatchedPlayerMap.remove(name)
                }
            }
        }
    }

    private List<FuadPlayer> rankedList(Map<String, FuadPlayer> playerMap, String position) {
        playerMap.values().findAll { p ->
            p.player.position.toUpperCase() == position && p.dynastyRank
        }.sort { it.dynastyRank.positionRank }
    }

    private List<FuadPlayer> rankedRookieList(Map<String, FuadPlayer> playerMap) {
        playerMap.values()
                .findAll { p -> p.rookieRank != null }
                .sort { it.rookieRank.overallRank }
    }

    private FpRankedPlayer findPlayer(Map<String, FpRankedPlayer> uppercasePlayerMap, MflPlayer mflPlayer, int minMatchLength) {
        def nameToMatch = mflPlayer.player.name.toUpperCase()
        FpRankedPlayer nameMatch = uppercasePlayerMap[nameToMatch]
        FpRankedPlayer match = nameMatch && nameMatch.player.position == mflPlayer.player.position ? nameMatch : null
        if(!match) {
            match = uppercasePlayerMap.values().find { p ->
                mflPlayer.player.position == p.player.position &&
                    LoadUtils.isNameMatch(p.player.name.toUpperCase(), nameToMatch.toUpperCase(), minMatchLength)
            }
        }
        if(match) {
            uppercasePlayerMap.remove(match.player.name.toUpperCase())
        }
        return match
    }
}
