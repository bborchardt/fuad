package ff.research.fuad.rookies

import ff.projection.fuad.*

import spock.lang.Specification
import spock.lang.TempDir
import groovy.json.JsonOutput

class RookieCaptureAuditSpec extends Specification {
    @TempDir File temporary

    def 'cohort matching is exact and missing is not ineligible'() {
        given:
        def player = [name: 'King, Haynes', position: 'QB', draft_year: '2026', id: '1']

        expect:
        RookieCaptureAudit.match('Haynes King', [player], '2026').status == 'MATCHED_ENTRY_YEAR'
        RookieCaptureAudit.match('Haynes King', [], '2026').status == 'UNMATCHED'
        RookieCaptureAudit.match('Haynes King', [player + [position: 'WR']], '2026').status == 'UNMATCHED'
        RookieCaptureAudit.match('Haynes King', [player + [draft_year: '2025']], '2026').status == 'OTHER_ENTRY_YEAR'
        RookieCaptureAudit.match('Haynes King', [player, player], '2026').status == 'AMBIGUOUS'
    }

    def 'verified copies preserve the pinned manifest and reject changed manifests'() {
        given:
        def capture = new File(temporary, 'original')
        capture.mkdir()
        def predictions = new File(capture, 'predictions.tsv')
        predictions.text = 'frozen'
        def manifest = new File(capture, 'manifest.json')
        manifest.text = JsonOutput.toJson([status: 'COMPLETE', artifactHashes: ['predictions.tsv': RookieProspectiveCapture.sha256(predictions)]])
        def hash = RookieProspectiveCapture.sha256(manifest)
        def destination = new File(temporary, 'copy')
        RookieCaptureAudit.backup(capture, hash, destination)

        expect:
        new File(destination, 'predictions.tsv').text == 'frozen'
        RookieProspectiveCapture.sha256(new File(destination, 'manifest.json')) == hash

        when:
        manifest.text = manifest.text + '\n'
        RookieCaptureAudit.verified(capture, hash)

        then:
        thrown(IOException)
    }
}
