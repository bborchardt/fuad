package ff.run

import spock.lang.Specification
import spock.lang.TempDir

/**
 * The documentation is held to the figures the model produces, the way a draft plan is held to its board.
 *
 * What is asserted here is the checking, not any season's numbers — pinning those in a spec would be the
 * drift problem again with a ceremony around it. See docs/PROJECTION.md.
 */
class DocsCheckSpec extends Specification {

    @TempDir
    File temp

    private File yearDir

    def setup() {
        yearDir = new File(temp, 'figures/2026')
        yearDir.mkdirs()
        new File(yearDir, 'curve.tsv').text =
                "POS\tRANK\tPTS\tPPG\tG\tTIER\n" +
                "QB\t1\t245.2\t21.39\t11.46\t1\n" +
                "QB\t6\t220.5\t19.26\t11.45\t3\n" +
                "RB\t1\t188.4\t16.94\t11.12\t1\n" +
                "RB\t6\t178.6\t16.45\t10.86\t2\n"
        new File(yearDir, 'positions.tsv').text =
                "POS\tPRICEDDEPTH\tSTARTED\tBACKWARD\n" +
                "QB\t36\t20\t6.6\n" +
                "RB\t65\t26\t12.4\n"
        new File(yearDir, 'board.tsv').text =
                "FIGURE\tVALUE\n" +
                "PLAYERS\t106\n" +
                "TOPPRICE\t90\n" +
                "PLAYERSABOVE1\t72\n"
    }

    private File document(String body) {
        File file = new File(temp, 'DOC.md')
        file.text = body
        file
    }

    def "passes a table whose cells are the model's"() {
        given:
        File doc = document('''<!-- figures: positions -->

| POS | PRICEDDEPTH | STARTED |
| --- | --- | --- |
| QB | 36 | 20 |
| RB | 65 | 26 |
''')

        expect:
        DocsCheck.check(doc, yearDir) == []
    }

    def "catches a figure that has drifted from what the model says"() {
        given: 'the depth moved from 35 to 36 and the prose did not'
        File doc = document('''<!-- figures: positions -->

| POS | PRICEDDEPTH |
| --- | --- |
| QB | 35 |
''')

        when:
        List<String> failures = DocsCheck.check(doc, yearDir)

        then:
        failures.size() == 1
        failures[0].contains('QB PRICEDDEPTH is 36 in positions.tsv, cited as 35')
    }

    /**
     * The shape almost every table in PROJECTION.md actually has: positions across the top, read downward.
     * Long format would be checkable and unreadable, so the checker reads the table as written.
     */
    def "checks a table written with the positions across the top"() {
        given:
        File doc = document('''<!-- figures: curve across=POS field=PTS -->

| | QB | RB |
| --- | --- | --- |
| 1 | 245.2 | 188.4 |
| 6 | 220.5 | 178.6 |
''')

        expect:
        DocsCheck.check(doc, yearDir) == []
    }

    def "catches one drifted cell in a table read across"() {
        given: 'the running back level moved and the quarterback one did not'
        File doc = document('''<!-- figures: curve across=POS field=PTS -->

| | QB | RB |
| --- | --- | --- |
| 1 | 245.2 | 169.6 |
''')

        when:
        List<String> failures = DocsCheck.check(doc, yearDir)

        then: 'named by position, rank and field, so the fix is mechanical'
        failures.size() == 1
        failures[0].contains('RB 1 PTS is 188.4')
        failures[0].contains('cited as 169.6')
    }

    def "reads a row label the way prose writes it"() {
        given: 'the document says Top price where the model wrote TOPPRICE'
        File doc = document('''<!-- figures: board -->

| FIGURE | VALUE |
| --- | --- |
| Top price | 90 |
| Players above $1 | 72 |
''')

        expect:
        DocsCheck.check(doc, yearDir) == []
    }

    def "leaves a heading the model does not produce alone, as the document's own commentary"() {
        given: 'what the league actually did is not something the model outputs'
        File doc = document('''<!-- figures: board -->

| FIGURE | VALUE | Actual 2025 |
| --- | --- | --- |
| TOPPRICE | 90 | 100 |
''')

        expect:
        DocsCheck.check(doc, yearDir) == []
    }

    def "leaves a column heading naming no position alone in a table read across"() {
        given:
        File doc = document('''<!-- figures: curve across=POS field=PTS -->

| | QB | RB | what a flat curve would use |
| --- | --- | --- | --- |
| 1 | 245.2 | 188.4 | 200.0 |
''')

        expect:
        DocsCheck.check(doc, yearDir) == []
    }

    def "ignores prose and any table that is not marked"() {
        given:
        File doc = document('''# Salary projection

The curve resolves QB2 from QB17 and has no business resolving QB10 from QB14.

| Rank | Levelled |
| --- | --- |
| 1 | 999.9 |
''')

        expect: 'an unmarked table is the document making a point, not citing a figure'
        DocsCheck.check(doc, yearDir) == []
    }

    def "names a row the model does not carry"() {
        given:
        File doc = document('''<!-- figures: positions -->

| POS | PRICEDDEPTH |
| --- | --- |
| DEF | 20 |
''')

        expect:
        DocsCheck.check(doc, yearDir).any { it.contains("'DEF' is not in positions.tsv") }
    }

    def "says so when a marked table cites a field the model does not produce"() {
        given:
        File doc = document('''<!-- figures: curve across=POS field=NOTATHING -->

| | QB |
| --- | --- |
| 1 | 245.2 |
''')

        expect:
        DocsCheck.check(doc, yearDir).any { it.contains("'NOTATHING' is not a column of curve.tsv") }
    }

    def "says so when a marked table names no figures file"() {
        given:
        File doc = document('''<!-- figures: nosuchtable -->

| POS | X |
| --- | --- |
| QB | 1 |
''')

        expect:
        DocsCheck.check(doc, yearDir).any { it.contains("no figures file for 'nosuchtable'") }
    }

    def "stops checking at the end of the table, so a later unmarked one is left alone"() {
        given:
        File doc = document('''<!-- figures: positions -->

| POS | PRICEDDEPTH |
| --- | --- |
| QB | 36 |

Prose in between, which ends the marked table.

| POS | PRICEDDEPTH |
| --- | --- |
| QB | 999 |
''')

        expect:
        DocsCheck.check(doc, yearDir) == []
    }
}
