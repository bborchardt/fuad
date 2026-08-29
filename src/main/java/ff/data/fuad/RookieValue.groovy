package ff.data.fuad

import ff.data.Draft
import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * What one ranked rookie is worth over the contract the league would sign him to.
 *
 * <b>The columns a reader actually chooses on are the last two.</b> Everything before them is the working:
 * what he is expected to be worth in each year of a five year deal, and what the league's own formula
 * charges for him at the pick he is expected to go at. {@code surplus} is the two put together and is the
 * only figure that compares a rookie to a veteran, because a veteran's price is a market and a rookie's is
 * a rule.
 */
@CompileStatic
@Immutable(copyWith = true)
class RookieValue {

    String playerId
    String playerName
    String position
    /** Where the rookie consensus puts him in the class, which is what demand is keyed on. */
    int overallRank
    /** Where it puts him at his position, which is what the curve, the spread and the taper all key on. */
    int positionRank
    /**
     * Where the dynasty ranking puts him at his position, or null where it does not carry him.
     *
     * Reported as context for the adjustment it drives. Read it against the rank rookies of his standing
     * usually hold — a class's third receiver typically sits around dynasty WR31, so one at WR23 is being
     * told something. A blank is not missing data: not being ranked among a few hundred dynasty assets is a
     * fact about a deep rookie, and it is also why he carries no adjustment.
     */
    Integer dynastyRank
    String nflTeam
    /**
     * Where the NFL took him, or null for an undrafted rookie.
     *
     * <b>A third index on the same player, and the only one no fantasy consensus produced.</b> The rookie
     * ranking orders him within his class and the dynasty ranking places him against the league; this says
     * what thirty-two front offices did with real money, which is information neither ranking contains and
     * which arrives before our draft. It is reported rather than priced — nothing here levels off it — and
     * it is on the sheet because it is what a room actually argues about.
     */
    Draft nflDraft
    Integer bye

    /**
     * What he is worth in each year of the contract, first year first, in cap dollars.
     *
     * The expectation over the error on the levels behind it rather than their point estimate, since value
     * over replacement is convex and the two are not the same number. See {@code RookieValuation}.
     */
    List<Integer> valueByYear
    /** The overall pick the league's own drafts say he goes at, or null where too few have gone there. */
    Integer expectedPick
    /** What bylaw 8.3 charges at that pick, held flat for every year of the contract by bylaw 2.2. */
    int salary
    /** Years worth committing to: the length that maximises what the contract is worth over its cost. */
    int contractLength
    /**
     * What the contract is worth if the levels behind it are a standard error low, and a standard error high.
     *
     * <b>Bounds on the estimate, not on the outcome.</b> They say how well nine rookie classes pin down what
     * a rank is worth — not how widely one rookie's career might run, which is a different and also large
     * quantity. The auction board's PTSLOW and PTSHIGH are that other thing, the tenth and ninetieth
     * percentile of realised seasons, which is why these are named differently.
     *
     * <b>Carried as two numbers rather than one band because they are not symmetric.</b> Value over
     * replacement is convex, so a level a standard error low loses less than a level a standard error high
     * gains, and the gap widens the closer a rank sits to replacement. A single plus-or-minus would round
     * that away exactly where it is largest.
     *
     * Propagated by revaluing him at the moved level rather than by scaling the answer, since a fractional
     * error on a level is a larger fractional error on a price.
     */
    int valueLow
    int valueHigh
    /**
     * Rookies at his position this cannot tell apart, 1 being the best.
     *
     * <b>Read a tier, never an order inside one.</b> The auction board carries the same column for the same
     * reason: the levels behind these numbers are means of a few dozen seasons, and any ordering inside
     * their error is noise the dollar column then dresses up. Two rookies in a tier are ties — choose
     * between them on the pick they will cost you, on their bye, or on what your roster is short of.
     */
    int tier
    /** Value less salary over that length, which is the number a pick is chosen on. */
    int surplus

    /** What he is worth in the season being drafted, which is all an auction salary would ever buy. */
    int getFirstYearValue() { valueByYear ? valueByYear.first() : 0 }

    /**
     * What his five seasons are worth, before what any of them cost.
     *
     * <b>Separated from the surplus because the two move for different reasons.</b> A rookie's worth is a
     * fact about him; his salary is a fact about the pick he goes at, and at quarterback this year that
     * price runs from $20 at the first pick to $1 by the fifteenth. The board reports him at the pick the
     * league's drafts say he goes at, so a quarterback's surplus is mostly an assumption about where he
     * lands — Fernando Mendoza reads $30 at pick three and $80 at pick nine — and this column does not move
     * at all.
     *
     * <b>All five years, and not the years worth signing.</b> Summing over {@link #contractLength} was the
     * first version and it defeated the purpose: length is chosen against the salary, so an expensive pick
     * shortens the contract and the gross value moves with the pick after all. Mendoza came out at $43 taken
     * first and $95 taken ninth, which is the same defect this column was added to remove. What it now says
     * is what his five seasons are worth to whoever holds them; how many of them are worth paying for is
     * {@code LEN}'s question and the salary's.
     *
     * It follows that {@code VALUE} less {@code LEN x SALARY} is the surplus only where the contract runs
     * the full five years, which is nearly always and not always.
     */
    int getContractValue() {
        valueByYear ? valueByYear.sum() as int : 0
    }

    /** How much of the surplus falls after the first season: the part an auction cannot buy at all. */
    int getDeferredSurplus() { surplus - (firstYearValue - salary) }
}
