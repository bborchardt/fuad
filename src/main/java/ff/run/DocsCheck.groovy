package ff.run

import ff.run.fuad.ReportManifest

/**
 * Hold the model's documentation to the figures the model actually produces.
 *
 * docs/STRATEGY.md requires a draft plan to cite a generated board rather than remember one, on the grounds
 * that "nothing about a stale figure looks stale" and that a document wrong in one section and right in the
 * next gives no sign of which is which. Both are true of the documentation itself, which quotes some 221
 * figures about the model and — until this existed — had nothing checking any of them. They drifted exactly
 * as predicted: a table that no longer multiplied out, an example that came to say the opposite of what the
 * curve shows, an availability range four files went on quoting after the curve had left it.
 *
 * So the same discipline, turned inward. A table marked with a source is checked cell by cell against
 * docs/figures, and a figure that moves fails the check in the commit that moves it.
 *
 * <b>Three things are reported, because passing has three meanings and only one of them is good.</b>
 *
 * <b>Provenance</b> comes first, once, for the whole run: the figures must have been written by the model
 * that is checked out. Without that this check compares prose against figures of any age and calls the
 * agreement a pass — the two agree, the model has moved on from both, and nothing says so. It is the same
 * question {@link StrategyCheck} asks of a board before holding a plan to it.
 *
 * <b>Figures</b> are then checked cell by cell, which is the part this was written for.
 *
 * <b>Coverage</b> is reported rather than assumed. A document with no marked table produces no failures, and
 * so does a document whose every figure is right; printing OK for both is the drift problem wearing the word
 * OK. A document nothing was checked in is reported as {@code NONE}, and one that was checked says how many
 * figures it was held to. That is not a failure — DATA.md and LEAGUE_RULES.md describe the league's files
 * and rules rather than the model's output, and legitimately cite no generated figure — but it must never
 * read as though it were verified.
 *
 * <b>The boundary rule is deliberately not applied here.</b> A plan may not name a model internal, because
 * reaching behind the board is how a plan argues a premium the board already priced. Documentation of the
 * model is nothing but names of model internals. That check is right for plans and would be nonsense here.
 *
 * Two orientations, because prose wants both.
 *
 * <pre>
 * &lt;!-- figures: positions --&gt;                        first column keys a row, headings name fields
 * &lt;!-- figures: curve across=POS field=PTS --&gt;       headings are positions, first column the other key
 * </pre>
 *
 * A heading matching no field is the document's own commentary and is left alone, so a table can carry a
 * note or a figure the model does not produce — what the league actually paid, say — beside the ones it
 * does.
 */
class DocsCheck {

    private static final String FIGURES_MARKER = /<!--\s*figures:\s*([^>]*?)\s*-->/

    /**
     * What checking a document came to: where it disagreed, and how much was actually looked at.
     *
     * The count is here because a document with nothing to check and a document whose every figure is
     * correct both produce no failures, and reporting them the same way is the drift problem wearing the
     * word OK. See {@link #main}.
     */
    static class Result {
        final List<String> failures
        /** Cells compared against a figure. A heading the figures do not carry is commentary and is not one. */
        final int verified
        /** Tables under a marker, whether or not any of their cells matched a field. */
        final int tables

        Result(List<String> failures, int verified, int tables) {
            this.failures = failures
            this.verified = verified
            this.tables = tables
        }
    }

    static void main(String[] args) {
        if (!args) {
            System.err.println('Usage: DocsCheck <year> <document.md> [<document.md> ...] [figures-dir]')
            System.exit(2)
        }
        String year = args[0]
        File figuresDir = new File('docs/figures')
        List<File> documents = args.drop(1).collect { new File(it) }
        if (documents && documents.last().isDirectory()) {
            figuresDir = documents.last()
            documents = documents.dropRight(1)
        }
        if (!documents) {
            System.err.println('No documents given')
            System.exit(2)
        }

        File yearDir = new File(figuresDir, year)
        boolean failed = false

        // Before any document, the figures themselves. Checking prose against figures an older model wrote
        // proves only that the prose matches something, which is the question nobody asked.
        List<String> stale = checkProvenance(yearDir)
        if (stale) {
            println "FAIL  $yearDir"
            stale.each { println "  $it" }
            System.exit(1)
        }

        documents.each { File document ->
            if (!document.exists()) {
                println "FAIL  $document"
                println '  no such document'
                failed = true
                return
            }
            Result result = inspect(document, yearDir)
            if (result.failures) {
                println "FAIL  $document"
                result.failures.each { println "  $it" }
                failed = true
            } else if (!result.tables) {
                // Not a failure: a document about the league's rules or its data files legitimately cites no
                // model figure. But it must not read as though something was verified here, because nothing was.
                println "NONE  $document"
                println '  no <!-- figures: --> marker, so nothing in this document is checked'
            } else {
                println "OK    $document  ($result.verified figures in $result.tables tables)"
            }
        }
        if (failed) {
            System.exit(1)
        }
    }

