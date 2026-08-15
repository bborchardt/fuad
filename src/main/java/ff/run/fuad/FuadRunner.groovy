package ff.run.fuad

import ff.data.fuad.FuadData
import ff.data.mfl.MflData
import ff.load.fuad.FuadLoader
import ff.load.mfl.MflLoader
import ff.load.util.LoadUtils
import ff.print.fuad.FuadFranchiseDraftPrinter
import ff.print.fuad.FuadRankingsDraftPrinter
import ff.load.fuad.FuadValuationLoader
import ff.print.fuad.FuadRookieDraftPrinter
import ff.print.fuad.FuadSalaryProjectionPrinter
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
    private static final String TYPE_ALL = 'all'
    private static final List<String> TYPES = [TYPE_FRANCHISES, TYPE_FRANCHISE_PROJECTIONS, TYPE_RANKINGS, TYPE_ROOKIES, TYPE_SALARIES, TYPE_TEAMS, TYPE_SCHEDULE]

    private static final String DEFAULT_OUTPUT_DIR = 'reports'

    static void main(String[] args) {
        try {
            def cli = new CliBuilder(usage: 'FuadRunner [options]', width: 120, stopAtNonOption: false,
                    header: "Executed with args: $args")
            cli.y(longOpt: 'year', args: 1, argName: 'year', required: false, 'The year, defaults to most recent.')
            cli.t(longOpt: 'type', args: 1, argName: 'type', required: true,
                    "The type of sheet to generate: ${TYPES + TYPE_ALL}")
            cli.o(longOpt: 'out', args: 1, argName: 'dir', required: false,
                    "The directory to write reports to, defaults to $DEFAULT_OUTPUT_DIR.")
            def options = cli.parse(args)
            if (options != null) {
                String year = options.year ?: LoadUtils.YEARS.last()
                if (!LoadUtils.YEARS.contains(year)) {
                    throw new IllegalArgumentException("Invalid year: $year")
                }
                String type = options.type
                if (!TYPES.contains(type) && TYPE_ALL != type) {
                    throw new IllegalArgumentException("Invalid type: $type")
                }
                List<String> types = TYPE_ALL == type ? TYPES : [type]
                File outputDir = new File(options.out ?: DEFAULT_OUTPUT_DIR, year)
                outputDir.mkdirs()

                // Loading is the expensive part, so do it once no matter how many reports are written.
                FuadData fuadData = types.any { it != TYPE_SCHEDULE } ? new FuadLoader().loadData(year) : null
                MflData mflData = types.contains(TYPE_SCHEDULE) ? (fuadData?.mflData ?: loadMflData(year)) : null

                List<String> written = []
                try {
                    types.each { String t ->
                        File file = new File(outputDir, fileName(t))
                        file.withPrintWriter { out -> printReport(t, year, out, fuadData, mflData) }
                        written << t
                        println "Wrote $file"
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
    private static String fileName(String type) {
        TYPE_SCHEDULE == type ? "${type}.csv" : "${type}.tsv"
    }

    private static void printReport(String type, String year, PrintWriter out, FuadData fuadData, MflData mflData) {
        if (TYPE_SCHEDULE == type) {
            def matchups = new FuadScheduleGenerator().generate(mflData.franchiseByIdMap.values())
            new FuadSchedulePrinter(matchups).print(out)
        } else if (TYPE_FRANCHISES == type) {
            new FuadFranchiseDraftPrinter(fuadData, false).print(out)
        } else if (TYPE_FRANCHISE_PROJECTIONS == type) {
            new FuadFranchiseDraftPrinter(fuadData, true).print(out)
        } else if (TYPE_RANKINGS == type) {
            new FuadRankingsDraftPrinter(fuadData).print(out)
        } else if (TYPE_ROOKIES == type) {
            new FuadRookieDraftPrinter(fuadData).print(out)
        } else if (TYPE_SALARIES == type) {
            new FuadSalaryProjectionPrinter(fuadData, new FuadValuationLoader().valuations(year, fuadData))
                    .print(out)
        } else if (TYPE_TEAMS == type) {
            new FuadTeamContextPrinter(fuadData, new FuadValuationLoader().valuations(year, fuadData),
                    salaryCap(year)).print(out)
        }
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
