package ff.data.mfl

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class MflData {
    Map<String, MflPlayer> playerByNameMap
    Map<String, MflFranchise> franchiseByIdMap
    List<MflDraftPick> draftPicks
}
