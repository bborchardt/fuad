package ff.research.fuad.rookies

import ff.projection.fuad.*

import ff.load.fantasypros.FantasyProsLoader
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.nio.file.Files
import java.time.Instant
import java.util.zip.ZipFile

/** Outcome-blind cohort checks against the original capture, never today's resource files. */
class RookieCaptureAudit {
    static Map verified(File capture, String expectedManifest) {
        if (!(expectedManifest ==~ /[a-f0-9]{64}/) ||
                RookieProspectiveCapture.sha256(new File(capture, 'manifest.json')) != expectedManifest) {
            throw new IOException('Manifest does not match the separately recorded SHA-256')
        }
        RookieProspectiveCapture.verifyCapture(capture)
    }

    static String archived(ZipFile zip, String path, Map manifest) {
        def entry = zip.getEntry(path)
        if (!entry || !manifest.inputHashes[path]) throw new IOException("Missing captured input: $path")
        byte[] bytes = zip.getInputStream(entry).withCloseable { it.bytes }
        String hash = java.security.MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
        if (hash != manifest.inputHashes[path]) throw new IOException("Captured input hash mismatch: $path")
        new String(bytes, 'UTF-8')
    }

    static Map match(String name, List<Map> players, String year) {
        def parts = name.tokenize(' ')
        String last = parts.removeLast()
        String expected = "$last, ${parts.join(' ')}"
        def found = players.findAll { it.position == 'QB' && it.name.equalsIgnoreCase(expected) }
        def player = found.size() == 1 ? found.first() : null
        [status: found.size() > 1 ? 'AMBIGUOUS' : !player ? 'UNMATCHED' :
                player.draft_year == year ? 'MATCHED_ENTRY_YEAR' : 'OTHER_ENTRY_YEAR',
         mflId: player?.id, entryYear: player?.draft_year]
    }

    static Map audit(File capture, String expectedManifest, File output) {
        def manifest = verified(capture, expectedManifest)
        String year = manifest.forecastClass.toString()
        List<Map> predictions = new File(capture, 'predictions.tsv').readLines('UTF-8').with { lines ->
            def columns = lines.first().split('\t', -1).toList()
            lines.tail().collect { line -> [columns, line.split('\t', -1).toList()].transpose().collectEntries { it } }
        }
        List<Map> rows
        new ZipFile(new File(capture, 'inputs.zip')).withCloseable { zip ->
            def players = new JsonSlurper().parseText(archived(zip, "src/main/resources/ff/mfl/data/$year/players.json", manifest)).players.player
            def ranking = new FantasyProsLoader().loadRankedPlayers(archived(zip,
                    "src/main/resources/ff/fantasypros/data/$year/rookie_rankings_ppr.csv", manifest).readLines())
                    .values().findAll { it.player.position == 'QB' }
            if ((predictions*.name as Set) != (ranking*.player*.name as Set)) throw new IOException('Forecast cohort differs from captured ranking')
            rows = ranking.collect { ranked ->
                def at = predictions.findAll { it.name == ranked.player.name }
                def expectedGrid = (1..5).collectMany { yearNumber -> RookieEventStudy.EVENTS.collectMany { event ->
                    ['CURRENT', 'FREQUENCY', 'NESTED'].collect { model -> ["$yearNumber".toString(), event, model] }
                } } as Set
                if (at.size() != 30 || (at.collect { [it.year, it.event, it.model] } as Set) != expectedGrid ||
                        at.any { it.overallRank.toInteger() != ranked.rank.overallRank || it.rank.toInteger() != ranked.rank.positionRank }) {
                    throw new IOException("Incomplete or inconsistent forecast grid: ${ranked.player.name}")
                }
                [name: ranked.player.name, rank: ranked.rank.positionRank, overallRank: ranked.rank.overallRank,
                 primary: ranked.rank.overallRank <= 50, forecastRows: at.size(), supportedRows: at.count { it.probability }] +
                        match(ranked.player.name, players, year)
            }
        }
        RookieProspectiveCapture.reserve(output.absoluteFile)
        def columns = 'name rank overallRank primary forecastRows supportedRows status mflId entryYear'.tokenize()
        RookieChronologicalStudy.writeReports([cohort: rows], output, [cohort: columns])
        def report = [auditCompletedUtc: Instant.now().toString(), capture: capture.name, manifestSha256: expectedManifest,
                      rule: 'Exact case-insensitive first/last-name reversal, QB position, captured MFL draft_year; no outcome or current roster filter',
                      cohortSize: rows.size(), primarySize: rows.count { it.primary }, statuses: rows.countBy { it.status },
                      primaryStatuses: rows.findAll { it.primary }.countBy { it.status },
                      decision: 'Original predictions unchanged; unmatched means unresolved, not a zero-game outcome or automatic exclusion',
                      cohortHash: RookieProspectiveCapture.sha256(new File(output, 'cohort.tsv'))]
        new File(output, 'audit.json').text = JsonOutput.prettyPrint(JsonOutput.toJson(report))
        report
    }

    static void backup(File capture, String expectedManifest, File destination) {
        def manifest = verified(capture, expectedManifest)
        RookieProspectiveCapture.reserve(destination.absoluteFile)
        (manifest.artifactHashes.keySet().toList() + ['manifest.json']).each { name ->
            Files.copy(new File(capture, name).toPath(), new File(destination, name).toPath())
        }
        verified(destination, expectedManifest)
    }

    static void main(String[] args) {
        if (args.size() != 4 || !(args[0] in ['audit', 'backup'])) {
            throw new IllegalArgumentException('Usage: ./research/run.sh capture-audit audit|backup capture-directory expected-manifest-sha256 new-output-directory')
        }
        if (args[0] == 'backup') {
            backup(new File(args[1]), args[2], new File(args[3]))
            println 'Verified copy created. A same-device copy is not off-device disaster recovery.'
        } else println audit(new File(args[1]), args[2], new File(args[3]))
    }
}
