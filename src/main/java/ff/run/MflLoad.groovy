package ff.run

import ff.fetch.FetchUtils
import ff.fetch.mfl.MflImport
import ff.load.fuad.FuadWorkbook
import ff.load.fuad.SeasonLoadPlan
import ff.load.util.LoadUtils
import groovy.cli.commons.CliBuilder
import groovy.json.JsonSlurper

/**
 * Load a season's auction and rookie draft onto the league site.
 *
 * Both happen away from myfantasyleague.com — the auction in a room, the rookie draft by email — so what
 * happened exists in the commissioner's workbook and nowhere else until this puts it there. See
 * docs/fuad/ROSTER_LOAD.md for the run book, which includes the two steps no API can do.
 *
 * <b>The rosters themselves are loaded by hand.</b> This is not an oversight. The league site offers no
 * roster import: the only bulk write that reaches a roster is auctionResults, which needs the site's
 * auction module that this league has never used and answers "No auction set up", and adding players one
 * at a time through fcfsWaiver is refused while the free agent pool is locked. The commissioner's Load
 * Rosters page has neither restriction, so `plan` writes the per-team lists it wants and `load` does
 * everything that hangs off the rosters once they are in.
 *
 * Steps:
 * <ul>
 *   <li><b>plan</b> — read the workbook, resolve it against the league's players, and write the roster
 *       lists, the review sheets and the injured reserve to restore. Reads the league site, writes nothing
 *       to it.</li>
 *   <li><b>clear</b> — drop every player from every roster, so the Load Rosters page will accept lists
 *       naming players who are still on somebody else's team.</li>
 *   <li><b>load</b> — contracts, the rookie draft, rookie salaries and injured reserve.</li>
 *   <li><b>verify</b> — compare the league site against the plan, player by player.</li>
 * </ul>
 */
class MflLoad {

    private static final String STEP_PLAN = 'plan'
    private static final String STEP_CLEAR = 'clear'
    private static final String STEP_LOAD = 'load'
    private static final String STEP_VERIFY = 'verify'
    private static final List<String> STEPS = [STEP_PLAN, STEP_CLEAR, STEP_LOAD, STEP_VERIFY].asImmutable()

    private static final String API_HOST = 'api.myfantasyleague.com'
    private static final String OK = '<status>OK</status>'

    static void main(String[] args) {
        def cli = new CliBuilder(usage: 'MflLoad [options]', width: 120)
        cli.y(longOpt: 'year', args: 1, argName: 'year', required: false, 'The year, defaults to most recent.')
        cli.l(longOpt: 'league', args: 1, argName: 'id', required: true,
                'League id. Deliberately has no default, so no step can reach the real league by omission.')
        cli.t(longOpt: 'step', args: 1, argName: 'step', required: true, "One of: $STEPS")
        cli.w(longOpt: 'workbook', args: 1, argName: 'file', required: false,
                'The season workbook, defaults to reports/fuad/fuad_<year>.xlsx.')
        cli.o(longOpt: 'out', args: 1, argName: 'dir', required: false,
                'Where the plan is written and read, defaults to reports/fuad/<year>/load.')
        def options = cli.parse(args)
        if (options == null) return

        try {
            String year = options.year ?: LoadUtils.YEARS.last()
            if (!LoadUtils.YEARS.contains(year)) throw new IllegalArgumentException("Invalid year: $year")
            String step = options.step
            if (!STEPS.contains(step)) throw new IllegalArgumentException("Invalid step: $step, expected $STEPS")
            int league = options.league as int
            File workbook = new File((options.workbook ?: "reports/fuad/fuad_${year}.xlsx") as String)
            File out = new File((options.out ?: "reports/fuad/$year/load") as String)

            new MflLoad(year, league, workbook, out).run(step)
        } catch (Exception e) {
            System.err.println("FAILED: $e.message")
            System.exit(1)
        }
    }

    private final String year
    private final int league
    private final File workbook
    private final File out
    private final String host

    MflLoad(String year, int league, File workbook, File out) {
        this.year = year
        this.league = league
        this.workbook = workbook
        this.out = out
        this.host = MflImport.resolveHost(year as int, league, API_HOST)
    }

