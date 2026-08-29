package ff.print.fuad

import ff.data.fuad.FuadData
import ff.data.fuad.RookieValue
import ff.data.mfl.MflDraftPick
import ff.load.fuad.RookieSeasons
import ff.projection.fuad.RookieSalary
import ff.projection.fuad.RookieValuation

/**
 * The rookie draft: what each rookie is worth over the contract he comes with, and what he will cost.
 *
 * <b>{@code VALUE} is what he is worth and {@code SURPLUS} is what the contract is worth, and they move for
 * different reasons.</b> His worth is a fact about him; his salary is a fact about the pick he goes at, and
 * at quarterback this year that price runs from $20 at the first pick to $1 by the fifteenth. So a
 * quarterback's surplus is dominated by an assumption about where he lands, and only {@code VALUE} stays
 * still. {@code DEFER} is how much of the surplus falls after the coming season — the part no auction dollar
 * can buy at any price, because the auction sells one year at a time.
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

    /**
     * How many rookies an outlook lists at each pick.
     *
     * <b>Deep enough to be the sheet you read at the table, not a shortlist.</b> It was six, being what the
     * drafts expect to still be there — which is the right answer to "what should I plan for" and the wrong
     * one to "who do I take now". On the day availability is observed rather than predicted, and the one
     * thing a shortlist cannot survive is somebody falling: a rookie the room was expected to take at pick
     * three is exactly who a reader most needs priced when he is still there at nine.
     */
    private static final int CANDIDATES = 40

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
        out.println((['OVR', 'POS', 'TIER', 'RANK', 'PLAYER', 'TEAM', 'NFL', 'BYE', 'PICK', 'SALARY'] +
                years + ['LEN', 'VALLOW', 'VALUE', 'VALHIGH', 'SURPLUS', 'DEFER']).join('\t'))
        values.each { RookieValue value ->
            out.println(([
                    value.overallRank,
                    value.position,
                    value.tier,
                    value.positionRank,
                    value.playerName,
                    value.nflTeam ?: '',
                    nflDraftOf(value),
                    value.bye ?: '',
                    value.expectedPick ?: '',
                    value.salary,
            ] + value.valueByYear + [
                    value.contractLength,
                    value.valueLow,
                    value.contractValue,
                    value.valueHigh,
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
     * What a reader needs instead is every rookie priced at the pick in front of him, best first. Salary is
     * this pick's rather than his expected pick's, so a reach shows its cost and a quarterback who falls
     * stops looking expensive. {@code EXP} marks the ones the league's own drafts expect to still be there,
     * which is what a plan reads beforehand and what a live draft ignores, having the board in view.
     *
     * <b>So the sheet is one column deep at the table:</b> go to your pick, take the best {@code SURPLUS}
     * still on the board. Everything else on the row is there to say how close the call was.
     */
    void printOutlook(PrintWriter out, String franchiseId) {
        out.println(['PICK', 'ROUND', 'SLOT', 'EXP', 'POS', 'TIER', 'RANK', 'OVR', 'PLAYER', 'TEAM', 'NFL',
                     'BYE', 'SALARY', 'Y1', 'LEN', 'VALLOW', 'VALUE', 'VALHIGH', 'SURPLUS', 'DEFER'].join('\t'))
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
                        expected(here, overall) ? 'Y' : '',
                        here.position,
                        here.tier,
                        here.positionRank,
                        here.overallRank,
                        here.playerName,
                        here.nflTeam ?: '',
                        nflDraftOf(here),
                        here.bye ?: '',
                        here.salary,
                        here.valueByYear.first(),
                        here.contractLength,
                        here.valueLow,
                        here.contractValue,
                        here.valueHigh,
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
        values.collect { RookieValue rookie ->
            RookieValuation.at(rookie, overall, baselines)
        }.sort { RookieValue rookie -> -rookie.surplus }.take(CANDIDATES)
    }

    /**
     * Whether the league's own drafts expect this rookie to still be on the board at this pick.
     *
     * A flag rather than a filter. Before the draft it is what a plan reads: {@code Y} is who will
     * realistically be there. During one it is worth nothing, because you can see the board — and filtering
     * on it would have hidden the rookie who fell, who is the one a reader most needs priced.
     */
    private boolean expected(RookieValue rookie, int overall) {
        Integer best = bestAvailable[rookie.position]?.get(overall)
        best == null || rookie.positionRank >= best
    }
}

