package ff.research.fuad.rookies

import spock.lang.Specification
import spock.lang.TempDir
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class ResearchSnapshotSpec extends Specification {
    @TempDir File temporary

    /** A capture shaped like a real one: forecasts, the omitted zip, and a manifest hashing both. */
    private File newCapture(String forecast = 'frozen') {
        def capture = new File(temporary, "capture-${UUID.randomUUID()}")
        capture.mkdir()
        new File(capture, 'predictions.tsv').text = forecast
        new File(capture, 'inputs.zip').text = "archived inputs for $forecast"
        new File(capture, 'manifest.json').text = JsonOutput.toJson([status: 'COMPLETE', forecastClass: 2026,
                artifactHashes: capture.listFiles().collectEntries { [(it.name): RookieProspectiveCapture.sha256(it)] }])
        capture
    }

    private String manifestHash(File capture) { RookieProspectiveCapture.sha256(new File(capture, 'manifest.json')) }

    def 'the forecast is committed and the inputs archive is kept by hash alone'() {
        given:
        def capture = newCapture()
        def snapshot = new File(temporary, 'snapshot')

        when:
        def record = ResearchSnapshot.export(capture, manifestHash(capture), snapshot)

        then:
        new File(snapshot, 'predictions.tsv').text == 'frozen'
        new File(snapshot, 'manifest.json').exists()
        !new File(snapshot, 'inputs.zip').exists()
        record.omitted['inputs.zip'] == RookieProspectiveCapture.sha256(new File(capture, 'inputs.zip'))
        ResearchSnapshot.verify(snapshot).capture == capture.name
    }

    def 'a snapshot edited after export no longer verifies'() {
        given:
        def capture = newCapture()
        def snapshot = new File(temporary, 'snapshot')
        ResearchSnapshot.export(capture, manifestHash(capture), snapshot)

        when:
        new File(snapshot, 'predictions.tsv').text = 'rewritten'
        ResearchSnapshot.verify(snapshot)

        then:
        thrown(IOException)
    }

    /**
     * The failure the record exists to prevent: a snapshot that reads as complete because its own hashes
     * agree with each other, while an artifact the capture held is neither committed nor accounted for.
     */
    def 'a snapshot that drops an artifact is refused rather than read as complete'() {
        given:
        def capture = newCapture()
        def snapshot = new File(temporary, 'snapshot')
        ResearchSnapshot.export(capture, manifestHash(capture), snapshot)
        def file = new File(snapshot, ResearchSnapshot.RECORD)
        def record = new JsonSlurper().parse(file)

        when:
        record.omitted.remove('inputs.zip')
        new File(snapshot, 'predictions.tsv').delete()
        record.committed.remove('predictions.tsv')
        file.text = JsonOutput.toJson(record)
        ResearchSnapshot.verify(snapshot)

        then:
        thrown(IOException)
    }

    def 'a capture whose manifest has moved is never exported'() {
        given:
        def capture = newCapture()
        def stale = manifestHash(capture)

        when:
        new File(capture, 'manifest.json').text = new File(capture, 'manifest.json').text + '\n'
        ResearchSnapshot.export(capture, stale, new File(temporary, 'snapshot'))

        then:
        thrown(IOException)
    }

    def 'a local capture is matched against the record, including the archive Git does not hold'() {
        given:
        def capture = newCapture()
        def snapshot = new File(temporary, 'snapshot')
        ResearchSnapshot.export(capture, manifestHash(capture), snapshot)
        def other = newCapture('a different forecast')

        expect:
        ResearchSnapshot.verify(snapshot, capture)

        when:
        ResearchSnapshot.verify(snapshot, other)

        then:
        thrown(IOException)
    }
}
