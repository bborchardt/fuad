package ff.projection.fuad

import ff.projection.fuad.RookieBacktest as B
import ff.projection.fuad.RookieAvailabilityStudy as A
import java.util.Locale

/** Fixed annual event diagnostics, not a new production model or threshold search. */
class RookieEventStudy {
    static final List<String> EVENTS = ['POSITIVE_VOR', 'VOR_26']
    static final Map<String, List<String>> COLUMNS = [
            predictions: 'draftClass name rank overallRank year model strength event actualVor actual probability baseline baselineN expectedVor'.tokenize(),
            metrics: 'fold cohort band year model event eligible scored missing observed probability bias brier baselineBrier skill'.tokenize(),
            calibration: 'cohort model event bin scored observed probability'.tokenize(),
            selection: A.COLUMNS.selection
    ]

    static boolean occurs(double vor, String event) {
        if (!(event in EVENTS)) throw new IllegalArgumentException("Unknown event $event")
        event == 'POSITIVE_VOR' ? vor > 0d : vor >= 26d
    }

    static List<List<Double>> distribution(List<Map> pool, double rate, double error, Map replacement) {
        def replay = B.vorAgainst(replacement)
        B.ERRORS.collectMany { point ->
            double shifted = rate * Math.max(0d, 1d + point[0] * error)
            pool.collect { [replay(shifted, it.outcome), point[1] * it.weight] }
        }
    }

    static Double probability(List<List<Double>> distribution, String event) {
        distribution ? distribution.sum { occurs(it[0], event) ? it[1] : 0d } : null
    }

    static Map summarize(List<Map> rows) {
        def scored = rows.findAll { it.probability != null && it.baseline != null }
        int n = scored.size()
        Double brier = n ? scored.sum { Math.pow(it.probability - it.actual, 2) } / n : null
        Double baseline = n ? scored.sum { Math.pow(it.baseline - it.actual, 2) } / n : null
        [eligible: rows.size(), scored: n, missing: rows.size() - n,
         observed: n ? scored.sum { it.actual } / (double) n : null,
         probability: n ? scored.sum { it.probability } / n : null,
         bias: n ? scored.sum { it.probability - it.actual } / n : null,
         brier: brier, baselineBrier: baseline, skill: baseline != null && baseline > 0d ? 1d - brier / baseline : null]
    }

    static Map metrics(List<Map> predictions) {
        List<Map> metrics = [], calibration = []
        (predictions*.draftClass.unique().sort() + ['POOLED']).each { fold ->
            ['ALL', 'TOP50'].each { cohort ->
                def selected = predictions.findAll { (fold == 'POOLED' || it.draftClass == fold) &&
                        (cohort == 'ALL' || it.overallRank <= 50) }
                ['ALL', 'QB1', 'QB2-3', 'QB4+'].each { band ->
                    def ranked = selected.findAll { band == 'ALL' || (band == 'QB1' ? it.rank == 1 :
                            band == 'QB2-3' ? it.rank in [2, 3] : it.rank >= 4) }
                    (['ALL'] + (1..5)).each { year ->
                        ranked.findAll { year == 'ALL' || it.year == year }.groupBy { [it.model, it.event] }.each { key, rows ->
                            metrics << [fold: fold, cohort: cohort, band: band, year: year, model: key[0], event: key[1]] + summarize(rows)
                        }
                    }
                }
                if (fold == 'POOLED') {
                    selected.findAll { it.probability != null && it.baseline != null }.groupBy {
                        [it.model, it.event, Math.min(4, (int) Math.floor(it.probability * 5))]
                    }.each { key, rows ->
                        calibration << [cohort: cohort, model: key[0], event: key[1], bin: key[2],
                                        scored: rows.size(), observed: rows.sum { it.actual } / (double) rows.size(),
                                        probability: rows.sum { it.probability } / rows.size()]
                    }
                }
            }
        }
        [metrics: metrics, calibration: calibration]
    }

    static Map run(Closure progress = { }) {
        def observations = B.observations()
        def availability = new A(observations)
        List<Map> predictions = [], selection = []
        observations*.draftClass.unique().sort().each { outer ->
            progress("Event probabilities: outer class $outer")
            def context = availability.context([outer])
            def chosen = availability.select(outer)
            selection.addAll(chosen.scores)
            def heldOut = observations.findAll { it.draftClass == outer }
            def replacement = B.replacementFor(heldOut).QB
            // A simple training-only benchmark: same contract year, top-50 QBs, no rank exceptions.
            def baselineRows = context.training.findAll { it.position == 'QB' && it.overallRank <= 50 }.groupBy { it.year }
            def baselines = baselineRows.collectEntries { year, rows ->
                [(year): EVENTS.collectEntries { event ->
                    [(event): rows.count { occurs(B.realisedVor(it.outcome, replacement), event) } / (double) rows.size()]
                }]
            }
            heldOut.findAll { it.position == 'QB' }.each { row ->
                def pools = availability.pools(context, row)
                def curve = context.seasons.curve(row.year)
                double points = curve.seasonPoints('QB', row.rookieRank)
                double error = points > 0d ? curve.standardError('QB', row.rookieRank).toDouble() / points : 0d
                double rate = curve.levelledRate('QB', row.rookieRank)
                double actualVor = B.realisedVor(row.outcome, replacement)
                def candidates = [CURRENT: pools.broad, PARTIAL: RookieRateStudy.mix(pools.broad, pools.local, chosen.strength)]
                boolean supported = candidates.values().every { !it.isEmpty() }
                candidates.each { model, pool ->
                    def dist = supported ? distribution(pool, rate, error, replacement) : []
                    EVENTS.each { event ->
                        predictions << [draftClass: outer, name: row.name, rank: row.rookieRank, overallRank: row.overallRank,
                                        year: row.year, model: model, strength: chosen.strength, event: event,
                                        actualVor: actualVor, actual: occurs(actualVor, event) ? 1 : 0,
                                        probability: probability(dist, event), baseline: baselines[row.year]?.get(event),
                                        baselineN: baselineRows[row.year]?.size() ?: 0,
                                        expectedVor: dist ? dist.sum { it[0] * it[1] } : null]
                    }
                }
            }
        }
        [predictions: predictions, selection: selection] + metrics(predictions)
    }

    static void main(String[] args) {
        if (args.size() > 1) throw new IllegalArgumentException('Usage: rookie_event_study.sh [output-directory]')
        def result = run { System.err.println(it) }
        File directory = new File(args ? args[0] : 'reports/fuad/event-study')
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
        println "Event study written to ${directory.absolutePath}"
        result.metrics.findAll { it.fold == 'POOLED' && it.cohort == 'TOP50' && it.band == 'ALL' && it.year == 'ALL' }.each { println it }
    }
}
