package ff.projection.fuad

import ff.projection.fuad.RookieBacktest as B
import ff.projection.fuad.RookieEventStudy as E
import ff.projection.fuad.RookieEventCalibration as C
import java.util.Locale

/** Rank-aware event frequency benchmark, with no rate curve in its probability calculation. */
class RookieRankFrequency extends C {
    static final Map<String, List<String>> OUTPUT_COLUMNS = [
            predictions: 'draftClass name rank overallRank year model strength event actual probability baseline baselineN local localEffectiveN'.tokenize(),
            metrics: E.COLUMNS.metrics, calibration: E.COLUMNS.calibration, selection: C.COLUMNS.selection
    ]

    RookieRankFrequency(List<B.Observation> observations, Closure provider = { excluded -> B.replacementFor(excluded).QB }) {
        super(observations, provider)
    }

    static Map localFrequency(List<Map> donors, int rank, String event) {
        if (!donors) return [probability: null, effectiveN: 0d]
        def weighted = donors.collect { [weight: Math.exp(-Math.abs(it.rank - rank)), actual: E.occurs(it.vor, event) ? 1d : 0d] }
        double mass = weighted.sum { it.weight }
        [probability: weighted.sum { it.weight * it.actual } / mass,
         effectiveN: mass * mass / weighted.sum { it.weight * it.weight }]
    }

    @Override
    List<Map> forecast(String target, List<String> excluded) {
        def rows = super.forecast(target, excluded)
        def replacement = replacementFor(excluded)
        def donors = observations.findAll { !(it.draftClass in excluded) && it.position == 'QB' && it.overallRank <= 50 }
                .groupBy { it.year }.collectEntries { year, at ->
            [(year): at.collect { [rank: it.rookieRank, vor: B.realisedVor(it.outcome, replacement)] }]
        }
        rows.collect { row ->
            def local = localFrequency(donors[row.year] ?: [], row.rank, row.event)
            row + [local: local.probability, localEffectiveN: local.effectiveN]
        }
    }

    @Override
    Map select(String outer, Closure progress = { }) {
        List<Map> scores = []
        observations*.draftClass.unique().sort().findAll { it != outer }.each { inner ->
            progress("Rank frequency: outer $outer / inner $inner")
            def rows = forecast(inner, [outer, inner]).findAll { it.overallRank <= 50 }
            def supported = rows.findAll { it.probability != null && it.baseline != null && it.local != null }
            C.STRENGTHS.each { strength ->
                scores << [outer: outer, inner: inner, strength: strength, eligible: rows.size(), scored: supported.size(),
                           brier: supported ? supported.sum {
                               Math.pow(C.blend(it.baseline as Double, it.local as Double, strength) - it.actual, 2)
                           } / supported.size() : null]
            }
        }
        double strength = C.choose(scores)
        [strength: strength, scores: scores.collect { it + [selected: it.strength == strength] }]
    }

    @Override
    Map run(Closure progress = { }) {
        List<Map> predictions = [], selection = []
        observations*.draftClass.unique().sort().each { outer ->
            def chosen = select(outer, progress)
            selection.addAll(chosen.scores)
            forecast(outer, [outer]).each { row ->
                boolean supported = row.probability != null && row.baseline != null && row.local != null
                [CURRENT: null, FREQUENCY: 0d, LOCAL: 1d, NESTED: chosen.strength].each { model, strength ->
                    predictions << row + [model: model, strength: strength, probability: !supported ? null :
                            model == 'CURRENT' ? row.probability : C.blend(row.baseline as Double, row.local as Double, strength)]
                }
            }
        }
        [predictions: predictions, selection: selection] + E.metrics(predictions)
    }

    static void main(String[] args) {
        if (args.size() > 1) throw new IllegalArgumentException('Usage: rookie_rank_frequency.sh [output-directory]')
        def result = new RookieRankFrequency(B.observations()).run { System.err.println(it) }
        File directory = new File(args ? args[0] : 'reports/fuad/rank-frequency')
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create $directory")
        OUTPUT_COLUMNS.each { name, columns ->
            new File(directory, "${name}.tsv").withWriter('UTF-8') { writer ->
                writer.println(columns.join('\t'))
                result[name].each { row -> writer.println(columns.collect { column ->
                    def value = row[column]
                    value == null ? '' : value instanceof Number && !(value instanceof Integer) ?
                            String.format(Locale.ROOT, '%.6f', value.toDouble()) : value.toString().replaceAll('[\t\r\n]', ' ')
                }.join('\t')) }
            }
        }
        println "Rank frequency study written to ${directory.absolutePath}"
        result.metrics.findAll { it.fold == 'POOLED' && it.cohort == 'TOP50' && it.band == 'ALL' && it.year == 'ALL' }.each { println it }
    }
}
