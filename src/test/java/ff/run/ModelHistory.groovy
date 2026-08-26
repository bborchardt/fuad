package ff.run

/**
 * A commit this repository actually has, found at run time rather than written down.
 *
 * <b>Why this exists.</b> Two checks are about a model having moved, and both need a real commit with real
 * model changes between it and HEAD to be about anything. They used to name one: {@code e440d47} in one
 * spec and {@code 86277a2} in the other, each chosen because it was a large change to the curve at the time.
 *
 * Neither commit is in this repository any more. The history was rewritten at some point — it ends in three
 * separate "Initial commit." entries — and every sha written into the source was orphaned with it. So both
 * specs went on failing, and worse than failing: they exercised the <b>missing commit</b> path while
 * claiming to assert the <b>superseded model</b> one, so the guarantee they name was never checked at all.
 *
 * A sha in a spec is a fact about the repository, and a fact about the repository is exactly the kind that
 * goes stale without anything noticing. Asking git is the version that cannot.
 */
class ModelHistory {

    /**
     * What counts as the model, mirroring {@code ReportManifest.MODEL_PATHS}.
     *
     * Duplicated rather than exposed. A test asserting against the production constant would pass whatever
     * that constant said, including nothing at all, and the point here is to be an independent statement of
     * where the model lives.
     */
    private static final List<String> MODEL_PATHS = ['src/main', 'pom.xml'].asImmutable()

    /** How far back to look before giving up, which is far more than the answer has ever needed. */
    private static final int SEARCH_DEPTH = 50

    /**
     * A commit under which the model has since moved: one this repository has, with changes under
     * {@link #MODEL_PATHS} between it and HEAD.
     *
     * Taken from the commits that touched the model, newest first, skipping any whose tree still matches
     * HEAD's. The most recent one usually does match — it is often HEAD itself — so the answer is normally
     * the one before it. Asking git for the diff rather than assuming it also means a change that was later
     * reverted exactly cannot be mistaken for one that stands.
     */
    static String supersededModel() {
        List<String> touched = (git(['log', '--format=%h', '-n', SEARCH_DEPTH as String, '--'] + MODEL_PATHS)
                ?: '').readLines()*.trim().findAll()
        String superseded = touched.find { String sha ->
            git(['diff', '--name-only', sha, 'HEAD', '--'] + MODEL_PATHS)
        }
        if (!superseded) {
            throw new IllegalStateException(
                    'No commit found with model changes between it and HEAD, across the last ' +
                            "$SEARCH_DEPTH commits touching $MODEL_PATHS. These specs assert that a model " +
                            'having moved is detected, which needs a commit it has moved since — a shallow ' +
                            'clone or a repository with a single model commit cannot answer.')
        }
        superseded
    }

    /** The trimmed output, or null where git could not answer or answered with nothing. */
    private static String git(List<String> args) {
        try {
            Process process = new ProcessBuilder(['git'] + args).start()
            String output = process.inputStream.text.trim()
            process.waitFor()
            process.exitValue() == 0 && output ? output : null
        } catch (Exception ignored) {
            null
        }
    }
}