    void run(String step) {
        println "league $league on $host ($year)"
        switch (step) {
            case STEP_PLAN -> plan()
            case STEP_CLEAR -> clear()
            case STEP_LOAD -> load()
            case STEP_VERIFY -> verify()
        }
    }

    // ---------------------------------------------------------------- plan

    /**
     * Resolve the workbook and write everything the rest of the run needs.
     *
     * <b>Injured reserve is captured here, before anything is cleared.</b> The workbook does not record it
     * and the Load Rosters page returns everybody active, so the only copy of who was on IR is the league
     * site as it stands right now. Read after the clear it would be empty, and eight players would quietly
     * come back as active.
     */
    private void plan() {
        FuadWorkbook sheet = new FuadWorkbook(workbook)
        SeasonLoadPlan plan = new SeasonLoadPlan(year)
        Map<String, List<Map>> rosters = plan.rosters(sheet.franchises())
        List<Map> releases = plan.releases(sheet.franchises())
        List<Map> picks = plan.picks(sheet.picks(), draftSlots())
        Map<String, Integer> rookiePay = plan.rookieSalaries(picks)

        out.mkdirs()
        new File(out, 'lists').mkdirs()
        Map<String, String> owners = rosters.collectEntries { fid, r -> [(fid): r.first().owner] }

        rosters.each { String fid, List<Map> roster ->
            // The site's own spelling, surname first, which is what its Load Rosters page matches on.
            new File(out, "lists/${fid}_${owners[fid]}.txt").text = roster*.name.join('\n') + '\n'
        }
        write('rosters.tsv', ['FRANCHISE', 'OWNER', 'PLAYER_ID', 'PLAYER', 'POS', 'NFL', 'SALARY', 'YEARS'],
                rosters.collectMany { fid, r -> r.collect { [fid, it.owner, it.id, it.name, it.position, it.team, it.salary, it.years] } })
        write('releases.tsv', ['FRANCHISE', 'OWNER', 'PLAYER_ID', 'PLAYER', 'POS'],
                releases.collect { [it.franchiseId, it.owner, it.id, it.name, it.position] })
        write('picks.tsv', ['ROUND', 'PICK', 'FRANCHISE', 'OWNER', 'PLAYER_ID', 'PLAYER', 'POS', 'SALARY'],
                picks.collect { [it.round, it.pick, it.franchiseId, it.owner, it.playerId, it.playerName, it.position, rookiePay[it.playerId]] })

        List<List> ir = injuredReserve(rosters)
        write('ir.tsv', ['FRANCHISE', 'PLAYER_ID', 'PLAYER'], ir)

        new File(out, 'contracts.xml').text = salariesXml(rosters.collectMany { fid, r ->
            r.collect { [id: it.id, salary: it.salary as String, years: it.years as String] } })
        new File(out, 'rookie_salaries.xml').text = salariesXml(picks.collect {
            [id: it.playerId, salary: rookiePay[it.playerId] as String, years: null] })
        new File(out, 'draft.xml').text = draftXml(picks)

        println "wrote $out"
        println "  ${rosters.values()*.size().sum()} signed players in ${rosters.size()} lists"
        println "  ${releases.size()} released, ${picks.size()} picks, ${ir.size()} on injured reserve"
        println "  rookie class costs \$${rookiePay.values().sum()}"
        println "\nNext: ./mfl_load.sh -t clear -l $league, then load the lists by hand. See docs/fuad/ROSTER_LOAD.md."
    }

    /** Who is on IR now and stays on the roster, so the manual load can be undone for exactly those. */
    private List<List> injuredReserve(Map<String, List<Map>> planned) {
        List<List> ir = []
        rosters(league).each { String fid, Map<String, Map> players ->
            Set<String> keeping = (planned[fid] ?: [])*.id as Set
            players.each { String id, Map player ->
                if (player.status == 'INJURED_RESERVE' && keeping.contains(id)) {
                    ir << [fid, id, new SeasonLoadPlan(year).player(id)?.name]
                }
            }
        }
        ir
    }

    // ---------------------------------------------------------------- clear