    /**
     * Whether the figures still describe the model that is checked out.
     *
     * The same question {@link StrategyCheck} asks of a board before holding a plan to it, asked here of the
     * figures before holding the documentation to them. Without it this check passes happily against figures
     * of any age: the prose and the figures agree, the model has moved on from both, and nothing says so.
     *
     * <b>Not an equality test against HEAD.</b> Figures are stamped with the commit that produced them and
     * then committed, so the stamp names that commit's parent forever after. What matters is whether the
     * model has changed since — see {@link ReportManifest#modelMovedSince}.
     */
    static List<String> checkProvenance(File yearDir) {
        Map<String, ReportManifest.Stamp> stamps = ReportManifest.read(yearDir)
        if (!stamps) {
            return ["holds no ${ReportManifest.FILE_NAME}: run ./figures_refresh.sh" as String]
        }
        List<ReportManifest.Stamp> dirty = stamps.values().findAll { it.dirty }
        if (dirty) {
            return ["${dirty*.type.sort().join(', ')} came from an uncommitted model " +
                            "(${dirty.first().model}), so nothing can be checked against them — commit the " +
                            'model and run ./figures_refresh.sh' as String]
        }

        // What the stamps say, before what the working tree says: a manifest naming a commit that does not
        // exist is broken in a way no amount of committing will fix, and should be reported as itself.
        List<String> failures = []
        stamps.values().groupBy { it.sha() }.each { String sha, List<ReportManifest.Stamp> written ->
            Boolean moved = ReportManifest.modelMovedSince(sha)
            if (moved == null) {
                failures << "${written*.type.sort().join(', ')} name model $sha, which this repository " +
                        'does not have — regenerate them'
            } else if (moved) {
                failures << "${written*.type.sort().join(', ')} were written by $sha and the model has " +
                        'moved since: run ./figures_refresh.sh'
            }
        }
        if (failures) {
            return failures
        }

        // Last, because it is the one thing the stamps cannot see. The figures may be exactly what their
        // commit produced and still not be what the model would produce now.
        ReportManifest.modelIsDirty() ?
                ['the model has uncommitted changes under src, so the figures are not what it would now ' +
                         'produce — commit it and run ./figures_refresh.sh'] : []
    }

    /** Every way the document disagrees with the figures, in the order they appear. */
    static List<String> check(File document, File yearDir) {
        inspect(document, yearDir).failures
    }

    /** The same, alongside how much of the document was actually held to anything. */
    static Result inspect(File document, File yearDir) {
        List<String> lines = document.readLines()
        List<String> failures = []
        Map<String, List<Map<String, String>>> cache = [:]
        int verified = 0
        int tables = 0

        Map<String, String> marker = null
        List<String> headings = null
        for (int i = 0; i < lines.size(); i++) {
            String line = lines[i]
            def matcher = line =~ FIGURES_MARKER
            if (matcher.find()) {
                marker = parseMarker(matcher.group(1))
                headings = null
                continue
            }
            if (!marker) {
                continue
            }
            if (!MarkdownTables.isTableRow(line)) {
                // A blank line between the marker and its table is fine; prose ends the table.
                if (line.trim() && headings != null) {
                    marker = null
                }
                continue
            }
            List<String> cells = MarkdownTables.cells(line)
            if (headings == null) {
                headings = cells
                tables++
                continue
            }
            if (MarkdownTables.isDivider(cells)) {
                continue
            }
            Result row = checkRow(marker, yearDir, headings, cells, i + 1, cache)
            failures.addAll(row.failures)
            verified += row.verified
        }
        new Result(failures, verified, tables)
    }

