package ff.projection.fuad

import ff.projection.fuad.RookieBacktest as B
import ff.projection.fuad.RookieAvailabilityStudy as A
import ff.projection.PointsCurve
import java.util.Locale

/** Conditional-rate and dependence diagnostics. Independence variants are counterfactuals, not proposed
 * valuation fixes. Availability strength is still selected on inner games error, never on these results.
 */
class RookieRateStudy {
    static final List<String> MODELS = ['CURRENT_JOINT', 'PARTIAL_JOINT', 'CURRENT_INDEPENDENT',
                                       'PARTIAL_INDEPENDENT', 'PARTIAL_GAMES_CURRENT_RATE']
    static final Map<String, List<String>> COLUMNS = [
            predictions: 'draftClass name rank overallRank year model strength actualGames games actualRate rate rateLow rateHigh rateCrps actualVor vor coupling'.tokenize(),
            rates: 'fold cohort band model eligible played scored bias rmse crps coverage80 width80 couplingMean'.tokenize(),
            careers: 'fold cohort horizon model eligible scored missing actualMean predictedMean bias mae rmse spearman'.tokenize(),
            selection: A.COLUMNS.selection
    ]

    static List<Map> mix(List<Map> broad, List<Map> local, double strength) {
        if (!broad || !local) return []
        broad.collect { [outcome: it.outcome, weight: it.weight * (1d - strength)] } +
                local.collect { [outcome: it.outcome, weight: it.weight * strength] }
    }

    /** Conditional on any recorded game, including relief appearances. Zero-game outcomes still enter
     * unconditional availability and VOR; only this rate distribution conditions them away.
     */
    static List<List<Double>> rateDistribution(List<Map> pool, double rate, double relativeError) {
        def active = pool.findAll { it.outcome.games > 0 && it.weight > 0 }
        double mass = active.sum { it.weight } ?: 0d
        if (mass <= 0) return []
        B.ERRORS.collectMany { point ->
            double shifted = rate * Math.max(0d, 1d + point[0] * relativeError)
            active.collect { [shifted * it.outcome.rateMultiplier, point[1] * it.weight / mass] }
        }
    }

    /** E|X-y| - 0.5 E|X-X'|, calculated from sorted weighted support without a quadratic pair loop. */
    static Double crps(List<List<Double>> distribution, double actual) {
        if (!distribution) return null
        double cumulative = 0d, halfPairs = 0d, absolute = 0d
        distribution.sort(false) { it[0] }.each { point ->
            absolute += point[1] * Math.abs(point[0] - actual)
            halfPairs += point[1] * point[0] * (2d * cumulative + point[1] - 1d)
            cumulative += point[1]
        }
        Math.max(0d, absolute - halfPairs)
    }

    /** Independent conditional-active rate and duration. Expected games includes zero-game mass, so
     * the probability of playing is applied once, not discarded or multiplied in a second time.
     */
    static Double independentVor(List<Map> gamesPool, List<List<Double>> rates, Map replacement) {
        if (!gamesPool) return null
        double games = A.poolSummary(gamesPool).games
        if (games == 0d) return 0d
        if (!rates) return null
        def replay = B.vorAgainst(replacement)
        double perGame = rates.sum { point ->
            point[1] * replay(point[0], new PointsCurve.Outcome(1d, B.LAST_WEEK - 1)) / (B.LAST_WEEK - 1)
        }
        games * perGame
    }

    static Map run(Closure progress = { }) {
        def observations = B.observations()
        def availability = new A(observations)
        List<Map> predictions = [], selection = []
        observations*.draftClass.unique().sort().each { outer ->
            progress("Conditional rates: outer class $outer")
            def context = availability.context([outer])
            def chosen = availability.select(outer)
            selection.addAll(chosen.scores)
            def heldOut = observations.findAll { it.draftClass == outer }
            def replacement = B.replacementFor(heldOut).QB
            heldOut.findAll { it.position == 'QB' }.each { row ->
                def pools = availability.pools(context, row)
                def partial = mix(pools.broad, pools.local, chosen.strength)
                def curve = context.seasons.curve(row.year)
                double points = curve.seasonPoints('QB', row.rookieRank)
                double error = points > 0d ? curve.standardError('QB', row.rookieRank).toDouble() / points : 0d
                double rate = curve.levelledRate('QB', row.rookieRank)
                def broadRates = rateDistribution(pools.broad, rate, error)
                def partialRates = rateDistribution(partial, rate, error)
                Double broadJoint = A.vor(pools.broad, curve, row, replacement)
                Double partialJoint = A.vor(partial, curve, row, replacement)
                Double broadIndependent = independentVor(pools.broad, broadRates, replacement)
                Double partialIndependent = independentVor(partial, partialRates, replacement)
                def forecasts = [CURRENT_JOINT: broadJoint, PARTIAL_JOINT: partialJoint,
                                 CURRENT_INDEPENDENT: broadIndependent, PARTIAL_INDEPENDENT: partialIndependent,
                                 PARTIAL_GAMES_CURRENT_RATE: independentVor(partial, broadRates, replacement)]
                MODELS.each { model ->
                    def ratePool = model in ['PARTIAL_JOINT', 'PARTIAL_INDEPENDENT'] ? partialRates : broadRates
                    def gamesPool = model.startsWith('CURRENT') ? pools.broad : partial
                    Double coupling = model == 'CURRENT_JOINT' && broadJoint != null && broadIndependent != null ?
                            broadJoint - broadIndependent : model == 'PARTIAL_JOINT' && partialJoint != null && partialIndependent != null ?
                            partialJoint - partialIndependent : null
                    predictions << [draftClass: outer, name: row.name, rank: row.rookieRank,
                                    overallRank: row.overallRank, year: row.year, model: model, strength: chosen.strength,
                                    actualGames: row.outcome.games, games: A.poolSummary(gamesPool).games,
                                    actualRate: row.outcome.rate,
                                    rate: ratePool ? ratePool.sum { it[0] * it[1] } : null,
                                    rateLow: ratePool ? B.weightedQuantile(ratePool, .1d) : null,
                                    rateHigh: ratePool ? B.weightedQuantile(ratePool, .9d) : null,
                                    rateCrps: row.outcome.games > 0 ? crps(ratePool, row.outcome.rate.toDouble()) : null,
                                    actualVor: B.realisedVor(row.outcome, replacement), vor: forecasts[model], coupling: coupling]
                }
            }
        }
        [predictions: predictions, selection: selection] + metrics(predictions)
    }

