package ff.run

import spock.lang.Specification
import spock.lang.TempDir

/**
 * The documentation is held to the figures the model produces, the way a draft plan is held to its board.
 *
 * What is asserted here is the checking, not any season's numbers — pinning those in a spec would be the
 * drift problem again with a ceremony around it. See docs/fuad/PROJECTION.md.
 */
class DocsCheckSpec extends Specification {

    @TempDir
    File temp

    private File figuresDir
    private File yearDir

    def setup() {
        figuresDir = new File(temp, 'figures')
        yearDir = new File(figuresDir, 'fuad/2026')
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
        File doc = document('''<!-- figures: fuad/positions -->

| POS | PRICEDDEPTH | STARTED |
| --- | --- | --- |
| QB | 36 | 20 |
| RB | 65 | 26 |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026') == []
    }

    def "catches a figure that has drifted from what the model says"() {
        given: 'the depth moved from 35 to 36 and the prose did not'
        File doc = document('''<!-- figures: fuad/positions -->

| POS | PRICEDDEPTH |
| --- | --- |
| QB | 35 |
''')

        when:
        List<String> failures = DocsCheck.check(doc, figuresDir, '2026')

        then:
        failures.size() == 1
        failures[0].contains('QB PRICEDDEPTH is 36 in fuad/positions.tsv, cited as 35')
    }

    /**
     * The shape almost every table in PROJECTION.md actually has: positions across the top, read downward.
     * Long format would be checkable and unreadable, so the checker reads the table as written.
     */
    def "checks a table written with the positions across the top"() {
        given:
        File doc = document('''<!-- figures: fuad/curve across=POS field=PTS -->

| | QB | RB |
| --- | --- | --- |
| 1 | 245.2 | 188.4 |
| 6 | 220.5 | 178.6 |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026') == []
    }

    def "catches one drifted cell in a table read across"() {
        given: 'the running back level moved and the quarterback one did not'
        File doc = document('''<!-- figures: fuad/curve across=POS field=PTS -->

| | QB | RB |
| --- | --- | --- |
| 1 | 245.2 | 169.6 |
''')

        when:
        List<String> failures = DocsCheck.check(doc, figuresDir, '2026')

        then: 'named by position, rank and field, so the fix is mechanical'
        failures.size() == 1
        failures[0].contains('RB 1 PTS is 188.4')
        failures[0].contains('cited as 169.6')
    }

    def "reads a row label the way prose writes it"() {
        given: 'the document says Top price where the model wrote TOPPRICE'
        File doc = document('''<!-- figures: fuad/board -->

| FIGURE | VALUE |
| --- | --- |
| Top price | 90 |
| Players above $1 | 72 |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026') == []
    }

    def "leaves a heading the model does not produce alone, as the document's own commentary"() {
        given: 'what the league actually did is not something the model outputs'
        File doc = document('''<!-- figures: fuad/board -->

| FIGURE | VALUE | Actual 2025 |
| --- | --- | --- |
| TOPPRICE | 90 | 100 |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026') == []
    }

    def "leaves a column heading naming no position alone in a table read across"() {
        given:
        File doc = document('''<!-- figures: fuad/curve across=POS field=PTS -->

| | QB | RB | what a flat curve would use |
| --- | --- | --- | --- |
| 1 | 245.2 | 188.4 | 200.0 |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026') == []
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
        DocsCheck.check(doc, figuresDir, '2026') == []
    }

    def "names a row the model does not carry"() {
        given:
        File doc = document('''<!-- figures: fuad/positions -->

| POS | PRICEDDEPTH |
| --- | --- |
| DEF | 20 |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026').any { it.contains("'DEF' is not in fuad/positions.tsv") }
    }

    def "says so when a marked table cites a field the model does not produce"() {
        given:
        File doc = document('''<!-- figures: fuad/curve across=POS field=NOTATHING -->

| | QB |
| --- | --- |
| 1 | 245.2 |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026').any { it.contains("'NOTATHING' is not a column of fuad/curve.tsv") }
    }

    def "says so when a marked table names no figures file"() {
        given:
        File doc = document('''<!-- figures: fuad/nosuchtable -->

| POS | X |
| --- | --- |
| QB | 1 |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026').any { it.contains("no figures file for 'fuad/nosuchtable'") }
    }

    def "stops checking at the end of the table, so a later unmarked one is left alone"() {
        given:
        File doc = document('''<!-- figures: fuad/positions -->

| POS | PRICEDDEPTH |
| --- | --- |
| QB | 36 |

Prose in between, which ends the marked table.

| POS | PRICEDDEPTH |
| --- | --- |
| QB | 999 |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026') == []
    }

    /**
     * A document nothing was checked in used to be indistinguishable from one whose every figure was right.
     *
     * Both produce no failures, and both printed OK, which is the drift this whole check exists to catch
     * wearing the word that says it was caught. Three of the four documents under docs/ carried no marker at
     * all and were reported OK on every run.
     */
    def "counts what it actually held the document to, so a pass cannot mean nothing was checked"() {
        given: 'two tables, five cells between them that name a figure, and one heading that names none'
        File doc = document('''<!-- figures: fuad/positions -->

| POS | PRICEDDEPTH | STARTED | note |
| --- | --- | --- | --- |
| QB | 36 | 20 | commentary, matching no field |
| RB | 65 | 26 | nor this |

Prose, which ends the table.

<!-- figures: fuad/board -->

| FIGURE | VALUE |
| --- | --- |
| Players | 106 |
''')

        when:
        DocsCheck.Result result = DocsCheck.inspect(doc, figuresDir, '2026')

        then: 'the commentary column is not counted, because nothing was compared for it'
        result.failures == []
        result.tables == 2
        result.verified == 5
    }

    def "reports a document with no marked table as checked against nothing at all"() {
        given: 'prose and a table, but no marker, which is DATA.md and LEAGUE_RULES.md today'
        File doc = document('''# Data

| POS | PRICEDDEPTH |
| --- | --- |
| QB | 999 |
''')

        when:
        DocsCheck.Result result = DocsCheck.inspect(doc, figuresDir, '2026')

        then: 'no failures, and no claim to have verified anything either'
        result.failures == []
        result.tables == 0
        result.verified == 0
    }

    /**
     * The figures have to be the model's own before the prose is held to them.
     *
     * Agreement between prose and figures that a superseded model wrote says only that the two were written
     * together. That is exactly the reassurance this check is supposed to be unable to give.
     */
    /**
     * The hole the commentary rule left, and the reason it could not simply be closed.
     *
     * A heading matching no field has to be allowed, or a table could never carry a note beside a figure.
     * Which meant a heading that was <b>meant</b> to bind and no longer did — mistyped, or left behind when
     * the figure was renamed underneath it — read as deliberate commentary: the column quietly stopped
     * being checked and the run went on printing OK. Every table in PROJECTION.md turns on exact heading
     * names, so this is the one way left to make a passing run mean nothing.
     */
    def "catches a heading that is one slip from a figure, where a plain miss is commentary"() {
        given: 'VALUEE for VALUE, which used to disable the column and pass, over two wrong rows'
        File doc = document('''<!-- figures: fuad/board -->

| FIGURE | VALUEE |
| --- | --- |
| Top price | 99999 |
| Players | 88888 |
''')

        when:
        List<String> failures = DocsCheck.check(doc, figuresDir, '2026')

        then: 'named, and pointed at what it was probably meant to be'
        failures.any { it.contains("'VALUEE'") && it.contains("'VALUE'") }

        and: 'reported once for the table rather than once for each of the rows beneath it'
        failures.size() == 1
    }

    def "catches a mistyped position in a table read across, which is two letters and used to be exempt"() {
        given: 'WB for WR, the commonest shape of table in the documentation'
        File doc = document('''<!-- figures: fuad/curve across=POS field=PTS -->

| Rank | QB | WB |
| --- | --- | --- |
| 1 | 245.2 | 99999 |
''')

        expect: 'every position it is equally near, since at two letters several usually are'
        DocsCheck.check(doc, figuresDir, '2026').any { it.contains("'WB'") && it.contains("'RB'") }
    }

    def "leaves real commentary alone, being nowhere near anything the model produces"() {
        given: 'a note and a column of what the league actually paid, neither of them a figure'
        File doc = document('''<!-- figures: fuad/board -->

| FIGURE | VALUE | Actual 2025 | note |
| --- | --- | --- | --- |
| Top price | 90 | $100 | the tag held him below it |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026') == []
    }

    /**
     * A table with one row per pair of things needs both of them to find a row.
     *
     * The tags are one per season and player and neither alone picks one out: a season holds several tags,
     * and a player is tagged in several seasons at different prices. Keyed by the first column alone, every
     * row of a season matched the first row of that season.
     */
    def "finds a row by a pair of columns where one column cannot name it"() {
        given:
        new File(yearDir, 'tags.tsv').text =
                "SEASON\tPLAYER\tPOS\tSALARY\n" +
                "2024\tLamar Jackson\tQB\t45\n" +
                "2024\tTravis Kelce\tTE\t38\n" +
                "2025\tLamar Jackson\tQB\t100\n"

        and: 'the same player twice, at two prices, which a season-keyed lookup would confuse'
        File doc = document('''<!-- figures: fuad/tags key=SEASON+PLAYER -->

| Season | Player | Pos | Salary |
| --- | --- | --- | --- |
| 2024 | Lamar Jackson | QB | 45 |
| 2024 | Travis Kelce | TE | 38 |
| 2025 | Lamar Jackson | QB | 100 |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026') == []
    }

    def "catches a drifted cell in a table keyed by a pair, naming both halves of the key"() {
        given:
        new File(yearDir, 'tags.tsv').text =
                "SEASON\tPLAYER\tPOS\tSALARY\n" +
                "2024\tLamar Jackson\tQB\t45\n" +
                "2025\tLamar Jackson\tQB\t100\n"
        File doc = document('''<!-- figures: fuad/tags key=SEASON+PLAYER -->

| Season | Player | Pos | Salary |
| --- | --- | --- | --- |
| 2025 | Lamar Jackson | QB | 45 |
''')

        expect: 'the 2025 row, not the 2024 one that carries a 45'
        DocsCheck.check(doc, figuresDir, '2026').any {
            it.contains('2025 LAMARJACKSON') && it.contains('is 100') && it.contains('cited as 45')
        }
    }

    def "says which pair it could not find when a keyed row is not there"() {
        given:
        new File(yearDir, 'tags.tsv').text =
                "SEASON\tPLAYER\tPOS\tSALARY\n" +
                "2024\tLamar Jackson\tQB\t45\n"
        File doc = document('''<!-- figures: fuad/tags key=SEASON+PLAYER -->

| Season | Player | Pos | Salary |
| --- | --- | --- | --- |
| 2024 | Tom Brody | QB | 41 |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026').any { it.contains("'2024 TOMBRODY' is not in fuad/tags.tsv") }
    }

    def "refuses to check against figures that have never been stamped"() {
        expect:
        DocsCheck.checkProvenance(yearDir).any { it.contains('holds no MANIFEST') }
    }

    def "refuses to check against figures built from uncommitted changes"() {
        given:
        new File(yearDir, 'MANIFEST').text =
                "# Written by figures_refresh.sh\n" +
                "board 1234abc-dirty 2026-08-17T16:09:39Z\n" +
                "curve 1234abc-dirty 2026-08-17T16:09:39Z\n"

        expect: 'the sha does not describe what ran, so nothing can be checked against it'
        DocsCheck.checkProvenance(yearDir).any {
            it.contains('uncommitted model') && it.contains('board, curve')
        }
    }

    def "says so when the figures name a model this repository does not have"() {
        given:
        new File(yearDir, 'MANIFEST').text =
                "# Written by figures_refresh.sh\n" +
                "board 0000000 2026-08-17T16:09:39Z\n"

        expect:
        DocsCheck.checkProvenance(yearDir).any { it.contains('does not have') }
    }

    /**
     * A heading that names a real position and finds no row is a fault, not commentary.
     *
     * The permissive rule is what lets a table carry a note beside a figure, and it used to swallow this
     * case too: any row that failed to match was passed over in silence, whatever the reason. So a table
     * read against the wrong key column reported nothing at all rather than reporting that it could not
     * find its rows — and the key column is inferred positionally, so inserting a column ahead of the real
     * one would do exactly that to every table reading the file.
     */
    def "reports a row it cannot find under a heading the figures do carry"() {
        given:
        File doc = document('''<!-- figures: fuad/curve across=POS field=PTS -->

| Rank | QB |
| --- | --- |
| 99 | 245.2 |
''')

        when:
        List<String> failures = DocsCheck.check(doc, figuresDir, '2026')

        then:
        failures.size() == 1
        failures[0].contains("curve.tsv has no QB row keyed '99' on RANK")
    }

    def "still leaves a heading the figures do not carry as the document's own commentary"() {
        given: 'RB is a real position here, so the note column is the only thing that binds to nothing'
        File doc = document('''<!-- figures: fuad/curve across=POS field=PTS -->

| Rank | QB | worth knowing |
| --- | --- | --- |
| 1 | 245.2 | the best of them |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026') == []
    }

    def "lets a table across the top name the column its rows are keyed by"() {
        given: 'the same table with the key stated rather than inferred'
        File doc = document('''<!-- figures: fuad/curve across=POS field=PTS key=RANK -->

| Rank | QB |
| --- | --- |
| 1 | 245.2 |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026') == []
    }

    def "says so when the named key is not a column of the figures at all"() {
        given:
        File doc = document('''<!-- figures: fuad/curve across=POS field=PTS key=SLOT -->

| Rank | QB |
| --- | --- |
| 1 | 245.2 |
''')

        expect:
        DocsCheck.check(doc, figuresDir, '2026').any { it.contains("'SLOT' is not a column of fuad/curve.tsv") }
    }
}
