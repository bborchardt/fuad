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

    /**
     * One ranked rookie, as a draft slot: overall rank read ten picks to the round.
     *
     * The {@code ?: 10} is the round boundary. Pick ten of a round is {@code overall % 10 == 0}, which has
     * to read as the tenth pick of that round rather than the zeroth of the next.
     *
     * <b>An absent rookie is padded to the width a present one takes.</b> The consensus ranks far more
     * rookies than the league holds picks and a team that has traded picks holds fewer, so one list or the
     * other is always short — and this used to emit one field fewer than it prints, which shifted every
     * column to its right by one in exactly the rows nobody looks at twice.
     */
    private void printRank(PrintWriter out, FuadPlayer player) {
        if (player) {
            String overallRank = "${((9 + player.rookieRank.overallRank) / 10).toInteger()}.${player.rookieRank.overallRank % 10 ?: 10}"
            String draftPick = player.draft ? "$player.draft.round.$player.draft.pick" : '?'
            String positionAndRank = "$player.player.position$player.rookieRank.positionRank"
            out.print "$overallRank\t$player.player.name\t$positionAndRank\t$draftPick\t$player.player.team"
        } else {
            out.print '\t\t\t\t'
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
