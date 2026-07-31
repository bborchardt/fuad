package ff.print.fuad

import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.mfl.MflFranchise
import ff.print.MultiListPrinter
import ff.projection.PlayerSalaryCalculator

class FuadRankingsDraftPrinter {

    private final FuadData fuadData

    FuadRankingsDraftPrinter(FuadData fuadData) {
        this.fuadData = fuadData
    }

    void print(PrintWriter out) {
        def printRank = this.&printRank.curry(out)
        new MultiListPrinter().printLists(out,
                new PrintableList(fuadData.qbRanks.findAll { p -> !p.contract }, printRank),
                new PrintableList(fuadData.rbRanks.findAll { p -> !p.contract }, printRank),
                new PrintableList(fuadData.wrRanks.findAll { p -> !p.contract }, printRank),
                new PrintableList(fuadData.teRanks.findAll { p -> !p.contract }, printRank),
                new PrintableList(fuadData.pkRanks.findAll { p -> !p.contract }, printRank),
        )
    }

    private void printRank(PrintWriter out, FuadPlayer player) {
        if(player) {
            int redraftRank = player.redraftRank?.positionRank ?: 999
            int dynastyRank = player.dynastyRank?.positionRank ?: 999
            out.print "$dynastyRank\t$redraftRank\t$player.player.name\t$player.player.team/$player.bye\t"
            if(player.rookie) {
                out.print "Rookie"
            } else {
                MflFranchise franchise = fuadData.mflData.franchiseByIdMap.values().find { f ->
                    f.players.find { fp -> fp.player.name == player.player.name } != null
                }
                String franchiseName = franchise?.ownerName ?: franchise?.name
                String shortName = franchiseName ? franchiseName.split(' ')[0] : 'UFA'
                out.print "$shortName"
            }
            out.print '\t'
            out.print PlayerSalaryCalculator.projectedSalary(player)
            out.print '\t'
        } else {
            out.print "\t\t\t\t\t\t"
        }
    }

}
