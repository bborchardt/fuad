package ff.projection

import spock.lang.Specification

/**
 * A bench is worth something because a season is not its own expectation.
 *
 * Value over replacement taken at a player's projection is `max(0, E[X] - replacement)`. What a roster spot
 * is actually worth is `E[max(0, X - replacement)]`, since a player only has to be started in the weeks he
 * is good. The second is never smaller and the gap is widest at replacement level, which is exactly where a
 * bench sits. See docs/PROJECTION.md.
 */
class ExpectedValueOverReplacementSpec extends Specification {

    /** One position, ranks projected 200 down to 20 over ten even weeks. */
    private static PointsCurve curveWith(Map<Integer, List<BigDecimal>> realised) {
        Map<Integer, Map<String, BigDecimal>> projected = (1..10).collectEntries { int week ->
            [(week): (1..30).collectEntries { [("p$it".toString()): ((210 - it * 6) / 10) as BigDecimal] }]
        }
        Map<String, String> positions = (1..30).collectEntries { [("p$it".toString()): 'WR'] }
        PointsCurve.of(projected, positions, [WR: realised])
    }

    /** Realised scoring equal to expectation, so the curve is unchanged and there is no spread. */
    private static Map<Integer, List<BigDecimal>> certain() {
        (1..30).collectEntries { [(it): [(210 - it * 6) as BigDecimal]] }
    }

    /** The same, plus seasons at half and one and a half, so outcomes spread either side. */
    private static Map<Integer, List<BigDecimal>> uncertain() {
        (1..30).collectEntries { int rank ->
            BigDecimal expected = (210 - rank * 6) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5]]
        }
    }

    private static Map<String, Map<Integer, BigDecimal>> replacementAt(BigDecimal weekly) {
        [WR: (1..10).collectEntries { [(it): weekly] }]
    }

    def "outcome multipliers average one, so carrying a spread moves no expected points"() {
        given:
        List<Double> multipliers = curveWith(uncertain()).outcomeMultipliers('WR')

        expect:
        multipliers.size() >= 20
        Math.abs(multipliers.sum() / multipliers.size() - 1.0d) < 0.001d
    }

    def "a player below replacement is worth nothing when outcomes are certain"() {
        given: 'rank 28 projects 4.2 a week against a replacement of 5.0'
        PointsCurve curve = curveWith(certain())

        expect:
        AuctionValuation.expectedValueOverReplacement(curve, replacementAt(5.0), 'WR', 28) == 0.0
    }

    def "the same player is worth something once outcomes can vary"() {
        given:
        PointsCurve curve = curveWith(uncertain())

        expect: 'the seasons he comes in high clear replacement, and those are the ones he is started in'
        AuctionValuation.expectedValueOverReplacement(curve, replacementAt(5.0), 'WR', 28) > 0.0
    }

    def "spread never lowers what a player is worth, at any rank"() {
        given:
        PointsCurve certain = curveWith(certain())
        PointsCurve uncertain = curveWith(uncertain())

        expect: 'exact in theory, so only rounding is allowed for'
        (1..30).every { int rank ->
            AuctionValuation.expectedValueOverReplacement(uncertain, replacementAt(5.0), 'WR', rank) >=
                    AuctionValuation.expectedValueOverReplacement(certain, replacementAt(5.0), 'WR', rank) -
                    0.000001
        }

        and: 'a player far clear of replacement gains nothing, since the floor never binds on him'
        (AuctionValuation.expectedValueOverReplacement(uncertain, replacementAt(5.0), 'WR', 1) -
                AuctionValuation.expectedValueOverReplacement(certain, replacementAt(5.0), 'WR', 1)).abs() < 0.01
    }

    def "the gap is widest around replacement, which is where a bench is"() {
        given:
        PointsCurve certain = curveWith(certain())
        PointsCurve uncertain = curveWith(uncertain())
        def gap = { int rank ->
            AuctionValuation.expectedValueOverReplacement(uncertain, replacementAt(5.0), 'WR', rank) -
                    AuctionValuation.expectedValueOverReplacement(certain, replacementAt(5.0), 'WR', rank)
        }

        expect: 'rank 28 sits just below replacement and gains more than the best player at the position'
        gap(28) > gap(1)
    }
}
