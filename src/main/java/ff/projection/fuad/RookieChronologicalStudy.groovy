package ff.projection.fuad

import ff.projection.fuad.RookieBacktest as B
import ff.projection.fuad.RookieEventStudy as E
import ff.projection.fuad.RookieEventCalibration as C
import ff.league.League
import ff.load.fantasypros.FantasyProsLoader
import ff.load.util.LoadUtils

/** Season-truncated sensitivity, under fixed 2026 rules and current archived data vintages. */
class RookieChronologicalStudy {
    static final Map<String, List<String>> COLUMNS = [
            predictions: 'draftClass name rank overallRank year outcomeSeason cutoff trainingMaxSeason replacementMaxSeason baselineN model strength event actual probability baseline'.tokenize(),
            metrics: E.COLUMNS.metrics, calibration: E.COLUMNS.calibration,
            selection: 'outer inner strength eligible scored latestLabelSeason brier selected'.tokenize()
    ]
    final List<B.Observation> observations
    final Closure replacementProvider
    private final Map<Integer, Map> contexts = [:]

    RookieChronologicalStudy(List<B.Observation> observations, Closure provider = { seasons -> B.replacementFor([], seasons).QB }) {
        this.observations = observations
        this.replacementProvider = provider
    }

    Map context(int cutoff) {
        if (!contexts.containsKey(cutoff)) {
            def training = RookieChronologyAudit.availableBefore(observations, cutoff)
            def seasons = League.FUAD.seasons.findAll { it.toInteger() < cutoff }
            def availability = new RookieAvailabilityStudy(training)
            contexts[cutoff] = [training: training, availability: availability, pools: availability.context([]),
                               replacement: seasons ? replacementProvider(seasons) : null,
                               replacementMaxSeason: seasons ? seasons*.toInteger().max() : null,
                               trainingMaxSeason: training ? training.collect { RookieChronologyAudit.outcomeSeason(it) }.max() : null]
        }
        contexts[cutoff]
    }

    /** Forecast calculation never reads target outcomes. Those are attached only for scoring. */
    List<Map> forecast(int cutoff, List<B.Observation> targets) {
        def at = context(cutoff)
        targets.collectMany { row ->
            if (row.draftClass.toInteger() != cutoff) throw new IllegalArgumentException('Forecast cutoff must match entry class')
            def donors = at.training.findAll { it.position == 'QB' && it.overallRank <= 50 && it.year == row.year }
            def dist = []
            if (at.replacement && donors) {
                def curve = at.pools.seasons.curve(row.year)
                double points = curve.seasonPoints('QB', row.rookieRank)
                double error = points > 0 ? curve.standardError('QB', row.rookieRank).toDouble() / points : 0d
                dist = E.distribution(at.availability.pools(at.pools, row).broad,
                        curve.levelledRate('QB', row.rookieRank).toDouble(), error, at.replacement)
            }
            def labelled = at.replacement ? donors.collect {
                [rank: it.rookieRank, vor: B.realisedVor(it.outcome, at.replacement)]
            } : []
            E.EVENTS.collect { event ->
                [draftClass: row.draftClass, name: row.name, rank: row.rookieRank, overallRank: row.overallRank,
                 year: row.year, outcomeSeason: RookieChronologyAudit.outcomeSeason(row), cutoff: cutoff,
                 trainingMaxSeason: at.trainingMaxSeason, replacementMaxSeason: at.replacementMaxSeason,
                 baselineN: donors.size(), event: event, probability: E.probability(dist, event),
                 baseline: labelled ? labelled.count { E.occurs(it.vor, event) } / (double) labelled.size() : null,
                 local: RookieRankFrequency.localFrequency(labelled, row.rookieRank, event).probability]
            }
        }
    }

    List<Map> labelledForecast(int cutoff, List<B.Observation> targets) {
        def byKey = targets.collectEntries { [([it.name, it.year]): it] }
        def replacement = context(cutoff).replacement
        forecast(cutoff, targets).collect { row ->
            def actual = byKey[[row.name, row.year]].outcome
            row + [actual: replacement != null && actual != null ?
                    (E.occurs(B.realisedVor(actual, replacement), row.event) ? 1 : 0) : null]
        }
    }

