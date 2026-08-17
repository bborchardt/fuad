package ff.run

import ff.data.fuad.FuadData
import ff.load.fuad.FuadLoader
import ff.load.fuad.FuadValuationLoader
import ff.load.util.LoadUtils
import ff.print.figures.ModelFiguresPrinter
import ff.run.fuad.ReportManifest

/**
 * Write the model's own figures under docs/figures, so the documentation can cite them rather than quote
 * them.
 *
 * Separate from {@code FuadRunner} and from {@code reports/} on purpose. A report describes one auction, is
 * regenerated constantly and is not committed. This describes the <b>model</b>: it changes only when the
 * model or the data behind it changes, it is committed, and the point of committing it is that a change to
 * the model shows up as a diff in the figures in the same commit. See docs/PROJECTION.md.
 */
class FiguresRefresh {

    static final String DEFAULT_OUTPUT_DIR = 'docs/figures'

    private static final Map<String, String> TABLES = [
            curve    : 'printCurve',
            positions: 'printPositions',
            board    : 'printBoard',
            spend    : 'printSpend',
            tags     : 'printTags',
            rates    : 'printRates',
    ].asImmutable() as Map<String, String>

    static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println('Usage: FiguresRefresh <year> [out-dir]')
            Runtime.getRuntime().exit(2)
        }
        String year = args[0]
        if (!LoadUtils.YEARS.contains(year)) {
            System.err.println("Invalid year: $year")
            Runtime.getRuntime().exit(2)
        }
        File outputDir = new File(args.length > 1 ? args[1] : DEFAULT_OUTPUT_DIR, year)
        outputDir.mkdirs()

        FuadData fuadData = new FuadLoader().loadData(year)
        FuadValuationLoader loader = new FuadValuationLoader()
        ModelFiguresPrinter printer = new ModelFiguresPrinter(loader.curve(), loader.requirements(year),
                loader.byes(year), loader.valuations(year, fuadData), loader.freeCap(year))

        List<String> written = []
        TABLES.each { String name, String method ->
            File file = new File(outputDir, "${name}.tsv")
            file.withPrintWriter { PrintWriter out -> printer."$method"(out) }
            written << name
            println "Wrote $file"
        }
        // Stamped like a report, since the question a reader asks of a figure is the same one they ask of a
        // board: which model produced it.
        ReportManifest.stamp(outputDir, written, 'figures_refresh.sh')
    }
}