    static Map metrics(List<Map> predictions) {
        List<Map> rates = [], careers = [], contracts = []
        predictions.groupBy { [it.draftClass, it.name] }.each { key, rows ->
            [1, 3, 5].each { horizon ->
                def required = rows.findAll { it.year <= horizon }
                if (MODELS.every { model -> required.count { it.model == model } == horizon }) {
                    boolean supported = required.every { it.vor != null }
                    MODELS.each { model ->
                        def at = required.findAll { it.model == model }
                        contracts << [draftClass: key[0], overallRank: rows.first().overallRank, horizon: horizon,
                                      model: model, actual: at.sum { it.actualVor }, predicted: supported ? at.sum { it.vor } : null]
                    }
                }
            }
        }
        (predictions*.draftClass.unique().sort() + ['POOLED']).each { fold ->
            ['ALL', 'TOP50'].each { cohort ->
                def selected = predictions.findAll { (fold == 'POOLED' || it.draftClass == fold) &&
                        (cohort == 'ALL' || it.overallRank <= 50) }
                ['ALL', 'QB1', 'QB2-3', 'QB4+'].each { band ->
                    selected.findAll { band == 'ALL' || (band == 'QB1' ? it.rank == 1 :
                            band == 'QB2-3' ? it.rank in [2, 3] : it.rank >= 4) }.groupBy { it.model }.each { model, rows ->
                        def played = rows.findAll { it.actualRate != null }
                        def scored = played.findAll { it.rate != null && it.rateCrps != null }
                        def coupling = rows.findAll { it.coupling != null }*.coupling
                        int n = scored.size()
                        rates << [fold: fold, cohort: cohort, band: band, model: model,
                                  eligible: rows.size(), played: played.size(), scored: n,
                                  bias: n ? scored.sum { it.rate - it.actualRate } / n : null,
                                  rmse: n ? Math.sqrt(scored.sum { Math.pow(it.rate - it.actualRate, 2) } / n) : null,
                                  crps: n ? scored.sum { it.rateCrps } / n : null,
                                  coverage80: n ? scored.count { it.actualRate >= it.rateLow && it.actualRate <= it.rateHigh } / (double) n : null,
                                  width80: n ? scored.sum { it.rateHigh - it.rateLow } / n : null,
                                  couplingMean: coupling ? coupling.sum() / coupling.size() : null]
                    }
                }
                contracts.findAll { (fold == 'POOLED' || it.draftClass == fold) &&
                        (cohort == 'ALL' || it.overallRank <= 50) }.groupBy { [it.horizon, it.model] }.each { key, rows ->
                    careers << [fold: fold, cohort: cohort, horizon: key[0], model: key[1]] + RookieQbDiagnostics.summarize(rows)
                }
            }
        }
        [rates: rates, careers: careers]
    }

    static void main(String[] args) {
        if (args.size() > 1) throw new IllegalArgumentException('Usage: rookie_rate_study.sh [output-directory]')
        def result = run { System.err.println(it) }
        File directory = new File(args ? args[0] : 'reports/fuad/rate-study')
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create $directory")
        COLUMNS.each { name, columns ->
            new File(directory, "${name}.tsv").withWriter('UTF-8') { writer ->
                writer.println(columns.join('\t'))
                result[name].each { row -> writer.println(columns.collect { column ->
                    def value = row[column]
                    value == null ? '' : value instanceof Number && !(value instanceof Integer) ?
                            String.format(Locale.ROOT, '%.6f', value.toDouble()) : value.toString().replaceAll('[\t\r\n]', ' ')
                }.join('\t')) }
            }
        }
        println "Rate study written to ${directory.absolutePath}"
        result.rates.findAll { it.fold == 'POOLED' && it.cohort == 'TOP50' && it.band == 'ALL' && it.model.endsWith('JOINT') }.each { println it }
        result.careers.findAll { it.fold == 'POOLED' && it.cohort == 'TOP50' && it.horizon == 5 }.each { println it }
    }
}
