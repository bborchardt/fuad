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

    void print(PrintWriter out) {
        new MultiListPrinter().printLists(out,
                new PrintableList(fuadData.rookieRanks, this.&printRank.curry(out)),
                new PrintableList(fuadData.mflData.draftPicks, this.&printPick.curry(out)),
        )
    }

    private void printRank(PrintWriter out, FuadPlayer player) {
        if (player) {
            String overallRank = "${((9 + player.rookieRank.overallRank) / 10).toInteger()}.${player.rookieRank.overallRank % 10 ?: 10}"
            String draftPick = player.draft ? "$player.draft.round.$player.draft.pick" : '?'
            String positionAndRank = "$player.player.position$player.rookieRank.positionRank"
            out.print "$overallRank\t$player.player.name\t$positionAndRank\t$draftPick\t$player.player.team"
        } else {
            out.print '\t\t\t'
        }
    }

    private void printPick(PrintWriter out, MflDraftPick pick) {
        if (pick) {
            String franchiseName = pick.franchise.ownerName ?: pick.franchise.name
            def shortName = franchiseName.split(' ')[0]
            out.print "$pick.round.$pick.pick\t$shortName"
        } else {
            out.print '\t'
        }
    }

}
