package ff.projection.fuad

import ff.data.RealisedSeason
import spock.lang.Specification

class RookieRankFrequencySpec extends Specification {
    def 'rank kernel retains busts and reports effective donor count'() {
        given:
        def donors = [[rank: 1, vor: 30d], [rank: 2, vor: 0d]]
        def at = RookieRankFrequency.localFrequency(donors, 1, 'VOR_26')

        expect:
        Math.abs(at.probability - 1d / (1d + Math.exp(-1d))) < 1e-10
        at.effectiveN > 1d && at.effectiveN < 2d
        RookieRankFrequency.localFrequency(donors, 2, 'VOR_26').probability < at.probability
        RookieRankFrequency.localFrequency([], 1, 'VOR_26').probability == null
        RookieRankFrequency.localFrequency([[rank: 1, vor: 0d]], 1, 'VOR_26').probability == 0d
    }

    def 'held-out outcomes cannot choose the local weight and event probabilities remain ordered'() {
        given:
        def observations = RookieAvailabilityStudySpec.fixture()
        def excludedClasses = []
        def provider = { excluded ->
            excludedClasses << excluded*.draftClass.unique().sort()
            (1..14).collectEntries { [(it): 10g] }
        }
        def study = new RookieRankFrequency(observations, provider)
        def chosen = study.select('2017')
        def before = study.forecast('2017', ['2017'])

        when:
        observations.findAll { it.draftClass == '2017' }.each { it.outcome = new RealisedSeason(100000g, 13) }
        def second = new RookieRankFrequency(observations, provider)

        then:
        second.select('2017') == chosen
        second.forecast('2017', ['2017'])*.local == before*.local
        excludedClasses.every { '2017' in it }
        before.groupBy { [it.name, it.year] }.values().every { rows ->
            rows.find { it.event == 'VOR_26' }.local <= rows.find { it.event == 'POSITIVE_VOR' }.local
        }
    }
}
