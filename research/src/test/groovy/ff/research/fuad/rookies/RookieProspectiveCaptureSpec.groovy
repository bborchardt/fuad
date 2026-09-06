package ff.research.fuad.rookies

import ff.projection.fuad.*

import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.FileAlreadyExistsException
import groovy.json.JsonOutput

class RookieProspectiveCaptureSpec extends Specification {
    @TempDir File temporary

    def 'existing captures cannot be overwritten'() {
        given:
        def target = new File(temporary, 'capture')
        RookieProspectiveCapture.reserve(target)

        when:
        RookieProspectiveCapture.reserve(target)

        then:
        thrown(FileAlreadyExistsException)
    }

    def 'input hashes detect changes during capture'() {
        given:
        def file = new File(temporary, 'input')
        file.text = 'original'
        def files = [input: file]
        def before = RookieProspectiveCapture.hashes(files)
        RookieProspectiveCapture.verifyUnchanged(files, before)

        when:
        file.text = 'changed'
        RookieProspectiveCapture.verifyUnchanged(files, before)

        then:
        thrown(IOException)
    }

    def 'capture verifier rejects altered forecast artifacts'() {
        given:
        def artifact = new File(temporary, 'predictions.tsv')
        artifact.text = 'original'
        new File(temporary, 'manifest.json').text = JsonOutput.toJson([status: 'COMPLETE',
                artifactHashes: ['predictions.tsv': RookieProspectiveCapture.sha256(artifact)]])
        RookieProspectiveCapture.verifyCapture(temporary)

        when:
        artifact.text = 'altered'
        RookieProspectiveCapture.verifyCapture(temporary)

        then:
        thrown(IOException)
    }
}