    /**
     * Drop every player from every roster.
     *
     * Reads the rosters as they are rather than working from the plan, so it is safe to re-run and cannot
     * leave behind a player the plan did not know about. Drops are never refused for a locked pool — only
     * adds are — which is why this half of the load can be automated and the other half cannot.
     */
    private void clear() {
        MflImport mfl = login()
        Map<String, Map<String, Map>> current = rosters(league)
        int total = current.values()*.size().sum() ?: 0
        if (total == 0) {
            println 'Rosters are already empty.'
            return
        }
        println "dropping $total players"
        int failed = 0
        current.sort { it.key }.each { String fid, Map<String, Map> players ->
            if (players.isEmpty()) return
            String response = mfl.importType('fcfsWaiver',
                    [FRANCHISE_ID: fid, DROP: players.keySet().join(',')])
            if (response.contains(OK)) {
                println "  ok   $fid dropped ${players.size()}"
            } else {
                failed++
                println "  FAIL $fid ${MflImport.errorText(response)}"
            }
        }
        int left = rosters(league).values()*.size().sum() ?: 0
        println left == 0 ? 'All rosters empty.' : "$left players still rostered."
        if (failed) throw new IllegalStateException("$failed drop call(s) failed.")
    }

    // ---------------------------------------------------------------- load

    /**
     * Everything that hangs off rosters that are already in place.
     *
     * The site answers a request it could not apply exactly as it answers one it did, so nothing here
     * believes OK: {@link #verify} reads the result back afterwards and is the only thing that decides
     * whether the load worked.
     */
    private void load() {
        [ 'contracts.xml', 'draft.xml', 'rookie_salaries.xml', 'ir.tsv' ].each {
            if (!new File(out, it).exists()) {
                throw new IllegalStateException("No $it in $out. Run -t plan first.")
            }
        }
        MflImport mfl = login()

        post(mfl, '1/4 contracts', 'salaries',
                [APPEND: '1', DATA: new File(out, 'contracts.xml').text])
        post(mfl, '2/4 rookie draft', 'draftResults',
                [DATA: new File(out, 'draft.xml').text])
        post(mfl, '3/4 rookie salaries', 'salaries',
                [APPEND: '1', DATA: new File(out, 'rookie_salaries.xml').text])

        println '--- 4/4 injured reserve ---'
        rows(new File(out, 'ir.tsv')).groupBy { it[0] }.each { String fid, List<List<String>> players ->
            String response = mfl.importType('ir', [FRANCHISE_ID: fid, DEACTIVATE: players.collect { it[1] }.join(',')])
            println response.contains(OK) ? "  ok   $fid ${players.size()} to IR"
                    : "  FAIL $fid ${MflImport.errorText(response)}"
        }
        println()
        verify()
    }

    private static void post(MflImport mfl, String label, String type, Map<String, String> params) {
        println "--- $label ---"
        String response = mfl.importType(type, params)
        println MflImport.failed(response) ? "  FAILED: ${MflImport.errorText(response)}" : '  OK'
        if (MflImport.failed(response)) throw new IllegalStateException("$label failed.")
    }

    // ---------------------------------------------------------------- verify

    /** Compare the league site against the plan, since OK from an import means nothing on its own. */
    private void verify() {
        List<List<String>> wanted = rows(new File(out, 'rosters.tsv'))
        List<List<String>> picks = rows(new File(out, 'picks.tsv'))
        List<List<String>> ir = rows(new File(out, 'ir.tsv'))
        Map<String, Map<String, Map>> actual = rosters(league)

        Map<String, Map<String, List<String>>> expect = [:].withDefault { [:] }
        wanted.each { expect[it[0]][it[2]] = it }
        picks.each { expect[it[2]][it[4]] = [it[2], it[3], it[4], it[5], null, null, it[7], null] }

        int problems = 0
        println 'franchise  site  plan  membership'
        expect.sort { it.key }.each { String fid, Map<String, List<String>> want ->
            Set<String> have = actual[fid]?.keySet() ?: [] as Set
            Set<String> missing = want.keySet() - have
            Set<String> extra = have - want.keySet()
            problems += missing.size() + extra.size()
            println "  $fid     ${have.size().toString().padLeft(4)}  ${want.size().toString().padLeft(4)}  " +
                    (missing || extra ? "+${extra.size()} extra / -${missing.size()} missing" : 'exact')
            missing.each { println "        MISSING ${want[it][3]}" }
            extra.each { println "        EXTRA   ${new SeasonLoadPlan(year).player(it)?.name ?: it}" }
        }

        int salaryProblems = 0, yearProblems = 0
        expect.each { String fid, Map<String, List<String>> want ->
            want.each { String id, List<String> row ->
                Map player = actual[fid]?.get(id)
                if (!player) return
                if ((player.salary ?: '0') as BigDecimal != (row[6] ?: '0') as BigDecimal) salaryProblems++
                if (row[7] != null && (player.contractYear ?: '0') as String != row[7]) yearProblems++
            }
        }
        int onIr = ir.count { actual[it[0]]?.get(it[1])?.status == 'INJURED_RESERVE' }
        int filled = draftSlots().count { it.player?.trim() }

        println()
        println "total players : ${actual.values()*.size().sum()} (plan ${expect.values()*.size().sum()})"
        println "salary        : $salaryProblems mismatches"
        println "contract year : $yearProblems mismatches"
        println "injured res   : $onIr/${ir.size()} restored"
        println "draft         : $filled/${draftSlots().size()} picks filled"
        problems += salaryProblems + yearProblems + (ir.size() - onIr)
        println problems == 0 ? '\nLOADED: the league site matches the plan.' : "\n$problems problem(s) above."
    }

