package ff.research.fuad.rookies

import ff.projection.fuad.*

import ff.data.RealisedSeason
import spock.lang.Specification

class RookieEventCalibrationSpec extends Specification {
    def 'shared shrinkage preserves ordering and chooses unchanged model on ties or missing evidence'() {
        expect:
        RookieEventCalibration.blend(.2d, .6d, .5d) == .4d
        RookieEventCalibration.blend(null, .6d, 1d) == null
        RookieEventCalibration.STRENGTHS.every {
            RookieEventCalibration.blend(.2d, .3d, it) <= RookieEventCalibration.blend(.4d, .6d, it)
        }
        RookieEventCalibration.choose([]) == 0d
        RookieEventCalibration.choose(RookieEventCalibration.STRENGTHS.collect {
            [inner: 'A', strength: it, brier: 1d]
        }) == 0d
    }

    def 'outer outcomes cannot select shrinkage and both excluded classes leave replacement training'() {
        given:
        def observations = RookieAvailabilityStudySpec.fixture()
        def excludedNames = []
        def provider = { excluded ->
            excludedNames << excluded*.draftClass.unique().sort()
            (1..14).collectEntries { [(it): 10g] }
        }
        def first = new RookieEventCalibration(observations, provider)
        def chosen = first.select('2017')

        when:
        observations.findAll { it.draftClass == '2017' }.each { it.outcome = new RealisedSeason(100000g, 13) }
        def second = new RookieEventCalibration(observations, provider)

        then:
        second.select('2017') == chosen
        excludedNames.every { it.size() == 2 && '2017' in it }
        chosen.scores.every { it.inner != '2017' && it.scored == it.eligible }
        first.availability.context(['2017', '2018']).training.every { !(it.draftClass in ['2017', '2018']) }
    }

    def 'selection weights classes equally and requires comparable candidates'() {
        given:
        def scores = RookieEventCalibration.STRENGTHS.collectMany { strength ->
            [[inner: 'large', strength: strength, scored: 1000, brier: strength == 0d ? 0d : .1d],
             [inner: 'small', strength: strength, scored: 2, brier: strength == 0d ? 1d : .1d]]
        }

        expect:
        RookieEventCalibration.choose(scores) == .25d
        RookieEventCalibration.choose([[inner: 'incomplete', strength: 1d, brier: 0d]]) == 0d
    }
}
