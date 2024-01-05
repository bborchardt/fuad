package ff.print.fuad

import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.mfl.MflFranchise
import ff.data.mfl.MflPlayer
import ff.projection.PlayerSalaryCalculator

class FuadFranchiseDraftPrinter {

    private final FuadData fuadData
    private final boolean printProjections

    FuadFranchiseDraftPrinter(FuadData fuadData, boolean printProjections = false) {
        this.fuadData = fuadData
        this.printProjections = printProjections
    }

    void print() {
        prettyPrintFranchiseRows()
    }

    private prettyPrintFranchiseRows() {
        Collection<MflFranchise> franchises = fuadData.mflData.franchiseByIdMap.values()
        String joiner = '\t\t$\tYrs\t\t'
        if(franchises.find { it.ownerName == null }) {
            println franchises*.name.join(joiner) + joiner
        } else {
            println franchises*.ownerName.join(joiner) + joiner
        }
        prettyPrintPositionRows(8, 'QB', franchises)
        prettyPrintPositionRows(14, 'RB', franchises)
        prettyPrintPositionRows(18, 'WR', franchises)
        prettyPrintPositionRows(10, 'TE', franchises)
        prettyPrintPositionRows(6, 'PK', franchises)
    }

    private prettyPrintPositionRows(int maxRows, String position, Collection<MflFranchise> franchises) {
        List<List<MflPlayer>> playersByFranchise = franchises*.players.collect { List<MflPlayer> players ->
            players.findAll { it.player.position == position }
                    .sort { a, b -> getRedraftRank(a) <=> getRedraftRank(b) }
        }
        (0..maxRows-1).each { i ->
            println playerRow(playersByFranchise*.getAt(i))
        }
    }

    private FuadPlayer getFuadPlayer(MflPlayer mflPlayer) {
        fuadData.playerByNameMap[mflPlayer.player.name]
    }

    private int getRedraftRank(MflPlayer mflPlayer) {
        getFuadPlayer(mflPlayer)?.redraftRank?.positionRank ?: 999
    }

    private String playerRow(List<MflPlayer> players) {
        players.collect(this.&playerString).join('\t\t')
    }

    private String playerString(MflPlayer player) {
        if(player) {
            String salary = player.contract?.salary ?: ''
            if(!salary && printProjections) {
                FuadPlayer fuadPlayer = getFuadPlayer(player)
                if(fuadPlayer) {
                    salary = PlayerSalaryCalculator.projectedSalary(fuadPlayer)
                }
            }
            String years = player.contract?.years ?: '0'
            String positionAndRank = "$player.player.position${getRedraftRank(player)}"
            if(!printProjections || salary) {
                "$player.player.name\t$positionAndRank\t$salary\t$years"
            } else {
                '\t' * 3
            }
        } else {
            '\t' * 3
        }
    }
}
