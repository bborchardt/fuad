package ff.print.fuad

import ff.data.PlayerValuation
import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.mfl.MflFranchise
import ff.data.mfl.MflPlayer

/**
 * Each franchise's roster laid out side by side, by position and in consensus rank order.
 *
 * With {@code printProjections} it also fills in what the expiring contracts are expected to cost, which is
 * what turns a roster listing into a picture of what keeping that roster would take.
 *
 * <b>Those dollars are the auction board's, not a second opinion.</b> This used to project them from a curve
 * fitted straight from positional rank, which is the model docs/PROJECTION.md rules out, and which meant two
 * sheets in the same pack quoted different numbers for the same player. There is now one price and this
 * reads it.
 *
 * The column is {@code COST} — what the team holding him actually pays, which is the franchise tag price
 * where the model expects a tag — because this sheet is read a roster at a time and the question it answers
 * is what keeping that roster costs. What an outside bidder would have to go to is on the salaries board.
 */
class FuadFranchiseDraftPrinter {

    private final FuadData fuadData
    private final boolean printProjections
    private final Map<String, PlayerValuation> valuationByPlayerId

    FuadFranchiseDraftPrinter(FuadData fuadData, List<PlayerValuation> valuations,
                              boolean printProjections = false) {
        this.fuadData = fuadData
        this.printProjections = printProjections
        this.valuationByPlayerId = valuations.collectEntries { [(it.playerId): it] }
    }

    void print(PrintWriter out) {
        prettyPrintFranchiseRows(out)
    }

    private prettyPrintFranchiseRows(PrintWriter out) {
        Collection<MflFranchise> franchises = fuadData.mflData.franchiseByIdMap.values()
        String joiner = '\t\t$\tYrs\t\t'
        if(franchises.find { it.ownerName == null }) {
            out.println franchises*.name.join(joiner) + joiner
        } else {
            out.println franchises*.ownerName.join(joiner) + joiner
        }
        prettyPrintPositionRows(out, 8, 'QB', franchises)
        prettyPrintPositionRows(out, 14, 'RB', franchises)
        prettyPrintPositionRows(out, 18, 'WR', franchises)
        prettyPrintPositionRows(out, 10, 'TE', franchises)
        prettyPrintPositionRows(out, 6, 'PK', franchises)
    }

    private prettyPrintPositionRows(PrintWriter out, int maxRows, String position, Collection<MflFranchise> franchises) {
        List<List<MflPlayer>> playersByFranchise = franchises*.players.collect { List<MflPlayer> players ->
            players.findAll { it.player.position == position }
                    .sort { a, b -> getRedraftRank(a) <=> getRedraftRank(b) }
        }
        (0..maxRows-1).each { i ->
            out.println playerRow(playersByFranchise*.getAt(i))
        }
    }

    private FuadPlayer getFuadPlayer(MflPlayer mflPlayer) {
        fuadData.playerByNameMap[mflPlayer.player.name]
    }

    /** What the holding team is expected to pay to keep him, or blank where the board does not price him. */
    private String cost(MflPlayer player) {
        PlayerValuation valuation = valuationByPlayerId[player.id]
        valuation ? valuation.salary as String : ''
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
                salary = cost(player)
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
