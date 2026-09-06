package ff.research.fuad.rookies

import ff.projection.fuad.*

import ff.projection.PointsCurve
import spock.lang.Specification

class RookieRateStudySpec extends Specification {
    def 'weighted CRPS agrees with explicit pairwise definition'() {
        given:
        def distribution = [[0d, .2d], [3d, .3d], [10d, .5d]]
        double truth = 4d
        double absolute = 0d, pairwise = 0d
        for (a in distribution) {
            absolute += a[1] * Math.abs(a[0] - truth)
            for (b in distribution) pairwise += a[1] * b[1] * Math.abs(a[0] - b[0])
        }
        double expected = absolute - .5d * pairwise

        expect:
        Math.abs(RookieRateStudy.crps(distribution, truth) - expected) < 1e-10
        RookieRateStudy.crps([[5d, 1d]], 5d) == 0d
        RookieRateStudy.crps([], truth) == null
    }

    def 'conditioning does not erase zero games from unconditional VOR'() {
        given:
        def pool = [[outcome: new PointsCurve.Outcome(0d, 0), weight: .5d],
                    [outcome: new PointsCurve.Outcome(1d, 10), weight: .5d]]
        def rates = RookieRateStudy.rateDistribution(pool, 20d, 0d)
        def replacement = (1..14).collectEntries { [(it): 10g] }

        expect:
        Math.abs(rates.sum { it[1] } - 1d) < 1e-10
        rates.every { it[0] == 20d }
        Math.abs(RookieRateStudy.independentVor(pool, rates, replacement) - 50d) < 1e-10
        RookieRateStudy.independentVor([pool.first() + [weight: 1d]], [], replacement) == 0d
        RookieRateStudy.independentVor([], rates, replacement) == null
    }

    def 'removing dependence preserves the marginals but changes the payoff'() {
        given:
        def pool = [[outcome: new PointsCurve.Outcome(.5d, 1), weight: .5d],
                    [outcome: new PointsCurve.Outcome(2d, 10), weight: .5d]]
        def replacement = (1..14).collectEntries { [(it): 10g] }
        def replay = RookieBacktest.vorAgainst(replacement)
        double joint = 0d
        for (point in pool) joint += point.weight * replay.call(10d, point.outcome)
        def rates = RookieRateStudy.rateDistribution(pool, 10d, 0d)

        expect:
        joint == 50d
        Math.abs(RookieRateStudy.independentVor(pool, rates, replacement) - 27.5d) < 1e-10
        Math.abs(RookieRateStudy.mix(pool, pool, .75d).sum { it.weight } - 1d) < 1e-10
    }

    def 'independence replay respects changing weekly thresholds and the games cap'() {
        given:
        def pool = [[outcome: new PointsCurve.Outcome(1d, 14), weight: 1d]]
        def replacement = (1..14).collectEntries { [(it): it as BigDecimal] }

        expect:
        RookieRateStudy.independentVor(pool, [[10d, 1d]], replacement) == 45d
    }

    def 'metrics retain missing support and do not invent future contract years'() {
        given:
        def predictions = RookieRateStudy.MODELS.collect { model ->
            [draftClass: '2025', name: 'QB', rank: 1, overallRank: 1, year: 1, model: model,
             actualRate: null, rate: null, rateCrps: null, actualVor: 0d, vor: null, coupling: null]
        }

        when:
        def result = RookieRateStudy.metrics(predictions)

        then:
        result.careers.every { it.horizon == 1 && it.eligible == 1 && it.scored == 0 && it.missing == 1 }
        result.rates.every { it.eligible == 1 && it.played == 0 && it.scored == 0 && it.crps == null }
        RookieRateStudy.metrics([]) == [rates: [], careers: []]
    }
}
