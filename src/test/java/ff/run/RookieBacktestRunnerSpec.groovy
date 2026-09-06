package ff.run

import ff.run.fuad.RookieBacktestRunner
import spock.lang.Specification
import spock.lang.TempDir

class RookieBacktestRunnerSpec extends Specification {
    @TempDir File directory

    def 'empty results replace stale reports with headers'() {
        given:
        RookieBacktestRunner.COLUMNS.keySet().each { new File(directory, "${it}.tsv").text = 'stale\n' }

        when:
        RookieBacktestRunner.writeReports(directory, [selection: []])

        then:
        RookieBacktestRunner.COLUMNS.every { name, columns ->
            new File(directory, "${name}.tsv").readLines() == [columns.join('\t')]
        }
    }

    def 'column order comes from the schema and missing cells remain blank'() {
        when:
        RookieBacktestRunner.writeReports(directory, [calibration: [[factor: .5d, year: 1, draftClass: '2021']]])

        then:
        new File(directory, 'calibration.tsv').readLines() ==
                ['draftClass\tposition\tyear\tfactor', '2021\t\t1\t0.500000']
        new File(directory, 'selection.tsv').readLines().size() == 1
    }
}
