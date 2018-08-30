package ff.print.fuad

import ff.data.fuad.FuadData
import ff.data.mfl.MflFranchise
import ff.data.mfl.MflPlayer

class FuadFranchiseDraftPrinter {

    private final FuadData fuadData

    FuadFranchiseDraftPrinter(FuadData fuadData) {
        this.fuadData = fuadData
    }

    void print() {
        prettyPrintFranchiseRows(fuadData.mflData.franchiseByIdMap.values())
    }

    private static prettyPrintFranchiseRows(Collection<MflFranchise> franchises) {
        if(franchises.find { it.ownerName == null }) {
            println franchises*.name.join('\t' * 5)
        } else {
            println franchises*.ownerName.join('\t' * 5)
        }
        prettyPrintPositionRows(7, 'QB', franchises)
        prettyPrintPositionRows(14, 'RB', franchises)
        prettyPrintPositionRows(14, 'WR', franchises)
        prettyPrintPositionRows(7, 'TE', franchises)
        prettyPrintPositionRows(4, 'PK', franchises)
    }

    private static prettyPrintPositionRows(int maxRows, String position, Collection<MflFranchise> franchises) {
        List<List<MflPlayer>> playersByFranchise = franchises*.players.collect { List<MflPlayer> players ->
            players.findAll { it.player.position == position }
        }
        (0..maxRows-1).each { i ->
            println playerRow(playersByFranchise*.getAt(i))
        }
    }

    private static String playerRow(List<MflPlayer> players) {
        players.collect(this.&playerString).join('\t\t')
    }

    private static String playerString(MflPlayer player) {
        if(player) {
            String salary = player.contract?.salary ?: ''
            String years = player.contract?.years ?: '0'
            "$player.player.name\t$player.player.position\t$salary\t$years"
        } else {
            '\t' * 3
        }
    }
}
