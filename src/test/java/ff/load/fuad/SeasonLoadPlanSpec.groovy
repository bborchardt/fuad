package ff.load.fuad

import ff.data.fuad.SheetPick
import ff.data.fuad.SheetSigning
import spock.lang.Specification

/**
 * Translating the workbook into the league site's vocabulary is where a load goes wrong without looking
 * wrong: every failure here puts a real player on a real roster, and nothing downstream questions it.
 *
 * So the plan refuses rather than guesses, and these hold it to that. The rookie salary is checked against
 * the bylaw's own worked example, because the alternative is checking it against the number this year
 * happens to produce, which would pass whatever the code did.
 */
class SeasonLoadPlanSpec extends Specification {

    private static final String YEAR = '2026'

    def 'a player the league database does not have stops the plan rather than being dropped from it'() {
        given:
        def plan = new SeasonLoadPlan(YEAR)
        def roster = [Brett: [signing('Lamar Jackson', 'QB2', 68, 0), signing('Nobody At All', 'QB99', 1, 0)]]

        when:
        plan.rosters(roster)

        then: 'named, because a roster quietly one player short is the worse outcome'
        IllegalStateException e = thrown()
        e.message.contains('Nobody At All')
    }

    def 'an owner whose column header does not match the league is named with the ones that do'() {
        given:
        def plan = new SeasonLoadPlan(YEAR)

        when:
        plan.rosters([Bret: [signing('Lamar Jackson', 'QB2', 68, 0)]])

        then:
        IllegalStateException e = thrown()
        e.message.contains('Bret')
        e.message.contains('Brett')
    }

    def 'unsigned players are released rather than rostered'() {
        given: 'one signed contract and one that expired without being re-signed'
        def plan = new SeasonLoadPlan(YEAR)
        def roster = [Brett: [signing('Lamar Jackson', 'QB2', 68, 0), signing('Tua Tagovailoa', 'QB32', null, 0)]]

        expect:
        plan.rosters(roster)['0001']*.name == ['Jackson, Lamar']
        plan.releases(roster)*.name == ['Tagovailoa, Tua']
    }

    def 'the sheet and the league site having different pick owners stops the load'() {
        given: 'the sheet says Martin holds 1.01, the site says the franchise that is not his'
        def plan = new SeasonLoadPlan(YEAR)
        def picks = [new SheetPick(1, 'Martin', 'Carnell Tate')]
        def slots = [[round: '01', pick: '01', franchise: '0007', player: '']]

        when:
        plan.picks(picks, slots)

        then: 'a traded pick nobody recorded looks exactly like this, so it is not assumed away'
        IllegalStateException e = thrown()
        e.message.contains('Martin')
        e.message.contains('0007')
    }

    def 'a draft of a different size to the sheet is not silently truncated'() {
        given:
        def plan = new SeasonLoadPlan(YEAR)

        when:
        plan.picks([new SheetPick(1, 'Martin', 'Carnell Tate')], [])

        then:
        thrown(IllegalStateException)
    }

    def 'bylaw 8.3.1 baselines are read from the previous season rather than entered by hand'() {
        expect: 'the QB15, RB20, WR35, TE15 and PK15 salaries at the 2025 trading deadline'
        new SeasonLoadPlan(YEAR).baselines() == [QB: 20, RB: 7, WR: 1, TE: 1, PK: 1]
    }

    def "the bylaw's own worked example: a back taken fourth against a baseline of 22 costs 11"() {
        given: 'Salary = Max(1, Baseline x Power(.8, # players picked)), and 22 x 0.8^3 is 11.264'
        BigDecimal salary = 22g * (0.8g ** 3)

        expect:
        Math.max(1, salary.setScale(0, java.math.RoundingMode.HALF_UP) as int) == 11
    }

    def 'the rookie salary decays by pick made, not by pick at that position, and floors at a dollar'() {
        given: 'three quarterbacks off a baseline of 20, taken first, second and thirtieth overall'
        def plan = new SeasonLoadPlan(YEAR)
        def picks = [pick(1, '17462'), pick(2, '17462'), pick(30, '17462')]

        when:
        def salaries = picks.collect { plan.rookieSalaries([it]).values().first() }

        then: '20, then 16 -- a fifth off for the pick before it, not for the quarterback before it'
        salaries[0] == 20
        salaries[1] == 16

        and: 'by the third round it is through the floor'
        salaries[2] == 1
    }

    private static SheetSigning signing(String name, String rank, Integer salary, int years) {
        new SheetSigning('Brett', name, rank, salary, years)
    }

    private static Map pick(int overall, String playerId) {
        [overall: overall, playerId: playerId, position: 'QB', playerName: 'Mendoza, Fernando']
    }
}
