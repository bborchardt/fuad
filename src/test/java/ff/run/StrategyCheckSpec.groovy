package ff.run

import ff.run.fuad.ReportManifest
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * A plan may reason from the board and from nothing else. The checks that hold it there: that the report
 * it cites came from the model it names, that its figures are the board's figures, and that nothing behind
 * the board is quoted into it. See docs/STRATEGY.md.
 */
class StrategyCheckSpec extends Specification {

    @TempDir
    File temp

    private File reportsDir
    private File yearDir

    def setup() {
        reportsDir = new File(temp, 'reports')
        yearDir = new File(reportsDir, '2026')
        yearDir.mkdirs()
        // Every position the league starts, since the board a plan is checked against carries all of them
        // and the check reads its vocabulary out of exactly that.
        new File(yearDir, 'salaries.tsv').text =
                "POS\tRANK\tPLAYER\tPTS\tVALUE\tPRICE\tAVAIL\n" +
                "QB\t2\tLamar Jackson\t244\t72\t76\t0.26\n" +
                "QB\t9\tKyler Murray\t174\t30\t21\t1.00\n" +
                "RB\t5\tJames Cook\t177\t72\t79\t0.26\n" +
                "WR\t4\tAmon-Ra St. Brown\t173\t57\t80\t0.26\n" +
                "TE\t3\tTrey McBride\t120\t30\t28\t0.46\n" +
                "PK\t1\tCameron Dicker\t0\t1\t1\t1.00\n"
        new File(yearDir, 'teams.tsv').text =
                "TEAM\tOWNER\tROSTER\tFREECAP\n" +
                "0001\tBrett\t29\t243\n"
    }

    private void manifest(String model, List<String> types = ['salaries', 'teams']) {
        new File(yearDir, ReportManifest.FILE_NAME).text =
                types.collect { "$it $model 2026-08-15T16:20:31Z" }.join('\n') + '\n'
    }

    private File document(String body) {
        File file = new File(temp, '2026-draft-plan.md')
        file.text = body
        file
    }

    def "passes a plan whose figures are the board's"() {
        given:
        manifest('86277a2')
        File plan = document('''<!-- model: 86277a2 -->

<!-- source: salaries -->

| PLAYER | PRICE | PTS | note |
| --- | --- | --- | --- |
| Kyler Murray | 21 | 174 | nobody can match |
| Lamar Jackson | 76 | 244 | walk away here |
''')

        expect:
        StrategyCheck.check(plan, reportsDir) == []
    }

    def "catches a figure the board disagrees with"() {
        given:
        manifest('86277a2')
        File plan = document('''<!-- model: 86277a2 -->

<!-- source: salaries -->

| PLAYER | PRICE |
| --- | --- |
| Lamar Jackson | 50 |
''')

        when:
        List<String> failures = StrategyCheck.check(plan, reportsDir)

        then:
        failures.size() == 1
        failures[0].contains('Lamar Jackson PRICE is 76 on the board, cited as 50')
    }

    def "catches a plan written against a model that has since moved"() {
        given: 'the reports were regenerated after the plan was written'
        manifest('86277a2')
        File plan = document('''<!-- model: a887b0a -->

<!-- source: salaries -->

| PLAYER | PRICE |
| --- | --- |
| Lamar Jackson | 76 |
''')

        when:
        List<String> failures = StrategyCheck.check(plan, reportsDir)

        then: 'the drift is reported even though this particular figure happens to be right'
        failures.any { it.contains('written against model a887b0a') && it.contains('86277a2') }
    }

    def "refuses to check against a board built from uncommitted changes"() {
        given:
        manifest('86277a2-dirty')
        File plan = document('''<!-- model: 86277a2 -->

<!-- source: salaries -->

| PLAYER | PRICE |
| --- | --- |
| Lamar Jackson | 76 |
''')

        expect:
        StrategyCheck.check(plan, reportsDir).any { it.contains('uncommitted model') }
    }

    def "requires the plan to declare a model at all"() {
        given:
        manifest('86277a2')

        expect:
        StrategyCheck.check(document('# A plan with no provenance\n'), reportsDir)
                .any { it.contains('no model declared') }
    }

    def "rejects reasoning from #term, which is behind the board rather than on it"() {
        given:
        manifest('86277a2')

        when:
        List<String> failures = StrategyCheck.check(
                document("<!-- model: 86277a2 -->\n\nThe premium shows up in $term.\n"), reportsDir)

        then:
        failures.any { it.contains(reported) }

        where:
        term                 | reported
        'rules.json'         | 'rules.json'
        'PointsCurve'        | 'PointsCurve'
        'SPEND_RATE'         | 'SPEND_RATE'
        'nflverse'           | 'nflverse'
        'src/main/java'      | 'src/'
        'docs/PROJECTION.md' | 'docs/'
    }

