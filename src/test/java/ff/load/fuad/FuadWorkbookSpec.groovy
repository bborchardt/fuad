package ff.load.fuad

import spock.lang.Specification
import spock.lang.TempDir

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The workbook is hand-kept and rewritten every year, so the reader is held to the two things about it that
 * have actually gone wrong rather than to its current shape.
 *
 * Both failures are silent. A reader that counted the cap accounting rows as players would load three
 * phantom players per franchise; a reader that treated a blank salary as zero would put a player who was
 * not re-signed back onto the roster he left.
 */
class FuadWorkbookSpec extends Specification {

    @TempDir
    File temp

    def 'the roster stops at the cap accounting rows rather than at the last non-empty one'() {
        given: 'a franchise column with two players and the sheet\'s own footer below them'
        File file = workbook(franchises: [['Brett', '', '$', 'Yrs'],
                                          ['Lamar Jackson', 'QB2', '68', '0'],
                                          ['Jalen Hurts', 'QB5', '65', '0'],
                                          ['cap penalties', '', '', ''],
                                          ['total', '', '133', '2'],
                                          ['max bid', '', '18', '']])

        when:
        def roster = new FuadWorkbook(file).franchises()

        then: 'the three footer rows are accounting, not players'
        roster.Brett*.playerName == ['Lamar Jackson', 'Jalen Hurts']
    }

    def 'a blank salary is unsigned rather than a dollar contract'() {
        given: 'one signed player and one whose contract expired and was not re-signed'
        File file = workbook(franchises: [['Pete', '', '$', 'Yrs'],
                                          ['Joe Burrow', 'QB4', '65', '1'],
                                          ['Joe Flacco', 'QB45', '', '0'],
                                          ['total', '', '65', '1']])

        when:
        def roster = new FuadWorkbook(file).franchises()

        then: 'the blank is null, not zero, and the row is not a roster spot'
        roster.Pete*.salary == [65, null]
        roster.Pete*.signed == [true, false]
    }

    def 'picks are numbered by row order, because the sheet cannot tell 1.1 from 1.10'() {
        given: 'a draft block whose pick column collides on the tenth pick of the round'
        File file = workbook(rookie: [['Player', 'Pick', 'Owner', 'Player'],
                                      ['Carnell Tate', '1.1000000000000001', 'Martin', 'Carnell Tate'],
                                      ['Jadarian Price', '1.2', 'Matthew', 'Jadarian Price'],
                                      ['Kenyon Sadiq', '1.1000000000000001', 'Quinlan', 'Kenyon Sadiq']])

        when:
        def picks = new FuadWorkbook(file).picks()

        then: 'row order separates them where the recorded number does not'
        picks*.overall == [1, 2, 3]
        picks*.owner == ['Martin', 'Matthew', 'Quinlan']
        picks*.playerName == ['Carnell Tate', 'Jadarian Price', 'Kenyon Sadiq']
    }

    def 'a tab the workbook does not have is named rather than returned empty'() {
        when:
        new FuadWorkbook(workbook(franchises: [['Brett', '', '$', 'Yrs']])).picks()

        then:
        IllegalStateException e = thrown()
        e.message.contains('Rookie')
    }

    /** Builds the smallest xlsx that exercises the reader: inline strings, no styles, no shared strings. */
    private File workbook(Map<String, List<List<String>>> tabs) {
        File file = new File(temp, 'workbook.xlsx')
        new ZipOutputStream(file.newOutputStream()).withCloseable { zip ->
            List<String> names = tabs.keySet().collect { it == 'franchises' ? 'Franchises' : 'Rookie' }
            entry(zip, 'xl/workbook.xml', '<workbook><sheets>' +
                    names.withIndex().collect { String name, int i ->
                        """<sheet name="$name" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
                    }.join('') + '</sheets></workbook>')
            entry(zip, 'xl/_rels/workbook.xml.rels', '<Relationships>' +
                    names.withIndex().collect { String name, int i ->
                        """<Relationship Id="rId${i + 1}" Target="worksheets/sheet${i + 1}.xml"/>"""
                    }.join('') + '</Relationships>')
            tabs.values().eachWithIndex { List<List<String>> rows, int i ->
                entry(zip, "xl/worksheets/sheet${i + 1}.xml", sheetXml(rows))
            }
        }
        file
    }

    private static String sheetXml(List<List<String>> rows) {
        StringBuilder xml = new StringBuilder('<worksheet><sheetData>')
        rows.eachWithIndex { List<String> row, int r ->
            xml << """<row r="${r + 1}">"""
            row.eachWithIndex { String value, int c ->
                if (value != null && !value.isEmpty()) {
                    xml << """<c r="${cell(c)}${r + 1}" t="inlineStr"><is><t>${value}</t></is></c>"""
                }
            }
            xml << '</row>'
        }
        xml << '</sheetData></worksheet>'
        xml
    }

    private static String cell(int column) {
        String reference = ''
        int n = column
        while (true) {
            reference = ((char) ('A' as char) + (n % 26)) as char as String + reference
            if (n < 26) break
            n = (n / 26) as int - 1
        }
        reference
    }

    private static void entry(ZipOutputStream zip, String name, String content) {
        zip.putNextEntry(new ZipEntry(name))
        zip.write(content.getBytes('UTF-8'))
        zip.closeEntry()
    }
}
