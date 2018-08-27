package ff.data.fuad

import ff.data.Contract
import ff.data.Draft
import ff.data.Player
import ff.data.Rank
import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class FuadPlayer {
    Player player
    Rank dynastyRank
    Rank redraftRank
    Rank rookieRank
    Contract contract
    String mflId
    boolean rookie
    String bye
    Draft draft
}
