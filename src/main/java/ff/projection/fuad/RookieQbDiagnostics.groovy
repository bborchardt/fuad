package ff.projection.fuad

import ff.projection.fuad.RookieBacktest as B
import java.util.Locale

/** Diagnostic sensitivity, not another tuned candidate. Each threshold scores both forecasts and truth
 * against the same replacement. Leave-one-player-out rows remove an evaluation observation, not training.
 */
class RookieQbDiagnostics {
    static final List<Double> REPLACEMENT_FACTORS = [.8d, 1d, 1.2d]
    static final List<String> MODELS = ['ROOKIE_ONLY', 'ROOKIE_POINT', 'FULL_REFIT']
    static final Map<String, List<String>> COLUMNS = [
            seasons: 'draftClass name rookieRank year actualPoints actualGames actualRate modelRate outcomeGames sameRankGames localGames zeroShare outcomes levelObservations levelClasses replacement'.tokenize(),
            predictions: 'draftClass name rookieRank year factor model actual predicted'.tokenize(),
            metrics: 'fold horizon factor model eligible scored missing actualMean predictedMean bias mae rmse spearman'.tokenize(),
            influence: 'draftClass name actual predicted error squaredErrorShare remainingBias remainingRmse'.tokenize()
    ]

    static Map run(Closure progress = { }) {
        def observations = B.observations()
        List<Map> annual = [], predictions = []
        observations*.draftClass.unique().sort().each { String draft ->
            progress("QB diagnostic: holding out $draft")
            def heldOut = observations.findAll { it.draftClass == draft }
            def training = B.trainingFor(observations, draft)
            def seasons = B.seasonsFrom(training)
            def outcomes = new RookieOutcomes(seasons)
            def fit = B.fitDynasty(training, seasons)
            def replacement = B.replacementFor(heldOut).QB
            heldOut.findAll { it.position == 'QB' && it.overallRank <= 50 }.each { row ->
                def curve = seasons.curve(row.year)
                def spread = outcomes.of('QB', row.rookieRank, row.year)
                def levels = training.findAll {
                    it.position == 'QB' && it.year == row.year && Math.abs(it.rookieRank - row.rookieRank) <= 2
                }
                def sameRank = levels.findAll { it.rookieRank == row.rookieRank }
                annual << [draftClass: draft, name: row.name, rookieRank: row.rookieRank, year: row.year,
                           actualPoints: row.outcome.points, actualGames: row.outcome.games, actualRate: row.outcome.rate,
                           modelRate: curve.levelledRate('QB', row.rookieRank),
                           outcomeGames: spread ? spread.sum { it.games } / (double) spread.size() : null,
                           sameRankGames: sameRank ? sameRank.sum { it.outcome.games } / (double) sameRank.size() : null,
                           localGames: levels ? levels.sum { it.outcome.games } / (double) levels.size() : null,
                           zeroShare: spread ? spread.count { it.games == 0 } / (double) spread.size() : null,
                           outcomes: spread.size(), levelObservations: levels.size(),
                           levelClasses: levels*.draftClass.unique().sort().join(','), replacement: replacement[1]]
                REPLACEMENT_FACTORS.each { double factor ->
                    def against = replacement.collectEntries { week, value -> [(week): (value * factor) as BigDecimal] }
                    MODELS.each { model ->
                        def predicted = B.prediction(row, model, training, seasons, outcomes, fit, against, false)
                        predictions << [draftClass: draft, name: row.name, rookieRank: row.rookieRank,
                                        year: row.year, factor: factor, model: model,
                                        actual: B.realisedVor(row.outcome, against), predicted: predicted.predicted]
                    }
                }
            }
        }
        def contracts = contracts(predictions)
        List<Map> metrics = []
        (observations*.draftClass.unique().sort() + ['POOLED']).each { fold ->
            contracts.findAll { fold == 'POOLED' || it.draftClass == fold }
                    .groupBy { [it.horizon, it.factor, it.model] }.each { key, rows ->
                metrics << [fold: fold, horizon: key[0], factor: key[1], model: key[2]] + summarize(rows)
            }
        }
        def baseline = contracts.findAll { it.horizon == 5 && it.factor == 1d && it.model == 'ROOKIE_ONLY' }
        [seasons: annual, predictions: predictions, metrics: metrics, influence: influence(baseline)]
    }

    static List<Map> contracts(List<Map> predictions) {
        List<Map> result = []
        predictions.groupBy { [it.draftClass, it.name] }.each { key, player ->
            [1, 3, 5].each { horizon ->
                def required = player.findAll { it.year <= horizon }
                if (REPLACEMENT_FACTORS.every { factor -> MODELS.every { model ->
                    required.count { it.factor == factor && it.model == model } == horizon
                } }) {
                    boolean supported = required.every { it.predicted != null }
                    required.groupBy { [it.factor, it.model] }.each { group, rows ->
                        result << [draftClass: key[0], name: key[1], horizon: horizon,
                                   factor: group[0], model: group[1], actual: rows.sum { it.actual },
                                   predicted: supported ? rows.sum { it.predicted } : null]
                    }
                }
            }
        }
        result
    }

    static Map summarize(List<Map> rows) {
        def supported = rows.findAll { it.predicted != null }
        def errors = supported.collect { it.predicted - it.actual }
        int n = supported.size()
        [eligible: rows.size(), scored: n, missing: rows.size() - n,
         actualMean: n ? supported.sum { it.actual } / n : null,
         predictedMean: n ? supported.sum { it.predicted } / n : null,
         bias: n ? errors.sum() / n : null,
         mae: n ? errors.sum { Math.abs(it) } / n : null,
         rmse: n ? Math.sqrt(errors.sum { it * it } / n) : null,
         spearman: B.spearman(supported*.predicted, supported*.actual)]
    }

    static List<Map> influence(List<Map> rows) {
        def supported = rows.findAll { it.predicted != null }
        double sse = supported.sum { Math.pow(it.predicted - it.actual, 2) } ?: 0d
        supported.collect { row ->
            double error = row.predicted - row.actual
            def remaining = summarize(supported.findAll { !it.is(row) })
            [draftClass: row.draftClass, name: row.name, actual: row.actual, predicted: row.predicted,
             error: error, squaredErrorShare: sse > 0 ? error * error / sse : 0d,
             remainingBias: remaining.bias, remainingRmse: remaining.rmse]
        }.sort { -it.squaredErrorShare }
    }

    static void main(String[] args) {
        if (args.size() > 1) throw new IllegalArgumentException('Usage: rookie_qb_diagnostics.sh [output-directory]')
        def results = run { System.err.println(it) }
        File directory = new File(args ? args[0] : 'reports/fuad/qb-diagnostics')
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create $directory")
        COLUMNS.each { name, columns ->
            new File(directory, "${name}.tsv").withWriter('UTF-8') { writer ->
                writer.println(columns.join('\t'))
                results[name].each { row -> writer.println(columns.collect { column ->
                    def value = row[column]
                    value == null ? '' : value instanceof Number && !(value instanceof Integer) ?
                            String.format(Locale.ROOT, '%.6f', value.toDouble()) : value.toString().replaceAll('[\t\r\n]', ' ')
                }.join('\t')) }
            }
        }
        println "QB diagnostics written to ${directory.absolutePath}"
        results.metrics.findAll { it.fold == 'POOLED' && it.horizon == 5 && it.model == 'ROOKIE_ONLY' }
                .each { println it }
        results.influence.take(5).each { println it }
    }
}
