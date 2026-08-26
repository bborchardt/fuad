package ff.run.greenfield

import ff.data.fantasypros.FpRankedPlayer
import ff.data.greenfield.KeeperSurplus
import ff.league.League
import ff.load.greenfield.DraftHistory
import ff.load.greenfield.GreenfieldValuationLoader
import ff.load.util.LoadUtils
import ff.print.greenfield.GreenfieldBoardPrinter
import ff.print.greenfield.GreenfieldKeeperPrinter
import ff.print.greenfield.GreenfieldPickPrinter
import ff.projection.ByeWeeks
import ff.projection.ExpectedValue
import ff.projection.PointsCurve
import ff.projection.greenfield.KeeperValuation
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
        PointsCurve curve = loader.curve()
        ByeWeeks byes = loader.byes(year)
        Map<String, Map<Integer, BigDecimal>> replacement = loader.replacement(year)
        Collection<FpRankedPlayer> ranked = loader.ranked(year)
                .findAll { League.GREENFIELD.scoredPositions.contains(it.player.position) }

        if (TYPE_PICKS == type) {
            Map<Integer, BigDecimal> byPick =
                    new DraftHistory(curve, loader.requirements(), League.GREENFIELD).bestAvailableByPick()
            return { PrintWriter out ->
                new GreenfieldPickPrinter(byPick, League.GREENFIELD.teams).print(out)
            }
        }
        if (TYPE_BOARD == type) {
            Map<String, String> keptBy = keepers(year).collectEntries { [(it.player as String): it.owner] }
            return { PrintWriter out ->
                new GreenfieldBoardPrinter(ranked, curve, replacement, byes, keptBy).print(out)
            }
        }
        Map<Integer, BigDecimal> byPick =
                new DraftHistory(curve, loader.requirements(), League.GREENFIELD).bestAvailableByPick()
        List<KeeperSurplus> valued = valueKeepers(year, ranked, curve, replacement, byes, byPick)
        return { PrintWriter out -> new GreenfieldKeeperPrinter(valued).print(out) }
    }

    private static List<KeeperSurplus> valueKeepers(String year, Collection<FpRankedPlayer> ranked,
                                                    PointsCurve curve,
                                                    Map<String, Map<Integer, BigDecimal>> replacement,
                                                    ByeWeeks byes, Map<Integer, BigDecimal> byPick) {
        Map<String, FpRankedPlayer> byName = ranked.collectEntries { [(it.player.name): it] }
        Closure<BigDecimal> valueOf = { String name ->
            FpRankedPlayer player = byName[name]
            player ? ExpectedValue.expectedValueOverReplacement(
                    curve, replacement, player.player.position, player.rank.positionRank, byes) : null
        }
        List<String> board = ranked.sort { a, b ->
            (a.rank.overallRank ?: Integer.MAX_VALUE) <=> (b.rank.overallRank ?: Integer.MAX_VALUE)
        }.collect { it.player.name }

        List<Map> keepers = keepers(year).collect { Map row ->
            FpRankedPlayer player = byName[row.player as String]
            row + [position: player?.player?.position, positionRank: player?.rank?.positionRank]
        }
        KeeperValuation.value(keepers, slots(year), valueOf, board, priorRounds(year),
                League.GREENFIELD.teams, byPick)
    }

    private static List<Map> keepers(String year) {
        rows("/ff/greenfield/data/$year/keepers.tsv")
                .collect { [owner: it[0], player: it[1], costRound: it[2] as int] }
    }

    private static Map<String, Integer> slots(String year) {
        rows("/ff/greenfield/data/$year/draft_order.tsv").collectEntries { [(it[1]): it[0] as int] }
    }

    /**
     * The round each player was drafted in last season, which is what decides whether he may be kept.
     *
     * A player absent from it went undrafted and qualifies at either price, which is why this is a lookup
     * that may miss rather than one that must hit.
     */
    private static Map<String, Integer> priorRounds(String year) {
        rows("/ff/greenfield/data/${(year as int) - 1}/draft.tsv")
                .collectEntries { [(it[2]): it[0] as int] }
    }

    private static List<List<String>> rows(String resource) {
        LoadUtils.loadCsvResource(resource).drop(1).collect { it.split('\t') as List<String> }
    }
}
