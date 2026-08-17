package ff.run.fuad

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Stamps a report directory with the model that produced each report.
 *
 * A strategy document reasons from a board, and a board is only meaningful alongside the model that
 * produced it. Reports are not committed, so nothing else records which one a given file came from: the
 * points curve was relevelled in one commit and every quarterback price moved by half, with no way to tell
 * an old board from a new one by looking at it.
 *
 * One line per report type, so a run of `-t salaries` does not erase the stamp on a `teams` report written
 * by an earlier one. A model with uncommitted changes under `src` is marked, because the sha alone does not
 * then describe what ran. See docs/STRATEGY.md.
 */
class ReportManifest {

    static final String FILE_NAME = 'MANIFEST'

    private static final String DEFAULT_WRITER = 'generate_report.sh'

    private static String header(String writtenBy) {
        "# Written by $writtenBy: which model produced each file here. See docs/STRATEGY.md."
    }

    /** What produced one report: the model sha, and when it was written. */
    static class Stamp {
        String type
        String model
        String generated

        boolean isDirty() { model?.endsWith('-dirty') }

        /** The sha alone, without the dirty marker. */
        String sha() { model?.replaceFirst(/-dirty$/, '') }

        @Override
        String toString() { "$type $model $generated" }
    }

    /** Record that these report types were just written from the working tree's model. */
    static void stamp(File outputDir, List<String> types, String writtenBy = DEFAULT_WRITER) {
        String model = currentModel()
        String generated = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
        Map<String, Stamp> stamps = read(outputDir)
        types.each { String type ->
            stamps[type] = new Stamp(type: type, model: model, generated: generated)
        }
        new File(outputDir, FILE_NAME).withPrintWriter { out ->
            out.println(header(writtenBy))
            stamps.keySet().sort().each { out.println(stamps[it].toString()) }
        }
    }

    /** Stamps by report type, empty when the directory has never been stamped. */
    static Map<String, Stamp> read(File outputDir) {
        File file = new File(outputDir, FILE_NAME)
        if (!file.exists()) {
            return [:]
        }
        Map<String, Stamp> stamps = [:]
        file.eachLine { String line ->
            String trimmed = line.trim()
            if (trimmed && !trimmed.startsWith('#')) {
                List<String> fields = trimmed.split(/\s+/) as List
                if (fields.size() >= 3) {
                    stamps[fields[0]] = new Stamp(type: fields[0], model: fields[1], generated: fields[2])
                }
            }
        }
        stamps
    }

    /** The working tree's short sha, marked when anything the model is built from is uncommitted. */
    static String currentModel() {
        String sha = git('rev-parse', '--short', 'HEAD')
        if (!sha) {
            return 'unknown'
        }
        git('status', '--porcelain', '--', 'src', 'pom.xml') ? "$sha-dirty" : sha
    }

    private static String git(String... args) {
        try {
            Process process = new ProcessBuilder(['git'] + (args as List<String>)).start()
            String output = process.inputStream.text.trim()
            process.waitFor()
            process.exitValue() == 0 ? output : ''
        } catch (Exception ignored) {
            ''
        }
    }
}