    /** `curve across=POS field=PTS` into its table name and its options. */
    private static Map<String, String> parseMarker(String body) {
        List<String> parts = body.trim().split(/\s+/) as List
        Map<String, String> marker = [table: parts.first()]
        parts.drop(1).each { String part ->
            List<String> pair = part.split('=', 2) as List
            if (pair.size() == 2) {
                marker[pair[0]] = pair[1]
            }
        }
        marker
    }

    private static Result checkRow(Map<String, String> marker, File yearDir, List<String> headings,
                                   List<String> cells, int line,
                                   Map<String, List<Map<String, String>>> cache) {
        List<Map<String, String>> table = table(yearDir, marker.table, cache)
        if (table == null) {
            return new Result(["line $line: no figures file for '${marker.table}'" as String], 0, 0)
        }
        String key = MarkdownTables.normaliseKey(MarkdownTables.clean(cells ? cells[0] : null))
        if (!key) {
            return new Result([], 0, 0)
        }
        marker.across ? checkAcross(marker, table, headings, cells, key, line)
                : checkDown(marker, table, headings, cells, key, line)
    }

    /**
     * Headings name fields and the first column names the row: the plain shape.
     *
     * The row is found on whichever of the file's own columns the key matches, so a table may be keyed by
     * whatever the file's first column is without having to say so.
     */
    private static Result checkDown(Map<String, String> marker, List<Map<String, String>> table,
                                    List<String> headings, List<String> cells, String key, int line) {
        String keyColumn = table ? table.first().keySet().first() : null
        Map<String, String> row = table.find { MarkdownTables.normaliseKey(it[keyColumn]) == key }
        if (row == null) {
            return new Result(["line $line: '$key' is not in ${marker.table}.tsv" as String], 0, 0)
        }
        List<String> failures = []
        int verified = 0
        headings.eachWithIndex { String heading, int column ->
            String field = row.keySet().find { MarkdownTables.normaliseKey(it) == MarkdownTables.normaliseKey(heading) }
            if (column == 0 || column >= cells.size() || !field) {
                return
            }
            String cited = MarkdownTables.clean(cells[column])
            if (!cited) {
                return
            }
            verified++
            if (!MarkdownTables.matches(cited, row[field])) {
                failures << "line $line: $key $field is ${row[field]} in ${marker.table}.tsv, cited as $cited"
            }
        }
        new Result(failures, verified, 0)
    }

    /**
     * Headings are values of one key and every cell holds the same field: the shape prose actually wants.
     *
     * Almost every table in PROJECTION.md puts the positions across the top and reads down, because that is
     * how the comparison it is making runs. Long format would be checkable and unreadable, so this reads the
     * table the way it is written: {@code across=POS field=PTS} means the heading is the position, the first
     * column is the rank, and the cell is what that rank levels at.
     */
    private static Result checkAcross(Map<String, String> marker, List<Map<String, String>> table,
                                      List<String> headings, List<String> cells, String key, int line) {
        String field = table && table.first().containsKey(marker.field) ? marker.field : null
        if (!field) {
            return new Result(["line $line: '${marker.field}' is not a column of ${marker.table}.tsv" as String],
                    0, 0)
        }
        // Whichever column is not the one across the top is the one the first column keys on.
        String keyColumn = table.first().keySet().find { it != marker.across }
        List<String> failures = []
        int verified = 0
        headings.eachWithIndex { String heading, int column ->
            if (column == 0 || column >= cells.size()) {
                return
            }
            String cited = MarkdownTables.clean(cells[column])
            if (!cited) {
                return
            }
            String across = MarkdownTables.normaliseKey(MarkdownTables.clean(heading))
            Map<String, String> row = table.find {
                MarkdownTables.normaliseKey(it[marker.across]) == across &&
                        MarkdownTables.normaliseKey(it[keyColumn]) == key
            }
            if (row == null) {
                // A heading naming no value of the key is the document's own commentary, not an error.
                return
            }
            verified++
            if (!MarkdownTables.matches(cited, row[field])) {
                failures << "line $line: $across $key $field is ${row[field]} in ${marker.table}.tsv, " +
                        "cited as $cited"
            }
        }
        new Result(failures, verified, 0)
    }

    private static List<Map<String, String>> table(File yearDir, String name,
                                                   Map<String, List<Map<String, String>>> cache) {
        if (cache.containsKey(name)) {
            return cache[name]
        }
        cache[name] = MarkdownTables.read(new File(yearDir, "${name}.tsv"))
    }
}
