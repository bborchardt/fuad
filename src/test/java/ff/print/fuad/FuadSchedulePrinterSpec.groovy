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
        StringWriter out = new StringWriter()

        when:
        new FuadSchedulePrinter(matchups).print(new PrintWriter(out))

        then:
        out.toString().readLines() == ['01,0006,0004', '02,0002,0003']
    }
}
