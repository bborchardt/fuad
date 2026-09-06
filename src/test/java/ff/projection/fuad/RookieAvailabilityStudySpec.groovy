package ff.projection.fuad

import ff.data.RealisedSeason
import ff.projection.PointsCurve
import spock.lang.Specification

class RookieAvailabilityStudySpec extends Specification {
    static List<RookieBacktest.Observation> fixture() {
        (2017..2021).collectMany { draft -> (1..3).collectMany { year -> (1..4).collect { rank ->
            int games = rank == 4 ? 0 : 14 - rank * 2
            new RookieBacktest.Observation(draftClass: "$draft", name: "$draft-$rank", position: 'QB',
                    rookieRank: rank, overallRank: rank, year: year,
                    outcome: new RealisedSeason((games * (20 - rank)) as BigDecimal, games))
        } } }
    }

    def 'current pooling reproduces the existing forecast and local weights preserve paired busts'() {
        given:
        def study = new RookieAvailabilityStudy(fixture())
        def row = study.observations.first()
        def context = study.context(['2017'])
        def pools = study.pools(context, row)
        def replacement = (1..14).collectEntries { [(it): 10g] }

        expect:
        Math.abs(RookieAvailabilityStudy.vor(pools.broad, context.seasons.curve(1), row, replacement) -
                RookieBacktest.prediction(row, 'ROOKIE_ONLY', context.training, context.seasons,
                        context.outcomes, null, replacement, false).predicted) < 1e-9
        Math.abs(pools.local.sum { it.weight } - 1d) < 1e-10
        pools.local.any { it.outcome.games == 0 && it.outcome.rateMultiplier == 0d && it.weight > 0d }
        RookieAvailabilityStudy.poolSummary(pools.local).games > RookieAvailabilityStudy.poolSummary(pools.broad).games
        RookieAvailabilityStudy.blend(2d, 10d, 0d) == 2d
        RookieAvailabilityStudy.blend(2d, 10d, .5d) == 6d
        RookieAvailabilityStudy.blend(null, 10d, 1d) == null
    }

    def 'outer outcomes cannot select strength and both held-out classes leave the inner pool'() {
        given:
        def observations = fixture()
        def first = new RookieAvailabilityStudy(observations)
        def chosen = first.select('2017')
        def inner = first.context(['2017', '2018'])

        when:
        observations.findAll { it.draftClass == '2017' }.each { it.outcome = new RealisedSeason(100000g, 13) }
        def second = new RookieAvailabilityStudy(observations)

        then:
        second.select('2017') == chosen
        inner.training.every { !(it.draftClass in ['2017', '2018']) }
        chosen.scores.every { it.inner != '2017' }
        RookieAvailabilityStudy.choose([]) == 0d
        RookieAvailabilityStudy.choose(RookieAvailabilityStudy.STRENGTHS.collect {
            [inner: 'A', strength: it, gamesMse: 1d]
        }) == 0d
    }

    def 'metrics retain missing predictions and do not invent future contract years'() {
        given:
        def predictions = RookieAvailabilityStudy.MODELS.collect { model ->
            [draftClass: '2025', name: 'QB', rank: 1, overallRank: 1, year: 1, model: model,
             actualGames: 0, games: null, actualZero: 1, zero: null, actualVor: 0d, vor: null]
        }

        when:
        def result = RookieAvailabilityStudy.metrics(predictions)

        then:
        result.careers.every { it.horizon == 1 && it.eligible == 1 && it.scored == 0 }
        result.availability.every { it.eligible == 1 && it.scored == 0 && it.zeroBrier == null }
    }
}