    // ---------------------------------------------------------------- site

    private MflImport login() {
        String user = System.getenv('MFL_USER')
        String password = System.getenv('MFL_PASS')
        if (!user || !password) {
            throw new IllegalStateException('Set MFL_USER and MFL_PASS. Read the password with `read -rs` ' +
                    'rather than typing it inline, so it stays out of the shell history.')
        }
        MflImport mfl = new MflImport(host, year as int, league)
        mfl.login(user, password)
        println "logged in as $user"
        mfl
    }

    private Map<String, Map<String, Map>> rosters(int leagueId) {
        def json = new JsonSlurper().parseText(
                FetchUtils.fetchText("https://$host/$year/export?JSON=1&TYPE=rosters&L=$leagueId"))
        json.rosters.franchise.collectEntries { franchise ->
            List players = franchise.player == null ? [] :
                    (franchise.player instanceof List ? franchise.player : [franchise.player])
            [(franchise.id as String): players.collectEntries { [(it.id as String): it as Map] }]
        }
    }

    private List<Map> draftSlots() {
        def json = new JsonSlurper().parseText(
                FetchUtils.fetchText("https://$host/$year/export?JSON=1&TYPE=draftResults&L=$league"))
        def unit = json.draftResults.draftUnit
        ((unit instanceof List ? unit.first() : unit).draftPick ?: []) as List<Map>
    }

    // ---------------------------------------------------------------- payloads

    /**
     * The import format is the export format. A salaries payload is the one shape MFL documents, and the
     * draft payload mirrors its own export: without the draftUnit wrapper, or with round="1" where the
     * export writes round="01", the site imports nothing and still answers OK.
     */
    private static String salariesXml(List<Map> players) {
        StringBuilder xml = new StringBuilder('<salaries>\n<leagueUnit unit="LEAGUE">\n')
        players.each { player ->
            xml << '  <player id="' << player.id << '" salary="' << player.salary << '"'
            if (player.years != null) xml << ' contractYear="' << player.years << '"'
            xml << ' />\n'
        }
        xml << '</leagueUnit>\n</salaries>\n'
        xml
    }

    private static String draftXml(List<Map> picks) {
        StringBuilder xml = new StringBuilder('<draftResults>\n  <draftUnit unit="LEAGUE">\n')
        picks.each { pick ->
            xml << '    <draftPick round="' << pick.round << '" pick="' << pick.pick <<
                    '" franchise="' << pick.franchiseId << '" player="' << pick.playerId <<
                    '" timestamp="" comments="" />\n'
        }
        xml << '  </draftUnit>\n</draftResults>\n'
        xml
    }

    private void write(String name, List<String> header, List<List> data) {
        new File(out, name).text = ([header.join('\t')] + data.collect { it.join('\t') }).join('\n') + '\n'
    }

    private static List<List<String>> rows(File file) {
        file.exists() ? file.readLines().tail().findAll { it }.collect { it.split('\t', -1) as List } : []
    }
}
