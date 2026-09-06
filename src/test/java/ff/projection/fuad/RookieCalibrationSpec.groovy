package ff.projection.fuad

import ff.projection.ExpectedValue
import ff.projection.PointsCurve
import ff.data.RealisedSeason
import spock.lang.Specification

class RookieCalibrationSpec extends Specification {
    def 'centering preserves aggregate predicted value and mixture endpoints'() {
        given:
        def base = RookieBacktest.summarize([[10d, 1d]])
        def raw = RookieBacktest.summarize([[20d, 1d]])
        double factor = RookieCalibration.factor(30d, 50d)

        expect:
        Math.abs(factor * (20d + 30d) - 30d) < 1e-10
        RookieCalibration.blend(base, raw, factor, 0d).is(base)
        RookieCalibration.blend(base, raw, factor, 1d).predicted == 12d
        RookieCalibration.blend(base, raw, factor, .5d).predicted == 11d
        RookieCalibration.blend(base, raw, factor, .5d).distribution.sum { it[1] } == 1d
        RookieCalibration.factor(0d, 0d) == 1d
        RookieCalibration.blend(base, raw, 0d, 1d).predicted == 0d
    }

    def 'selection weights classes equally and prefers zero on a tie or absent evidence'() {
        given:
        def scores = ['A', 'B'].collectMany { inner ->
            RookieCalibration.STRENGTHS.collect { strength ->
                [inner: inner, strength: strength, scored: inner == 'A' ? 100 : 1,
                 mse: inner == 'A' ? 10d - strength : 10d + 2d * strength]
            }
        }

        expect:
        RookieCalibration.choose(scores) == 0d
        RookieCalibration.choose(scores.collect { it + [mse: 1d] }) == 0d
        RookieCalibration.choose([]) == 0d
        RookieCalibration.choose(scores.collect { it + [mse: Math.pow(it.strength - .5d, 2)] }) == .5d
    }

    def 'fast replay agrees with production for zero games clipping and varying replacement'() {
        expect:
        [0, 5, 13, 17].every { games ->
            [0d, .5d, 2d].every { multiplier ->
                [false, true].every { varying ->
                    def replacement = (1..14).collectEntries { [(it): (varying ? it : 10) as BigDecimal] }
                    def outcome = new PointsCurve.Outcome(multiplier, games)
                    double production = ExpectedValue.expectedValueOverReplacement(12g, [outcome], replacement, 14, 14)
                    Math.abs(RookieBacktest.outcomeVor(12d, outcome, replacement) - production) < 1e-9
                }
            }
        }
    }

    def 'outer outcomes cannot choose strength or centering and both classes leave every inner fit'() {
        given:
        def rows = (2017..2020).collectMany { draft -> (1..5).collectMany { year ->
            (1..8).collect { rank -> new RookieBacktest.Observation(draftClass: "$draft", name: "$draft-$rank",
                    position: 'RB', rookieRank: rank, overallRank: rank, dynastyRank: rank * (draft - 2015),
                    year: year, outcome: new RealisedSeason((200 - 10 * rank) as BigDecimal, 10)) }
        } }
        List<Set> excludedSets = []
        def replacement = { excluded ->
            excludedSets << (excluded*.draftClass as Set)
            [RB: (1..14).collectEntries { [(it): 10g] }]
        }
        def first = new RookieCalibration(rows, { }, replacement)
        def initial = first.select('2020')
        def centers = first.context(['2020']).calibration

        when:
        rows.findAll { it.draftClass == '2020' }.each {
            it.outcome = new RealisedSeason(100000g, 13)
            it.dynastyRank = 1
        }
        def second = new RookieCalibration(rows, { }, replacement)
        def changed = second.select('2020')

        then:
        changed.strength == initial.strength
        changed.scores == initial.scores
        second.context(['2020']).calibration == centers
        changed.scores.every { !it.trainingClasses.tokenize(',').contains('2020') &&
                !it.trainingClasses.tokenize(',').contains(it.inner) }
        excludedSets.every { it.contains('2020') }
        excludedSets.findAll { it.size() == 2 }.size() == 6
    }
}
