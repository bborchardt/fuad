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

    /**
     * One position, ranks levelled 204 down to 30 over ten weeks.
     *
     * Ten games to match the ten week season these fixtures run: a rate is points divided by games, so a
     * fixture claiming thirteen games of a ten week year would price every rank a fifth low.
     */
    private static PointsCurve curveWith(Map<Integer, List<BigDecimal>> realised) {
        PointsCurve.of([WR: TestSeasons.byRank(realised, 10)])
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
     * The inequality the whole spread argument rests on, asserted rather than illustrated.
     *
     * {@code E[max(0, X - r)] >= max(0, E[X] - r)} is Jensen's, so it holds at every rank of every position
     * and not merely at the ones the documentation happens to tabulate. The two readings treat availability
     * identically, so what separates them is the spread and nothing else.
     */
    def "is never worth less than the same rank taken at its expectation"() {
        given:
        PointsCurve curve = curveWith(uncertain())
        Map<String, Map<Integer, BigDecimal>> replacement = replacementAt(5.0)

        expect:
        (1..30).every { int rank ->
            AuctionValuation.expectedValueOverReplacement(curve, replacement, 'WR', rank, NO_BYES) >=
                    AuctionValuation.valueOverReplacementAtExpectation(curve, replacement, 'WR', rank, NO_BYES) -
                    0.000001
        }
    }

    def "and the gap between the two readings is widest at replacement level"() {
        given:
        PointsCurve curve = curveWith(uncertain())
        Map<String, Map<Integer, BigDecimal>> replacement = replacementAt(5.0)
        def gap = { int rank ->
            AuctionValuation.expectedValueOverReplacement(curve, replacement, 'WR', rank, NO_BYES) -
                    AuctionValuation.valueOverReplacementAtExpectation(curve, replacement, 'WR', rank, NO_BYES)
        }

        expect: 'the player level with replacement gains most, and the best player nothing at all'
        gap(28) > gap(1)
        gap(1).abs() < 0.01
    }

    /**
     * A bye costs a game of production, because a rate is per game played.
     *
     * This is the reading the split changed. Levelling on season totals, a bye was free: the total was what
     * it was and spreading it over fewer weeks simply raised the per-week figure, so a player with a bye
     * came out worth slightly <i>more</i> than the same player without one. That was an artefact of dividing
     * a season by the calendar. On a rate, the week he is off is a week he does not score, and the season is
     * shorter by exactly one game.
     *
     * What he gains is only the replacement he no longer has to clear that week, which is much the smaller
     * of the two, so a bye is a modest net cost rather than a modest net gain.
     */
    def "a bye costs a game of production, less the replacement it saves"() {
        given: 'the same player, once with a bye and once without'
        PointsCurve curve = curveWith(certain())
        ByeWeeks byes = new ByeWeeks([WR: [(1): 5]], 10)

        when:
        Map<Integer, BigDecimal> weekly = curve.weeklyRate('WR', 1, 5, 10)
        BigDecimal withBye = AuctionValuation.expectedValueOverReplacement(curve, replacementAt(5.0), 'WR', 1, byes)
        BigDecimal without = AuctionValuation.expectedValueOverReplacement(curve, replacementAt(5.0), 'WR', 1, NO_BYES)

        then: 'he scores nothing in the week he is off'
        weekly[5] == 0.0

        and: 'and is worth less for it, by that week net of the replacement he would have had to beat'
        withBye < without
        // The levelled rate, not the raw one. What a week is worth has to be measured on the scale the week
        // was priced on, and value over replacement is taken on the anchored rate so that the sum across
        // positions is a sum of comparable things.
        ((without - withBye) - (curve.levelledRate('WR', 1) - 5.0)).abs() < 0.001
    }
}
