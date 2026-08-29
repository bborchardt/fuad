package ff.load.fuad

import ff.data.fuad.RookiePick
import ff.load.util.LoadUtils

/**
 * Every rookie draft the league has held, joined to the rosters that say what happened to each pick.
 *
 * <b>Recovering this was a data collection problem before it was a modelling one.</b> The draft export is
 * pulled for the season being played, which is before its draft has run, so the copy this project kept
 * carried the slots and the franchises and an empty player at every one of them. The league site fills the
 * same file in afterwards and keeps it, so all nine drafts were recoverable at once. Nothing else records a
 * pick: a draft selection is not a transaction, and the transaction log has no entry for one.
 *
 * The join to week 1 rosters is what turns a selection into an outcome. It supplies the salary, which is
 * set by rule and checks {@link ff.projection.fuad.RookieSalary} against the record, and it supplies the
 * fact of a pick having been kept at all, which is the league's own verdict on it.
 */
class RookieDraftHistory {

    /** Seasons whose draft has been held and whose week 1 rosters are collected. */
    static final List<String> SEASONS = (2017..2025).collect { it as String }.asImmutable() as List<String>

    /**
     * Seasons whose rookie salaries can be checked, which starts one later than the drafts do.
     *
     * A baseline is a salary from the previous season's trading deadline, and 2017 has no 2016 behind it.
     */
    static final List<String> PRICED_SEASONS =
            (2018..2025).collect { it as String }.asImmutable() as List<String>

    /** One draft, in pick order. */
    static List<RookiePick> picks(String season) {
        Map draft = LoadUtils.loadJsonResource(LoadUtils.mflDraftResourcePath(season)) as Map
        def unit = draft.draftResults.draftUnit
        List<Map> drafted = ((unit instanceof List ? unit.first() : unit) as Map).draftPick as List<Map>

        Map<String, Map> players = ((LoadUtils.loadJsonResource(
                LoadUtils.mflPlayersResourcePath(season)) as Map).players.player as List<Map>)
                .collectEntries { [(it.id as String): it] }

        Map<String, Map> rostered = rosteredAtWeekOne(season)

        drafted.withIndex().collect { Map pick, int index ->
            Map player = players[pick.player as String]
            Map contract = rostered[pick.player as String]
            new RookiePick(
                    season: season,
                    overall: index + 1,
                    round: (pick.round as String) as int,
                    pick: (pick.pick as String) as int,
                    franchiseId: pick.franchise as String,
                    playerId: pick.player as String,
                    playerName: player?.name as String,
                    position: player?.position as String,
                    salary: contract ? new BigDecimal(contract.salary as String).intValue() : null,
                    contractYears: contract?.contractYear ? (contract.contractYear as String) as int : null)
        }
    }

    /** Every draft, in the order the seasons were played. */
    static Map<String, List<RookiePick>> picksBySeason() {
        SEASONS.collectEntries { [(it): picks(it)] } as Map<String, List<RookiePick>>
    }

    private static Map<String, Map> rosteredAtWeekOne(String season) {
        Map rosters = LoadUtils.loadJsonResource(LoadUtils.mflPostDraftRostersResourcePath(season)) as Map
        (rosters.rosters.franchise as List<Map>)
                .collectMany { Map franchise ->
                    def held = franchise.player ?: []
                    (held instanceof List ? held : [held]) as List<Map>
                }
                .collectEntries { [(it.id as String): it] }
    }
}
