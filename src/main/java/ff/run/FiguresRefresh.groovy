package ff.run

import ff.data.PlayerValuation
import ff.data.fuad.FuadData
import ff.load.fuad.FuadLoader
import ff.load.fuad.FuadValuationLoader
import ff.load.util.LoadUtils
import ff.league.League
import ff.load.greenfield.GreenfieldBoard
import ff.print.figures.greenfield.GreenfieldFiguresPrinter
import ff.print.figures.fuad.ModelFiguresPrinter
import ff.projection.fuad.AuctionAccuracy
import ff.projection.fuad.AuctionSpend
import ff.projection.fuad.PriceSteepness
import ff.print.figures.fuad.RookieFiguresPrinter
import ff.print.greenfield.GreenfieldAdpPrinter
import ff.print.greenfield.GreenfieldDemandPrinter
import ff.print.greenfield.GreenfieldPickPrinter
import ff.run.ReportManifest

/**
 * Write the model's own figures under docs/figures, so the documentation can cite them rather than quote
 * them.
 *
 * Separate from {@code FuadRunner} and from {@code reports/} on purpose. A report describes one auction, is
 * regenerated constantly and is not committed. This describes the <b>model</b>: it changes only when the
 * model or the data behind it changes, it is committed, and the point of committing it is that a change to
 * the model shows up as a diff in the figures in the same commit. See docs/fuad/PROJECTION.md.
 */
class FiguresRefresh {

    static final String DEFAULT_OUTPUT_DIR = 'docs/figures'

    private static final Map<String, String> TABLES = [
            curve    : 'printCurve',
            positions: 'printPositions',
            board    : 'printBoard',
            spend    : 'printSpend',
            retention: 'printRetention',
            depth    : 'printDepth',
            stretch  : 'printStretch',
            tags     : 'printTags',
            rates    : 'printRates',
    ].asImmutable() as Map<String, String>

    /**
    /** The other league's tables, by the name each takes inside its own directory. */
    private static Map<String, Closure<Void>> greenfield(String year) {
        GreenfieldBoard board = new GreenfieldBoard(year)
        GreenfieldFiguresPrinter printer = new GreenfieldFiguresPrinter(board.curve, board.replacement,
                board.byes, board.starters(), board.ranked)
        Map<String, Closure<Void>> tables = [
                curve    : { PrintWriter out -> printer.printCurve(out) },
                positions: { PrintWriter out -> printer.printPositions(out) },
                picks    : { PrintWriter out ->
                    new GreenfieldPickPrinter(board.pickValues(), League.GREENFIELD.teams).print(out)
                },
                demand   : { PrintWriter out ->
                    new GreenfieldDemandPrinter(board.demand(),
                            League.GREENFIELD.startedLeagueWide(board.starters())).print(out)
                },
                adp      : { PrintWriter out ->
                    new GreenfieldAdpPrinter(board.demand(),
                            League.GREENFIELD.scoredPositions +
                                    League.GREENFIELD.unpricedStarters.keySet().toList()).print(out)
                },
                keepers  : { PrintWriter out ->
                    GreenfieldFiguresPrinter.printKeepers(out, board.keepers())
                },
        ]
        tables
    }

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
        File figuresDir = new File(args.length > 1 ? args[1] : DEFAULT_OUTPUT_DIR)

        FuadData fuadData = new FuadLoader().loadData(year)
        FuadValuationLoader loader = new FuadValuationLoader()
        ModelFiguresPrinter printer = new ModelFiguresPrinter(loader.curve(), loader.requirements(year),
                loader.byes(year), loader.valuations(year, fuadData), loader.freeCap(year))

