package ff.data.mfl

import ff.data.Contract
import ff.data.Draft
import ff.data.Player
import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class MflPlayer {
    Player player
    Contract contract
    String id
    boolean rookie
    Draft draft
}
