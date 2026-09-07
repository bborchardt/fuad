package ff.data.fuad

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * One selection recorded on the workbook's Rookie tab.
 *
 * <b>The sheet's own pick column cannot be trusted.</b> It is written as a decimal — round point pick — and
 * Excel stores it as a number, so 1.1 and 1.10 are both 1.1000000000000001 and the tenth pick of a round is
 * indistinguishable from the first. Row order is unambiguous and is what {@link #overall} is read from; the
 * round and pick are then derived from the draft's own shape rather than from the sheet.
 */
@CompileStatic
@Immutable
class SheetPick {

    /** Position in the draft, one based, taken from row order. */
    int overall
    /** Owner's first name, as the tab writes it. */
    String owner
    /** As the owner spelled it. */
    String playerName
}
