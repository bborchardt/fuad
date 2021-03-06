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

    void print() {
        new MultiListPrinter().printLists(
                new PrintableList(fuadData.qbRanks.findAll { p -> !p.contract }, this.&printRank),
                new PrintableList(fuadData.rbRanks.findAll { p -> !p.contract }, this.&printRank),
                new PrintableList(fuadData.wrRanks.findAll { p -> !p.contract }, this.&printRank),
                new PrintableList(fuadData.teRanks.findAll { p -> !p.contract }, this.&printRank),
                new PrintableList(fuadData.pkRanks.findAll { p -> !p.contract }, this.&printRank),
        )
    }

    private void printRank(FuadPlayer player) {
        if(player) {
            int redraftRank = player.redraftRank?.positionRank ?: 999
            print "$player.dynastyRank.positionRank\t$redraftRank\t$player.player.name\t$player.player.team/$player.bye\t"
            if(player.rookie) {
                print "Rookie"
            } else {
                MflFranchise franchise = fuadData.mflData.franchiseByIdMap.values().find { f ->
                    f.players.find { fp -> fp.player.name == player.player.name } != null
                }
                String franchiseName = franchise ? franchise.name.split(' ')[0] : 'UFA'
                print "$franchiseName"
            }
        } else {
            print "\t\t\t\t"
        }
    }

}
