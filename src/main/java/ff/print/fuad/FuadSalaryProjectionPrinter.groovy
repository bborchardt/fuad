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
 *
 * This is the whole permitted input to a draft plan, so it carries the things a plan would otherwise go
 * behind it for: the bye week that decides how a set of players covers a season, and the range a position's
 * seasons actually run to either side of expectation. See docs/STRATEGY.md.
 */
class FuadSalaryProjectionPrinter {

    private final FuadData fuadData
    private final List<PlayerValuation> valuations

    FuadSalaryProjectionPrinter(FuadData fuadData, List<PlayerValuation> valuations) {
        this.fuadData = fuadData
        this.valuations = valuations
    }

    void print(PrintWriter out) {
        out.println(['POS', 'TIER', 'RANK', 'DYNRANK', 'PLAYER', 'TEAM', 'HOLDER', 'BYE', 'PTS', 'PTSLOW',
                     'PTSHIGH', 'VOR', 'VALUE', 'PRICE', 'COST', 'ACQUIRE',
                     'EDGE', 'BAND', 'RFRCOST', 'AVAIL', 'TAG', 'FRANCHISED'].join('\t'))
        valuations.each { PlayerValuation v ->
            out.println([
                    v.position,
                    v.tier,
                    v.positionRank,
                    v.dynastyRank ?: '',
                    v.playerName,
                    fuadData.playerByNameMap[v.playerName]?.player?.team ?: '',
                    holder(v),
                    v.bye ?: '',
                    v.points.setScale(0, RoundingMode.HALF_UP),
                    v.pointsLow.setScale(0, RoundingMode.HALF_UP),
                    v.pointsHigh.setScale(0, RoundingMode.HALF_UP),
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
    }

    private String holder(PlayerValuation v) { ownerName(v.franchiseId) }

    private String ownerName(String franchiseId) {
        MflFranchise franchise = fuadData.mflData.franchiseByIdMap[franchiseId]
        String name = franchise?.ownerName ?: franchise?.name
        name ? name.split(' ')[0] : 'UFA'
    }
}
