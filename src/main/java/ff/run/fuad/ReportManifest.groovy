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
 * by an earlier one. A model with uncommitted changes under {@link #MODEL_PATHS} is marked, because the sha
 * alone does not then describe what ran. See docs/STRATEGY.md.
 */
class ReportManifest {

    static final String FILE_NAME = 'MANIFEST'

    /**
     * What counts as the model, for the purpose of saying whether it has changed.
     *
     * <b>Main sources and the build, and deliberately not the tests.</b> Every generated file here is
     * written by a runner launched against {@code target/classes} — see generate_report.sh and
     * figures_refresh.sh, which put nothing else on the classpath — so test code cannot reach a report or a
     * figure even in principle. Counting it meant a commit that only added a spec reported the model as
     * moved, and the fix was to regenerate figures that could not have changed and commit the new stamp:
     * ceremony that teaches a reader to run the refresh without reading what it did.
     *
     * pom.xml stays in, because what is compiled and what it is compiled against are both things a figure
     * can turn on.
     */
    private static final List<String> MODEL_PATHS = ['src/main', 'pom.xml'].asImmutable()

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
        modelIsDirty() ? "$sha-dirty" : sha
    }

    /**
     * True where the model has uncommitted changes, so no sha describes what would run.
     *
     * <b>A git that cannot answer counts as dirty.</b> {@link #git} returns null where the command failed
     * and an empty string where it succeeded and found nothing, and Groovy truth flattens those to the same
     * false — so an unanswerable question used to report a clean model, which is the one answer that lets a
     * check pass. The guard exists to refuse a comparison it cannot stand behind, and not knowing is not the
     * same as knowing there is nothing there.
     */
    static boolean modelIsDirty() {
        String changed = git(['status', '--porcelain', '--'] + MODEL_PATHS as String[])
        changed == null || !changed.isEmpty()
    }

    /**
     * Whether anything the model is built from has changed between that commit and the working tree.
     *
     * <b>A sha is not compared against HEAD, because that would be the wrong question.</b> A generated file
     * is stamped with HEAD as it was when it was written, and committing it moves HEAD past that — so the
     * stamp names the parent commit of the one holding the file, always, and a check for equality would
     * fail every time. What matters is not which commit wrote the figures but whether the model has moved
     * since, and a commit touching only documentation, figures or tests has moved nothing — see
     * {@link #MODEL_PATHS}.
     *
     * @return true where the model moved, false where it did not, null where the sha cannot be resolved
     *         and so nothing can be said either way
     */
    static Boolean modelMovedSince(String sha) {
        if (!sha) {
            return null
        }
        String changed = git(['diff', '--name-only', sha, 'HEAD', '--'] + MODEL_PATHS as String[])
        changed == null ? null : !changed.isEmpty()
    }

    /** The trimmed output, or null where git could not answer — which is not the same as an empty answer. */
    private static String git(String... args) {
        try {
            Process process = new ProcessBuilder(['git'] + (args as List<String>)).start()
            String output = process.inputStream.text.trim()
            process.waitFor()
            process.exitValue() == 0 ? output : null
        } catch (Exception ignored) {
            null
        }
    }
}
