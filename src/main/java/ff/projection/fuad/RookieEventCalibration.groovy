package ff.projection.fuad

import ff.projection.fuad.RookieBacktest as B
import ff.projection.fuad.RookieEventStudy as E
import java.util.Locale

/** Nested shrinkage of CURRENT event probabilities. Does not alter production or assign new VOR. */
class RookieEventCalibration {
    static final List<Double> STRENGTHS = [0d, .25d, .5d, .75d, 1d]
    static final Map<String, List<String>> COLUMNS = [
            predictions: 'draftClass name rank overallRank year model strength event actual probability baseline baselineN'.tokenize(),
            metrics: E.COLUMNS.metrics, calibration: E.COLUMNS.calibration,
            selection: 'outer inner strength eligible scored brier selected'.tokenize()
    ]
    final List<B.Observation> observations
    final RookieAvailabilityStudy availability
    final Closure replacementProvider
    private final Map<List, Map> replacements = [:]

    RookieEventCalibration(List<B.Observation> observations, Closure replacementProvider = { excluded -> B.replacementFor(excluded).QB }) {
        this.observations = observations
        this.availability = new RookieAvailabilityStudy(observations)
        this.replacementProvider = replacementProvider
    }

    static Double blend(Double probability, Double baseline, double strength) {
        probability == null || baseline == null ? null : (1d - strength) * probability + strength * baseline
    }

    protected Map replacementFor(List<String> excluded) {
        def key = excluded.unique(false).sort(false).asImmutable()
        if (!replacements.containsKey(key)) {
            replacements[key] = replacementProvider(observations.findAll { it.draftClass in key })
        }
        replacements[key]
    }

    List<Map> forecast(String target, List<String> excluded) {
        if (!(target in excluded)) throw new IllegalArgumentException('Target class must leave training')
        def key = excluded.unique().sort().asImmutable()
        def context = availability.context(new ArrayList(key))
        def replacement = replacementFor(key)
        def donors = context.training.findAll { it.position == 'QB' && it.overallRank <= 50 }.groupBy { it.year }
        def baseline = donors.collectEntries { year, rows ->
            [(year): E.EVENTS.collectEntries { event ->
                [(event): rows.count { E.occurs(B.realisedVor(it.outcome, replacement), event) } / (double) rows.size()]
            }]
        }
        observations.findAll { it.draftClass == target && it.position == 'QB' }.collectMany { row ->
            def pool = availability.pools(context, row).broad
            def curve = context.seasons.curve(row.year)
            double points = curve.seasonPoints('QB', row.rookieRank)
            double error = points > 0 ? curve.standardError('QB', row.rookieRank).toDouble() / points : 0d
            def dist = E.distribution(pool, curve.levelledRate('QB', row.rookieRank).toDouble(), error, replacement)
            double actualVor = B.realisedVor(row.outcome, replacement)
            E.EVENTS.collect { event ->
                [draftClass: target, name: row.name, rank: row.rookieRank, overallRank: row.overallRank, year: row.year,
                 event: event, actual: E.occurs(actualVor, event) ? 1 : 0, probability: E.probability(dist, event),
                 baseline: baseline[row.year]?.get(event), baselineN: donors[row.year]?.size() ?: 0]
            }
        }
    }

    static double choose(List<Map> scores) {
        def comparable = scores.groupBy { it.inner }.values().findAll { rows ->
            STRENGTHS.every { strength -> rows.any { it.strength == strength && it.brier != null } }
        }.flatten()
        if (!comparable) return 0d
        // Ascending grid breaks exact ties toward the unchanged model.
        STRENGTHS.min { strength ->
            def losses = comparable.findAll { it.strength == strength }*.brier
            losses.sum() / losses.size()
        }
    }

    Map select(String outer, Closure progress = { }) {
        List<Map> scores = []
        observations*.draftClass.unique().sort().findAll { it != outer }.each { inner ->
            progress("Calibration: outer $outer / inner $inner")
            def rows = forecast(inner, [outer, inner]).findAll { it.overallRank <= 50 }
            def supported = rows.findAll { it.probability != null && it.baseline != null }
            STRENGTHS.each { strength ->
                scores << [outer: outer, inner: inner, strength: strength, eligible: rows.size(), scored: supported.size(),
                           brier: supported ? supported.sum {
                               Math.pow(blend(it.probability as Double, it.baseline as Double, strength) - it.actual, 2)
                           } / supported.size() : null]
            }
        }
        double strength = choose(scores)
        [strength: strength, scores: scores.collect { it + [selected: it.strength == strength] }]
    }

    Map run(Closure progress = { }) {
        List<Map> predictions = [], selection = []
        observations*.draftClass.unique().sort().each { outer ->
            def chosen = select(outer, progress)
            selection.addAll(chosen.scores)
            forecast(outer, [outer]).each { row ->
                [CURRENT: 0d, CALIBRATED: chosen.strength, FREQUENCY: 1d].each { model, strength ->
                    predictions << row + [model: model, strength: strength,
                                          probability: blend(row.probability as Double, row.baseline as Double, strength)]
                }
            }
        }
        [predictions: predictions, selection: selection] + E.metrics(predictions)
    }

    static void main(String[] args) {
        if (args.size() > 1) throw new IllegalArgumentException('Usage: rookie_event_calibration.sh [output-directory]')
        def result = new RookieEventCalibration(B.observations()).run { System.err.println(it) }
        File directory = new File(args ? args[0] : 'reports/fuad/event-calibration')
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
        println "Event calibration written to ${directory.absolutePath}"
        result.metrics.findAll { it.fold == 'POOLED' && it.cohort == 'TOP50' && it.band == 'ALL' && it.year == 'ALL' }.each { println it }
    }
}
