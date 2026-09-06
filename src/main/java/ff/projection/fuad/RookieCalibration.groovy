package ff.projection.fuad

import ff.projection.fuad.RookieBacktest as B

/** Nested class validation of a VOR-centered dynasty signal. Nothing here changes production valuation.
 * Calibration preserves rookie-only predicted totals, not fitted observed totals, by position/year.
 * A mixture weight is chosen on complete five-year TOP50 inner careers with squared-error loss.
 */
class RookieCalibration {
    static final List<Double> STRENGTHS = [0d, .25d, .5d, .75d, 1d]
    private final List<B.Observation> observations
    private final Closure progress
    private final Closure replacementProvider
    private final Map<List<String>, Map> contexts = [:]

    RookieCalibration(List<B.Observation> observations, Closure progress = { },
                      Closure replacementProvider = { excluded -> B.replacementFor(excluded) }) {
        this.observations = observations
        this.progress = progress
        this.replacementProvider = replacementProvider
    }

    /** Both inner and outer classes leave the curves, adjustment, centering and replacement input.
     * Cache by the sorted excluded set: holding A then B out trains exactly the same model as B then A.
     */
    Map context(List<String> excluded) {
        List<String> key = excluded.unique().sort().asImmutable()
        if (contexts.containsKey(key)) return contexts[key]
        def training = observations.findAll { !(it.draftClass in key) }
        def seasons = B.seasonsFrom(training)
        def outcomes = new RookieOutcomes(seasons)
        def fit = B.fitDynasty(training, seasons)
        def replacement = replacementProvider(observations.findAll { it.draftClass in key })
        def sums = [:].withDefault { [base: 0d, full: 0d] }
        training.findAll { it.overallRank <= 50 }.each { row ->
            def base = B.prediction(row, 'ROOKIE_ONLY', training, seasons, outcomes, fit, replacement[row.position], false)
            def full = B.prediction(row, 'FULL_REFIT', training, seasons, outcomes, fit, replacement[row.position], false)
            if (base.predicted != null && full.predicted != null) {
                def total = sums[[row.position, row.year]]
                total.base += base.predicted
                total.full += full.predicted
            }
        }
        Map calibration = sums.collectEntries { at, totals ->
            [(at): factor(totals.base as double, totals.full as double)]
        }
        contexts[key] = [training: training, seasons: seasons, outcomes: outcomes, fit: fit,
                         replacement: replacement, calibration: calibration]
    }

    static double factor(double base, double full) { full > 0d ? base / full : 1d }

    static Map blend(Map base, Map full, double factor, double strength) {
        if (base.predicted == null || full.predicted == null) return [predicted: null, low: null, high: null]
        if (strength == 0d) return base
        def distribution = []
        if (strength < 1d) distribution.addAll(base.distribution.collect { [it[0], it[1] * (1d - strength)] })
        distribution.addAll(full.distribution.collect { [it[0] * factor, it[1] * strength] })
        B.summarize(distribution)
    }

    /** Scores use an equal mean over inner classes so the longer ranking lists cannot pick the weight.
     * RMSE targets a conditional mean; a tie goes to the smaller adjustment. Zero is a valid winner.
     */
    Map select(String outer) {
        List<Map> scores = []
        def innerClasses = observations.findAll { it.year == 5 && it.draftClass != outer }*.draftClass.unique().sort()
        innerClasses.each { String inner ->
            progress("  Inner class ${inner} (outer ${outer} excluded)")
            def trained = context([outer, inner])
            def eligible = observations.findAll { it.draftClass == inner && it.overallRank <= 50 }
                    .groupBy { it.name }.values().findAll { it.size() == 5 }
            List<Map> careers = eligible.collect { career ->
                def annual = career.collect { row ->
                    def against = trained.replacement[row.position]
                    def base = B.prediction(row, 'ROOKIE_ONLY', trained.training, trained.seasons,
                            trained.outcomes, trained.fit, against, false)
                    def full = B.prediction(row, 'FULL_REFIT', trained.training, trained.seasons,
                            trained.outcomes, trained.fit, against, false)
                    [base: base.predicted, centered: full.predicted == null ? null :
                            full.predicted * trained.calibration.getOrDefault([row.position, row.year], 1d),
                     actual: B.realisedVor(row.outcome, against)]
                }
                annual.every { it.base != null && it.centered != null } ?
                        [base: annual.sum { it.base }, centered: annual.sum { it.centered }, actual: annual.sum { it.actual }] : null
            }.findAll()
            STRENGTHS.each { double strength ->
                def errors = careers.collect { (1d - strength) * it.base + strength * it.centered - it.actual }
                scores << [outer: outer, inner: inner, strength: strength, eligible: eligible.size(),
                           scored: careers.size(), missing: eligible.size() - careers.size(),
                           mse: errors ? errors.sum { it * it } / errors.size() : null,
                           bias: errors ? errors.sum() / errors.size() : null,
                           trainingClasses: trained.training*.draftClass.unique().sort().join(',')]
            }
        }
        double strength = choose(scores)
        [strength: strength, scores: scores.collect { it + [selected: it.strength == strength] }]
    }

    static double choose(List<Map> scores) {
        def comparable = scores.groupBy { it.inner }.values().findAll { rows ->
            STRENGTHS.every { strength -> rows.any { it.strength == strength && it.mse != null } }
        }.flatten()
        if (!comparable) return 0d
        STRENGTHS.min { strength ->
            def losses = comparable.findAll { it.strength == strength }*.mse
            losses.sum() / losses.size()
        }
    }
}
