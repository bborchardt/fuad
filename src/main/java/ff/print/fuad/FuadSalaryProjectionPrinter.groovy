package ff.print.fuad

import ff.data.PlayerValuation
import ff.data.fuad.FuadData
import ff.data.mfl.MflFranchise

import java.math.RoundingMode

/**
 * The auction board: every player up for auction, what they are expected to go for, and whether the
 * franchise tag is the cheaper way to keep them.
 *
 * Tagged players are shown at the tag price rather than a bid price, because that is what their team will
 * pay. Their row is the answer to a different question from everyone else's: not what to bid, but who will
 * not be biddable.
 */
class FuadSalaryProjectionPrinter {

    private final FuadData fuadData
    private final List<PlayerValuation> valuations

    FuadSalaryProjectionPrinter(FuadData fuadData, List<PlayerValuation> valuations) {
        this.fuadData = fuadData
        this.valuations = valuations
    }

    void print(PrintWriter out) {
        out.println(['POS', 'RANK', 'PLAYER', 'TEAM', 'HOLDER', 'PTS', 'VOR', 'VALUE', 'PRICE', 'COST', 'ACQUIRE',
                     'EDGE', 'BAND', 'RFRCOST', 'AVAIL', 'TAG', 'FRANCHISED'].join('\t'))
        valuations.each { PlayerValuation v ->
            out.println([
                    v.position,
                    v.positionRank,
                    v.playerName,
                    fuadData.playerByNameMap[v.playerName]?.player?.team ?: '',
                    holder(v),
                    v.points.setScale(0, RoundingMode.HALF_UP),
                    v.valueOverReplacement.setScale(0, RoundingMode.HALF_UP),
                    v.value,
                    v.marketSalary,
                    v.salary,
                    v.acquisitionSalary,
                    v.edge,
                    v.edgeBand,
                    v.restrictionPremium,
                    v.availability.setScale(2, RoundingMode.HALF_UP),
                    v.franchiseSalary,
                    v.franchiseTagged ? 'TAG' : ''
            ].join('\t'))
        }
        printSummary(out)
    }

    /** Totals worth eyeballing before trusting a board: what it thinks the auction costs, and who is out of it. */
    private void printSummary(PrintWriter out) {
        List<PlayerValuation> tagged = valuations.findAll { it.franchiseTagged }
        int bid = valuations.findAll { !it.franchiseTagged }.sum { it.salary } as int
        out.println()
        out.println(['', '', "${valuations.size()} players priced", '', '', '', '', '',
                     bid + (tagged.sum { it.salary } ?: 0)].join('\t'))
        out.println(['', '', "${tagged.size()} expected to be franchised", '', '', '', '', '',
                     tagged.sum { it.salary } ?: 0].join('\t'))
        out.println(['', '', "${valuations.count { it.edgeBand == 'BARGAIN' }} bargains, " +
                "${valuations.count { it.edgeBand == 'OVERPRICED' }} overpriced"].join('\t'))
        tagged.groupBy { it.franchiseId }.sort { it.key }.each { franchise, held ->
            out.println(['', '', "  ${ownerName(franchise)}", held*.playerName.join(', '), '', '', '',
                         held.sum { it.salary }].join('\t'))
        }
    }

    private String holder(PlayerValuation v) { ownerName(v.franchiseId) }

    private String ownerName(String franchiseId) {
        MflFranchise franchise = fuadData.mflData.franchiseByIdMap[franchiseId]
        String name = franchise?.ownerName ?: franchise?.name
        name ? name.split(' ')[0] : 'UFA'
    }
}
