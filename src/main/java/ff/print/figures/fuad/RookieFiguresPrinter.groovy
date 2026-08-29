package ff.print.figures.fuad

import ff.data.fuad.RookiePick
import ff.data.fuad.RookieValue
import ff.load.fuad.RookieDemand
import ff.load.fuad.RookieDraftHistory
import ff.load.fuad.RookieSeasons
import ff.projection.fuad.RookieSalary
import ff.projection.PointsCurve

import java.math.RoundingMode

/**
 * The rookie board's own account of itself, for the documentation to cite rather than quote.
 *
 * Three tables, for the three claims the prose makes and cannot check by eye.
 *
 * <b>rookies.tsv</b> is per position and contract year: what a rookie rank has levelled at in each year of
 * the contract, and how many classes that year is levelled off. It carries the finding the whole board rests
 * on — that a quarterback and a receiver are worth much more in their second season and a running back is
 * not — and it carries the thinning sample beside it, so nobody reads a fifth year figure as though it were
 * as well evidenced as a first.
 *
 * <b>rookiesalary.tsv</b> is per season: the baselines bylaw 8.3 decays from, and how many of that draft's
 * kept picks the rule reproduces exactly. It is the check that a rule read out of a bylaw is the rule the
 * league actually charged.
 *
 * <b>rookiedemand.tsv</b> is per pick: the best rookie at each position the league's own drafts have left
 * there. It is what separates who is worth taking from who will still be available, which is the whole of
 * planning a draft.
 */
class RookieFiguresPrinter {

    /** Positions in the order the documentation reads them. Kickers are not drafted here and are omitted. */
    private static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE'].asImmutable()

    /** Picks the demand table runs to: the deepest a five round draft of ten teams reaches. */
    private static final int PICKS = 50

    private final RookieSeasons seasons
    private final RookieDemand demand
    private final List<RookieValue> values

    RookieFiguresPrinter(RookieSeasons seasons, RookieDemand demand, List<RookieValue> values) {
        this.seasons = seasons
        this.demand = demand
        this.values = values
    }

    /**
     * What a rookie rank levels at in each year of his contract, and what that year is levelled off.
     *
     * Ranks one to three at each position, because a rookie draft is decided at the top of a class: by the
     * fourth back or the eighth receiver the curve is levelling players most of whom never played.
     */
    void printCurve(PrintWriter out) {
        out.println(['POS', 'YEAR', 'CLASSES', 'PTS1', 'PTS2', 'PTS3'].join('\t'))
        POSITIONS.each { String position ->
            (1..RookieSeasons.CONTRACT_YEARS).each { int year ->
                PointsCurve curve = seasons.curve(year)
                out.println(([position, year, seasons.classesBehind(year)] +
                        (1..3).collect { int rank ->
                            curve.seasonPoints(position, rank).setScale(0, RoundingMode.HALF_UP)
                        }).join('\t'))
            }
        }
    }

    /**
     * The rule against the record: what each season's baselines were, and how many picks it comes out right
     * for.
     *
     * {@code EXACT} short of {@code KEPT} is 2018 and 2019 running backs and nothing else — seven picks in
     * nine drafts, each a dollar light, both years charged off a baseline of 5 where the deadline rosters
     * hold 4. See docs/fuad/LEAGUE_RULES.md.
     */
    void printSalary(PrintWriter out) {
        out.println((['SEASON'] + RookieSalary.BASELINE_RANK.keySet().toList() +
                ['PICKS', 'KEPT', 'EXACT', 'CLASSCOST']).join('\t'))
        RookieDraftHistory.PRICED_SEASONS.each { String season ->
            Map<String, Integer> baselines = RookieSalary.baselinesFor(season)
            List<RookiePick> picks = RookieDraftHistory.picks(season)
            List<RookiePick> kept = picks.findAll {
                it.kept && RookieSalary.BASELINE_RANK.containsKey(it.position)
            }
            out.println(([season] + RookieSalary.BASELINE_RANK.keySet().collect { baselines[it] } + [
                    picks.size(),
                    kept.size(),
                    kept.count { RookiePick pick ->
                        RookieSalary.salary(baselines[pick.position], pick.overall - 1) == pick.salary
                    },
                    kept.sum { it.salary } ?: 0,
            ]).join('\t'))
        }
    }

    /**
     * The best rookie at each position still on the board at each pick, over the league's own nine drafts.
     *
     * Blank where too few drafts reached that pick to say. Only five of the nine ran to five rounds and one
     * ran past fifty picks, so the deep end thins out rather than stopping.
     */
    void printDemand(PrintWriter out) {
        Map<String, Map<Integer, Integer>> best = demand.bestAvailableByPick()
        out.println((['PICK'] + POSITIONS.collect { "BEST$it" as String }).join('\t'))
        (1..PICKS).each { int pick ->
            out.println(([pick] + POSITIONS.collect { best[it]?.get(pick) ?: '' }).join('\t'))
        }
    }

    /**
     * Where each rookie rank has actually gone, which is the other direction and a different table.
     *
     * Kept apart from the demand ladder rather than added to it as a column, because the two are not the
     * same mapping read backwards: several ranks go at the same pick and some picks take a rank nobody
     * expected, so inverting one to fill the other leaves gaps at exactly the picks a reader would ask
     * about. This one is keyed by the rank because it answers a question about a player.
     */
    void printAdp(PrintWriter out) {
        Map<Integer, Integer> expected = demand.expectedPickByRank()
        out.println(['RANK', 'PICK'].join('\t'))
        expected.keySet().sort().findAll { it <= PICKS }.each { int rank ->
            out.println([rank, expected[rank]].join('\t'))
        }
    }

    /**
     * The class being drafted, as one row: what it holds and how much of it falls after this season.
     *
     * {@code DEFERSHARE} is the figure the board exists to make: the share of the top ten rookies' surplus
     * that arrives after the season the pick is spent in. An auction cannot buy any of it.
     */
    void printBoard(PrintWriter out) {
        List<RookieValue> top = values.findAll { it.expectedPick && it.expectedPick <= 10 }
        int surplus = top.sum { it.surplus } as int ?: 0
        int deferred = top.sum { it.deferredSurplus } as int ?: 0
        // One figure per row, as fuad/board is written, so a name in the first column can key the check.
        out.println(['FIGURE', 'VALUE'].join('\t'))
        [
                RANKED    : values.size(),
                TOPTEN    : top.size(),
                SURPLUS   : surplus,
                DEFERRED  : deferred,
                DEFERSHARE: surplus ? (deferred * 100.0g / surplus).setScale(0, RoundingMode.HALF_UP) : 0,
                SALARY    : top.sum { it.salary } ?: 0,
        ].each { String figure, Object value -> out.println([figure, value].join('\t')) }
    }
}
