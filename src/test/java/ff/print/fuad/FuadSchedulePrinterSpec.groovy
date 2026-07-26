package ff.print.fuad

import ff.data.fuad.FuadMatchup
import spock.lang.Specification

class FuadSchedulePrinterSpec extends Specification {

    def "prints week,franchise1Id,franchise2Id lines sorted by week"() {
        given:
        List<FuadMatchup> matchups = [
                new FuadMatchup(2, '0002', '0003'),
                new FuadMatchup(1, '0006', '0004'),
        ]
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        PrintStream original = System.out

        when:
        System.out = new PrintStream(out)
        new FuadSchedulePrinter(matchups).print()

        then:
        out.toString().readLines() == ['01,0006,0004', '02,0002,0003']

        cleanup:
        System.out = original
    }
}
