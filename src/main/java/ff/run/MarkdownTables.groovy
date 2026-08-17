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

    /** Strip the markdown a figure may be dressed in, so **$76** and 76 are the same number. */
    static String clean(String cell) {
        cell?.replaceAll(/[*`]/, '')?.replaceAll(/[$,±]/, '')?.trim()
    }

    /** Numbers compare as numbers so 0.26 and .26 agree; everything else compares as text. */
    static boolean matches(String cited, String actual) {
        if (cited.equalsIgnoreCase(actual)) {
            return true
        }
        try {
            return new BigDecimal(cited).compareTo(new BigDecimal(actual)) == 0
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
