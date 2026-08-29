package ff.print.fuad

import ff.data.fuad.FuadData
import ff.data.fuad.RookieValue
import ff.data.mfl.MflDraftPick
import ff.load.fuad.RookieSeasons
import ff.projection.fuad.RookieSalary
import ff.projection.fuad.RookieValuation

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

    /** How many candidates an outlook shows at each pick. Enough to choose between, few enough to read. */
    private static final int CANDIDATES = 6

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
        out.println((['OVR', 'POS', 'RANK', 'PLAYER', 'TEAM', 'NFL', 'BYE', 'PICK', 'SALARY'] + years +
                ['VOR1', 'LEN', 'SURPLUS', 'DEFER']).join('\t'))
        values.each { RookieValue value ->
            out.println(([
                    value.overallRank,
                    value.position,
                    value.positionRank,
                    value.playerName,
                    value.nflTeam ?: '',
                    nflDraftOf(value),
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

    /**
     * One team's draft: at each of its own picks, who the room has typically left and what they are worth.
     *
     * <b>A plan rather than an optimisation, deliberately.</b> The keeper league solves its draft — every
     * ordering of positions across a slot's picks, scored on what it starts — and that works there because
     * a snake draft fills a starting lineup from nothing. This one does not. A rookie draft adds five
     * players to a roster of thirty, mostly as depth, so "the most starting value across five picks" is a
     * question about a lineup these picks will not decide. Solving it would put a confident-looking answer
     * on top of an objective nobody can defend.
     *
     * What a reader needs instead is the choice each pick actually offers, which is measured rather than
     * assumed: a rookie is shown at a pick when the league's own drafts have typically left his positional
     * rank on the board that long. Salary is this pick's, not his expected pick's, so a reach shows its
     * cost.
     *
     * The rows are what will fall to you. The decision stays yours.
     */
    void printOutlook(PrintWriter out, String franchiseId) {
        out.println(['PICK', 'ROUND', 'SLOT', 'POS', 'RANK', 'OVR', 'PLAYER', 'TEAM', 'NFL', 'BYE',
                     'SALARY', 'Y1', 'LEN', 'SURPLUS', 'DEFER'].join('\t'))
        fuadData.mflData.draftPicks.eachWithIndex { MflDraftPick pick, int index ->
            if (pick.franchise?.id != franchiseId) {
                return
            }
            int overall = index + 1
            candidatesAt(overall).each { RookieValue here ->
                out.println([
                        overall,
                        pick.round,
                        pick.pick,
                        here.position,
                        here.positionRank,
                        here.overallRank,
                        here.playerName,
                        here.nflTeam ?: '',
                        nflDraftOf(here),
                        here.bye ?: '',
                        here.salary,
                        here.valueByYear.first(),
                        here.contractLength,
                        here.surplus,
                        here.deferredSurplus,
                ].join('\t'))
            }
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

    /**
     * The rookies worth considering at a pick: those the drafts have left there, best surplus first.
     *
     * A rookie is expected to be available when his positional rank is at or past the best one still on the
     * board at that pick. Where the drafts are too few to say — the deep picks — nothing is filtered, since
     * refusing to answer is not the same as saying nobody is left.
     *
     * <b>Repriced before it is sorted.</b> Ordering on the board's surplus would rank these by what they are
     * worth at the pick each is <i>expected</i> to go at, which is a different pick for every row and not
     * the one being made. At the top of a draft that is the difference between two orderings: a quarterback
     * whose baseline has not decayed yet costs real money at pick nine and a dollar at pick nineteen.
     */
    private List<RookieValue> candidatesAt(int overall) {
        values.findAll { RookieValue rookie ->
            Integer best = bestAvailable[rookie.position]?.get(overall)
            best == null || rookie.positionRank >= best
        }.collect { RookieValue rookie ->
            RookieValuation.at(rookie, overall, baselines)
        }.sort { RookieValue rookie -> -rookie.surplus }.take(CANDIDATES)
    }
}

