package ff.projection

import spock.lang.Specification

/**
 * A bench is worth something because a season is not its own expectation.
 *
 * Value over replacement taken at a player's expected points is `max(0, E[X] - replacement)`. What a roster
 * spot is actually worth is `E[max(0, X - replacement)]`, since a player only has to be started in the weeks
 * he is good. The second is never smaller and the gap is widest at replacement level, which is exactly where
 * a bench sits. See docs/PROJECTION.md.
 */
class ExpectedValueOverReplacementSpec extends Specification {

    /** Nobody is ever off, so a week is a tenth of a season and replacement is flat. */
    private static final ByeWeeks NO_BYES = new ByeWeeks([:], 10)

    /** One position, ranks levelled 204 down to 30 over ten weeks. */
    private static PointsCurve curveWith(Map<Integer, List<BigDecimal>> realised) {
        PointsCurve.of([WR: realised])
    }

    /** Every season at exactly its rank's level, so there is no spread to carry. */
    private static Map<Integer, List<BigDecimal>> certain() {
        (1..30).collectEntries { int rank -> [(rank): (1..9).collect { (210 - rank * 6) as BigDecimal }] }
    }

    /** The same, plus seasons at half and half again, so outcomes spread either side. */
    private static Map<Integer, List<BigDecimal>> uncertain() {
        (1..30).collectEntries { int rank ->
            BigDecimal expected = (210 - rank * 6) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5] * 3]
        }
    }

    private static Map<String, Map<Integer, BigDecimal>> replacementAt(BigDecimal weekly) {
        [WR: (1..10).collectEntries { [(it): weekly] }]
    }

    def "a player below replacement is worth nothing when outcomes are certain"() {
        given: 'rank 28 is levelled around 4.2 a week against a replacement of 5.0'
        PointsCurve curve = curveWith(certain())

        expect:
        AuctionValuation.expectedValueOverReplacement(curve, replacementAt(5.0), 'WR', 28, NO_BYES) == 0.0
    }

    def "the same player is worth something once outcomes can vary"() {
        given:
        PointsCurve curve = curveWith(uncertain())

        expect: 'the seasons he comes in high clear replacement, and those are the ones he is started in'
        AuctionValuation.expectedValueOverReplacement(curve, replacementAt(5.0), 'WR', 28, NO_BYES) > 0.0
    }

    def "spread never lowers what a player is worth, at any rank"() {
        given:
        PointsCurve certain = curveWith(certain())
        PointsCurve uncertain = curveWith(uncertain())

        expect: 'exact in theory, so only rounding is allowed for'
        (1..30).every { int rank ->
            AuctionValuation.expectedValueOverReplacement(uncertain, replacementAt(5.0), 'WR', rank, NO_BYES) >=
                    AuctionValuation.expectedValueOverReplacement(certain, replacementAt(5.0), 'WR', rank, NO_BYES) -
                    0.000001
        }

        and: 'a player far clear of replacement gains nothing, since the floor never binds on him'
        (AuctionValuation.expectedValueOverReplacement(uncertain, replacementAt(5.0), 'WR', 1, NO_BYES) -
                AuctionValuation.expectedValueOverReplacement(certain, replacementAt(5.0), 'WR', 1, NO_BYES)).abs() < 0.01
    }

    def "the gap is widest around replacement, which is where a bench is"() {
        given:
        PointsCurve certain = curveWith(certain())
        PointsCurve uncertain = curveWith(uncertain())
        def gap = { int rank ->
            AuctionValuation.expectedValueOverReplacement(uncertain, replacementAt(5.0), 'WR', rank, NO_BYES) -
                    AuctionValuation.expectedValueOverReplacement(certain, replacementAt(5.0), 'WR', rank, NO_BYES)
        }

        expect: 'rank 28 sits just below replacement and gains more than the best player at the position'
        gap(28) > gap(1)
    }

    /**
     * A bye takes a week away from a player without taking any scoring away from him: his season is what it
     * is, and the curve is levelled on seasons that already had a bye in them. Spreading that total over the
     * weeks he plays rather than over the whole calendar leaves him worth slightly <i>more</i> against a flat
     * replacement, not less, because the week he misses costs nothing — a team simply starts someone else.
     *
     * Which is the whole reason byes are carried for the ranked pool rather than for the players being
     * priced. What a bye really does is take the replacements out too, and that is a thing about the week
     * and not about him.
     */
    def "a bye takes a week off a player without taking scoring off him"() {
        given: 'the same expected season, once with a bye and once without'
        PointsCurve curve = curveWith(certain())
        ByeWeeks byes = new ByeWeeks([WR: [(1): 5]], 10)

        when:
        Map<Integer, BigDecimal> weekly = curve.weeklyPoints('WR', 1, 5, 10)
        BigDecimal withBye = AuctionValuation.expectedValueOverReplacement(curve, replacementAt(5.0), 'WR', 1, byes)
        BigDecimal without = AuctionValuation.expectedValueOverReplacement(curve, replacementAt(5.0), 'WR', 1, NO_BYES)

        then: 'nothing is scored in the week he is off, and it costs nothing against replacement'
        weekly[5] == 0.0
        withBye >= without

        and: 'the gain is only the replacement he no longer has to beat that week'
        (withBye - without) < 5.0 + 0.001
    }
}
