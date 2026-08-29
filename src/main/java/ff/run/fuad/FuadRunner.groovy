package ff.run.fuad

import ff.data.fuad.FuadData
import ff.data.mfl.MflData
import ff.load.fuad.FuadLoader
import ff.load.mfl.MflLoader
import ff.load.util.LoadUtils
import ff.run.ReportManifest
import ff.print.fuad.FuadFranchiseDraftPrinter
import ff.print.fuad.FuadRankingsDraftPrinter
import ff.load.fuad.FuadValuationLoader
import ff.print.fuad.FuadRookieDraftPrinter
import ff.print.fuad.FuadSalaryProjectionPrinter
import ff.print.fuad.FuadRosterFitPrinter
import ff.print.fuad.FuadTeamContextPrinter
import ff.print.fuad.FuadSchedulePrinter
import ff.schedule.fuad.FuadScheduleGenerator
import groovy.util.logging.Slf4j
import groovy.cli.commons.CliBuilder

@Slf4j
class FuadRunner {
    private static final String TYPE_FRANCHISES = 'franchises'
    private static final String TYPE_FRANCHISE_PROJECTIONS = 'franchise_projections'
    private static final String TYPE_RANKINGS = 'rankings'
    private static final String TYPE_ROOKIES = 'rookies'
    private static final String TYPE_SALARIES = 'salaries'
    private static final String TYPE_TEAMS = 'teams'
    private static final String TYPE_SCHEDULE = 'schedule'
    private static final String TYPE_ROSTER = 'roster'
    private static final String TYPE_ALL = 'all'
    private static final List<String> TYPES = [TYPE_FRANCHISES, TYPE_FRANCHISE_PROJECTIONS, TYPE_RANKINGS, TYPE_ROOKIES, TYPE_SALARIES, TYPE_TEAMS, TYPE_SCHEDULE]

    /** Asks what a player adds to one named team, so it has no answer without being told which. */
    private static final List<String> TYPES_NEEDING_FRANCHISE = [TYPE_ROSTER]

    private static final String DEFAULT_OUTPUT_DIR = 'reports/fuad'

    static void main(String[] args) {
        try {
            def cli = new CliBuilder(usage: 'FuadRunner [options]', width: 120, stopAtNonOption: false,
                    header: "Executed with args: $args")
            cli.y(longOpt: 'year', args: 1, argName: 'year', required: false, 'The year, defaults to most recent.')
            cli.t(longOpt: 'type', args: 1, argName: 'type', required: true,
                    "The type of sheet to generate: ${TYPES + TYPE_ROSTER + TYPE_ALL}")
            cli.f(longOpt: 'franchise', args: 1, argName: 'id', required: false,
                    "The franchise to report for, required by: $TYPES_NEEDING_FRANCHISE")
            cli.o(longOpt: 'out', args: 1, argName: 'dir', required: false,
                    "The directory to write reports to, defaults to $DEFAULT_OUTPUT_DIR.")
            def options = cli.parse(args)
            if (options != null) {
                String year = options.year ?: LoadUtils.YEARS.last()
                if (!LoadUtils.YEARS.contains(year)) {
                    throw new IllegalArgumentException("Invalid year: $year")
                }
                String type = options.type
                if (!TYPES.contains(type) && TYPE_ROSTER != type && TYPE_ALL != type) {
                    throw new IllegalArgumentException("Invalid type: $type")
                }
                String franchiseId = options.franchise ?: null
                if (TYPES_NEEDING_FRANCHISE.contains(type) && !franchiseId) {
                    throw new IllegalArgumentException("$type needs a franchise: pass -f <id>")
                }
                // Left out of `all`, since it reports for one team and there is no team to assume.
                List<String> types = TYPE_ALL == type ? TYPES : [type]
                File outputDir = new File(options.out ?: DEFAULT_OUTPUT_DIR, year)
                outputDir.mkdirs()

                // Loading is the expensive part, so do it once no matter how many reports are written.
                FuadData fuadData = types.any { it != TYPE_SCHEDULE } ? new FuadLoader().loadData(year) : null
                MflData mflData = types.contains(TYPE_SCHEDULE) ? (fuadData?.mflData ?: loadMflData(year)) : null

                // One loader across every report, so the points curve is built from nine seasons once.
                FuadValuationLoader valuationLoader = new FuadValuationLoader()

                List<String> written = []
                try {
                    types.each { String t ->
                        // A type may write more than one report: the roster fit and its depth curve come
                        // from one evaluation and would otherwise cost the same expensive run twice.
                        reportsFor(t, year, fuadData, mflData, valuationLoader, franchiseId)
                                .each { String name, Closure<Void> printer ->
                            File file = new File(outputDir, fileName(t, name))
                            file.withPrintWriter { out -> printer(out) }
                            written << name
                            println "Wrote $file"
                        }
                    }
                } finally {
                    // Stamp what was written even if a later report failed, as -t all does for 2023's
                    // schedule, so the reports that did succeed are still attributable to a model.
                    ReportManifest.stamp(outputDir, written)
                }
            } else {
                Runtime.getRuntime().exit(-1)
            }
        } catch (Exception ex) {
            log.error('Error running FuadRunner.', ex)
            Runtime.getRuntime().exit(-1)
        }
    }

