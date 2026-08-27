package ff.print.greenfield

import ff.league.League
import ff.load.greenfield.GreenfieldBoard
import spock.lang.Shared
import spock.lang.Specification

/**
 * The draft sheet, against the real board, since every figure on it is the board's rearranged.
 */
class GreenfieldSheetPrinterSpec extends Specification {

    @Shared
    GreenfieldBoard board = new GreenfieldBoard('2026')

    @Shared
    List<String> lines = {
        StringWriter text = new StringWriter()
        text.withPrintWriter { PrintWriter out ->
            new GreenfieldSheetPrinter(board, League.GREENFIELD.scoredPositions).print(out)
        }
        text.toString().readLines()
    }()

    private static List<String> fields(String line) { line.split(',', -1) as List<String> }

    private static Map<String, String> row(List<String> lines, int i) {
        [fields(lines[0]), fields(lines[i])].transpose().collectEntries { [(it[0]): it[1]] }
    }

    def "one list in value order, every position together"() {
        given:
        List<BigDecimal> vor = lines.drop(1).collect { new BigDecimal(fields(it)[7]) }

        expect: 'a draft is one queue and reads best as one'
        vor == vor.sort(false).reverse()

        and: 'and every position the league prices is in it'
        lines.drop(1).collect { fields(it)[2] }.toSet() == League.GREENFIELD.scoredPositions.toSet()
    }

    def "VORRANK counts the list it is in, so it can be read against ADP"() {
        expect:
        lines.drop(1).eachWithIndex { String line, int i -> assert fields(line)[1] == "${i + 1}" }
    }

    def "EDGE is how far the room lets a player fall past his worth"() {
        expect: 'ADP less VORRANK, and blank where nine drafts have too little to say about the rank'
        lines.drop(1).every { String line ->
            List<String> f = fields(line)
            f[8] ? (f[9] as int) == (f[8] as int) - (f[1] as int) : !f[9]
        }
    }

    def "the keepers start taken, which is the state the draft actually begins in"() {
        given:
        Map<String, String> taken = lines.drop(1).collectEntries { [(fields(it)[4]): fields(it)[0]] }

        expect: 'filter the blanks and the sheet is the live board'
        taken['Cam Skattebo'] == 'brett-b'
        taken['Chris Olave'] == 'christian-k'
        taken['Jahmyr Gibbs'] == ''
    }

    def "a name carrying a comma survives being comma separated"() {
        expect: 'no row has more fields than the heading, which a bare comma in a name would cause'
        int width = fields(lines[0]).size()
        lines.drop(1).every { fields(it).size() == width }
    }

    def "a player the curve does not price is left off rather than shown at zero"() {
        expect: 'the sheet is what can be drafted from, and a rank nothing is known about cannot be'
        lines.size() - 1 < board.ranked.size()
        lines.drop(1).every { fields(it)[7] }
    }
}
