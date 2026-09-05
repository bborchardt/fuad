package ff.load.fuad

import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.fantasypros.FpRankedPlayer
import ff.data.mfl.MflData
import ff.data.mfl.MflPlayer
import ff.load.fantasypros.FantasyProsLoader
import ff.load.mfl.MflLoader
import ff.load.util.NflTeams
import ff.load.util.LoadUtils

class FuadLoader {

    /**
     * Rookies ranked past this are deep enough that MFL may not carry them at all, so treat only the ones
     * above it as a data problem worth failing on.
     */
    private static final int DRAFTABLE_ROOKIE_RANK = 80

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
                .loadRedraftRankedPlayers(year)
                .collectEntries { k, v -> [k.toUpperCase(), v] }
        Map<String, FpRankedPlayer> upperRookieRankedPlayers = new FantasyProsLoader()
                .loadRankedPlayers(LoadUtils.fpRookieRankingsPprResourcePath(year))
                .collectEntries { k, v -> [k.toUpperCase(), v] }

        Map<String, String> byeByTeam = byeByTeam(upperRedraftRankedPlayers)

        Map<String, MflPlayer> unmatchedPlayerMap = new HashMap(mflData.playerByNameMap)
        populatePlayerMap(mflData.playerByNameMap, unmatchedPlayerMap, playerMap,
                upperDynastyRankedPlayers, upperRedraftRankedPlayers, upperRookieRankedPlayers, byeByTeam, 10)
        populatePlayerMap(mflData.playerByNameMap, unmatchedPlayerMap, playerMap,
                upperDynastyRankedPlayers, upperRedraftRankedPlayers, upperRookieRankedPlayers, byeByTeam, 5)
        populatePlayerMap(mflData.playerByNameMap, unmatchedPlayerMap, playerMap,
                upperDynastyRankedPlayers, upperRedraftRankedPlayers, upperRookieRankedPlayers, byeByTeam, 3)

        Collection<FpRankedPlayer> unmatchedDynasty = upperDynastyRankedPlayers.values().findAll {it.player.position != 'DST' && it.rank.overallRank < 300 }
        Collection<FpRankedPlayer> unmatchedRedraft = upperRedraftRankedPlayers.values().findAll {it.player.position != 'DST' && it.rank.overallRank < 300 }
        Collection<FpRankedPlayer> unmatchedRookie = upperRookieRankedPlayers.values().findAll {
            it.player.team != 'FA' && it.rank.overallRank < DRAFTABLE_ROOKIE_RANK
        }
        if(unmatchedDynasty || unmatchedRedraft || unmatchedRookie) {
            println "unmatched dynasty: $unmatchedDynasty"
            println "unmatched redraft: $unmatchedRedraft"
            println "unmatched rookie: $unmatchedRookie"
            throw new IllegalStateException("Invalid data found")
        }

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
            Map<String, String> byeByTeam,
            int minMatchLength) {
        allPlayerMap.each { String name, MflPlayer mflPlayer ->
            if (unmatchedPlayerMap.containsKey(name)) {
                FpRankedPlayer dynastyRankedPlayer = findPlayer(upperDynastyRankedPlayers, mflPlayer, minMatchLength)
                FpRankedPlayer redraftRankedPlayer = findPlayer(upperRedraftRankedPlayers, mflPlayer, minMatchLength)
                FpRankedPlayer rookieRankedPlayer = findPlayer(upperRookieRankedPlayers, mflPlayer, minMatchLength)
                if (dynastyRankedPlayer || redraftRankedPlayer || rookieRankedPlayer) {
                    mapToPopulate[mflPlayer.player.name] = new FuadPlayer(
                            mflPlayer.player, dynastyRankedPlayer?.rank, redraftRankedPlayer?.rank, rookieRankedPlayer?.rank,
                            mflPlayer.contract, mflPlayer.id, mflPlayer.rookie,
                            byeOf(mflPlayer, byeByTeam, redraftRankedPlayer, dynastyRankedPlayer),
                            mflPlayer.draft)
                    unmatchedPlayerMap.remove(name)
                }
            }
        }
    }

    /**
     * The bye week, from whichever ranking actually carries one, and otherwise from the player's team.
     *
     * Only the redraft export has a {@code BYE WEEK} column, kickers' own export included; the dynasty
     * export has never had one and the loader reads a missing column as 0. Asking the dynasty player
     * therefore put a bye of 0 against every player on the rankings sheet — a week no season has, and one
     * a reader pairing byes across a roster cannot tell apart from a real answer.
     *
     * A bye is a fact of the schedule rather than an opinion about a player, so anyone the ranking does not
     * carry still has one as long as a team does: rookies, and the veterans deep enough that no ranking
     * lists them. Only a free agent, who has no team to take it from, is left at {@code ?} — which is not
     * the same claim as a number and must not be written as one.
     */
    private static String byeOf(MflPlayer mflPlayer, Map<String, String> byeByTeam, FpRankedPlayer... ranked) {
        ranked.findResult { byeWeek(it?.bye) } ?: byeByTeam[teamOf(mflPlayer.player.team)] ?: '?'
    }

    /**
     * Each team's bye, from the ranked players who do carry one.
     *
     * Keyed on the canonical abbreviation because the two sources spell a team differently — the league
     * site writes KCC and NEP where the rankings write KC and NE — and an unmapped one silently leaves a
     * player playing a week nobody plays. See {@link NflTeams}.
     */
    private static Map<String, String> byeByTeam(Map<String, FpRankedPlayer> rankedPlayers) {
        rankedPlayers.values().findResults { FpRankedPlayer p ->
            String bye = byeWeek(p.bye)
            bye ? [teamOf(p.player.team), bye] : null
        }.collectEntries { it }
    }

    /** A bye the source actually states, or null: a missing column reads as 0, which is no week at all. */
    private static String byeWeek(String bye) {
        bye?.isInteger() && (bye as int) > 0 ? bye : null
    }

    private static String teamOf(String team) {
        NflTeams.abbreviationOf(team) ?: team
    }

    private List<FuadPlayer> rankedList(Map<String, FuadPlayer> playerMap, String position) {
        playerMap.values().findAll { p ->
            p.player.position.toUpperCase() == position && p.redraftRank
        }.sort { it.redraftRank.positionRank }
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
