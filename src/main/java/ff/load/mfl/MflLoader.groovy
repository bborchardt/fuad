package ff.load.mfl

import ff.data.Contract
import ff.data.Draft
import ff.data.Player
import ff.data.mfl.MflDraftPick
import ff.data.mfl.MflFranchise
import ff.data.mfl.MflData
import ff.data.mfl.MflPlayer
import ff.load.util.LoadUtils

class MflLoader {

    MflData loadData(playersResource, ownersResource, leagueResource, rostersResource, draftResource) {
        List playerData = LoadUtils.loadJsonResource(playersResource).players.player
        Map<String, HashMap<String, String>> initialPlayerMap = playerData
                .findAll { p -> ['QB', 'RB', 'WR', 'TE', 'PK'].contains(p.position) }
                .collectEntries() { p ->
            Draft draft = p.draft_pick ? new Draft(p.draft_round.toInteger(), p.draft_pick.toInteger()) : null
            boolean rookie = p.status == 'R'
            [(p.id): [player: new Player(name: LoadUtils.nameFirstThenLast(p.name), team: p.team, position: p.position),
                      id    : p.id, rookie: rookie, draft: draft]]
        }

        Map ownerData = LoadUtils.loadJsonResource(ownersResource).owners.collectEntries {
            [(it.franchiseId): it.name]
        }
        List franchiseData = LoadUtils.loadJsonResource(leagueResource).league.franchises.franchise
        Map<String, HashMap<String, String>> initialFranchiseMap = franchiseData.collectEntries { f ->
            [(f.id): [id: f.id, name: f.name, ownerName: ownerData[f.id], players: []]]
        }

        List rosterData = LoadUtils.loadJsonResource(rostersResource).rosters.franchise
        rosterData.each { f ->
            f.player.each { p ->
                initialPlayerMap[p.id].with { player ->
                    int years = p.contractYear.toInteger()
                    player.contract = years ? new Contract(years, p.salary.toInteger()) : null
                }
            }
        }

        Map<String, MflPlayer> playerByIdMap = initialPlayerMap.collectEntries { k, v ->
            [(k): new MflPlayer(v)]
        }
        Map<String, MflPlayer> playerByNameMap = playerByIdMap.collectEntries { k, v ->
            [(v.player.name): v]
        }

        rosterData.each { f ->
            Map<String, String> franchise = initialFranchiseMap[f.id]
            franchise.players.addAll(f.player.collect { p ->
                MflPlayer player = playerByIdMap[p.id]
                if (!player) {
                    throw new IllegalStateException("Player $p.id not found for franchise $f.id.")
                }
                player
            })
        }

        initialFranchiseMap.values().each { f ->
            f.players.sort { p1, p2 ->
                p1.player.position <=> p2.player.position ?:
                        p2?.contract?.years <=> p1?.contract?.years ?:
                                p2?.contract?.salary <=> p1?.contract?.salary
            }
        }

        Map<String, MflFranchise> franchiseMap = initialFranchiseMap.collectEntries { k, v ->
            [(k): new MflFranchise(v)]
        }

        List draftData = LoadUtils.loadJsonResource(draftResource).draftResults.draftUnit.draftPick
        List<MflDraftPick> draftPicks = draftData.collect { d ->
            new MflDraftPick(d.round.toInteger(), d.pick.toInteger(), franchiseMap[d.franchise])
        }

        new MflData(playerByNameMap, franchiseMap, draftPicks)
    }
}
