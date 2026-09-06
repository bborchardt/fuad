package ff.run.fuad

import ff.projection.fuad.RookieBacktest
import java.util.Locale

class RookieBacktestRunner {
    static void main(String[] args) {
        if (args.size() > 1) throw new IllegalArgumentException('Usage: rookie_backtest.sh [output-directory]')
        File directory = new File(args ? args[0] : 'reports/fuad/backtest')
        def result = RookieBacktest.run(RookieBacktest.observations()) { System.err.println(it) }
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create $directory")
        ['predictions', 'fits', 'metrics', 'selection', 'calibration'].each { String name ->
            List<Map> rows = result[name]
            new File(directory, "${name}.tsv").withWriter('UTF-8') { writer ->
                writer.println(rows.first().keySet().join('\t'))
                rows.each { row -> writer.println(row.values().collect { value ->
                    value == null ? '' : value instanceof Float || value instanceof Double || value instanceof BigDecimal ?
                            String.format(Locale.ROOT, '%.6f', value.toDouble()) : value.toString().replaceAll('[\t\r\n]', ' ')
                }.join('\t')) }
            }
        }
        println "Backtest written to ${directory.absolutePath}"
        println 'POOLED TOP50: model, horizon, N, MAE, RMSE, bias, Spearman'
        result.metrics.findAll { it.fold == 'POOLED' && it.cohort == 'TOP50' && it.position == 'ALL' }
                .each { println([it.model, it.horizon, it.scored, it.mae, it.rmse, it.bias, it.spearman].join('\t')) }
    }
}
