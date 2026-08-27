package ff.projection.greenfield

import spock.lang.Specification

/**
 * The plan, on a board small enough to solve by hand.
 *
 * Three picks and two positions, arranged so that taking whichever falls furthest before the next pick is
 * the wrong answer — which is the whole reason this exists.
 */
class DraftPlanSpec extends Specification {

    private static final List<Integer> PICKS = [1, 2, 3]
    private static final List<String> POSITIONS = ['X', 'Y']

    /** Both positions run one rank deeper at each successive pick. */
    private static final Map<String, Map<Integer, Integer>> BEST_RANK =
            [X: [1: 1, 2: 2, 3: 3], Y: [1: 1, 2: 2, 3: 3]]

    /**
     * X falls hard at once and then holds; Y holds and then falls off a cliff.
     *
     * Y carries a rank 3 as well, because taking one for yourself pushes the next a rank deeper than the
     * room alone would leave it.
     */
    private static final Map<String, Map<Integer, BigDecimal>> VALUE =
            [X: [1: 10.0g, 2: 4.0g, 3: 3.0g, 4: 3.0g],
             Y: [1: 9.0g, 2: 8.0g, 3: 8.0g, 4: 1.0g]]

    def "solves for the whole draft, where taking the steepest fall would not"() {
        when:
        Map<Integer, String> plan = DraftPlan.best(PICKS, [:], [X: 1, Y: 2], POSITIONS, BEST_RANK, VALUE)

        then: 'X falls 6 before the next pick and Y falls 1, so a one-gap rule takes X first and scores 19'
        plan == [1: 'Y', 2: 'Y', 3: 'X']

        and: 'Y twice then X is worth 20, because X still has something left in the last round and Y does not'
        VALUE.Y[1] + VALUE.Y[3] + VALUE.X[3] == 20.0g
        VALUE.X[1] + VALUE.Y[2] + VALUE.Y[4] == 19.0g
    }

    def "never starts more of a position than the league lets it"() {
        when:
        Map<Integer, String> plan = DraftPlan.best(PICKS, [:], [X: 1, Y: 1], POSITIONS, BEST_RANK, VALUE)

        then: 'two slots between them, so the third pick plans nothing'
        plan.values().count { it == 'X' } == 1
        plan.values().count { it == 'Y' } == 1
        plan.size() == 2
    }

    def "counts what is already held, a keeper filling a slot like any other player"() {
        when: 'X is already held and capped at one'
        Map<Integer, String> plan = DraftPlan.best(PICKS, [X: 1], [X: 1, Y: 2], POSITIONS, BEST_RANK, VALUE)

        then: 'only Y is left to take, and only twice'
        plan == [1: 'Y', 2: 'Y']
    }

    def "leaves a pick unplanned once nothing can improve a starting lineup"() {
        expect: 'a pick with no entry is a bench pick, which this does not price'
        DraftPlan.best(PICKS, [X: 1, Y: 2], [X: 1, Y: 2], POSITIONS, BEST_RANK, VALUE) == [:]
    }

    def "skips a position the board has nothing left of, rather than failing"() {
        given: 'Y runs out after the first pick'
        Map<String, Map<Integer, Integer>> thin = [X: [1: 1, 2: 2, 3: 3], Y: [1: 1]]

        when:
        Map<Integer, String> plan = DraftPlan.best(PICKS, [:], [X: 1, Y: 2], POSITIONS, thin, VALUE)

        then:
        plan[1] == 'Y'
        plan[2] == 'X'
        plan.size() == 2
    }
}
