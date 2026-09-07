package ff.data.fuad

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * One row of a franchise's column on the workbook's Franchises tab, as written rather than as resolved.
 *
 * The name is the owner's spelling and carries no player id: matching it to the league's player database is
 * a separate step that can fail, and a type that held an id would have nowhere to put a row that did not.
 *
 * A blank salary is the sheet's record of a contract that expired and was not re-signed. The league site
 * stores those at 0.01 because it will not accept a zero, and the two representations mean the same thing:
 * the player is unsigned. Unsigned players are released rather than loaded, so {@link #isSigned} is what
 * decides whether a row becomes a roster spot.
 */
@CompileStatic
@Immutable
class SheetSigning {

    /** Owner's first name, as the tab's header writes it. */
    String owner
    /** As the owner spelled it, which is not always as the league site spells it. */
    String playerName
    /** The board's positional rank, e.g. QB12. Blank for a player the board did not rank. */
    String positionRank
    /** Dollars, or null where the contract expired and was not re-signed. */
    Integer salary
    /** Years remaining. Zero is a real value: a deal expiring at the end of this season. */
    int years

    boolean isSigned() { salary != null && salary > 0 }
}