    /**
     * The failure the old check had, asserted so it cannot come back.
     *
     * None of these was on the denylist, because all of them were written after it was. That is the defect
     * inverting the question fixes: a plan's vocabulary is now checked against what the board says rather
     * than against a list of what it must not say, so an internal is covered the day it exists.
     */
    @Unroll
    def "rejects #term, which no denylist was ever told about"() {
        given:
        manifest('86277a2')

        when:
        List<String> failures = StrategyCheck.check(
                document("<!-- model: 86277a2 -->\n\nThe reasoning rests on $term.\n"), reportsDir)

        then:
        failures.any { it.contains(term) }

        where:
        term << ['LineupValue', 'CutPenalty', 'KICKER_DEPTH', 'SAMPLES', 'MONOTONICITY_WINDOW',
                 'FuadLoader', 'salary_adjustments.json', 'kicker_rankings.csv']
    }

    def "rejects an internal nobody has written yet"() {
        given: 'a constant that does not exist in the model at all, standing in for one added tomorrow'
        manifest('86277a2')

        expect: 'fails closed: it is not on the board, so it is behind it'
        StrategyCheck.check(
                document('<!-- model: 86277a2 -->\n\nWe lean on SOME_FUTURE_CONSTANT here.\n'), reportsDir)
                .any { it.contains('SOME_FUTURE_CONSTANT') }
    }

    def "lets a plan name every column and value the board actually carries"() {
        given:
        manifest('86277a2')

        expect: 'columns, players, owners and positions are all the board\'s own words'
        StrategyCheck.check(document('''<!-- model: 86277a2 -->

Lamar Jackson is QB2 at a PRICE of 76 with PTS of 244, and AVAIL says 0.26. Brett holds him.
Kyler Murray is the unrestricted one. Compare VALUE against PRICE before bidding.
'''), reportsDir) == []
    }

    def "lets a plan write a position and a rank run together, which nothing carries literally"() {
        given:
        manifest('86277a2')

        expect: 'QB2 is a POS cell and a RANK cell side by side, and the plainest shorthand in the game'
        StrategyCheck.check(document('''<!-- model: 86277a2 -->

QB2 is the one to walk away from; WR38 and TE13 are where the bench value is.
'''), reportsDir) == []
    }

    def "still rejects shorthand whose letters the board does not use"() {
        given:
        manifest('86277a2')

        expect: 'the position has to be one the board itself knows, so this is not a hole'
        StrategyCheck.check(document('''<!-- model: 86277a2 -->

The FOO2 slot is where the value is.
'''), reportsDir).any { it.contains('FOO2') }
    }

    def "lets a plan use ordinary football shorthand the board has no column for"() {
        given:
        manifest('86277a2')

        expect:
        StrategyCheck.check(document('''<!-- model: 86277a2 -->

Half PPR scoring, and the NFL bye weeks are what make depth worth holding.
'''), reportsDir) == []
    }

    def "leaves ordinary auction prose alone"() {
        given:
        manifest('86277a2')

        expect: 'the words a plan is actually written in are not model internals'
        StrategyCheck.check(document('''<!-- model: 86277a2 -->

The tag is $66, above both prices, and the right to match already protects us. Replacement at quarterback
is high, so the mid tier is where the points per dollar sit.
'''), reportsDir) == []
    }

    def "allows the source markers themselves to name a report"() {
        given:
        manifest('86277a2')

        expect:
        StrategyCheck.check(document('<!-- model: 86277a2 -->\n\n<!-- source: salaries -->\n'),
                reportsDir) == []
    }

    def "checks a table keyed by owner against the team context report"() {
        given:
        manifest('86277a2')
        File plan = document('''<!-- model: 86277a2 -->

<!-- source: teams -->

| OWNER | FREECAP |
| --- | --- |
| Brett | 240 |
''')

        expect:
        StrategyCheck.check(plan, reportsDir).any { it.contains('Brett FREECAP is 243') }
    }

    def "names a player the board does not carry"() {
        given:
        manifest('86277a2')
        File plan = document('''<!-- model: 86277a2 -->

<!-- source: salaries -->

| PLAYER | PRICE |
| --- | --- |
| Some Rookie | 12 |
''')

        expect:
        StrategyCheck.check(plan, reportsDir).any { it.contains("'Some Rookie' is not on the salaries board") }
    }

    def "reads figures through the markdown they are dressed in"() {
        expect:
        StrategyCheck.clean('**$76**') == '76'
        StrategyCheck.matches('.26', '0.26')
        StrategyCheck.matches('76', '76')
        !StrategyCheck.matches('50', '76')
    }
}
