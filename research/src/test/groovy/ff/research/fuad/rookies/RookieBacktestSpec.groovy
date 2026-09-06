package ff.research.fuad.rookies

import ff.projection.fuad.*

import ff.data.RealisedSeason
import spock.lang.Specification

class RookieBacktestSpec extends Specification {
    private static RookieBacktest.Observation row(String draft, int rank, int year, int dynasty, int points = 100) {
        new RookieBacktest.Observation(draftClass: draft, name: "${draft}-${rank}", position: 'RB',
                rookieRank: rank, overallRank: rank, dynastyRank: dynasty, year: year,
                outcome: new RealisedSeason(points as BigDecimal, 10))
    }

    def 'a held-out class cannot affect any contract-year curve or fitted dynasty parameter'() {
        given:
        def observations = (2017..2020).collectMany { draft ->
            (1..5).collectMany { year -> (1..8).collect { rank -> row("$draft", rank, year, rank * 3, 160 - rank * 10) } }
        }
        def before = RookieBacktest.trainingFor(observations, '2019')
        def first = RookieBacktest.seasonsFrom(before)
        def fitted = RookieBacktest.fitDynasty(before, first)

        when: 'every outcome and dynasty rank in the target career is made extreme'
        observations.findAll { it.draftClass == '2019' }.each {
            it.outcome = new RealisedSeason(100000g, 13)
            it.dynastyRank = 1
        }
        def after = RookieBacktest.trainingFor(observations, '2019')
        def second = RookieBacktest.seasonsFrom(after)
        def refitted = RookieBacktest.fitDynasty(after, second)

        then:
        after.every { it.draftClass != '2019' }
        after.size() == 3 * 5 * 8
        (1..5).every { first.curve(it).seasonPoints('RB', 1) == second.curve(it).seasonPoints('RB', 1) }
        fitted.expected == refitted.expected
        fitted.slope == refitted.slope
        fitted.cap == refitted.cap
        fitted.plateau == refitted.plateau
        fitted.decay == refitted.decay
    }

    def 'a lost season stays in the dynasty baseline and is worth zero'() {
        given:
        def lost = row('2018', 1, 1, 10)
        lost.outcome = new RealisedSeason(0g, 0)
        def productive = row('2019', 1, 1, 10, 200)
        def target = row('2020', 1, 1, 10)
        def replacement = (1..14).collectEntries { [(it): 10g] }

        expect:
        RookieBacktest.dynastyNeighbours([lost, productive], target).size() == 2
        RookieBacktest.realisedVor(lost.outcome, replacement) == 0d
        RookieBacktest.prediction(target, 'DYNASTY_ONLY', [lost, productive], null, null, null, replacement).predicted == 50d
    }

    def 'career scoring does not pad recent classes or use different samples across models'() {
        given:
        def annual = RookieBacktest.MODELS.collectMany { model ->
            (1..3).collect { year -> [draftClass: '2023', name: 'A', position: 'RB', overallRank: 1,
                                    model: model, year: year, actual: 10d,
                                    predicted: model == 'DYNASTY_ONLY' && year == 3 ? null : 12d,
                                    low: 0d, high: 20d] }
        }

        when:
        def pooled = RookieBacktest.metrics(annual).findAll {
            it.fold == 'POOLED' && it.cohort == 'TOP50' && it.position == 'ALL'
        }

        then:
        pooled.every { it.horizon != 5 }
        pooled.findAll { it.horizon == 1 }.every { it.scored == 1 && it.mae == 2d && it.coverage80 == 1d }
        pooled.findAll { it.horizon == 3 }.every { it.scored == 0 && it.missing == 1 && it.coverage80 == null }
    }

    def 'rank correlation handles ties and constant predictions honestly'() {
        expect:
        Math.abs(RookieBacktest.spearman([1d, 1d, 3d], [2d, 2d, 9d]) - 1d) < 1e-10
        Math.abs(RookieBacktest.spearman([1d, 2d, 3d], [3d, 2d, 1d]) + 1d) < 1e-10
        RookieBacktest.spearman([1d, 1d], [2d, 3d]) == null
        RookieBacktest.spearman([], []) == null
    }

    def 'a negative log-rate offset does not erase a positive dynasty slope'() {
        when:
        def fitted = RookieBacktest.regression([0d, 1d, 2d], [-2d, -1.5d, -1d])

        then:
        Math.abs(fitted.slope - .5d) < 1e-10
        Math.abs(fitted.intercept + 2d) < 1e-10
        fitted.loss < 1e-10
        RookieBacktest.regression([0d, 1d, 2d], [2d, 1d, 0d]).slope == 0d
    }

    def 'weighted predictive quantiles retain a probability mass at zero'() {
        expect:
        RookieBacktest.weightedQuantile([[0d, .8d], [100d, .2d]], .1d) == 0d
        RookieBacktest.weightedQuantile([[0d, .8d], [100d, .2d]], .9d) == 100d
    }

    def 'historical outcomes stay attached to players despite duplicate positional ranks'() {
        given:
        def observations = RookieBacktest.observations()
        def felton = observations.find { it.draftClass == '2021' && it.year == 1 && it.name == 'Demetric Felton' }

        expect:
        observations*.draftClass.unique().size() == 9
        observations.groupBy { [it.draftClass, it.name, it.year] }.values().every { it.size() == 1 }
        felton != null
        felton.outcome.games > 0
        observations.count { it.name == 'Brennan Eagles' && it.draftClass == '2021' } == 5
        observations.findAll { it.name == 'Brennan Eagles' }.every { it.outcome.games == 0 }
        observations.findAll { it.draftClass == '2025' }.every { it.year == 1 }
        observations.any { it.outcome.games == 0 }
    }
}
