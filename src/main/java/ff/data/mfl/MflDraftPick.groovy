package ff.data.mfl

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class MflDraftPick {
    int round
    int pick
    MflFranchise franchise
}
