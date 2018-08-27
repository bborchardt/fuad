package ff.data.mfl

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class MflFranchise {
    String id
    String name
    List<MflPlayer> players
}
