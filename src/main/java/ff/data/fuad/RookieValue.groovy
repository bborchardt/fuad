package ff.data.fuad

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
@Immutable
class RookieValue {

    String playerId
    String playerName
    String position
    /** Where the rookie consensus puts him in the class, which is the order this report is read in. */
    int overallRank
    /** Where it puts him at his position, which is what the curve levels him at. */
    int positionRank
    String nflTeam
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
    /** Value less salary over that length, which is the number a pick is chosen on. */
    int surplus

    /** What he is worth in the season being drafted, which is all an auction salary would ever buy. */
    int getFirstYearValue() { valueByYear ? valueByYear.first() : 0 }

    /** How much of the surplus falls after the first season: the part an auction cannot buy at all. */
    int getDeferredSurplus() { surplus - (firstYearValue - salary) }
}
