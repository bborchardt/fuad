package ff.print.greenfield

import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.mfl.MflFranchise
import ff.print.MultiListPrinter
import ff.print.fuad.PrintableList

class GreenfieldRankingsDraftPrinter {

    private final FuadData fuadData

    GreenfieldRankingsDraftPrinter(FuadData fuadData) {
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
            out.print "$player.dynastyRank.positionRank\t$redraftRank\t$player.player.name\t$player.player.team/$player.bye\t"
            if(player.rookie) {
                out.print "Rookie"
            } else {
                MflFranchise franchise = fuadData.mflData.franchiseByIdMap.values().find { f ->
                    f.players.find { fp -> fp.player.name == player.player.name } != null
                }
                String franchiseName = franchise ? franchise.name.split(' ')[0] : 'UFA'
                out.print "$franchiseName"
            }
        } else {
            out.print "\t\t\t\t"
        }
    }

}
