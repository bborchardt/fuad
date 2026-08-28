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
        List<BigDecimal> vor = lines.drop(1).findAll { fields(it)[7] }
                .collect { new BigDecimal(fields(it)[7]) }

        expect: 'a draft is one queue and reads best as one, as far as the board has opinions'
        vor == vor.sort(false).reverse()

        and: 'and every position the league prices is in it'
        lines.drop(1).collect { fields(it)[2] }.toSet() == League.GREENFIELD.scoredPositions.toSet()
    }

    def "the ranks the curve declines to price are listed last, and blank rather than zero"() {
        given:
        List<String> vors = lines.drop(1).collect { fields(it)[7] }
        int lastPriced = vors.findLastIndexOf { it }

        expect: 'the sheet runs out of value before it runs out of players, and says which is which'
        vors.take(lastPriced + 1).every { it }
        vors.drop(lastPriced + 1).every { !it }
        lastPriced + 1 < vors.size()

        and: 'a rank nothing is known about carries no tier and no points either'
        lines.drop(1).findAll { !fields(it)[7] }.every { !fields(it)[10] && !fields(it)[11] }
    }

    def "an unpriced player still carries what a draft needs to find him"() {
        given:
        List<String> deep = lines.drop(1).findAll { !fields(it)[7] }

        expect: 'position, rank, name and bye, which is what a late round pick is made on'
        deep.every { fields(it)[2] && fields(it)[3] && fields(it)[4] }

        and: 'and enough of them that the draft cannot run past the end of the sheet'
        deep.size() > 100
    }

    def "VORRANK counts the value order, so only the part that has one carries a place in it"() {
        given:
        List<String> priced = lines.drop(1).findAll { fields(it)[7] }

        expect:
        priced.eachWithIndex { String line, int i -> assert fields(line)[1] == "${i + 1}" }

        and:
        lines.drop(1).findAll { !fields(it)[7] }.every { !fields(it)[1] }
    }

    def "EDGE is how far the room lets a player fall past his worth"() {
        expect: 'ADP less VORRANK, and blank where either is missing'
        lines.drop(1).every { String line ->
            List<String> f = fields(line)
            f[8] && f[1] ? (f[9] as int) == (f[8] as int) - (f[1] as int) : !f[9]
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

    def "everyone ranked is on it, since a full roster has to come from somewhere"() {
        expect: 'fifteen rounds of fourteen is 210 picks, and the sheet has to outrun that at every position'
        lines.size() - 1 == board.ranked.size()
        lines.drop(1).collect { fields(it)[2] }.countBy { it }.every { pos, n -> n > 15 }
    }
}