    Map select(int outer) {
        List<Map> scores = []
        observations*.draftClass.unique().sort().findAll { it.toInteger() < outer }.each { inner ->
            def targets = observations.findAll { it.draftClass == inner && it.position == 'QB' && it.overallRank <= 50 &&
                    RookieChronologyAudit.outcomeSeason(it) < outer }
            def rows = labelledForecast(inner.toInteger(), targets)
            def supported = rows.findAll { it.probability != null && it.baseline != null && it.local != null && it.actual != null }
            C.STRENGTHS.each { strength ->
                scores << [outer: outer, inner: inner, strength: strength, eligible: rows.size(), scored: supported.size(),
                           latestLabelSeason: rows ? rows*.outcomeSeason.max() : null,
                           brier: supported ? supported.sum {
                               Math.pow(C.blend(it.baseline as Double, it.local as Double, strength) - it.actual, 2)
                           } / supported.size() : null]
            }
        }
        double strength = C.choose(scores)
        [strength: strength, scores: scores.collect { it + [selected: it.strength == strength] }]
    }

    static List<Map> variants(List<Map> rows, double strength) {
        rows.collectMany { row ->
            boolean supported = row.probability != null && row.baseline != null && row.local != null
            [CURRENT: null, FREQUENCY: 0d, NESTED: strength].collect { model, weight ->
                row + [model: model, strength: weight, probability: !supported ? null : model == 'CURRENT' ?
                        row.probability : C.blend(row.baseline as Double, row.local as Double, weight)]
            }
        }
    }

    Map run(Closure progress = { }) {
        List<Map> predictions = [], selection = []
        observations*.draftClass.unique().sort().each { draft ->
            int cutoff = draft.toInteger()
            progress("Chronological validation: $cutoff")
            def chosen = select(cutoff)
            selection.addAll(chosen.scores)
            def targets = observations.findAll { it.draftClass == draft && it.position == 'QB' }
            predictions.addAll(variants(labelledForecast(cutoff, targets), chosen.strength))
        }
        // No replacement implies no forecast support, so null labels must not be scored.
        [predictions: predictions, selection: selection] + E.metrics(predictions)
    }

    Map prospective(int cutoff) {
        def ranked = new FantasyProsLoader().loadRankedPlayers(LoadUtils.fpRookieRankingsPprResourcePath("$cutoff"))
        def targets = ranked.values().findAll { it.player.position == 'QB' }.collectMany { rookie ->
            (1..5).collect { year -> new B.Observation(draftClass: "$cutoff", name: rookie.player.name, position: 'QB',
                    rookieRank: rookie.rank.positionRank, overallRank: rookie.rank.overallRank, year: year) }
        }
        def chosen = select(cutoff)
        [predictions: variants(forecast(cutoff, targets), chosen.strength), selection: chosen.scores,
         replacement: context(cutoff).replacement]
    }

    static void writeReports(Map result, File directory, Map columns = COLUMNS) {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create $directory")
        columns.each { name, fields ->
            new File(directory, "${name}.tsv").withWriter('UTF-8') { writer ->
                writer.println(fields.join('\t'))
                result[name].each { row -> writer.println(fields.collect { field ->
                    def value = row[field]
                    value == null ? '' : value.toString().replaceAll('[\t\r\n]', ' ')
                }.join('\t')) }
            }
        }
    }

    static void main(String[] args) {
        if (args.size() > 1) throw new IllegalArgumentException('Usage: rookie_chronological_study.sh [output-directory]')
        def result = new RookieChronologicalStudy(B.observations()).run { System.err.println(it) }
        writeReports(result, new File(args ? args[0] : 'reports/fuad/chronological-study'))
        result.metrics.findAll { it.fold == 'POOLED' && it.cohort == 'TOP50' && it.band == 'ALL' && it.year == 'ALL' }.each { println it }
    }
}
