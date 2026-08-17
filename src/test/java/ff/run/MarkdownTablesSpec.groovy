package ff.run

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The parsing both checks are built on, asserted directly rather than through them.
 *
 * {@link DocsCheckSpec} and {@link StrategyCheckSpec} exercise this class thoroughly and only ever
 * incidentally: they assert what a document check concluded, so a fault in here reaches them as a wrong
 * conclusion about a table, if it reaches them at all. Since every guarantee either check makes passes
 * through {@link MarkdownTables#matches} and {@link MarkdownTables#nearMisses}, the two rules that decide
 * whether a figure agrees and whether a heading was meant to bind are worth stating on their own.
 */
class MarkdownTablesSpec extends Specification {

    @TempDir
    File temp

    @Unroll
    def "'#cited' and '#actual' #description"() {
        expect:
        MarkdownTables.matches(MarkdownTables.clean(cited), actual) == agree

        where:
        cited     | actual   | agree || description
        '76'      | '76'     | true  || 'are the same figure'
        '**$76**' | '76'     | true  || 'are the same figure under markdown and a currency sign'
        '13.9%'   | '13.9'   | true  || 'are the same share written two ways'
        '.26'     | '0.26'   | true  || 'are the same number written two ways'
        '88.30'   | '88.3'   | true  || 'are the same number to a trailing zero'
        '89'      | '88.6'   | false || 'are a dollar apart and must not be rounded together'
        '36'      | '35'     | false || 'are a rank apart'
        'fair'    | 'fair'   | true  || 'are the same word'
        'FAIR'    | 'fair'   | true  || 'are the same word cased differently'
        'fair'    | 'OVERPRICED' | false || 'are different bands'
    }

    /**
     * The permission that lets a table carry a note is the same permission that hides a typo, and distance
     * is what separates them. Two-letter headings are the case that matters: every position is two letters,
     * and a table spread across positions is the commonest shape in this documentation.
     */
    @Unroll
    def "heading '#heading' #description"() {
        expect:
        MarkdownTables.nearMisses(heading, candidates).toSet() == meant as Set

        where:
        heading       | candidates                | meant             || description
        'WB'          | ['QB', 'RB', 'WR', 'TE']  | ['QB','RB','WR']  || 'is one slip from three positions and names them all'
        'VALUEE'      | ['VALUE', 'FIGURE']       | ['VALUE']         || 'is one slip from a field and is reported'
        'note'        | ['VALUE', 'FIGURE']       | []                || 'is nowhere near a field and stays commentary'
        'Actual 2025' | ['VALUE', 'FIGURE']       | []                || 'is commentary beside the model\'s own column'
        'G'           | ['QB', 'RB']              | []                || 'is a single character with nothing to be a slip of'
    }

    /**
     * The nearest candidates only, so a long heading may be two letters out and a short one only one.
     * Without that a six letter word would drag in most of a figures file's columns.
     */
    def "reports the nearest candidates and not merely the reachable ones"() {
        expect: 'PRICEDDEPTH is two edits from PRICEDEPTH and nowhere near the others'
        MarkdownTables.nearMisses('PRICEDDEPTH', ['PRICEDEPTH', 'STARTED', 'GAMMA']) == ['PRICEDEPTH']
    }

    /**
     * <b>It does not exclude an exact match, and it is not supposed to.</b> {@code nearMisses} answers only
     * "what is this within an edit or two of", and {@link DocsCheck#checkHeadings} asks it only after the
     * heading has failed to match any candidate outright. Asserted because the two are separable and the
     * ordering is the whole of what keeps a correct heading from being reported as a typo.
     */
    def "answers distance alone, leaving the exact match to the caller"() {
        expect: 'QB is a real field here and still comes back near RB, which is why the caller checks first'
        MarkdownTables.nearMisses('QB', ['QB', 'RB']) == ['RB']
    }

    def "reads a figures file past its comment header, keeping the rows in order"() {
        given:
        File file = new File(temp, 'positions.tsv')
        file.text = "# Written by figures_refresh.sh\nPOS\tSTARTED\tREPLRANK\nQB\t20\t21\nRB\t26\t27\n"

        when:
        List<Map<String, String>> rows = MarkdownTables.read(file)

        then:
        rows*.POS == ['QB', 'RB']
        rows[0].REPLRANK == '21'
    }

    def "reports a missing figures file as absent rather than as empty"() {
        expect: 'null and [] mean different things to a caller — no such table, against a table with no rows'
        MarkdownTables.read(new File(temp, 'nothing.tsv')) == null
    }

    def "splits a row on its pipes, keeping the cells a table deliberately leaves blank"() {
        expect: 'the VOR tables in PROJECTION.md read down one position at a time, so blanks carry meaning'
        MarkdownTables.cells('| 30 |  | 19.1 | 15.3 |') == ['30', '', '19.1', '15.3']
    }

    @Unroll
    def "'#line' is #description"() {
        expect:
        MarkdownTables.isDivider(MarkdownTables.cells(line)) == divider

        where:
        line                  | divider || description
        '| --- | --- |'       | true    || 'a divider'
        '| :-- | --: |'       | true    || 'an aligned divider'
        '| QB | 20 |'         | false   || 'a row of figures'
    }
}
