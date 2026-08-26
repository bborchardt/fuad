package ff.run.greenfield

import ff.league.League
import ff.load.greenfield.GreenfieldBoard
import ff.load.greenfield.GreenfieldValuationLoader
import ff.print.greenfield.GreenfieldBoardPrinter
import ff.print.greenfield.GreenfieldKeeperPrinter
import ff.print.greenfield.GreenfieldPickPrinter
import ff.run.fuad.ReportManifest
import groovy.cli.commons.CliBuilder
import groovy.util.logging.Slf4j

/**
 * The Greenfield league's sheets: a draft board, the keeper decisions, and what a pick has been worth.
 *
 * Separate from the auction's runner because they share no report. That one prices a market against a cap;
 * this one values picks against a board and has no dollars in it at all. What they do share is underneath —
 * the curve, replacement, and value over replacement — and is shared as code rather than as a report type.
 *
 * Reports are stamped by the same manifest the auction uses, so a Greenfield plan can be held to the board
 * it was written from in the same way and by the same check. See docs/STRATEGY.md.
 */
@Slf4j
class GreenfieldRunner {

    private static final String TYPE_BOARD = 'board'
    private static final String TYPE_KEEPERS = 'keepers'
    private static final String TYPE_PICKS = 'picks'
    private static final String TYPE_ALL = 'all'
    private static final List<String> TYPES = [TYPE_BOARD, TYPE_KEEPERS, TYPE_PICKS]

    private static final String DEFAULT_OUTPUT_DIR = 'reports/greenfield'
    private static final String DEFAULT_YEAR = '2026'

    static void main(String[] args) {
        try {
            def cli = new CliBuilder(usage: 'GreenfieldRunner [options]', width: 120,
                    header: "Executed with args: $args")
            cli.y(longOpt: 'year', args: 1, argName: 'year', required: false,
                    "The year to draft, defaults to $DEFAULT_YEAR.")
            cli.t(longOpt: 'type', args: 1, argName: 'type', required: true,
                    "The type of sheet to generate: ${TYPES + TYPE_ALL}")
            cli.o(longOpt: 'out', args: 1, argName: 'dir', required: false,
                    "The directory to write reports to, defaults to $DEFAULT_OUTPUT_DIR.")
            def options = cli.parse(args)
            if (options == null) {
                Runtime.getRuntime().exit(-1)
                return
            }
            String year = options.year ?: DEFAULT_YEAR
            String type = options.type
            if (!TYPES.contains(type) && TYPE_ALL != type) {
                throw new IllegalArgumentException("Invalid type: $type")
            }
            List<String> types = TYPE_ALL == type ? TYPES : [type]

            File outputDir = new File(options.out ?: DEFAULT_OUTPUT_DIR, year)
            outputDir.mkdirs()

            // One loader across every sheet: the curve is nine seasons of statistics and is wanted by all.
            GreenfieldValuationLoader loader = new GreenfieldValuationLoader()

            List<String> written = []
            try {
                types.each { String t ->
                    File file = new File(outputDir, "${t}.tsv")
                    file.withPrintWriter { PrintWriter out -> printer(t, year, loader)(out) }
                    written << t
                    println "Wrote $file"
                }
            } finally {
                ReportManifest.stamp(outputDir, written, 'greenfield_report.sh')
            }
        } catch (Exception ex) {
            log.error('Error running GreenfieldRunner.', ex)
            Runtime.getRuntime().exit(-1)
        }
    }

    private static Closure<Void> printer(String type, String year, GreenfieldValuationLoader loader) {
        GreenfieldBoard board = new GreenfieldBoard(year, loader)
        if (TYPE_PICKS == type) {
            return { PrintWriter out ->
                new GreenfieldPickPrinter(board.pickValues(), League.GREENFIELD.teams).print(out)
            }
        }
        if (TYPE_BOARD == type) {
            return { PrintWriter out ->
                new GreenfieldBoardPrinter(board.ranked, board.curve, board.replacement, board.byes,
                        board.keptBy()).print(out)
            }
        }
        return { PrintWriter out -> new GreenfieldKeeperPrinter(board.keepers()).print(out) }
    }
}
