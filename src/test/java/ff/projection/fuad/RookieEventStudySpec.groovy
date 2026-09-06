package ff.projection.fuad

import ff.projection.PointsCurve
import spock.lang.Specification

class RookieEventStudySpec extends Specification {
    def 'events retain bust mass and use explicit threshold boundaries'() {
        given:
        def pool = [[outcome: new PointsCurve.Outcome(0d, 0), weight: .5d],
                    [outcome: new PointsCurve.Outcome(1d, 13), weight: .5d]]
        def replacement = (1..14).collectEntries { [(it): 10g] }
        def dist = RookieEventStudy.distribution(pool, 12d, 0d, replacement)

        expect:
        Math.abs(dist.sum { it[1] } - 1d) < 1e-10
        Math.abs(RookieEventStudy.probability(dist, 'POSITIVE_VOR') - .5d) < 1e-10
        Math.abs(RookieEventStudy.probability(dist, 'VOR_26') - .5d) < 1e-10
        !RookieEventStudy.occurs(0d, 'POSITIVE_VOR')
        !RookieEventStudy.occurs(25.999d, 'VOR_26')
        RookieEventStudy.occurs(26d, 'VOR_26')
        RookieEventStudy.probability([], 'VOR_26') == null
    }

    def 'Brier comparison uses common benchmark support and retains missing rows'() {
        given:
        def rows = [[actual: 1, probability: .8d, baseline: .5d],
                    [actual: 0, probability: .2d, baseline: .5d],
                    [actual: 1, probability: null, baseline: .5d]]
        def result = RookieEventStudy.summarize(rows)

        expect:
        result.eligible == 3 && result.scored == 2 && result.missing == 1
        Math.abs(result.brier - .04d) < 1e-10
        Math.abs(result.skill - .84d) < 1e-10
        Math.abs(result.bias) < 1e-10
        RookieEventStudy.summarize([]).brier == null
        RookieEventStudy.summarize([[actual: 0, probability: 0d, baseline: 0d]]).skill == null
        RookieEventStudy.metrics([]) == [metrics: [], calibration: []]
    }

    def 'reliability bins include zero and one and contract years remain separate'() {
        given:
        def rows = [0d, 1d].collect { p ->
            [draftClass: '2025', name: "QB$p", rank: 1, overallRank: 1, year: 1,
             model: 'CURRENT', event: 'POSITIVE_VOR', actual: p as int, probability: p, baseline: .5d]
        }
        def result = RookieEventStudy.metrics(rows)

        expect:
        result.calibration*.bin.unique().sort() == [0, 4]
        result.calibration.every { it.scored == 1 && it.observed == it.probability }
        result.metrics.every { it.year in ['ALL', 1] && it.scored == 2 && it.brier == 0d }
    }
}
