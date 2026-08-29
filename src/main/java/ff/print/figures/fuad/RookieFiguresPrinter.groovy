package ff.print.figures.fuad

import ff.data.fuad.RookiePick
import ff.data.fuad.RookieValue
import ff.load.fuad.RookieDemand
import ff.load.fuad.RookieDraftHistory
import ff.load.fuad.RookieSeasons
import ff.data.fantasypros.FpRankedPlayer
import ff.load.fantasypros.FantasyProsLoader
import ff.load.util.LoadUtils
import ff.projection.fuad.RookieOutcomes
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
 *
 * <b>rookiespread.tsv</b> is per position and rank band: how widely a rookie's seasons actually run, and how
 * many of them never happen. It is the evidence for measuring the spread on rookies rather than borrowing
 * the veterans', and it carries the number that makes the case — the spread is narrower than a veteran's at
 * the top of a class and half again as wide at the bottom.
 *
 * <b>rookieclass.tsv</b> is per season: where each class's best rookies sat in that year's dynasty ranking.
 * It is the only figure here about a <i>class</i> rather than a rank, and it is what lets a reader see that
 * the board in front of them is a weak year rather than a bad model.
 */
class RookieFiguresPrinter {

    /** Positions in the order the documentation reads them. Kickers are not drafted here and are omitted. */
    private static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE'].asImmutable()

    /** Picks the demand table runs to: the deepest a five round draft of ten teams reaches. */
    private static final int PICKS = 50

    /** Ranks reported for the spread, one inside each band {@link RookieOutcomes} measures. */
    private static final List<Integer> BAND_RANKS = [3, 8, 15, 25].asImmutable()

    /** How many of a class's best rookies the class quality figure reports. */
    private static final int CLASS_TOP = 5

    private final RookieSeasons seasons
    private final RookieOutcomes outcomes
    private final RookieDemand demand
    private final List<RookieValue> values

    RookieFiguresPrinter(RookieSeasons seasons, RookieOutcomes outcomes, RookieDemand demand,
                         List<RookieValue> values) {
        this.seasons = seasons
        this.outcomes = outcomes
        this.demand = demand
        this.values = values
    }

    /**
     * How widely a rookie rank's seasons run, and how many of them never happen.
     *
     * Reported in the second contract year, which is the one a rookie board turns on: the first is the
     * season he is learning the league and the later ones rest on fewer classes. {@code MISSING} is the
     * share of seasons with no games at all, which is where a deep rookie's bimodality lives — the spread
     * of the seasons that <i>did</i> happen is only half the story.
     *
     * {@code POOLED} marks a band too thin to measure on its own, which fell back to the whole position.
     * Quarterback and tight end past rank ten are both of those, and the two bands there report the same
     * figures for that reason rather than by accident.
     */
    void printSpread(PrintWriter out) {
        out.println(['POS', 'RANK', 'SEASONS', 'MISSING', 'P50', 'P90', 'MAX', 'POOLED'].join('\t'))
        POSITIONS.each { String position ->
            BAND_RANKS.each { int rank ->
                List<PointsCurve.Outcome> band = outcomes.of(position, rank, 2)
                List<Double> played = band.findAll { it.games > 0 }*.rateMultiplier.sort()
                if (!played) {
                    return
                }
                def at = { double f -> played[Math.min(played.size() - 1, (played.size() * f) as int)] }
                out.println([
                        position,
                        rank,
                        band.size(),
                        percent(band.count { it.games == 0 } / (band.size() as BigDecimal)),
                        round(at(0.5)),
                        round(at(0.9)),
                        round(played.last()),
                        outcomes.isPooled(position, rank, 2) ? 'POS' : '',
                ].join('\t'))
            }
        }
    }

    /**
     * Where each class's best rookies sat in that year's dynasty ranking, which is class quality.
     *
     * The rookie ranking cannot say this: it orders a class from one and starts again the next year, so its
     * first pick is its first pick in a generational year and a bare one alike. The dynasty ranking places
     * the same players against the whole league, and the difference between a top five sitting at 7 and one
     * sitting at 51 is the difference between the two classes.
     */
    void printClass(PrintWriter out) {
        out.println((['SEASON'] + (1..CLASS_TOP).collect { "TOP$it" as String } + ['MEAN']).join('\t'))
        LoadUtils.YEARS.each { String season ->
            List<Integer> ranks = topRookieDynastyRanks(season)
            if (ranks.size() < CLASS_TOP) {
                return
            }
            out.println(([season] + ranks + [
                    (ranks.sum() / ranks.size()).setScale(0, RoundingMode.HALF_UP),
            ]).join('\t'))
        }
    }

    /**
     * The dynasty rank of each of a class's top five rookies, in rookie order.
     *
     * Joined by name through the same prefix matching everything else here uses, since the two exports are
     * different files with the same people in them. A rookie the dynasty ranking does not carry is skipped
     * rather than defaulted: that happens to deep rookies and not to the top five of a class.
     */
    private static List<Integer> topRookieDynastyRanks(String season) {
        Map<String, FpRankedPlayer> dynasty =
                new FantasyProsLoader().loadRankedPlayers(LoadUtils.fpDynastyRankingsPprResourcePath(season))
        new FantasyProsLoader().loadRankedPlayers(LoadUtils.fpRookieRankingsPprResourcePath(season))
                .values().sort { it.rank.overallRank }.take(CLASS_TOP).collect { FpRankedPlayer rookie ->
            FpRankedPlayer matched = dynasty[rookie.player.name] ?:
                    dynasty.values().find { LoadUtils.isNameMatch(it.player.name, rookie.player.name, 5) }
            matched?.rank?.overallRank
        }.findAll()
    }

    private static String percent(BigDecimal share) {
        (share * 100).setScale(0, RoundingMode.HALF_UP).toString()
    }

    private static String round(double value) {
        String.format('%.2f', value)
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
