package ff.research.fuad.rookies

import ff.projection.fuad.*

import ff.data.RealisedSeason
import spock.lang.Specification
import spock.lang.TempDir

class RookieChronologicalStudySpec extends Specification {
    @TempDir File temporary

    def 'empty reports still write explicit headers'() {
        when:
        RookieChronologicalStudy.writeReports([predictions: [], metrics: [], calibration: [], selection: []], temporary)

        then:
        RookieChronologicalStudy.COLUMNS.every { name, columns ->
            new File(temporary, "${name}.tsv").readLines() == [columns.join('\t')]
        }
    }
    def 'later outcomes cannot change earlier predictions or selection'() {
        given:
        def rows = RookieAvailabilityStudySpec.fixture()
        def seasonsSeen = []
        def provider = { seasons ->
            seasonsSeen << seasons
            (1..14).collectEntries { [(it): 10g] }
        }
        def first = new RookieChronologicalStudy(rows, provider)
        def targets = rows.findAll { it.draftClass == '2020' && it.position == 'QB' }
        def forecasts = first.forecast(2020, targets)
        def selected = first.select(2020)

        when:
        rows.findAll { RookieChronologyAudit.outcomeSeason(it) >= 2020 }.each { it.outcome = new RealisedSeason(100000g, 13) }
        def second = new RookieChronologicalStudy(rows, provider)

        then:
        second.forecast(2020, targets) == forecasts
        second.select(2020) == selected
        selected.scores.every { it.latestLabelSeason == null || it.latestLabelSeason < 2020 }
        seasonsSeen.flatten().every { it.toInteger() < 2020 }
        forecasts.every { it.trainingMaxSeason < 2020 && it.replacementMaxSeason < 2020 }
    }

    def 'empty early training stays missing rather than inventing zero probabilities'() {
        given:
        def rows = RookieAvailabilityStudySpec.fixture()
        def study = new RookieChronologicalStudy(rows, { throw new AssertionError('No replacement seasons exist') })
        def forecasts = study.forecast(2017, rows.findAll { it.draftClass == '2017' })

        expect:
        forecasts.every { it.probability == null && it.baseline == null }
        study.select(2017).strength == 0d
        RookieChronologicalStudy.variants(forecasts, 0d).every { it.probability == null }
    }
}
