package ff.print.fuad

import ff.data.fuad.FuadData
import ff.data.fuad.RookieValue
import ff.data.mfl.MflDraftPick
import ff.load.fuad.RookieSeasons
import ff.projection.fuad.RookieSalary

/**
 * The rookie draft: what each rookie is worth over the contract he comes with, and what he will cost.
 *
 * <b>{@code VALUE} is the column to read, and the sheet sorts on it.</b> It is what a rookie is worth over
 * the five years a contract can run, and it is deliberately the only thing here that is not mixed with a
 * price: what he costs is a fact about the pick he goes at rather than about him, and it lives on the pick
 * sheet beside the pick. Y1 to Y5 are the same figure year by year, kept because the shape of a career is a
 * real distinction — a back worth most immediately and a quarterback worth most in his third season are not
 * the same asset — and kept to the right, because nobody chooses on them.
 *
 * <b>{@code TIER} exists to stop the rest being over-read, and {@code VALLOW} to {@code VALHIGH} does the
 * same job across positions.</b> Rookies sharing a tier are ties: the levels behind these dollars are means
 * of a few dozen seasons, and any ordering inside their error is noise the price column dresses up. A tier
 * is only comparable within a position, so the bounds are there for the choice a rookie draft actually
 * poses — a back against a tight end against a receiver — where two overlapping ranges are a tie whatever
 * their midpoints say.
 *
 * The bounds are on the <b>estimate</b> and not on the outcome: they say how well nine rookie classes pin
 * down what a rank is worth, not how widely one career might run. They are asymmetric because value over
 * replacement is convex, which is why they are two columns and not a single band.
 *
 * <b>Sorted by value, best first.</b> It was in consensus order, on the reasoning that a draft is read that
 * way and that sorting by worth hides who is actually left — but a reader with the board in front of him can
 * see who is left, and what he cannot see is which of them is worth most. Consensus order is still on the
 * sheet as {@code FP_ROOKIE} for anyone who wants to read down it.
 *
 * {@code DEMAND} is where the league's own drafts say a rookie of that <b>consensus rank</b> goes, so the
 * distance between it and your pick is the reach or the wait. It is keyed on the rank rather than on the
 * player: it says what has happened to rookies ranked here, not what this room will do with this man.
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
        out.println((['PLAYER', 'POS', 'TEAM', 'BYE', 'VAL_LOW', 'VALUE', 'VAL_HIGH', 'TIER',
                      'FP_ROOKIE', 'FP_DYNASTY', 'NFL', 'DEMAND'] + years).join('\t'))
        values.sort { RookieValue a, RookieValue b ->
            (b.contractValue <=> a.contractValue) ?: (a.overallRank <=> b.overallRank)
        }.each { RookieValue value ->
            out.println(([
                    value.playerName,
                    value.position,
                    value.nflTeam ?: '',
                    value.bye ?: '',
                    value.valueLow,
                    value.contractValue,
                    value.valueHigh,
                    value.tier,
                    value.overallRank,
                    value.dynastyRank ? "$value.position$value.dynastyRank" : '',
                    nflDraftOf(value),
                    value.expectedPick ?: '',
            ] + value.valueByYear).join('\t'))
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


    /**
     * Where the NFL took him, as round and pick, or a question mark where it did not take him at all.
     *
     * A question mark rather than a blank, which would read as missing data. Going undrafted is a fact
     * about a player and one of the strongest the sheet carries.
     */
    private static String nflDraftOf(RookieValue value) {
        value.nflDraft ? "${value.nflDraft.round}.${value.nflDraft.pick}" : '?'
    }
}

