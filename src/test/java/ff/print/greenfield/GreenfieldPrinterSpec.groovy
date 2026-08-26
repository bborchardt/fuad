package ff.print.greenfield

import ff.data.greenfield.KeeperSurplus
import spock.lang.Specification

/** The two sheets that are pure arithmetic on what they are handed, asserted without building a curve. */
class GreenfieldPrinterSpec extends Specification {

    private static List<String> print(Closure<Void> printing) {
        StringWriter text = new StringWriter()
        text.withPrintWriter { PrintWriter out -> printing(out) }
        text.toString().readLines()
    }

    def "the keeper sheet leads with the best decision and reports both readings"() {
        given:
        List<KeeperSurplus> keepers = [
                new KeeperSurplus(owner: 'a', player: 'Thin', position: 'TE', positionRank: 6, costRound: 8,
                        costPick: 109, keeperValue: 31.4, alternativeValue: 25.7,
                        measuredAlternativeValue: 37.0, priorRound: 6, eligible: true),
                new KeeperSurplus(owner: 'b', player: 'Fat', position: 'QB', positionRank: 3, costRound: 8,
                        costPick: 107, keeperValue: 67.1, alternativeValue: 11.2,
                        measuredAlternativeValue: 37.0, priorRound: 7, eligible: true),
        ]

        when:
        List<String> lines = print { out -> new GreenfieldKeeperPrinter(keepers).print(out) }

        then: 'sorted by the measured reading, which is the one to act on'
        lines[1].startsWith('b\tFat\t')
        lines[2].startsWith('a\tThin\t')

        and: 'a keeper worth less than the pick shows negative rather than being dropped'
        lines[2].endsWith('\t5.7\t-5.6')
    }

    def "a keeper with no measurement leaves the column blank rather than calling it zero"() {
        given:
        List<KeeperSurplus> keepers = [new KeeperSurplus(owner: 'a', player: 'X', position: 'RB',
                positionRank: 1, costRound: 2, costPick: 3, keeperValue: 50.0, alternativeValue: 20.0,
                measuredAlternativeValue: null, priorRound: null, eligible: true)]

        when:
        List<String> lines = print { out -> new GreenfieldKeeperPrinter(keepers).print(out) }

        then: 'absent and worthless are different claims, and an undrafted prior year says so in words'
        lines[1].endsWith('\t50.0\t20.0\t\t30.0\t')
        lines[1].contains('undrafted')
    }

    def "an ineligible keeper is marked rather than silently priced"() {
        given:
        List<KeeperSurplus> keepers = [new KeeperSurplus(owner: 'a', player: 'X', position: 'RB',
                positionRank: 1, costRound: 8, costPick: 99, keeperValue: 50.0, alternativeValue: 20.0,
                priorRound: 2, eligible: false)]

        when:
        List<String> lines = print { out -> new GreenfieldKeeperPrinter(keepers).print(out) }

        then:
        lines[1].contains('\tNO\t')
    }

    def "the pick sheet reports a round by its ends and the drop across it"() {
        given: 'two rounds of four teams, the first falling further than the second'
        Map<Integer, BigDecimal> byPick = [1: 100.0, 2: 96.0, 3: 92.0, 4: 90.0,
                                           5: 88.0, 6: 87.0, 7: 86.0, 8: 85.0]

        when:
        List<String> lines = print { out -> new GreenfieldPickPrinter(byPick, 4).print(out) }

        then:
        lines[1] == ['1', '1', '4', '100.0', '90.0', '10.0'].join('\t')
        lines[2] == ['2', '5', '8', '88.0', '85.0', '3.0'].join('\t')
    }

    def "a part-drafted round is reported by the picks that exist, not padded out"() {
        given: 'the last round stops early, as it does when keepers take picks out of the draft'
        Map<Integer, BigDecimal> byPick = [1: 100.0, 2: 96.0, 3: 92.0, 4: 90.0, 5: 88.0, 6: 87.0]

        when:
        List<String> lines = print { out -> new GreenfieldPickPrinter(byPick, 4).print(out) }

        then:
        lines.size() == 3
        lines[2] == ['2', '5', '6', '88.0', '87.0', '1.0'].join('\t')
    }
}
