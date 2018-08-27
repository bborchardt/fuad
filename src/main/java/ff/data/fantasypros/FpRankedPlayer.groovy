package ff.data.fantasypros

import ff.data.Player
import ff.data.Rank
import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class FpRankedPlayer {
    Player player
    Rank rank
    String bye
}
