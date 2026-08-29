package ff.print.fuad

import ff.data.fuad.FuadData
import ff.data.fuad.RookieValue
import ff.data.mfl.MflDraftPick
import ff.load.fuad.RookieSeasons
import ff.projection.fuad.RookieSalary

import java.math.RoundingMode

/**
 * The rookie draft: what each rookie is worth over the contract he comes with, and what he will cost.
 *
 * <b>Read the last two columns and nothing else, if only two are read.</b> {@code SURPLUS} is what the
 * contract is worth over what it costs, and {@code DEFER} is how much of that falls after the coming
 * season — the part no auction dollar can buy at any price, because the auction sells one year at a time.
 * A rookie whose surplus is mostly deferred is a different asset from one whose surplus is all in year one,
 * and the two are not interchangeable for a team that has to win now.
 *
 * The board is in consensus order because that is the order a draft is read in. It is deliberately not
 * sorted by surplus: the pick in front of you is a choice among whoever is left, and sorting by value hides
 * which of them that is. {@code PICK} is where the league's own drafts say a rookie of that rank goes, so
 * the distance between it and your pick is the reach or the wait.
 *
 * See docs/fuad/PROJECTION.md.
 */
class FuadRookieDraftPrinter {

    private final FuadData fuadData
    private final List<RookieValue> values
    private final Map<String, Integer> baselines
    private final Map<String, Map<Integer, Integer>> bestAvailable

    FuadRookieDraftPrinter(FuadData fuadData, List<RookieValue> values, Map<String, Integer> baselines,
                           Map<String, Map<Integer, Integer>> bestAvailable) {
        this.fuadData = fuadData
        this.values = values
        this.baselines = baselines
        this.bestAvailable = bestAvailable
    }

    /** One ranked rookie a row, in consensus order. */
    void print(PrintWriter out) {
        List<String> years = (1..RookieSeasons.CONTRACT_YEARS).collect { "Y$it" as String }
        out.println((['OVR', 'POS', 'RANK', 'PLAYER', 'TEAM', 'BYE', 'PICK', 'SALARY'] + years +
                ['VOR1', 'LEN', 'SURPLUS', 'DEFER']).join('\t'))
        values.each { RookieValue value ->
            out.println(([
                    value.overallRank,
                    value.position,
                    value.positionRank,
                    value.playerName,
                    value.nflTeam ?: '',
                    value.bye ?: '',
                    value.expectedPick ?: '',
                    value.salary,
            ] + value.valueByYear + [
                    value.pointsOverReplacement.first().setScale(0, RoundingMode.HALF_UP),
                    value.contractLength,
                    value.surplus,
                    value.deferredSurplus,
            ]).join('\t'))
        }
    }

    /**
     * The picks themselves: who holds each one, what it costs at each position, and what is usually left.
     *
     * <b>A pick's price is a property of the pick and not of the player.</b> Bylaw 8.3 decays the baseline
     * by a fifth for every selection already made, so the same rookie costs a different salary at 1.02 and
     * at 2.02, and by the third round every position has decayed to the minimum dollar. That is the column
     * a trade is priced against, and it cannot be read off the player board at all.
     *
     * {@code BEST<POS>} is the best rookie at that position the league's own drafts have typically left at
     * that pick, as a positional rank. Where the drafts are too few to say — the deep picks, which only the
     * five round years reach — it is left empty rather than extrapolated.
     */
    void printPicks(PrintWriter out) {
        List<String> positions = RookieSalary.BASELINE_RANK.keySet().toList()
        out.println((['PICK', 'ROUND', 'SLOT', 'TEAM'] + positions.collect { "\$$it" as String } +
                positions.collect { "BEST$it" as String }).join('\t'))
        fuadData.mflData.draftPicks.eachWithIndex { MflDraftPick pick, int index ->
            int overall = index + 1
            String franchise = pick.franchise?.ownerName ?: pick.franchise?.name ?: ''
            out.println(([
                    overall,
                    pick.round,
                    pick.pick,
                    franchise.split(' ').first(),
            ] + positions.collect { String position ->
                RookieSalary.salary(baselines[position] ?: RookieSalary.MINIMUM_SALARY, index)
            } + positions.collect { String position ->
                bestAvailable[position]?.get(overall) ?: ''
            }).join('\t'))
        }
    }
}
