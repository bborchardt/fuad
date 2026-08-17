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

    /** Where a documentation table says which season's figures it is citing, when it is not the default. */
    private static final String YEAR_MARKER = /<!--\s*figures-year:\s*(\d{4})\s*-->/

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

        boolean failed = false
        documents.each { File document ->
            if (!document.exists()) {
                println "FAIL  $document"
                println '  no such document'
                failed = true
                return
            }
            List<String> failures = check(document, new File(figuresDir, year))
            if (failures) {
                println "FAIL  $document"
                failures.each { println "  $it" }
                failed = true
            } else {
                println "OK    $document"
            }
        }
        if (failed) {
            System.exit(1)
        }
    }

    /** Every way the document disagrees with the figures, in the order they appear. */
    static List<String> check(File document, File yearDir) {
        List<String> lines = document.readLines()
        List<String> failures = []
        Map<String, List<Map<String, String>>> cache = [:]

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
                continue
            }
            if (MarkdownTables.isDivider(cells)) {
                continue
            }
            failures.addAll(checkRow(marker, yearDir, headings, cells, i + 1, cache))
        }
        failures
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

    private static List<String> checkRow(Map<String, String> marker, File yearDir, List<String> headings,
                                         List<String> cells, int line,
                                         Map<String, List<Map<String, String>>> cache) {
        List<Map<String, String>> table = table(yearDir, marker.table, cache)
        if (table == null) {
            return ["line $line: no figures file for '${marker.table}'" as String]
        }
        String key = MarkdownTables.normaliseKey(MarkdownTables.clean(cells ? cells[0] : null))
        if (!key) {
            return []
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
    private static List<String> checkDown(Map<String, String> marker, List<Map<String, String>> table,
                                          List<String> headings, List<String> cells, String key, int line) {
        String keyColumn = table ? table.first().keySet().first() : null
        Map<String, String> row = table.find { MarkdownTables.normaliseKey(it[keyColumn]) == key }
        if (row == null) {
            return ["line $line: '$key' is not in ${marker.table}.tsv" as String]
        }
        List<String> failures = []
        headings.eachWithIndex { String heading, int column ->
            String field = row.keySet().find { MarkdownTables.normaliseKey(it) == MarkdownTables.normaliseKey(heading) }
            if (column == 0 || column >= cells.size() || !field) {
                return
            }
            String cited = MarkdownTables.clean(cells[column])
            if (cited && !MarkdownTables.matches(cited, row[field])) {
                failures << "line $line: $key $field is ${row[field]} in ${marker.table}.tsv, cited as $cited"
            }
        }
        failures
    }

    /**
     * Headings are values of one key and every cell holds the same field: the shape prose actually wants.
     *
     * Almost every table in PROJECTION.md puts the positions across the top and reads down, because that is
     * how the comparison it is making runs. Long format would be checkable and unreadable, so this reads the
     * table the way it is written: {@code across=POS field=PTS} means the heading is the position, the first
     * column is the rank, and the cell is what that rank levels at.
     */
    private static List<String> checkAcross(Map<String, String> marker, List<Map<String, String>> table,
                                            List<String> headings, List<String> cells, String key, int line) {
        String field = table && table.first().containsKey(marker.field) ? marker.field : null
        if (!field) {
            return ["line $line: '${marker.field}' is not a column of ${marker.table}.tsv" as String]
        }
        // Whichever column is not the one across the top is the one the first column keys on.
        String keyColumn = table.first().keySet().find { it != marker.across }
        List<String> failures = []
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
            if (!MarkdownTables.matches(cited, row[field])) {
                failures << "line $line: $across $key $field is ${row[field]} in ${marker.table}.tsv, " +
                        "cited as $cited"
            }
        }
        failures
    }

    private static List<Map<String, String>> table(File yearDir, String name,
                                                   Map<String, List<Map<String, String>>> cache) {
        if (cache.containsKey(name)) {
            return cache[name]
        }
        cache[name] = MarkdownTables.read(new File(yearDir, "${name}.tsv"))
    }

    /** Which model wrote the figures a document is being checked against. */
    static String figuresModel(File yearDir) {
        ReportManifest.read(yearDir).values().collect { it.model }.unique().sort().join(', ')
    }
}
