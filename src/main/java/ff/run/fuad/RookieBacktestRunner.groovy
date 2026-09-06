package ff.run.fuad

import ff.projection.fuad.RookieBacktest
import java.util.Locale

class RookieBacktestRunner {
    static final Map<String, List<String>> COLUMNS = [
            predictions: 'draftClass name position overallRank dynastyRank year model actual predicted low high'.tokenize(),
            fits: 'draftClass trainingClasses slope plateau decay cap intercept fitted strength'.tokenize(),
            metrics: 'fold cohort position model horizon eligible scored missing mae rmse bias actualMean predictedMean spearman coverage80 width80'.tokenize(),
            selection: 'outer inner strength eligible scored missing mse bias trainingClasses selected'.tokenize(),
            calibration: 'draftClass position year factor'.tokenize()
    ].asImmutable()

    static void main(String[] args) {
        if (args.size() > 1) throw new IllegalArgumentException('Usage: rookie_backtest.sh [output-directory]')
        File directory = new File(args ? args[0] : 'reports/fuad/backtest')
        def result = RookieBacktest.run(RookieBacktest.observations()) { System.err.println(it) }
        writeReports(directory, result)
        println "Backtest written to ${directory.absolutePath}"
        println 'POOLED TOP50: model, horizon, N, MAE, RMSE, bias, Spearman'
        result.metrics.findAll { it.fold == 'POOLED' && it.cohort == 'TOP50' && it.position == 'ALL' }
                .each { println([it.model, it.horizon, it.scored, it.mae, it.rmse, it.bias, it.spearman].join('\t')) }
    }

    /** Always replace all five reports, even when a narrowed study has no eligible rows. */
    static void writeReports(File directory, Map result) {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create $directory")
        COLUMNS.each { String name, List<String> columns ->
            List<Map> rows = result[name] ?: []
            new File(directory, "${name}.tsv").withWriter('UTF-8') { writer ->
                writer.println(columns.join('\t'))
                rows.each { row -> writer.println(columns.collect { column ->
                    def value = row[column]
                    value == null ? '' : value instanceof Float || value instanceof Double || value instanceof BigDecimal ?
                            String.format(Locale.ROOT, '%.6f', value.toDouble()) : value.toString().replaceAll('[\t\r\n]', ' ')
                }.join('\t')) }
            }
        }
    }
}
