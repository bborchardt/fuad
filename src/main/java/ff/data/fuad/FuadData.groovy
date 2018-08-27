package ff.data.fuad

import ff.data.mfl.MflData
import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class FuadData {
    MflData mflData
    Map<String, FuadPlayer> playerByNameMap
    List<FuadPlayer> qbRanks
    List<FuadPlayer> rbRanks
    List<FuadPlayer> wrRanks
    List<FuadPlayer> teRanks
    List<FuadPlayer> pkRanks
    List<FuadPlayer> rookieRanks
}
