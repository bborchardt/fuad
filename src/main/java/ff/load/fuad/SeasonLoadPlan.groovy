package ff.load.fuad

import ff.data.fuad.SheetPick
import ff.data.fuad.SheetSigning
import ff.load.util.LoadUtils
import ff.projection.fuad.RookieSalary

import java.text.Normalizer

/**
 * Resolve the season's workbook against the league site's own vocabulary, so it can be loaded.
 *
 * The workbook is written by hand in owners' spelling and organised by owner first name. The league site
 * knows franchise ids and player ids and nothing else. Everything that can go wrong in that translation
 * goes wrong quietly — a name that matches two players, an owner whose column was renamed — so this class
 * resolves eagerly and refuses to produce a partial plan. A load that ran on a plan missing four players
 * would look exactly like a load that worked.
 *
 * <b>Unsigned players are released, not loaded.</b> A blank salary on the sheet is a contract that expired
 * and was not re-signed; the site stores those at 0.01 because it rejects a zero. They stay off the roster,
 * and {@link #releases} names them so the load can say who left rather than silently omitting them.
 */
class SeasonLoadPlan {

    /** Suffixes the two sources disagree about often enough to be worth ignoring outright. */
    private static final String SUFFIXES = /\b(jr|sr|ii|iii|iv|v)\b/

    final String year
    private final Map<String, String> franchiseByOwner
    private final Map<String, Map> playersById
    private final Map<String, List<Map>> playersByName

    SeasonLoadPlan(String year) {
        this.year = year
        def owners = LoadUtils.loadJsonResource(LoadUtils.mflOwnersResourcePath(year)).owners
        franchiseByOwner = owners.collectEntries { [(it.name as String): it.franchiseId as String] }
        List players = LoadUtils.loadJsonResource(LoadUtils.mflPlayersResourcePath(year)).players.player
        playersById = players.collectEntries { [(it.id as String): it as Map] }
        playersByName = players.groupBy { normalise(displayName(it.name as String)) }
    }

    /** The signed roster for each franchise id, in the order the sheet lists them. */
    Map<String, List<Map>> rosters(Map<String, List<SheetSigning>> franchises) {
        Map<String, List<Map>> out = [:]
        franchises.each { String owner, List<SheetSigning> roster ->
            String franchiseId = franchiseId(owner)
            out[franchiseId] = roster.findAll { it.signed }.collect { signing ->
                Map player = resolve(signing.playerName, positionOf(signing))
                [id: player.id as String, name: player.name as String, position: player.position as String,
                 team: player.team as String, salary: signing.salary, years: signing.years, owner: owner]
            }
        }
        out
    }

    /** Who the sheet shows leaving: on a roster last season, unsigned in this one's auction. */
    List<Map> releases(Map<String, List<SheetSigning>> franchises) {
        franchises.collectMany { String owner, List<SheetSigning> roster ->
            roster.findAll { !it.signed }.collect { signing ->
                Map player = resolve(signing.playerName, positionOf(signing))
                [id: player.id as String, name: player.name as String, position: player.position as String,
                 owner: owner, franchiseId: franchiseId(owner)]
            }
        }
    }

    /**
     * The draft, joined to the slots the league site holds.
     *
     * The sheet records who picked and whom; the site records which franchise owns each slot, which is the
     * only place a traded pick shows up. Joining them in order checks one against the other, and a
     * disagreement means the sheet and the site describe different drafts — worth stopping for, since the
     * cheapest explanation is a stale local copy of the draft and the next cheapest is a misrecorded pick.
     */
    List<Map> picks(List<SheetPick> sheetPicks, List<Map> slots) {
        if (sheetPicks.size() != slots.size()) {
            throw new IllegalStateException("The sheet records ${sheetPicks.size()} picks and the league " +
                    "site holds ${slots.size()} slots, so they cannot be the same draft.")
        }
        List<Map> ordered = slots.sort(false) { [it.round as String, it.pick as String] }
        [sheetPicks, ordered].transpose().collect { SheetPick sheet, Map slot ->
            String expected = franchiseId(sheet.owner)
            if (expected != slot.franchise) {
                throw new IllegalStateException("Pick ${slot.round}.${slot.pick} is $sheet.owner ($expected) " +
                        "on the sheet and franchise ${slot.franchise} on the league site. Refresh the " +
                        'league data and check for a pick trade before loading.')
            }
            Map player = resolve(sheet.playerName, null)
            [round: slot.round as String, pick: slot.pick as String, franchiseId: expected,
             playerId: player.id as String, playerName: player.name as String,
             position: player.position as String, overall: sheet.overall, owner: sheet.owner]
        }
    }

