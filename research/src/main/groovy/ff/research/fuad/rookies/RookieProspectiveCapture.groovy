package ff.research.fuad.rookies

import ff.projection.fuad.*

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import ff.league.League

/** Append-only-by-creation local snapshot. Not a trusted timestamp or tamper-proof external archive. */
class RookieProspectiveCapture {
    static final Instant ANNOUNCED_KICKOFF = Instant.parse('2026-09-10T00:20:00Z')
    static final String SCHEDULE_SOURCE = 'https://amp.nfl.com/news/seahawks-to-kick-off-2026-nfl-regular-season-on-wednesday-sept-9-in-seattle'
    static String sha256(File file) {
        def digest = MessageDigest.getInstance('SHA-256')
        file.withInputStream { input ->
            byte[] buffer = new byte[65536]
            int read
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read)
        }
        digest.digest().encodeHex().toString()
    }

    static File reserve(File directory) {
        Files.createDirectories(directory.toPath().parent)
        Files.createDirectory(directory.toPath()) // Refuse existing paths, even empty ones.
        directory
    }

    static String git(File root, List<String> arguments) {
        def process = new ProcessBuilder(['git'] + arguments).directory(root).redirectErrorStream(true).start()
        String output = process.inputStream.getText('UTF-8')
        if (process.waitFor() != 0) throw new IOException("Git provenance command failed: $arguments")
        output.trim()
    }

    static Map<String, File> inputFiles(File root) {
        Map<String, File> files = [:]
        ['src', '.mvn'].each { dir ->
            Files.walk(new File(root, dir).toPath()).withCloseable { paths ->
                paths.filter { Files.isRegularFile(it) }.forEach { path ->
                    if (Files.isSymbolicLink(path)) throw new IOException("Symlink input cannot be captured: $path")
                    files[root.toPath().relativize(path).toString()] = path.toFile()
                }
            }
        }
        root.listFiles().findAll { it.isFile() && (it.name.endsWith('.sh') || it.name in ['pom.xml', 'mvnw', '.sdkmanrc']) }.each {
            files[it.name] = it
        }
        files.sort()
    }

    static Map hashes(Map<String, File> files) { files.collectEntries { path, file -> [(path): sha256(file)] } }

    static void verifyUnchanged(Map<String, File> files, Map expected) {
        if (hashes(files) != expected) throw new IOException('Inputs changed during capture; no complete manifest will be written')
    }

    static Map verifyCapture(File directory) {
        def manifest = new JsonSlurper().parse(new File(directory, 'manifest.json'))
        if (manifest.status != 'COMPLETE' || !manifest.artifactHashes) throw new IOException('Incomplete capture')
        manifest.artifactHashes.each { name, expected ->
            if (new File(name).name != name || sha256(new File(directory, name)) != expected) {
                throw new IOException("Capture artifact failed integrity check: $name")
            }
        }
        manifest
    }

    static void main(String[] args) {
        if (args.size() == 2 && args[0] == '--verify') {
            verifyCapture(new File(args[1]))
            println 'Capture artifact hashes verified against its local manifest (not an external authenticity guarantee).'
            return
        }
        if (args.size() > 1) throw new IllegalArgumentException('Usage: ./research/run.sh prospective-capture [new-output-directory]')
        int cutoff = League.FUAD.seasons*.toInteger().max() + 1
        if (cutoff != 2026) throw new IllegalStateException('Review rules and capture protocol before advancing beyond 2026')
        File root = new File('.').canonicalFile
        String started = Instant.now().toString()
        File directory = reserve(new File(args ? args[0] : "reports/fuad/captures/2026-${UUID.randomUUID()}").absoluteFile)
        def files = inputFiles(root)
        def sourceHashes = hashes(files)
        String revision = git(root, ['rev-parse', 'HEAD'])
        String status = git(root, ['status', '--porcelain=v1', '--untracked-files=all'])
        new File(directory, 'working-tree.patch').setText(git(root, ['diff', '--binary', 'HEAD']), 'UTF-8')
        new File(directory, 'inputs.zip').withOutputStream { output ->
            new ZipOutputStream(output).withCloseable { zip ->
                files.each { path, file ->
                    zip.putNextEntry(new ZipEntry(path))
                    file.withInputStream { zip << it }
                    zip.closeEntry()
                }
            }
        }
        def study = new RookieChronologicalStudy(RookieBacktest.observations())
        def result = study.prospective(cutoff)
        def columns = [predictions: RookieChronologicalStudy.COLUMNS.predictions - ['actual'],
                       selection: RookieChronologicalStudy.COLUMNS.selection]
        RookieChronologicalStudy.writeReports(result, directory, columns)
        new File(directory, 'replacement.json').text = JsonOutput.prettyPrint(JsonOutput.toJson(result.replacement))
        def dependencyFiles = System.getProperty('java.class.path').split(File.pathSeparator).collect { new File(it) }.findAll { it.isFile() }
        verifyUnchanged(files, sourceHashes)
        if (hashes(inputFiles(root)) != sourceHashes || git(root, ['rev-parse', 'HEAD']) != revision ||
                git(root, ['status', '--porcelain=v1', '--untracked-files=all']) != status) {
            throw new IOException('Repository changed during capture; no complete manifest will be written')
        }
        def artifacts = directory.listFiles().findAll { it.isFile() }.collectEntries { [(it.name): sha256(it)] }
        Instant completed = Instant.now()
        def manifest = [schemaVersion: 1, status: 'COMPLETE', captureStartedUtc: started,
                        captureCompletedUtc: completed.toString(), forecastClass: cutoff,
                        trainingOutcomeSeasonExclusiveUpperBound: cutoff,
                        timingEligibility: completed.isBefore(ANNOUNCED_KICKOFF) ? 'BEFORE_ANNOUNCED_REGULAR_SEASON_KICKOFF' : 'NOT_BEFORE_ANNOUNCED_REGULAR_SEASON_KICKOFF',
                        announcedKickoffUtc: ANNOUNCED_KICKOFF.toString(), scheduleSource: SCHEDULE_SOURCE,
                        scheduleVerifiedOn: '2026-09-06', draftTimeEligibility: 'UNVERIFIED',
                        historicalInputAvailability: 'UNVERIFIED — archived rankings and revised statistics',
                        scope: 'QB annual events, contract years 1–5; CURRENT means rookie-only, not full production valuation',
                        rules: 'FUAD_2026 scoring; 2026 lineup; 13 playable weeks; fixed throughout training',
                        events: ['VOR > 0', 'VOR >= 26'], models: ['CURRENT', 'FREQUENCY', 'NESTED'],
                        localKernel: 'exp(-abs(donorRank-targetRank))', localWeightGrid: RookieEventCalibration.STRENGTHS,
                        revision: revision, workingTreeStatus: status, dirty: !status.isEmpty(),
                        javaVersion: System.getProperty('java.version'), inputHashes: sourceHashes,
                        dependencyHashes: dependencyFiles.collectEntries { [(it.name): sha256(it)] }, artifactHashes: artifacts]
        // Written last: absence of this file means the capture is incomplete and must not be used.
        new File(directory, 'manifest.json').text = JsonOutput.prettyPrint(JsonOutput.toJson(manifest))
        println "Complete local capture: ${directory.absolutePath}"
        println "Timing: ${manifest.timingEligibility}; draft-time eligibility UNVERIFIED. Existing capture directories are never overwritten."
    }
}
