package ff.run.greenfield

import ff.league.League
import ff.load.greenfield.GreenfieldBoard
import ff.load.greenfield.GreenfieldValuationLoader
import ff.print.greenfield.GreenfieldAdpPrinter
import ff.print.greenfield.GreenfieldBoardPrinter
import ff.print.greenfield.GreenfieldDemandPrinter
import ff.print.greenfield.GreenfieldKeeperPrinter
import ff.print.greenfield.GreenfieldOutlookPrinter
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
    private static final String TYPE_DEMAND = 'demand'
    private static final String TYPE_ADP = 'adp'
    private static final String TYPE_OUTLOOK = 'outlook'
    private static final String TYPE_ALL = 'all'
    private static final List<String> TYPES = [TYPE_BOARD, TYPE_KEEPERS, TYPE_PICKS, TYPE_DEMAND, TYPE_ADP]

    private static final String DEFAULT_OUTPUT_DIR = 'reports/greenfield'
    private static final String DEFAULT_YEAR = '2026'

    /** Rounds in this league's draft, which every collected season has run. */
    private static final int ROUNDS = 15

    static void main(String[] args) {
        try {
            def cli = new CliBuilder(usage: 'GreenfieldRunner [options]', width: 120,
                    header: "Executed with args: $args")
            cli.y(longOpt: 'year', args: 1, argName: 'year', required: false,
                    "The year to draft, defaults to $DEFAULT_YEAR.")
            cli.t(longOpt: 'type', args: 1, argName: 'type', required: true,
                    "The type of sheet to generate: ${TYPES + TYPE_OUTLOOK + TYPE_ALL}")
            cli.s(longOpt: 'slot', args: 1, argName: 'slot', required: false,
                    "The draft slot to report for, required by: $TYPE_OUTLOOK")
            cli.o(longOpt: 'out', args: 1, argName: 'dir', required: false,
                    "The directory to write reports to, defaults to $DEFAULT_OUTPUT_DIR.")
            def options = cli.parse(args)
            if (options == null) {
                Runtime.getRuntime().exit(-1)
                return
            }
            String year = options.year ?: DEFAULT_YEAR
            String type = options.type
            if (!TYPES.contains(type) && TYPE_ALL != type && TYPE_OUTLOOK != type) {
                throw new IllegalArgumentException("Invalid type: $type")
            }
            // Left out of `all`, since it reports for one slot and there is no slot to assume.
            Integer slot = options.slot ? options.slot as int : null
            if (TYPE_OUTLOOK == type && !slot) {
                throw new IllegalArgumentException("$TYPE_OUTLOOK needs a slot: pass -s <1..${League.GREENFIELD.teams}>")
            }
            List<String> types = TYPE_ALL == type ? TYPES : [type]

            File outputDir = new File(options.out ?: DEFAULT_OUTPUT_DIR, year)
            outputDir.mkdirs()

            // One loader across every sheet: the curve is nine seasons of statistics and is wanted by all.
            GreenfieldValuationLoader loader = new GreenfieldValuationLoader()

            List<String> written = []
            try {
                types.each { String t ->
                    // A per-slot sheet carries the slot in its name: one slot's outlook is not
                    // interchangeable with another's, and separate names mean separate manifest stamps
                    // rather than one file that silently depends on the last -s passed.
                    String name = TYPE_OUTLOOK == t ? "${t}_${slot}" : t
                    File file = new File(outputDir, "${name}.tsv")
                    file.withPrintWriter { PrintWriter out -> printer(t, year, loader, slot)(out) }
                    written << name
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

    private static Closure<Void> printer(String type, String year, GreenfieldValuationLoader loader,
                                         Integer slot) {
        GreenfieldBoard board = new GreenfieldBoard(year, loader)
        if (TYPE_OUTLOOK == type) {
            return { PrintWriter out ->
                new GreenfieldOutlookPrinter(board, slot, League.GREENFIELD.teams, ROUNDS,
                        board.forfeitedBy(slot), League.GREENFIELD.scoredPositions).print(out)
            }
        }
        if (TYPE_PICKS == type) {
            return { PrintWriter out ->
                new GreenfieldPickPrinter(board.pickValues(), League.GREENFIELD.teams).print(out)
            }
        }
        if (TYPE_DEMAND == type) {
            return { PrintWriter out ->
                new GreenfieldDemandPrinter(board.demand(),
                        League.GREENFIELD.startedLeagueWide(board.starters())).print(out)
            }
        }
        if (TYPE_ADP == type) {
            return { PrintWriter out ->
                new GreenfieldAdpPrinter(board.demand(),
                        League.GREENFIELD.scoredPositions +
                                League.GREENFIELD.unpricedStarters.keySet().toList()).print(out)
            }
        }
        if (TYPE_BOARD == type) {
            return { PrintWriter out ->
                new GreenfieldBoardPrinter(board.ranked, board.curve, board.replacement, board.byes,
                        board.keptBy(), board.demand().averageDraftPosition()).print(out)
            }
        }
        return { PrintWriter out -> new GreenfieldKeeperPrinter(board.keepers()).print(out) }
    }
}
