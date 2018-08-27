package ff.data.greenfield

import ff.data.Contract
import ff.data.Draft
import ff.data.Player
import ff.data.Rank
import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class GreenfieldPlayer {
    Player player
    Rank redraftRank
    String bye
}