        // The rookie board is figures about the same model and is built from its own five curves, which
        // are five times the cost of the board's one. It is kept in its own printer for that reason and
        // written into the same directory, being the same league.
        RookieFiguresPrinter rookies = new RookieFiguresPrinter(loader.rookieSeasons(),
                loader.rookieOutcomes(), loader.rookieDemand(), loader.rookieValues(year, fuadData))
        // Priced once and read twice: the seasons the board is scored against are the seasons the steepness
        // is fitted from, and each is the whole board built again.
        Map<String, List<PlayerValuation>> past = pastBoards(loader)
        write(figuresDir, 'fuad', year, TABLES.collectEntries { String name, String method ->
            [(name): { PrintWriter out -> printer."$method"(out) } as Closure<Void>]
        } + [
                accuracy    : { PrintWriter out ->
                    ModelFiguresPrinter.printAccuracy(out, past.collectMany { String season, board ->
                        AuctionAccuracy.of(season, board)
                    })
                } as Closure<Void>,
                steepness   : { PrintWriter out ->
                    ModelFiguresPrinter.printSteepness(out, PriceSteepness.of(
                            PriceSteepness.observationsFrom(past.findAll {
                                AuctionSpend.CALIBRATED_SEASONS.contains(it.key)
                            })))
                } as Closure<Void>,
                rookiecurve : { PrintWriter out -> rookies.printCurve(out) } as Closure<Void>,
                rookiesalary: { PrintWriter out -> rookies.printSalary(out) } as Closure<Void>,
                rookiedemand: { PrintWriter out -> rookies.printDemand(out) } as Closure<Void>,
                rookieadp   : { PrintWriter out -> rookies.printAdp(out) } as Closure<Void>,
                rookiespread: { PrintWriter out -> rookies.printSpread(out) } as Closure<Void>,
                rookieclass : { PrintWriter out -> rookies.printClass(out) } as Closure<Void>,
                rookiepace  : { PrintWriter out -> rookies.printPace(out) } as Closure<Void>,
                rookieboard : { PrintWriter out -> rookies.printBoard(out) } as Closure<Void>,
        ])
        write(figuresDir, 'greenfield', year, greenfield(year))
    }

    /**
     * The board held to what the league actually paid, over every season it can be held to.
     *
     * Each of those seasons has to be priced, which is the whole board built again — the curve is shared, so
     * it is the valuation rather than the levelling that is paid for four times. That is the cost of having
     * an answer at all, and it is why this lives in the figures rather than in a report: it changes when the
     * model changes and it is committed so that the change shows up in the diff.
     *
     * A season that cannot be priced is left out rather than scored as a miss, and what that takes is asked
     * of the record rather than assumed from the year: pricing needs the prior season's rosters to know what
     * is expiring, and scoring needs this season's post-draft rosters to know what was paid. The season
     * being priced now has the first and not the second, so it is measurable only in arrears.
     */
    private static Map<String, List<PlayerValuation>> pastBoards(FuadValuationLoader loader) {
        AuctionAccuracy.MEASURED_SEASONS.findAll { AuctionSpend.isMeasurable(it) }
                .collectEntries { String season ->
                    [(season): loader.valuations(season, new FuadLoader().loadData(season))]
                }
    }

    /**
     * One league's figures, into its own directory and under its own stamp.
     *
     * A directory each because the table names collide: both leagues have a curve and a positions table,
     * describing different lineups, different scoring and different currencies. Prefixing them into one
     * directory was the first arrangement and it made every greenfield figure read as a variant of a fuad
     * one, which is the opposite of what they are.
     *
     * A stamp each for the same reason it exists at all — a reader asks of a figure which model produced it,
     * and one manifest covering two leagues cannot answer separately for them.
     */
    private static void write(File figuresDir, String league, String year,
                              Map<String, Closure<Void>> tables) {
        File outputDir = new File(figuresDir, "$league/$year")
        outputDir.mkdirs()
        List<String> written = tables.collect { String name, Closure<Void> print ->
            File file = new File(outputDir, "${name}.tsv")
            file.withPrintWriter { PrintWriter out -> print(out) }
            println "Wrote $file"
            name
        }
        ReportManifest.stamp(outputDir, written, 'figures_refresh.sh')
    }
}