    /** The schedule is comma separated for MFL to import; every other report is pasted into a spreadsheet. */
    private static String fileName(String type, String reportName) {
        TYPE_SCHEDULE == type ? "${reportName}.csv" : "${reportName}.tsv"
    }

    /**
     * The reports a type writes, by name, each one a single table.
     *
     * One table per file, because that is what lets a strategy document cite a figure and have it checked.
     * A per-team report carries the team in its name: a roster fit for one franchise is not interchangeable
     * with one for another, and separate names mean separate manifest stamps rather than one file that
     * silently depends on the last -f passed. See docs/STRATEGY.md.
     */
    private static Map<String, Closure<Void>> reportsFor(String type, String year, FuadData fuadData,
                                                         MflData mflData,
                                                         FuadValuationLoader valuationLoader,
                                                         String franchiseId) {
        if (TYPE_SCHEDULE == type) {
            def matchups = new FuadScheduleGenerator().generate(mflData.franchiseByIdMap.values())
            return [(type): { PrintWriter out -> new FuadSchedulePrinter(matchups).print(out) }]
        }
        // Every sheet that quotes a dollar quotes the auction board's, so each of these needs the
        // valuations. Building them costs the points curve, which is nine seasons of statistics, and the
        // shared loader is what keeps -t all from paying for it more than once.
        if (TYPE_FRANCHISES == type) {
            return [(type): { PrintWriter out ->
                new FuadFranchiseDraftPrinter(fuadData, [], false).print(out)
            }]
        }
        if (TYPE_FRANCHISE_PROJECTIONS == type) {
            return [(type): { PrintWriter out ->
                new FuadFranchiseDraftPrinter(fuadData, valuationLoader.valuations(year, fuadData), true)
                        .print(out)
            }]
        }
        if (TYPE_RANKINGS == type) {
            return [(type): { PrintWriter out ->
                new FuadRankingsDraftPrinter(fuadData, valuationLoader.valuations(year, fuadData)).print(out)
            }]
        }
        if (TYPE_ROOKIES == type) {
            // Two tables from one evaluation: the players, and the picks they will be taken with. The
            // second is not a view of the first — a pick has a price whoever is taken with it, and a team
            // weighing a trade needs that ladder without a player attached to it.
            def printer = new FuadRookieDraftPrinter(fuadData,
                    valuationLoader.rookieValues(year, fuadData),
                    valuationLoader.rookieBaselines(year),
                    valuationLoader.rookieDemand().bestAvailableByPick())
            return [(type)         : { PrintWriter out -> printer.print(out) },
                    ('rookie_picks'): { PrintWriter out -> printer.printPicks(out) }]
        }
        if (TYPE_SALARIES == type) {
            return [(type): { PrintWriter out ->
                new FuadSalaryProjectionPrinter(fuadData, valuationLoader.valuations(year, fuadData)).print(out)
            }]
        }
        if (TYPE_TEAMS == type) {
            return [(type): { PrintWriter out ->
                new FuadTeamContextPrinter(fuadData, valuationLoader.valuations(year, fuadData),
                        salaryCap(year), valuationLoader.requirements(year)).print(out)
            }]
        }
        if (TYPE_ROSTER == type) {
            def printer = new FuadRosterFitPrinter(fuadData, valuationLoader.valuations(year, fuadData),
                    valuationLoader.lineups(year), franchiseId)
            String fit = "${type}_${franchiseId}"
            String depth = "${type}_depth_${franchiseId}"
            return [(fit): { PrintWriter out -> printer.print(out) },
                    (depth): { PrintWriter out -> printer.printDepth(out) }]
        }
        [:]
    }

    private static int salaryCap(String year) {
        LoadUtils.loadJsonResource(LoadUtils.mflLeagueResourcePath(year)).league.salaryCapAmount as int
    }

    private static MflData loadMflData(String year) {
        new MflLoader().loadData(
                LoadUtils.mflPlayersResourcePath(year),
                LoadUtils.mflOwnersResourcePath(year),
                LoadUtils.mflLeagueResourcePath(year),
                LoadUtils.mflRostersResourcePath(year),
                LoadUtils.mflDraftResourcePath(year)
        )
    }
}
