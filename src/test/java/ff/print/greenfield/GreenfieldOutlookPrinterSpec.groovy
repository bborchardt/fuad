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

    private List<String> outlook(int slot, Set<Integer> forfeited) {
        StringWriter text = new StringWriter()
        text.withPrintWriter { PrintWriter out ->
            new GreenfieldOutlookPrinter(board, slot, 14, 15, forfeited,
                    League.GREENFIELD.scoredPositions).print(out)
        }
        text.toString().readLines()
    }

    def "a forfeited pick is left out of the slot's own picks"() {
        given: 'slot 13 gave up its eighth, which is pick 100'
        List<String> without = outlook(13, [100] as Set)
        List<String> with = outlook(13, [] as Set)

        expect:
        !without.any { fields(it)[1] == '100' }
        with.any { fields(it)[1] == '100' }

        and: 'and the pick before it now looks across the gap the keeper made'
        fields(without.find { it.startsWith('7\t97\t') })[5] == '125'
        fields(with.find { it.startsWith('7\t97\t') })[5] == '100'
    }

    def "the position that cannot wait is reported first at every pick"() {
        given:
        List<String> lines = outlook(13, [100] as Set).drop(1)

        expect: 'rows are grouped by pick and ordered by decay within it, so the top row is the one to act on'
        lines.groupBy { fields(it)[1] }.every { String pick, List<String> rows ->
            List<BigDecimal> decays = rows.collect { String row ->
                String d = fields(row)[8]
                d ? new BigDecimal(d) : -999.0g
            }
            decays == decays.sort(false).reverse()
        }
    }

    def "running back decays hardest across the long wait after the turn"() {
        given: 'slot 13 picks at 16 and not again until 41, which is 24 picks of other people drafting'
        List<String> atSixteen = outlook(13, [100] as Set).findAll { it.startsWith('2\t16\t') }

        expect: 'the room empties the backs in that gap and leaves the quarterbacks alone'
        fields(atSixteen.first())[2] == 'RB'

        and:
        new BigDecimal(fields(atSixteen.first())[8]) >
                new BigDecimal(fields(atSixteen.find { fields(it)[2] == 'QB' })[8])
    }

    def "the last pick of the draft has nothing after it, and says so rather than reporting a decay"() {
        given:
        List<String> lines = outlook(13, [] as Set)
        String lastPick = fields(lines.drop(1).last())[1]

        expect:
        lines.findAll { fields(it)[1] == lastPick }.every { !fields(it)[8] }
    }
}
