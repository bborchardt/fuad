package ff.data.greenfield

import ff.data.fuad.FuadPlayer
import ff.data.mfl.MflData
import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class GreenfieldData {
    Map<String, GreenfieldPlayer> playerByNameMap
    List<GreenfieldPlayer> qbRanks
    List<GreenfieldPlayer> rbRanks
    List<GreenfieldPlayer> wrRanks
    List<GreenfieldPlayer> teRanks
    List<GreenfieldPlayer> pkRanks
    List<GreenfieldPlayer> rookieRanks
}
