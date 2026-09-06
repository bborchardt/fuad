package ff.research.fuad.rookies

import ff.projection.fuad.*

import spock.lang.Specification

class RookieQbDiagnosticsSpec extends Specification {
    def 'influence removes one evaluation player without hiding the others'() {
        given:
        def rows = [[draftClass: 'A', name: 'outlier', actual: 100d, predicted: 0d],
                    [draftClass: 'B', name: 'ordinary', actual: 10d, predicted: 20d]]

        when:
        def result = RookieQbDiagnostics.influence(rows)

        then:
        result.first().name == 'outlier'
        Math.abs(result*.squaredErrorShare.sum() - 1d) < 1e-10
        result.first().remainingBias == 10d
        result.first().remainingRmse == 10d
        rows.size() == 2
        RookieQbDiagnostics.influence([]) == []
    }

    def 'unsupported forecasts and unobserved years cannot change the comparison sample'() {
        given:
        def predictions = RookieQbDiagnostics.REPLACEMENT_FACTORS.collectMany { factor ->
            RookieQbDiagnostics.MODELS.collectMany { model ->
                (1..3).collect { year -> [draftClass: '2023', name: 'QB', factor: factor, model: model,
                        year: year, actual: 10d, predicted: factor == .8d && year == 3 ? null : 12d] }
            }
        }

        when:
        def contracts = RookieQbDiagnostics.contracts(predictions)

        then:
        contracts.every { it.horizon != 5 }
        contracts.findAll { it.horizon == 1 }.every { it.predicted == 12d }
        contracts.findAll { it.horizon == 3 }.every { it.predicted == null }
    }
}
