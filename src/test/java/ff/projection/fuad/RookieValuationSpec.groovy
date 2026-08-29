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
     * <b>The length that leaves the most value over its cost, not the run that stops at the first bad
     * year.</b> The stopping rule was the first thing here and it is backwards for a rookie: value rises
     * into the second and third seasons, so a break-even first year ended the contract before the years
     * worth having. It signed rookie QB1 for a single season and reported his contract as worth nothing,
     * with $172 of surplus in the four years it refused to look at.
     */
    @Unroll
    def "signs #value against a salary of #salary for #expected years"() {
        expect:
        RookieValuation.contractLength(value, salary) == expected

        where:
        value                | salary | expected
        [20, 30, 30, 10, 5]  | 4      | 5
        [20, 30, 30, 10, 5]  | 12     | 3   // the fourth year is worth less than he costs
        [20, 30, 30, 10, 5]  | 25     | 3   // a losing first year, carried by the two that pay for it
        [1, 1, 1, 1, 1]      | 1      | 1   // never worth a year, but a contract is at least one
        [40, 1, 60, 60, 60]  | 5      | 5   // the lean year is carried, the three after it pay for it
        [13, 58, 55, 35, 24] | 13     | 5   // a rookie quarterback: level in year one, and then not
        [40, 0, 0, 0, 0]     | 5      | 1   // and a year one asset is still signed for one year
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
        values.every { it.valueByYear.size() == 5 }
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

    /**
     * The calibration that joins a dynasty rank to one season of it.
     *
     * A dynasty rank prices a career, so a rookie scores less per game than his rank implies in the year he
     * is drafted and close to it afterwards. Asserted as a shape rather than as values, since the values are
     * a measurement that will move when a season is added: year one is the outlier, and the rest are level.
     */
    def "a rookie plays below his dynasty rank in his first season and close to it after"() {
        given:
        List<BigDecimal> calibration = RookieValuation.RATE_CALIBRATION

        expect: 'the first year is the discounted one'
        calibration.first() < 0.85g
        calibration.first() == calibration.min()

        and: 'and every later year is close to the rank he was given'
        calibration.tail().every { it > 0.9g && it <= 1.0g }
    }

    /**
     * The board can see a weak draft class, which is the whole point of blending the dynasty index in.
     *
     * The rookie ranking orders a class and says nothing about how good it is, so "rookie RB1" is levelled
     * identically in a generational year and a bare one. The dynasty ranking makes exactly that comparison:
     * 2024's best five were the fifth, ninth and seventeenth receivers and the third tight end of all
     * dynasty assets, averaging 12.8 at their positions, against 2026's 16.4.
     *
     * Positional ranks, because the dynasty export went superflex in 2026 and its overall ranks are not
     * comparable across that break. See {@code rookieclass.tsv}.
     *
     * Asserted across seasons because a single season's numbers say nothing on their own: what has to hold
     * is that the ordering of classes by what the board thinks they are worth follows the ordering by what
     * the consensus placed them at.
     */
    def "a weak class is valued below a strong one"() {
        given:
        def topFive = { String season ->
            FuadData data = new FuadLoader().loadData(season)
            List<RookieValue> values = new FuadValuationLoader().rookieValues(season, data)
            List<RookieValue> top = values.findAll { it.overallRank <= 5 }
            top.sum { it.surplus } / top.size()
        }

        expect: '2024, averaging 12.8 at their positions, against 2026 averaging 16.4'
        topFive('2024') > topFive('2026')
    }

    /**
     * A rookie levelled off two indices is levelled off the one he has.
     *
     * Not being carried by the dynasty ranking is a fact about a deep rookie rather than a missing
     * measurement, so he keeps the rookie index alone rather than being dropped or given a default.
     */
    def "values a rookie the dynasty ranking does not carry"() {
        given:
        List<RookieValue> unranked = values.findAll { RookieValue rookie ->
            data.playerByNameMap.values().find { it.mflId == rookie.playerId }?.dynastyRank == null
        }

        expect:
        unranked.size() > 0
        unranked.every { it.valueByYear.size() == 5 && it.valueByYear.every { year -> year >= 0 } }
    }

    /**
     * The deep picks have an ordering again, which is what the rookie spread was built for.
     *
     * Every candidate at a fourth round pick used to read $0, so the sheet ranked them by consensus and gave
     * a reader nothing. Value over replacement is convex — five seasons of fifty points are worth nothing
     * and one season of two hundred is worth seventy-eight — so a rank whose outcomes are bimodal is worth
     * real money at a mean that looks worthless, and a spread that cannot reach two hundred cannot see it.
     */
    def "rookies past the first round are separated rather than all reading zero"() {
        given:
        List<RookieValue> deep = values.findAll { it.overallRank > 12 && it.overallRank <= 32 }

        expect:
        deep.size() >= 15
        deep.count { it.surplus > 0 } >= deep.size() / 2
        deep*.surplus.unique().size() >= 8
    }

    /**
     * Value and surplus are different questions, and the board carries both because they move apart.
     *
     * A rookie's worth is a fact about him; his salary is a fact about the pick he goes at. At quarterback
     * this year that price runs from $20 at the first pick to $1 by the fifteenth, so a quarterback's
     * surplus is mostly an assumption about where he lands — Fernando Mendoza is the same $95 asset reading
     * $30 at pick three and $80 at pick nine.
     */
    def "value is what his seasons are worth, and does not move when the pick does"() {
        given: 'the quarterback whose salary is large enough to shorten his own contract'
        RookieValue quarterback = values.find { it.position == 'QB' && it.positionRank == 1 }
        Map<String, Integer> baselines = [QB: 20, RB: 7, WR: 1, TE: 1, PK: 1]

        expect: 'taken first he is signed for fewer years and is worth less, and is the same asset'
        RookieValuation.at(quarterback, 1, baselines).contractLength <
                RookieValuation.at(quarterback, 20, baselines).contractLength
        RookieValuation.at(quarterback, 1, baselines).surplus <
                RookieValuation.at(quarterback, 20, baselines).surplus
        RookieValuation.at(quarterback, 1, baselines).contractValue ==
                RookieValuation.at(quarterback, 20, baselines).contractValue

        and: 'and where the contract runs its full length the two reconcile'
        values.findAll { it.contractLength == 5 }.every { RookieValue rookie ->
            rookie.contractValue - 5 * rookie.salary == rookie.surplus
        }
    }

    /**
     * The column that stops the rest being over-read.
     *
     * The levels behind these dollars are means of a few dozen seasons, and any ordering inside their error
     * is noise the price column dresses up. Two questions that came up reading this board were both answered
     * by it: Makai Lemon above Jordyn Tyson is one tier, so a tie, and so is the gap between the two best
     * rookie quarterbacks.
     */
    def "rookies the evidence cannot separate share a tier"() {
        given:
        def tierOf = { String name -> values.find { it.playerName == name }?.tier }

        expect: 'a tier is assigned to everyone, from one up, within a position'
        values.every { it.tier >= 1 }
        values.groupBy { it.position }.every { String position, List<RookieValue> atPosition ->
            atPosition*.tier.min() == 1
        }

        and: 'and it separates the board without pretending to separate what it cannot'
        values.findAll { it.position == 'WR' }*.tier.unique().size() > 3
    }

    /**
     * A rookie quarterback's value cannot be told from zero, and the band says so.
     *
     * This is the honest state of the position rather than a defect to be tuned away. Superflex starts up to
     * twenty quarterbacks, so replacement is 209 points a season and only about half of the rookie
     * quarterback seasons that happened have ever cleared it — and a third of them never happened. Value
     * over replacement is convex, so a standard error on a level that sits near replacement is worth as much
     * as the value itself.
     */
    def "the band on a rookie quarterback is wider than his value, and on a back it is not"() {
        given:
        List<RookieValue> quarterbacks = values.findAll { it.position == 'QB' && it.positionRank <= 2 }
        List<RookieValue> backs = values.findAll { it.position == 'RB' && it.positionRank <= 2 }

        expect: 'the whole range is worth more than the estimate it qualifies, at quarterback'
        quarterbacks.every { it.valueHigh - it.valueLow > it.contractValue }

        and: 'and comfortably less than it at the top of the running backs'
        backs.every { it.valueHigh - it.valueLow < it.contractValue }
    }

    /**
     * The bounds are not symmetric, and the asymmetry is the convexity showing.
     *
     * Value over replacement is {@code max(0, points - replacement)} summed weekly, so a level a standard
     * error low loses less than the same error high gains. It holds for all 117 rookies on the board without
     * exception, which is what makes it a property rather than an observation, and it is why the bounds are
     * two columns rather than one plus-or-minus.
     */
    def "the bounds are wider above than below, at every rookie on the board"() {
        expect: 'the value sits inside its own bounds'
        values.every { it.valueLow <= it.contractValue && it.contractValue <= it.valueHigh }

        and: 'and always nearer the low one, which is convexity and holds without exception'
        values.every { it.valueHigh - it.contractValue >= it.contractValue - it.valueLow }
    }
}
