package ff.data.fuad

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * One selection from one of the league's rookie drafts, joined to what became of it.
 *
 * A pick is three separate facts that only the league's own record holds together: where it was made, who
 * was taken, and whether he was still on the roster when the season started. The last is what a pick is
 * really worth a claim about — bylaw 12.2 lets a rookie be waived before the cut down date with no cap
 * consequence at all, so a pick that did not work out costs its team nothing and leaves nothing behind
 * except this row.
 */
@CompileStatic
@Immutable
class RookiePick {

    String season
    /** Pick number across the whole draft, one based, which is what bylaw 8.3 decays the salary by. */
    int overall
    int round
    /** Position within the round. The order does not reverse, so this is the team's slot in every round. */
    int pick
    String franchiseId
    String playerId
    /** As the league writes him, last name first. */
    String playerName
    String position
    /** What he was paid at week 1, or null where he was waived before the season started. */
    Integer salary
    /** Years the drafting team committed to, or null where he was waived. Bylaw 2.2 allows one to five. */
    Integer contractYears

    /** Whether the pick was still rostered when the season began. */
    boolean isKept() { salary != null }
}
