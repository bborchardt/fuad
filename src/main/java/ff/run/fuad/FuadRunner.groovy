package ff.run.fuad

import ff.data.fuad.FuadData
import ff.load.fuad.FuadLoader
import ff.load.util.LoadUtils
import ff.print.fuad.FuadFranchiseDraftPrinter
import ff.print.fuad.FuadRankingsDraftPrinter
import ff.print.fuad.FuadRookieDraftPrinter
import groovy.util.logging.Slf4j

@Slf4j
class FuadRunner {
    private static final String TYPE_FRANCHISES = 'franchises'
    private static final String TYPE_RANKINGS = 'rankings'
    private static final String TYPE_ROOKIES = 'rookies'
    private static final List<String> TYPES = [TYPE_FRANCHISES, TYPE_RANKINGS, TYPE_ROOKIES]

    public static void main(String[] args) {
        try {
            def cli = new CliBuilder(usage: 'FuadRunner [options]', width: 120, stopAtNonOption: false,
                    header: "Executed with args: $args")
            cli.y(longOpt: 'year', args: 1, argName: 'year', required: false, 'The year, defaults to most recent.')
            cli.t(longOpt: 'type', args: 1, argName: 'type', required: true, "The type of sheet to generate: $TYPES")
            def options = cli.parse(args)
            if (options != null) {
                String year = options.year ?: LoadUtils.YEARS.last()
                if (!LoadUtils.YEARS.contains(year)) {
                    throw new IllegalArgumentException("Invalid year: $year")
                }
                String type = options.type
                if (!TYPES.contains(type)) {
                    throw new IllegalArgumentException("Invalid type: $type")
                }
                FuadData fuadData = new FuadLoader().loadData(year)
                if (TYPE_FRANCHISES == type) {
                    new FuadFranchiseDraftPrinter(fuadData).print()
                } else if (TYPE_RANKINGS == type) {
                    new FuadRankingsDraftPrinter(fuadData).print()
                } else if (TYPE_ROOKIES == type) {
                    new FuadRookieDraftPrinter(fuadData).print()
                }
            } else {
                Runtime.getRuntime().exit(-1)
            }
        } catch (Exception ex) {
            log.error('Error running CirrusBackboneSourceGenerator.', ex)
            Runtime.getRuntime().exit(-1)
        }
    }
}
