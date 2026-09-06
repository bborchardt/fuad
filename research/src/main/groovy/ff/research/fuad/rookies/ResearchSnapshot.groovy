package ff.research.fuad.rookies

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.file.Files
import java.time.Instant

/**
 * A local capture, reduced to the part Git should hold.
 *
 * A capture under reports/ is ignored by Git and lives on one machine, so the roadmap's durable forecast
 * record described a directory that did not exist. This writes that record under research/snapshots/,
 * where it is committed, reviewed and distributed like anything else in the tree.
 *
 * <b>What is left behind is inputs.zip</b>: megabytes of source files this repository already stores at
 * the revision the manifest names. Nothing verifiable is lost by omitting it — manifest.json carries a
 * SHA-256 for every captured input, so the committed record still identifies the inputs exactly, and the
 * zip's own hash is recorded here so a surviving local copy can be matched against the record. An export
 * that silently dropped an artifact would be a worse record than none, so {@link #verify} refuses a
 * snapshot whose committed and omitted files do not together account for every artifact the manifest names.
 *
 * <b>Git history is not an independent timestamp.</b> A commit date is written by whoever makes the commit.
 * What this adds to a local capture is distribution and tamper-evidence within a shared history, not proof
 * that a forecast existed on the day it claims — that remains UNVERIFIED, as the capture manifest says.
 */
class ResearchSnapshot {

    /** Recorded by hash and not stored. See the class note: the manifest already hashes every input. */
    static final List<String> OMITTED = ['inputs.zip'].asImmutable()

    static final String RECORD = 'snapshot.json'

    static final String OMITTED_REASON =
            'Stored by SHA-256 only. manifest.json hashes every captured input individually, so the ' +
                    'committed record identifies the inputs without carrying a second copy of the tree.'

    static final String PROVENANCE =
            'Committed to Git for distribution and tamper-evidence within a shared history. A commit date ' +
                    'is not an independent trusted timestamp, and draft-time eligibility remains UNVERIFIED.'

    /** The capture, verified against a separately recorded manifest hash, written where Git will keep it. */
    static Map export(File capture, String expectedManifest, File snapshot) {
        Map manifest = RookieCaptureAudit.verified(capture, expectedManifest)
        Map artifacts = manifest.artifactHashes
        List<String> absent = OMITTED.findAll { !artifacts.containsKey(it) }
        if (absent) {
            throw new IOException("Capture does not hold the artifacts this export omits: $absent")
        }
        List<String> committed = ((artifacts.keySet() - OMITTED).toList() + ['manifest.json']).sort()
        RookieProspectiveCapture.reserve(snapshot.absoluteFile)
        committed.each { Files.copy(new File(capture, it).toPath(), new File(snapshot, it).toPath()) }
        Map record = [schemaVersion: 1, exportedUtc: Instant.now().toString(), capture: capture.name,
                      manifestSha256: expectedManifest,
                      committed: committed.collectEntries { [(it): RookieProspectiveCapture.sha256(new File(snapshot, it))] },
                      omitted: OMITTED.collectEntries { [(it): artifacts[it]] },
                      omittedReason: OMITTED_REASON, provenance: PROVENANCE]
        new File(snapshot, RECORD).text = JsonOutput.prettyPrint(JsonOutput.toJson(record))
        verify(snapshot)
    }

    /**
     * Whether the snapshot is still what was exported, and still the whole of what was captured.
     *
     * Hashes are checked against the record and against the manifest, because those are two different
     * questions: the first asks whether the committed files have been edited since the export, the second
     * whether the export ever agreed with the capture it claims to come from.
     *
     * @param capture an optional surviving local capture, whose omitted artifacts are matched too
     */
    static Map verify(File snapshot, File capture = null) {
        Map record = new JsonSlurper().parse(new File(snapshot, RECORD))
        record.committed.each { String name, String expected ->
            File file = new File(snapshot, name)
            if (new File(name).name != name || !file.isFile() || RookieProspectiveCapture.sha256(file) != expected) {
                throw new IOException("Snapshot file failed integrity check: $name")
            }
        }
        if (record.committed['manifest.json'] != record.manifestSha256) {
            throw new IOException('Snapshot manifest does not match the SHA-256 it was exported against')
        }
        Map manifest = new JsonSlurper().parse(new File(snapshot, 'manifest.json'))
        Set<String> accounted = (record.committed.keySet() + record.omitted.keySet()) - ['manifest.json']
        if (accounted != manifest.artifactHashes.keySet()) {
            throw new IOException('Snapshot does not account for every artifact the capture manifest names')
        }
        (record.committed + record.omitted).each { String name, String expected ->
            if (name != 'manifest.json' && manifest.artifactHashes[name] != expected) {
                throw new IOException("Snapshot hash disagrees with the capture manifest: $name")
            }
        }
        if (capture) {
            record.omitted.each { String name, String expected ->
                if (RookieProspectiveCapture.sha256(new File(capture, name)) != expected) {
                    throw new IOException("Local capture does not match the snapshot record: $name")
                }
            }
        }
        record
    }

    /** A new forecast capture and its committed record in one step, for the season after this one. */
    static Map capture(File capture, File snapshot) {
        RookieProspectiveCapture.main([capture.path] as String[])
        return export(capture, RookieProspectiveCapture.sha256(new File(capture, 'manifest.json')), snapshot)
    }

    static final String USAGE =
            'Usage: ./research/run.sh snapshot-export <capture-directory> <expected-manifest-sha256> <new-snapshot-directory>\n' +
                    '       ./research/run.sh snapshot-verify <snapshot-directory> [<local-capture-directory>]\n' +
                    '       ./research/run.sh snapshot-capture <new-capture-directory> <new-snapshot-directory>'

    static void main(String[] args) {
        switch (args ? args[0] : '') {
            case 'export':
                if (args.size() != 4) throw new IllegalArgumentException(USAGE)
                Map exported = export(new File(args[1]), args[2], new File(args[3]))
                println "Snapshot written to ${new File(args[3]).absolutePath}; commit it."
                println "Stored by hash only: ${exported.omitted.keySet().join(', ')}. $PROVENANCE"
                break
            case 'verify':
                if (!(args.size() in [2, 3])) throw new IllegalArgumentException(USAGE)
                Map record = verify(new File(args[1]), args.size() == 3 ? new File(args[2]) : null)
                println "Snapshot of capture ${record.capture} verified against its own record and manifest."
                println args.size() == 3 ? 'The local capture matches it too.' : PROVENANCE
                break
            case 'capture':
                if (args.size() != 3) throw new IllegalArgumentException(USAGE)
                Map exported = capture(new File(args[1]), new File(args[2]))
                println "Snapshot written to ${new File(args[2]).absolutePath}; commit it."
                println "Record this manifest SHA-256 separately: $exported.manifestSha256"
                break
            default: throw new IllegalArgumentException(USAGE)
        }
    }
}
