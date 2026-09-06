package ff.projection.fuad

import spock.lang.Specification

class RookieChronologyAuditSpec extends Specification {
    def 'prior draft classes do not make future contract seasons available'() {
        given:
        def rows = (1..5).collect { year ->
            new RookieBacktest.Observation(draftClass: '2017', name: 'QB', position: 'QB',
                    rookieRank: 1, overallRank: 1, year: year)
        }

        expect:
        RookieChronologyAudit.availableBefore(rows, 2017).empty
        RookieChronologyAudit.availableBefore(rows, 2020)*.year == [1, 2, 3]
        RookieChronologyAudit.outcomeSeason(rows.last()) == 2021
        RookieChronologyAudit.availableBefore(rows, 2022).size() == 5
        RookieChronologyAudit.audit([]).empty
    }
}
