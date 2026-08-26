package ff.run

import ff.run.ReportManifest

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
 * does. <b>Unless it is a near miss.</b> That permission is what let a mistyped or renamed heading read as
 * deliberate commentary and stop being checked in silence, so a heading one slip from a real field is
 * reported rather than excused. See {@link MarkdownTables#nearMisses}.
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

        boolean failed = false

        // Before any document, the figures themselves. Checking prose against figures an older model wrote
        // proves only that the prose matches something, which is the question nobody asked.
        //
        // Every league's, not one league's: a document may cite either, and figures for a league nobody
        // happened to read would otherwise go unchecked until the day something read them.
        leagueDirs(figuresDir, year).each { File leagueYear ->
            List<String> stale = checkProvenance(leagueYear)
            if (stale) {
                println "FAIL  $leagueYear"
                stale.each { println "  $it" }
                System.exit(1)
            }
        }

        documents.each { File document ->
            if (!document.exists()) {
                println "FAIL  $document"
                println '  no such document'
                failed = true
                return
            }
            Result result = inspect(document, figuresDir, year)
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
                ['the model has uncommitted changes under src/main, so the figures are not what it would now ' +
                         'produce — commit it and run ./figures_refresh.sh'] : []
    }

    /** Every way the document disagrees with the figures, in the order they appear. */
    static List<String> check(File document, File figuresDir, String year) {
        inspect(document, figuresDir, year).failures
    }

    /** The same, alongside how much of the document was actually held to anything. */
    static Result inspect(File document, File figuresDir, String year) {
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
                // Once for the table rather than once per row: a mistyped heading is one mistake, and
                // reporting it against every row below would bury the rows that are genuinely wrong.
                failures.addAll(checkHeadings(marker, figuresDir, year, headings, i + 1, cache))
                continue
            }
            if (MarkdownTables.isDivider(cells)) {
                continue
            }
            Result row = checkRow(marker, figuresDir, year, headings, cells, i + 1, cache)
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

    /**
     * Catch a heading that was meant to name a figure and no longer does.
     *
     * The permissive rule below — a heading matching nothing is the document's own commentary — is what
     * makes a table able to carry a note beside a figure, and it is also what let a mistyped or renamed
     * heading go quiet instead of failing. {@link MarkdownTables#nearMiss} separates the two by distance,
     * so commentary stays commentary and a near miss is reported as what it is.
     *
     * The candidates differ by orientation. Read down, a heading names a field of the figures file. Read
     * across, it names a value of the key the table is spread over — a position, usually.
     */
    private static List<String> checkHeadings(Map<String, String> marker, File figuresDir, String year,
                                              List<String> headings,
                                              int line, Map<String, List<Map<String, String>>> cache) {
        List<Map<String, String>> table = table(figuresDir, year, marker.table, cache)
        if (!table) {
            // Missing or empty: the rows report that themselves, and better than a heading can.
            return []
        }
        Collection<String> candidates = marker.across ?
                table.collect { it[marker.across] }.findAll { it }.unique() :
                table.first().keySet()

        // The leading columns label the key rather than naming a figure, so they bind to nothing by design.
        int keyWidth = marker.across ? 1 : keyColumnsOf(marker, table).size()
        List<String> failures = []
        headings.eachWithIndex { String heading, int column ->
            if (column < keyWidth) {
                return
            }
            String cleaned = MarkdownTables.clean(heading)
            if (candidates.any { MarkdownTables.normaliseKey(it) == MarkdownTables.normaliseKey(cleaned) }) {
                return
            }
            List<String> meant = MarkdownTables.nearMisses(cleaned, candidates)
            if (meant) {
                failures << "line $line: heading '$cleaned' is checked against nothing in " +
                        "${marker.table}.tsv and is one slip from ${meant.collect { "'$it'" }.join(' / ')}" +
                        ' — if it really is commentary, name it something that is not'
            }
        }
        failures
    }

    private static Result checkRow(Map<String, String> marker, File figuresDir, String year,
                                   List<String> headings,
                                   List<String> cells, int line,
                                   Map<String, List<Map<String, String>>> cache) {
        List<Map<String, String>> table = table(figuresDir, year, marker.table, cache)
        if (table == null) {
            return new Result(["line $line: no figures file for '${marker.table}'" as String], 0, 0)
        }
        String key = MarkdownTables.normaliseKey(MarkdownTables.clean(cells ? cells[0] : null))
        if (!key) {
            return new Result([], 0, 0)
        }
        marker.across ? checkAcross(marker, table, headings, cells, key, line)
                : checkDown(marker, table, headings, cells, line)
    }

    /**
     * Which of the file's columns the document's leading columns name a row by.
     *
     * One by default, being the file's first column, which is every table holding one row per thing. A
     * table with one row per <b>pair</b> of things needs both named — the tags are one per season and
     * player, and neither alone picks out a row — so the marker may say {@code key=SEASON+PLAYER} and the
     * document's first two columns are read together.
     */
    private static List<String> keyColumnsOf(Map<String, String> marker, List<Map<String, String>> table) {
        marker.key ? marker.key.split(/\+/).toList() : [table.first().keySet().first()]
    }

    /** The document's leading cells, normalised and joined, as one key to look a row up by. */
    private static String compositeKey(List<String> cells, int width) {
        (0..<width).collect {
            it < cells.size() ? MarkdownTables.normaliseKey(MarkdownTables.clean(cells[it])) : ''
        }.join('|')
    }

    /**
     * Headings name fields and the leading columns name the row: the plain shape.
     *
     * The row is found on whichever of the file's own columns the key names, so a table may be keyed by
     * whatever the file's first column is without having to say so, or by a pair where one will not do.
     */
    private static Result checkDown(Map<String, String> marker, List<Map<String, String>> table,
                                    List<String> headings, List<String> cells, int line) {
        List<String> keyColumns = keyColumnsOf(marker, table)
        int keyWidth = keyColumns.size()
        String composite = compositeKey(cells, keyWidth)
        Map<String, String> row = table.find { Map<String, String> candidate ->
            keyColumns.collect { MarkdownTables.normaliseKey(candidate[it]) }.join('|') == composite
        }
        if (row == null) {
            return new Result(["line $line: '${composite.replace('|', ' ')}' is not " +
                                       "in ${marker.table}.tsv" as String], 0, 0)
        }
        List<String> failures = []
        int verified = 0
        headings.eachWithIndex { String heading, int column ->
            String field = row.keySet().find { MarkdownTables.normaliseKey(it) == MarkdownTables.normaliseKey(heading) }
            if (column < keyWidth || column >= cells.size() || !field) {
                return
            }
            String cited = MarkdownTables.clean(cells[column])
            if (!cited) {
                return
            }
            verified++
            if (!MarkdownTables.matches(cited, row[field])) {
                failures << "line $line: ${composite.replace('|', ' ')} $field is ${row[field]} " +
                        "in ${marker.table}.tsv, cited as $cited"
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
     *
     * <b>The row key may be named, and is inferred when it is not.</b> The inference takes whichever column
     * is not the one spread across the top, which is correct for every figures file here and is nonetheless
     * positional: a column inserted ahead of the real key would rekey every table reading that file, quietly,
     * because a row that fails to match was indistinguishable from a heading that was only ever commentary.
     * Those two are now separated — a heading naming a real value of the key and finding no row is a
     * failure — so a mis-keyed table reports a fault rather than reporting nothing.
     */
    private static Result checkAcross(Map<String, String> marker, List<Map<String, String>> table,
                                      List<String> headings, List<String> cells, String key, int line) {
        String field = table && table.first().containsKey(marker.field) ? marker.field : null
        if (!field) {
            return new Result(["line $line: '${marker.field}' is not a column of ${marker.table}.tsv" as String],
                    0, 0)
        }
        // Named where the marker says so, inferred otherwise as whichever column is not the one across the
        // top. The inference is right for every table here and is positional, so a column inserted ahead of
        // the real key would silently rekey every table reading that file — which is why a heading that
        // names a real value and then finds no row is reported below rather than passed over.
        String keyColumn = marker.key ?: table.first().keySet().find { it != marker.across }
        if (!table.first().containsKey(keyColumn)) {
            return new Result(["line $line: '$keyColumn' is not a column of ${marker.table}.tsv" as String],
                    0, 0)
        }
        Set<String> acrossValues = table.collect { MarkdownTables.normaliseKey(it[marker.across]) }.toSet()
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
                // Two different things used to land here. A heading naming no value of the key is the
                // document's own commentary and is left alone; a heading that names a real one and still
                // finds no row means the key is wrong or the row has gone, and passing over that is how a
                // mis-keyed table would report nothing at all rather than reporting a fault.
                if (acrossValues.contains(across)) {
                    failures << "line $line: ${marker.table}.tsv has no $across row keyed " +
                            "'$key' on $keyColumn"
                }
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

    /**
     * A figure named as {@code <league>/<table>}, which is what the marker carries.
     *
     * <b>The league is part of the name because the table names collide.</b> Both leagues have a curve and
     * a positions table, and they describe different lineups, different scoring and different currencies —
     * a document citing the wrong one would be checked against real figures and pass.
     */
    private static List<Map<String, String>> table(File figuresDir, String year, String name,
                                                   Map<String, List<Map<String, String>>> cache) {
        if (cache.containsKey(name)) {
            return cache[name]
        }
        List<String> parts = name.split('/', 2) as List
        if (parts.size() != 2) {
            throw new IllegalArgumentException(
                    "Figure '$name' names no league: markers read <!-- figures: <league>/<table> -->, " +
                            "the leagues being ${leagues(figuresDir, year)}")
        }
        cache[name] = MarkdownTables.read(
                new File(figuresDir, "${parts[0]}/$year/${parts[1]}.tsv"))
    }

    /** Every league that has figures for this year, which is a directory each. */
    private static List<File> leagueDirs(File figuresDir, String year) {
        (figuresDir.listFiles() ?: [] as File[])
                .findAll { it.isDirectory() && new File(it, year).isDirectory() }
                .collect { new File(it, year) }
                .sort { it.parentFile.name }
    }

    private static List<String> leagues(File figuresDir, String year) {
        leagueDirs(figuresDir, year)*.parentFile*.name
    }
}
