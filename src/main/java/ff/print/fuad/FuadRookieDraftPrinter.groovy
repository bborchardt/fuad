package ff.print.fuad

import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.mfl.MflDraftPick
import ff.print.MultiListPrinter

class FuadRookieDraftPrinter {

    private final FuadData fuadData

    FuadRookieDraftPrinter(FuadData fuadData) {
        this.fuadData = fuadData
    }

    void print() {
        new MultiListPrinter().printLists(
                new PrintableList(fuadData.rookieRanks, this.&printRank),
                new PrintableList(fuadData.mflData.draftPicks, this.&printPick),
        )
    }

    private void printRank(FuadPlayer player) {
        if (player) {
            String overallRank = "${((9 + player.rookieRank.overallRank) / 10).toInteger()}.${player.rookieRank.overallRank % 10 ?: 10}"
            String draftPick = player.draft ? "$player.draft.round.$player.draft.pick" : '?'
            String positionAndRank = "$player.player.position$player.rookieRank.positionRank"
            print "$overallRank\t$player.player.name\t$positionAndRank\t$draftPick\t$player.player.team"
        } else {
            print '\t\t\t'
        }
    }

    private void printPick(MflDraftPick pick) {
        if (pick) {
            def franchiseName = pick.franchise.name.split(' ')[0]
            print "$pick.round.$pick.pick\t$franchiseName"
        } else {
            print '\t'
        }
    }

}
