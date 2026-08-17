package ff.print.fuad

import ff.data.PlayerValuation
import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.mfl.MflFranchise
import ff.print.MultiListPrinter

/**
 * Every player not under contract, by position and in consensus rank order, with what he is expected to
 * fetch.
 *
 * <b>The dollars are the auction board's, not a second opinion.</b> This used to price its own column from
 * a curve fitted straight from positional rank to dollars, which is the model docs/PROJECTION.md rules out:
 * it pools caps and lineups the league has since changed, reads franchise tags as bids, and prices each
 * player alone so nothing makes the answers add up to the money that exists. Two sheets quoting different
 * numbers for the same player is worse than either, so there is now one price and this reads it.
 *
 * The column is {@code PRICE} — what open bidding is expected to settle at — because this is the sheet a
 * bid is made from. What the team holding him pays instead, where a franchise tag is cheaper, is on the
 * salaries board as {@code COST}.
 *
 * A player the board does not carry prices blank rather than at zero: rookies, who are drafted separately
 * and cannot be bid on, and ranks past the depth the curve still makes a claim at. See docs/PROJECTION.md.
 */
class FuadRankingsDraftPrinter {

    private final FuadData fuadData
    private final Map<String, PlayerValuation> valuationByPlayerId

    FuadRankingsDraftPrinter(FuadData fuadData, List<PlayerValuation> valuations) {
        this.fuadData = fuadData
        this.valuationByPlayerId = valuations.collectEntries { [(it.playerId): it] }
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

    /** What open bidding is expected to pay, or blank where the board does not price this player. */
    private String price(FuadPlayer player) {
        PlayerValuation valuation = player.mflId ? valuationByPlayerId[player.mflId] : null
        valuation ? valuation.marketSalary as String : ''
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
            out.print price(player)
            out.print '\t'
        } else {
            out.print "\t\t\t\t\t\t"
        }
    }

}
