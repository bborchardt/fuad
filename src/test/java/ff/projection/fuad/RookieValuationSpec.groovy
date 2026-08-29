package ff.projection.fuad

import ff.data.PlayerValuation
import ff.data.fuad.FuadData
import ff.data.fuad.RookieValue
import ff.load.fuad.FuadLoader
import ff.load.fuad.FuadValuationLoader
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Pricing a rookie draft: the two arithmetic decisions, and the guard that keeps the answer on the board.
 */
class RookieValuationSpec extends Specification {

    private static final List<List<BigDecimal>> BOARD =
            [[10.0g, 5.0g], [20.0g, 20.0g], [30.0g, 50.0g]]

    @Unroll
    def "#points points over replacement prices at #expected"() {
        expect:
        RookieValuation.priceOf(points as BigDecimal, BOARD) == expected

        where:
        points | expected
        10     | 5      // exactly a priced player
        20     | 20
        15     | 12     // halfway between two, priced halfway between their prices
        25     | 35
        5      | 2      // below the cheapest, scaled down towards nothing
        0      | 0
        -5     | 0
    }

    /**
     * Above the top of the board the price is extended, not flattened.
     *
     * The board holds who is available, and the best players in the league are under contract or franchised
     * and never reach it. A rookie can be worth more than anyone on it, and saying he is worth exactly what
     * the most expensive available player costs would be a claim about the pool rather than about him.
     */
    def "extends past the top of the board at the rate of its last pair"() {
        expect: 'ten points past the top, at three dollars a point'
        RookieValuation.priceOf(40.0g, BOARD) == 80
    }

    def "prices nothing where the position has no board at all"() {
        expect:
        RookieValuation.priceOf(50.0g, []) == 0
        RookieValuation.priceOf(50.0g, null) == 0
    }

    /**
     * How long to sign him for, decided before any of it is known.
     *
     * Bylaw 12.4 wants the length at the cut down date of the year he is drafted, so the rule is the
     * expectation and nothing cleverer. Years run consecutively: a contract is a run of seasons and not a
     * selection of them, so one bad year ends it rather than being skipped over.
     */
    @Unroll
    def "signs #value against a salary of #salary for #expected years"() {
        expect:
        RookieValuation.contractLength(value, salary) == expected

        where:
        value                | salary | expected
        [20, 30, 30, 10, 5]  | 4      | 5
        [20, 30, 30, 10, 5]  | 12     | 3   // the fourth year is worth less than he costs
        [20, 30, 30, 10, 5]  | 25     | 0 + 1
        [1, 1, 1, 1, 1]      | 1      | 1   // never worth a year, but a contract is at least one
        [40, 1, 60, 60, 60]  | 5      | 1   // the run stops at the bad year, not around it
    }

    /** Everything below runs the real board, which is expensive, so it is loaded once. */
    @Shared
    FuadData data = new FuadLoader().loadData('2026')
    @Shared
    FuadValuationLoader loader = new FuadValuationLoader()
    @Shared
    List<RookieValue> values = loader.rookieValues('2026', data)

    def "values every rookie the consensus ranks and the league scores"() {
        expect:
        values.size() > 80
        values.every { it.valueByYear.size() == 5 && it.pointsOverReplacement.size() == 5 }
        values*.overallRank == values*.overallRank.sort()
    }

    /**
     * The guard that would have caught the worst bug this board has had.
     *
     * Levelling a rookie rank off its own outcome spread priced rookie quarterbacks at $151 in their third
     * year, against a board whose most expensive player is $89 — a rookie worth nearly twice the best
     * quarterback in the league, off a level less than half his. The cause is in
     * {@link ff.projection.ExpectedValue}: a season-total multiplier applied to a rate double counts
     * availability, which is harmless at a veteran rank and severe at a rookie one.
     *
     * A rookie may be worth more than anyone <b>available</b>, since the best players never reach the
     * auction. He may not be worth half as much again as the most expensive of them.
     */
    def "no rookie is priced beyond what the board can be extended to"() {
        given:
        Map<String, Integer> ceiling = loader.valuations('2026', data)
                .groupBy { PlayerValuation v -> v.position }
                .collectEntries { String position, List<PlayerValuation> priced ->
                    [(position): (priced*.marketSalary.max() * 1.5) as int]
                }

        expect:
        values.every { RookieValue rookie ->
            rookie.valueByYear.every { it <= ceiling[rookie.position] }
        }
    }

    /**
     * The finding the board exists to report, asserted so it cannot quietly stop being true.
     *
     * A rookie contract is one to five years at a salary fixed when he is picked, so most of what a pick is
     * worth falls after the season it is used in. If that ever stopped holding, the rookie draft would be an
     * auction with a fixed price and the whole of this could be deleted.
     */
    def "most of what an early pick is worth falls after its first season"() {
        given:
        List<RookieValue> firstRound = values.findAll { it.expectedPick && it.expectedPick <= 10 }

        expect:
        firstRound.size() >= 8
        firstRound.every { it.deferredSurplus > it.surplus - it.deferredSurplus }
    }
}
