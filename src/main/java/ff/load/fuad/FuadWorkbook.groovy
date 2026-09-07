package ff.load.fuad

import ff.data.fuad.SheetPick
import ff.data.fuad.SheetSigning
import groovy.xml.XmlSlurper

import java.util.zip.ZipFile

/**
 * Read the season's hand-kept workbook: the post-auction rosters and the rookie draft as the commissioner
 * recorded them.
 *
 * This is the one input to the league load that the model does not produce. An auction is held off the
 * league site and a rookie draft is run by email, so what actually happened exists first in this sheet and
 * nowhere else until it is loaded.
 *
 * <b>An xlsx is a zip of XML</b>, and reading it that way costs nothing: adding a spreadsheet library for
 * two tabs of strings and small integers would be the larger commitment. Only what the load needs is read —
 * shared strings, cell values and row order — and formatting, styles and formulas are ignored.
 *
 * Both tabs are located by their headers rather than by fixed cell ranges, because the sheet is rewritten
 * every year and a row inserted at the top would otherwise shift a load onto the wrong players silently.
 */
class FuadWorkbook {

    private static final String NS = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'

    /**
     * Rows below the roster on the Franchises tab: cap accounting, not players.
     *
     * The tab's own total sums through the cap penalties row, so a reader that stopped at the last
     * non-empty row would count three summary rows per franchise as three more players.
     */
    private static final List<String> FOOTER_MARKERS = ['cap penalties', 'total', 'max bid'].asImmutable()

    /** Column headers on the Franchises tab that label a value rather than name a franchise. */
    private static final List<String> VALUE_HEADERS = ['$', 'Yrs'].asImmutable()

    private final Map<String, List<List<String>>> sheets = [:]

    FuadWorkbook(File workbook) {
        if (!workbook.exists()) {
            throw new IllegalArgumentException("No workbook at $workbook.absolutePath")
        }
        new ZipFile(workbook).withCloseable { zip -> read(zip) }
    }

    /** Every franchise's roster as the sheet records it, keyed by the owner name in the column header. */
    Map<String, List<SheetSigning>> franchises() {
        List<List<String>> rows = sheet('Franchises')
        Map<String, List<SheetSigning>> out = [:]
        columnsOf(rows.first()).each { String owner, int column ->
            List<SheetSigning> roster = []
            for (List<String> row : rows.tail()) {
                String name = cell(row, column)
                if (!name) continue
                if (FOOTER_MARKERS.contains(name.toLowerCase())) break
                roster << new SheetSigning(owner, name, cell(row, column + 1),
                        dollars(cell(row, column + 2)), (cell(row, column + 3) ?: '0') as BigDecimal as int)
            }
            out[owner] = roster
        }
        out
    }

    /**
     * The rookie draft in the order it was made.
     *
     * The Rookie tab carries the board on its left and the draft as it happened on its right, so the block
     * is found by its own Pick/Owner/Player headers rather than by column position.
     */
    List<SheetPick> picks() {
        List<List<String>> rows = sheet('Rookie')
        List<String> header = rows.first()
        int pickColumn = (0..<header.size()).find { header[it]?.trim()?.equalsIgnoreCase('Pick') }
        if (pickColumn == null) {
            throw new IllegalStateException('The Rookie tab has no Pick column, so the draft cannot be read.')
        }
        List<SheetPick> picks = []
        for (List<String> row : rows.tail()) {
            String owner = cell(row, pickColumn + 1)
            String player = cell(row, pickColumn + 2)
            if (owner && player) picks << new SheetPick(picks.size() + 1, owner, player)
        }
        picks
    }

    /** Franchise columns on the Franchises tab, owner name to the column its players are listed in. */
    private static Map<String, Integer> columnsOf(List<String> header) {
        Map<String, Integer> columns = [:]
        header.eachWithIndex { String value, int index ->
            String name = value?.trim()
            if (name && !VALUE_HEADERS.contains(name)) columns[name] = index
        }
        columns
    }

    /**
     * A blank salary is unsigned, not zero.
     *
     * Returning zero here would make an unsigned player look like a dollar contract and load him onto a
     * roster he is no longer on.
     */
    private static Integer dollars(String value) {
        String text = value?.trim()
        text ? (text as BigDecimal).intValue() : null
    }

    private static String cell(List<String> row, int column) {
        (column < row.size() ? row[column] : null)?.trim() ?: null
    }

    private List<List<String>> sheet(String name) {
        List<List<String>> rows = sheets[name]
        if (rows == null) {
            throw new IllegalStateException("The workbook has no '$name' tab. Tabs: ${sheets.keySet().join(', ')}")
        }
        rows
    }

    private void read(ZipFile zip) {
        List<String> strings = zip.getEntry('xl/sharedStrings.xml') ? parse(zip, 'xl/sharedStrings.xml')
                .'*'.findAll { it.name() == 'si' }.collect { si -> si.depthFirst()
                .findAll { it.name() == 't' }*.text().join('') } : []

        Map<String, String> targets = [:]
        parse(zip, 'xl/_rels/workbook.xml.rels').'*'.each { targets[it.@Id.text()] = it.@Target.text() }

        parse(zip, 'xl/workbook.xml').sheets.sheet.each { entry ->
            String id = entry.attributes().find { it.key.toString().endsWith('id') }?.value
            String target = targets[id]
            // Relationship targets are relative to xl/, and may or may not carry a leading slash.
            if (target) {
                String path = target.startsWith('/') ? target.substring(1) : "xl/$target"
                sheets[entry.@name.text()] = rowsOf(parse(zip, path), strings)
            }
        }
    }

    private static List<List<String>> rowsOf(sheet, List<String> strings) {
        sheet.sheetData.row.collect { row ->
            List<String> cells = []
            row.c.each { c ->
                int index = column(c.@r.text())
                while (cells.size() <= index) cells << null
                cells[index] = value(c, strings)
            }
            cells
        }
    }

    private static String value(cell, List<String> strings) {
        String type = cell.@t.text()
        if (type == 'inlineStr') return cell.depthFirst().findAll { it.name() == 't' }*.text().join('')
        String raw = cell.v.text()
        if (!raw) return null
        type == 's' ? strings[raw as int] : raw
    }

    /** Column index from a cell reference: A1 is 0, AA1 is 26. */
    private static int column(String reference) {
        int index = 0
        for (char c : reference.toCharArray()) {
            if (!Character.isLetter(c)) break
            index = index * 26 + (Character.toUpperCase(c) - ('A' as char) + 1)
        }
        index - 1
    }

    private static parse(ZipFile zip, String entry) {
        zip.getInputStream(zip.getEntry(entry)).withCloseable { new XmlSlurper(false, false).parse(it) }
    }
}
