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
    /** Where the rookie consensus puts him in the class, which is the order this report is read in. */
    int overallRank
    /** Where it puts him at his position, which is what the curve levels him at. */
    int positionRank
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

    /** Points over replacement in each year of the contract, first year first. */
    List<BigDecimal> pointsOverReplacement
    /** What each of those years would cost to buy at auction instead, in cap dollars. */
    List<Integer> valueByYear
    /** The overall pick the league's own drafts say he goes at, or null where too few have gone there. */
    Integer expectedPick
    /** What bylaw 8.3 charges at that pick, held flat for every year of the contract by bylaw 2.2. */
    int salary
    /** Years worth committing to: the length that maximises what the contract is worth over its cost. */
    int contractLength
    /**
     * The band the value carries, being what one standard error on the levels behind it is worth.
     *
     * Propagated by revaluing him a standard error higher rather than by scaling the answer, since value
     * over replacement is convex and a fractional error on a level is a larger fractional error on a price.
     */
    int valueError
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
     * What the contract is worth before what it costs, over the years worth committing to.
     *
     * <b>Separated from the surplus because the two move for different reasons.</b> A rookie's worth is a
     * fact about him; his salary is a fact about the pick he goes at, and at quarterback this year that
     * price runs from $20 at the first pick to $1 by the fifteenth. The board reports him at the pick the
     * league's drafts say he goes at, so a quarterback's surplus is dominated by an assumption about where
     * he lands — Fernando Mendoza is a $95 asset reading $30 at pick three and $80 at pick nine. Only this
     * column stays still.
     */
    int getContractValue() {
        valueByYear ? (0..<contractLength).sum { int year -> valueByYear[year] } as int : 0
    }

    /** How much of the surplus falls after the first season: the part an auction cannot buy at all. */
    int getDeferredSurplus() { surplus - (firstYearValue - salary) }
}
