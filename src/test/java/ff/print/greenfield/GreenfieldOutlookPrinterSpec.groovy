package ff.print.greenfield

import ff.league.League
import ff.load.greenfield.GreenfieldBoard
import spock.lang.Shared
import spock.lang.Specification

/**
 * One slot's outlook, against the real board, because what it reports is the board's own arithmetic.
 */
class GreenfieldOutlookPrinterSpec extends Specification {

    @Shared
    GreenfieldBoard board = new GreenfieldBoard('2026')

    /** Split keeping trailing blanks: the last round is past every ADP and so is empty to the end. */
    private static List<String> fields(String line) { line.split('\t', -1) as List<String> }

    private List<String> outlook(int slot, Set<Integer> forfeited, Map<String, Integer> held = [:]) {
        StringWriter text = new StringWriter()
        text.withPrintWriter { PrintWriter out ->
            new GreenfieldOutlookPrinter(board, slot, 14, 15, forfeited,
                    League.GREENFIELD.scoredPositions, League.GREENFIELD.starterMinimums,
                    League.GREENFIELD.starterMaximums, board.heldBy(slot, held)).print(out)
        }
        text.toString().readLines()
    }

    private static List<Map<String, String>> taken(List<String> lines) {
        List<String> headings = fields(lines[0])
        lines.drop(1).findAll { fields(it)[11] == 'TAKE' }
                .collect { [headings, fields(it)].transpose().collectEntries { [(it[0]): it[1]] } }
    }

    def "a forfeited pick is left out of the slot's own picks"() {
        given: 'slot 13 gave up its eighth, which is pick 100'
        List<String> without = outlook(13, [100] as Set)
        List<String> with = outlook(13, [] as Set)

        expect:
        !without.any { fields(it)[1] == '100' }
        with.any { fields(it)[1] == '100' }

        and: 'and the pick before it now looks across the gap the keeper made'
        fields(without.find { it.startsWith('7\t97\t') })[7] == '125'
        fields(with.find { it.startsWith('7\t97\t') })[7] == '100'
    }

    def "the position that cannot wait is reported first at every pick"() {
        given:
        List<String> lines = outlook(13, [100] as Set).drop(1)

        expect: 'usable positions first, and within them by decay, so the top row is the one to act on'
        lines.groupBy { fields(it)[1] }.every { String pick, List<String> rows ->
            List<List> keys = rows.collect { String row ->
                String d = fields(row)[10]
                [fields(row)[4] == 'FULL' ? 0 : 1, d ? new BigDecimal(d) : -999.0g]
            }
            keys == keys.sort(false) { a, b -> (b[0] <=> a[0]) ?: (b[1] <=> a[1]) }
        }
    }

    def "running back decays hardest across the long wait after the turn"() {
        given: 'slot 13 picks at 16 and not again until 41, which is 24 picks of other people drafting'
        List<String> atSixteen = outlook(13, [100] as Set).findAll { it.startsWith('2\t16\t') }

        expect: 'the room empties the backs in that gap and leaves the quarterbacks alone'
        fields(atSixteen.first())[2] == 'RB'

        and:
        new BigDecimal(fields(atSixteen.first())[10]) >
                new BigDecimal(fields(atSixteen.find { fields(it)[2] == 'QB' })[10])
    }

    def "the last pick of the draft has nothing after it, and says so rather than reporting a decay"() {
        given:
        List<String> lines = outlook(13, [] as Set)
        String lastPick = fields(lines.drop(1).last())[1]

        expect:
        lines.findAll { fields(it)[1] == lastPick }.every { !fields(it)[10] }
    }

    def "a position that cannot start another player is never recommended"() {
        given: 'a roster already at every cap the league allows'
        Map<String, Integer> full = [QB: 1, RB: 3, WR: 3, TE: 2, PK: 1, DST: 1]

        when:
        List<String> lines = outlook(13, [] as Set, full)

        then: 'every position is FULL at every pick, and nothing is advised'
        lines.drop(1).every { fields(it)[4] == 'FULL' }
        taken(lines).isEmpty()
    }

    def "the plan fills what it needs, then stops rather than inventing a bench preference"() {
        given:
        List<String> lines = outlook(13, [100] as Set)
        List<Map<String, String>> plan = taken(lines)

        expect: 'it fills to the caps -- one quarterback, three backs, three receivers, two ends, a kicker'
        plan.count { it.POS == 'QB' } <= 1
        plan.count { it.POS == 'RB' } <= 3
        plan.count { it.POS == 'WR' } <= 3
        plan.count { it.POS == 'TE' } <= 2
        plan.count { it.POS == 'PK' } <= 1

        and: 'and once they are full the later rounds carry no recommendation at all'
        plan.size() < 15
        lines.drop(1).findAll { fields(it)[11] == 'TAKE' }*.with { fields(it)[4] } .every { it != 'FULL' }
    }

    def "a keeper is counted without being asked for"() {
        given: 'slot 13 keeps Cam Skattebo, a back'
        List<String> lines = outlook(13, [100] as Set)

        expect: 'the first pick is advised against a roster that already holds one back'
        fields(lines.drop(1).find { fields(it)[2] == 'RB' })[3] == '1'
    }

    def "what has already been taken re-plans the rest"() {
        given: 'two backs taken since the draft started, on top of the keeper'
        List<Map<String, String>> withBacks = taken(outlook(13, [100] as Set, [RB: 2]))
        // Skattebo is the third, so the position is full before the first pick is made.
        List<Map<String, String>> without = taken(outlook(13, [100] as Set))

        expect: 'the plan stops recommending backs once the position is full and spends the picks elsewhere'
        withBacks.count { it.POS == 'RB' } < without.count { it.POS == 'RB' }
        withBacks.first().POS != 'RB'
    }

    def "HELD and STATUS describe the same roster, being the one the decision is made from"() {
        given:
        List<String> lines = outlook(13, [100] as Set)

        expect: 'two backs held against a minimum of two is FLEX, never NEED beside a number that met it'
        lines.drop(1).every { String line ->
            List<String> f = fields(line)
            int have = f[3] as int
            int minimum = League.GREENFIELD.starterMinimums[f[2]] ?: 0
            int maximum = League.GREENFIELD.starterMaximums[f[2]] ?: 0
            f[4] == (have < minimum ? 'NEED' : have < maximum ? 'FLEX' : 'FULL')
        }
    }
}
