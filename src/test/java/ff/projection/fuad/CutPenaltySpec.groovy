package ff.projection.fuad

import ff.load.util.LoadUtils
import spock.lang.Specification
import spock.lang.Unroll

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * The cut penalty was the least verified rule the league has, stated by the commissioner and absent from
 * every export. The salary adjustments are the record of it being charged, so it is now checked against all
 * 384 of them rather than taken on trust. See docs/fuad/LEAGUE_RULES.md.
 */
class CutPenaltySpec extends Specification {

    private static final List<String> SEASONS = (2017..2025).collect { it as String }

    /** Adjustments name the contracts they cover: `Treylon Burks (2yrs@1) : Tyler Boyd (1yrs@4)`. */
    private static final Pattern CONTRACT = ~/\((\d+)yrs?@(\d+)\)/

    /** One charge the league actually made, and the contracts it covered. */
    private static class Charge {
        String season
        String description
        BigDecimal amount
        List<List<BigDecimal>> contracts = []
    }

    private static List<Charge> charges() {
        SEASONS.collectMany { String season ->
            def adjustments = LoadUtils.loadJsonResource(
                    LoadUtils.mflSalaryAdjustmentsResourcePath(season))?.salaryAdjustments?.salaryAdjustment
            if (!adjustments) {
                return []
            }
            ((adjustments instanceof List ? adjustments : [adjustments]) as List<Map>).collect { Map row ->
                Charge charge = new Charge(season: season, description: row.description as String,
                        amount: new BigDecimal(row.amount as String))
                Matcher matcher = CONTRACT.matcher(charge.description)
                while (matcher.find()) {
                    charge.contracts << [new BigDecimal(matcher.group(1)), new BigDecimal(matcher.group(2))]
                }
                charge
            }
        }
    }

    def "every adjustment in the league's history is a cut penalty naming its contracts"() {
        when:
        List<Charge> charges = charges()

        then: 'nine seasons of them, and none is anything else'
        charges.size() == 384
        charges.every { it.contracts }
    }

    def "the rule reproduces what the league actually charged"() {
        given:
        List<Charge> charges = charges()

        when: 'each covered contract is priced by the rule and the batch summed'
        List<Charge> wrong = charges.findAll { Charge charge ->
            charge.contracts.sum { List c -> CutPenalty.of(c[0] as int, c[1] as BigDecimal) } != charge.amount
        }

        then: 'all but one, which is a six-cut batch entered a dollar light'
        wrong.size() == 1
        wrong[0].season == '2020'
        wrong[0].contracts.size() == 6
        charges.size() - wrong.size() == 383
    }

    def "both halves of the rule are doing work"() {
        given:
        List<Charge> charges = charges()

        when: 'the rate alone, then the floor alone'
        int rateOnly = charges.count { Charge charge ->
            charge.contracts.sum { List c ->
                ((CutPenalty.RATE * c[0] * c[1]).setScale(0, java.math.RoundingMode.CEILING)) as int
            } == charge.amount
        }
        int floorOnly = charges.count { Charge charge ->
            charge.contracts.sum { List c -> c[0] as int } == charge.amount
        }

        then: 'neither explains the record on its own, and the greater of the two explains nearly all of it'
        rateOnly == 225
        floorOnly == 253
    }

    def "the floor is what governs a cheap contract, and only a cheap one"() {
        given: 'every individual contract released across nine seasons'
        List<List<BigDecimal>> contracts = charges().collectMany { it.contracts }

        when: 'the ones the minimum decides rather than the rate'
        List<List<BigDecimal>> governed = contracts.findAll { List c ->
            CutPenalty.MINIMUM_PER_YEAR * c[0] > CutPenalty.RATE * c[0] * c[1]
        }

        then: 'three quarters of all releases, and never above the $2.50 the rule implies'
        contracts.size() == 615
        governed.size() == 460
        governed.collect { it[1] }.toSet() == [1.0, 2.0] as Set
        CutPenalty.FLOOR_BINDS_BELOW == 2.5
    }

    @Unroll
    def 'a #years year deal at #salary a year costs #expected to release'() {
        expect:
        CutPenalty.of(years, salary as BigDecimal) == expected

        where:
        years | salary | expected
        5     | 1      | 5          // the floor: five times what he costs to keep for a year
        5     | 2      | 5          // still the floor, at $2.50 a year of value
        3     | 1      | 3
        1     | 1      | 1
        3     | 10     | 12         // the rate, on real money
        1     | 50     | 20
        1     | 5      | 2
        1     | 3      | 2          // 1.2 rounded up, which is what the record shows
        0     | 40     | 0          // nothing left to run, nothing to pay
    }
}
