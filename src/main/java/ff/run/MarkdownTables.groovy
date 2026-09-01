package ff.run

/**
 * Reading markdown tables and the tab separated files they are checked against.
 *
 * Shared by the two checks that compare a document against something generated: {@link StrategyCheck},
 * which holds a draft plan to the board it was written from, and {@link DocsCheck}, which holds the model's
 * own documentation to the figures the model produces. They ask different questions of a table and parse it
 * identically, so the parsing lives here.
 */
class MarkdownTables {

    static boolean isTableRow(String line) { line.trim().startsWith('|') }

    static boolean isDivider(List<String> cells) {
        cells.every { it.trim() ==~ /:?-{2,}:?/ }
    }

    static List<String> cells(String line) {
        String trimmed = line.trim()
        trimmed = trimmed.replaceFirst(/^\|/, '').replaceFirst(/\|$/, '')
        trimmed.split(/\|/, -1).collect { it.trim() } as List<String>
    }

    /**
     * Strip the markdown and the units a figure may be dressed in, so <b>$76</b> and 76 are the same number.
     *
     * The percent sign is in here because a share reads as a percentage in prose and is written as a bare
     * number in a file, and a table forced to say 13.9 where the sentence around it says 13.9% is a table
     * nobody will keep writing. Stripping it can only widen what matches, never narrow it.
     */
    static String clean(String cell) {
        cell?.replaceAll(/[*`]/, '')?.replaceAll(/[$,±%]/, '')?.trim()
    }

    /**
     * Numbers compare as numbers so 0.26 and .26 agree; everything else compares as text.
     *
     * <b>Both sides are cleaned, which they were not.</b> Only the citation was, so a report cell carrying
     * any of the punctuation {@link #clean} strips could never be cited at all: the comma is in that set for
     * the sake of 1,234, and it made a comma-separated list of names unmatchable — the cited "Sanders,
     * Richardson" arrived as "Sanders Richardson" and was compared against a value nothing had touched.
     * Cleaning one side of a comparison is the sort of asymmetry that reads as a stale figure, which is
     * exactly what this check exists to tell apart from a real one.
     */
    static boolean matches(String cited, String actual) {
        String tidied = clean(actual)
        if (cited.equalsIgnoreCase(tidied)) {
            return true
        }
        try {
            return new BigDecimal(cited).compareTo(new BigDecimal(tidied)) == 0
        } catch (NumberFormatException ignored) {
            return false
        }
    }

    /**
     * A key as written in prose, reduced to the key as written in a file.
     *
     * So a documentation table can label its row <i>Top price</i> and still be checked against the
     * {@code TOPPRICE} the model wrote. Without this the choice is between a document that reads like a
     * spreadsheet and a document nothing can check, and neither is worth having.
     */
    static String normaliseKey(String key) {
        key?.replaceAll(/[^A-Za-z0-9]/, '')?.toUpperCase()
    }

    /**
     * Which of a table's columns a citation names a row by.
     *
     * <b>Shared, because both checks ask it and each had invented its own answer.</b> DocsCheck read the
     * file's first column and let a marker override with {@code key=SEASON+PLAYER}; StrategyCheck read the
     * file's first column and then quietly also indexed by PLAYER and by OWNER, those being the two that
     * happened to be wanted. The second is the first with the general case left out.
     *
     * The rule is: whatever {@code key=} names, else the citation's own leading heading where that names a
     * column of the table, else the table's first column. The middle clause is what the PLAYER and OWNER
     * special cases were reaching for — a document keying its table by player has already said so, in the
     * heading it put first.
     *
     * A table with one row per <b>pair</b> of things needs both named: the franchise tags are one per season
     * and player and neither alone picks out a row, so a marker may say {@code key=SEASON+PLAYER}.
     */
    static List<String> keyColumns(String keySpec, List<Map<String, String>> table,
                                   List<String> citedHeadings) {
        if (keySpec) {
            return keySpec.split(/\+/).toList()
        }
        Set<String> columns = table ? table.first().keySet() : [] as Set
        String leading = citedHeadings ? clean(citedHeadings.first()) : null
        String named = leading ? columns.find { normaliseKey(it) == normaliseKey(leading) } : null
        named ? [named] : (columns ? [columns.first()] : [])
    }

    /** The citation's leading cells, normalised and joined, as one key to look a row up by. */
    static String compositeKey(List<String> cells, int width) {
        (0..<width).collect { it < cells.size() ? normaliseKey(clean(cells[it])) : '' }.join('|')
    }

    /** The key a row of the table carries under these columns. */
    static String rowKey(Map<String, String> row, List<String> keyColumns) {
        keyColumns.collect { normaliseKey(row[it]) }.join('|')
    }

    /**
     * Every row the key matches.
     *
     * All of them rather than the first, so a caller can refuse an ambiguous citation. A key that picks out
     * several rows picks out none, and answering from whichever came first or last is a wrong answer wearing
     * the shape of a right one.
     */
    static List<Map<String, String>> rowsMatching(List<Map<String, String>> table, List<String> keyColumns,
                                                  String composite) {
        table.findAll { rowKey(it, keyColumns) == composite }
    }

    /**
     * The name this heading was probably meant to be, or null where it names nothing like one.
     *
     * <b>A heading that binds to no field has to be allowed, and that is what makes a typo invisible.</b>
     * A document legitimately carries columns the model does not produce — a note, or what the league
     * actually paid beside what the model says it will — so an unrecognised heading cannot simply be an
     * error. Which meant a heading that was <i>meant</i> to bind and no longer does, because it was
     * mistyped or because the figure was renamed underneath it, read as deliberate commentary: the column
     * stopped being checked and the run went on saying OK.
     *
     * The two are told apart by distance rather than by a list. A typo is a near miss — {@code VALUEE} is
     * one edit from {@code VALUE} — while real commentary is nowhere near any field, {@code note} being
     * three edits from the closest thing the figures carry. So the rule is derived from the figures
     * themselves and needs telling nothing when they gain a column, which is the lesson
     * {@link StrategyCheck#PROSE} records at length.
     *
     * <b>Two-letter headings are in scope, and they are the ones that matter most.</b> Exempting them was
     * the first attempt, on the grounds that at that length everything is near everything — but every
     * position is two letters, and a table spread across positions is the commonest shape in this
     * documentation, so exempting them exempted exactly the typo most likely to be made. {@code WB} for
     * {@code WR} went straight through.
     *
     * The cost is a commentary column of two letters that happens to sit one edit from a position — a
     * {@code TD} beside a {@code TE} — being reported. That is the right way round to be wrong: the author
     * renames a column and moves on, where the other way round a column stops being checked and says
     * nothing. Only a single character is exempt, having nothing to be a slip of.
     */
    static List<String> nearMisses(String heading, Collection<String> candidates) {
        String key = normaliseKey(heading)
        if (!key || key.length() < 2) {
            return []
        }
        // Scaled to length, so a long heading may be two letters out and a short one only one.
        int allowed = key.length() >= 6 ? 2 : 1
        Map<Integer, List<String>> byDistance = [:].withDefault { [] }
        candidates.each { String candidate ->
            String other = normaliseKey(candidate)
            if (!other) {
                return
            }
            int distance = editDistance(key, other)
            if (distance > 0 && distance <= allowed) {
                byDistance[distance] << candidate
            }
        }
        // Every candidate that is equally near, because at two letters several usually are and naming one
        // of them confidently would be pointing at the wrong repair as often as the right one.
        byDistance.isEmpty() ? [] : byDistance[byDistance.keySet().min()]
    }

    /** Levenshtein, over strings short enough that nothing cleverer is worth having. */
    private static int editDistance(String from, String to) {
        int[] previous = (0..to.length()).toList() as int[]
        int[] current = new int[to.length() + 1]
        for (int i = 1; i <= from.length(); i++) {
            current[0] = i
            for (int j = 1; j <= to.length(); j++) {
                int substitute = previous[j - 1] + (from.charAt(i - 1) == to.charAt(j - 1) ? 0 : 1)
                current[j] = Math.min(substitute, Math.min(previous[j] + 1, current[j - 1] + 1))
            }
            int[] swap = previous
            previous = current
            current = swap
        }
        previous[to.length()]
    }

    /** The rows of a tab separated file, in order, each a heading-to-value map. */
    static List<Map<String, String>> read(File file) {
        if (!file.exists()) {
            return null
        }
        List<String> lines = file.readLines().dropWhile { it.startsWith('#') || !it.trim() }
        if (!lines) {
            return []
        }
        List<String> headings = lines[0].split('\t', -1) as List
        lines.drop(1).findAll { it.trim() }.collect { String line ->
            List<String> values = line.split('\t', -1) as List
            Map<String, String> row = [:]
            headings.eachWithIndex { String heading, int i ->
                row[heading] = i < values.size() ? values[i].trim() : ''
            }
            row
        }
    }
}