    /**
     * What each rookie costs, by bylaw 8.3.
     *
     * The rule itself lives in {@link RookieSalary}, where it is held against every pick the league has
     * ever kept to week 1. This only supplies the two things it needs that the model does not already know:
     * which position each pick was, and where in the draft it fell.
     */
    Map<String, Integer> rookieSalaries(List<Map> picks) {
        Map<String, Integer> baselines = RookieSalary.baselinesFor(year)
        picks.collectEntries { pick ->
            Integer baseline = baselines[pick.position as String]
            if (baseline == null) {
                throw new IllegalStateException("No bylaw 8.3 baseline for position ${pick.position} " +
                        "(${pick.playerName}). Baselines are defined for ${RookieSalary.BASELINE_RANK.keySet()}.")
            }
            [(pick.playerId as String), RookieSalary.salary(baseline, (pick.overall as int) - 1)]
        }
    }

    /** Bylaw 8.3.1 baselines coming into this season's draft. */
    Map<String, Integer> baselines() { RookieSalary.baselinesFor(year) }

    String franchiseId(String owner) {
        String id = franchiseByOwner[owner]
        if (!id) {
            throw new IllegalStateException("No franchise for owner '$owner'. The workbook's column headers " +
                    "must match owners.json: ${franchiseByOwner.keySet().sort().join(', ')}.")
        }
        id
    }

    Map player(String id) { playersById[id] }

    /** MFL writes a player surname first; the sheet writes him the way anybody says his name. */
    static String displayName(String mflName) {
        mflName?.contains(',') ? "${mflName.split(',', 2)[1].trim()} ${mflName.split(',', 2)[0].trim()}" : mflName
    }

    /**
     * Find the one player a sheet name means, or refuse.
     *
     * Position narrows a name that two players share — the league has carried a Justin Jefferson at both
     * receiver and linebacker — and where it does not, this throws rather than guessing. Guessing here puts
     * a real player on a real roster and nothing downstream would question it.
     */
    private Map resolve(String name, String position) {
        List<Map> matches = playersByName[normalise(name)] ?: []
        if (matches.size() > 1 && position) {
            List<Map> narrowed = matches.findAll { it.position == position }
            if (narrowed.size() == 1) return narrowed.first()
        }
        if (matches.size() == 1) return matches.first()
        if (matches.isEmpty()) {
            throw new IllegalStateException("No player in the $year league database is named '$name'.")
        }
        throw new IllegalStateException("'$name' matches ${matches.size()} players in the $year league " +
                "database: ${matches.collect { "$it.id/$it.position/$it.team" }.join(', ')}. " +
                'Add the position to the sheet row to say which.')
    }

    /** The board's rank carries the position, e.g. QB12; a row the board did not rank carries nothing. */
    private static String positionOf(SheetSigning signing) {
        def matcher = signing.positionRank =~ /^([A-Z]+)/
        matcher ? matcher[0][1] : null
    }

    private static String normalise(String name) {
        if (name == null) return null
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll(/\p{M}/, '')
        ascii.toLowerCase().replace('.', '').replace("'", '').replace('-', ' ')
                .replaceAll(SUFFIXES, '').replaceAll(/\s+/, ' ').trim()
    }
}
