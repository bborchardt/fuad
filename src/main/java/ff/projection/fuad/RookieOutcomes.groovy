package ff.projection.fuad

import ff.data.RealisedSeason
import ff.load.fuad.RookieSeasons
import ff.projection.PointsCurve

/**
 * How widely a rookie rank's seasons actually turn out, measured on rookies rather than borrowed.
 *
 * <b>The board's rule is that a spread belongs to a position and never to a player, and this keeps that
 * rule while fixing what it was applied to.</b> Rookies were being given the <i>veteran</i> position's
 * spread, which is a distribution of established players. At the top of a class that is close to right —
 * measured, the rate spread of a rookie ranked in his position's top five is 1.56 to 1.58 times its mean at
 * the ninetieth percentile against 1.66 to 1.71 for the veterans, so slightly <b>narrower</b>. By rookie
 * rank 21 it is 2.31 to 2.53, and six in ten of those seasons never happen at all.
 *
 * That gap is the whole reason a fourth round pick priced at zero. Value over replacement is convex — five
 * seasons of fifty points are worth nothing five times over and one season of two hundred is worth
 * seventy-eight — so a rank whose outcomes are bimodal is worth real money at a mean that looks worthless.
 * A spread that cannot reach two hundred cannot see it.
 *
 * <b>Multipliers are ratios of rate, never of season totals.</b> That is the correction that makes this
 * safe. A season-total ratio applied to a rate double counts availability, which is mild at a veteran rank
 * and severe at a rookie one, and it is what once priced rookie quarterbacks at $151 against a board topping
 * out at $89. Here the numerator and the denominator are both rates and availability travels separately, in
 * the {@code games} of the same realised season.
 *
 * <b>Pooled across neighbouring ranks, because a distribution needs more seasons than a mean does.</b> A
 * single rookie rank has nine observations at best, so each one is given the seasons of the ranks around it
 * — a sliding window and never fixed bands, which have edges, and an edge near replacement is a cliff worth
 * tens of dollars. It is a stated pooling unit either way: a deep rookie is given the spread of the deep
 * rookies around him, which is a claim about that stretch of the board and not about him.
 *
 * See docs/fuad/PROJECTION.md.
 */
class RookieOutcomes {

    /**
     * Ranks either side of one that are pooled in to give it a distribution.
     *
     * <b>A sliding window rather than fixed bands, because a band has edges and edges are cliffs.</b> The
     * first version of this banded ranks 1-5, 6-10, 11-20 and 21 up, and the boundary showed up on the board
     * immediately: Omar Cooper at WR5 and Denzel Boston at WR6 have blended rates within one per cent of each
     * other and were priced $52 and $85, entirely because one fell in the top band and the other did not.
     *
     * Near replacement that difference is not small. A rookie levelled at 9.2 points a game against a
     * receiver replacement of 9.8 is worth nothing at his mean, so <b>all</b> of his value comes from the
     * right tail — and a tenth more tail is most of a doubling. Banding was a reasonable way to get a sample
     * and a poor way to get a smooth answer, and the whole argument for measuring the spread at every rank
     * was to keep the board free of exactly this.
     *
     * Five either side, which is what {@link ff.projection.PointsCurve} does to levels for the same reason
     * and to the same end.
     */
    private static final int SMOOTHING_RADIUS = 5

    /** How much wider the window goes, a step at a time, when a rank is too thinly observed to speak. */
    private static final int WIDENING_STEP = 5

    /** Below this many seasons a window is widened, and past the position's depth it becomes the position. */
    private static final int MINIMUM_OBSERVATIONS = 20

    private final RookieSeasons seasons
    private final Map<List, List<PointsCurve.Outcome>> byRank = [:]

    RookieOutcomes(RookieSeasons seasons) {
        this.seasons = seasons
    }

    /**
     * The seasons a rank of this kind has actually produced, as rate multipliers paired with their games.
     *
     * Paired, because the two halves of one realised season belong together: a rookie who wins a job plays
     * a full year <b>and</b> scores at a starter's rate, and drawing the two independently would lose
     * exactly the correlation that makes a pick worth taking.
     */
    List<PointsCurve.Outcome> of(String position, int rank, int contractYear) {
        byRank.computeIfAbsent([position, rank, contractYear]) { List key ->
            outcomesOf(windowAround(position, rank, contractYear))
        }
    }

    /**
     * Whether a rank was too thinly observed at the standard window and had to be widened.
     *
     * True at quarterback and tight end down most of the board, where nine classes do not rank enough of
     * them for eleven ranks to make a distribution. Widening is the right answer — a spread over more ranks
     * beats a spread over eleven seasons — but a reader comparing two ranks should know when one of them is
     * speaking for a wider stretch of the board than the other.
     */
    boolean isWidened(String position, int rank, int contractYear) {
        seasonsWithin(position, rank, SMOOTHING_RADIUS, contractYear).size() < MINIMUM_OBSERVATIONS
    }

    /**
     * The seasons pooled for one rank: its neighbours, widened until there are enough of them.
     *
     * Widening rather than falling back in one step, so a rank that is nearly well enough observed keeps
     * most of its locality instead of being handed the whole position.
     */
    private List<RealisedSeason> windowAround(String position, int rank, int contractYear) {
        for (int radius = SMOOTHING_RADIUS; ; radius += WIDENING_STEP) {
            List<RealisedSeason> within = seasonsWithin(position, rank, radius, contractYear)
            if (within.size() >= MINIMUM_OBSERVATIONS || radius > MAX_RADIUS) {
                return within
            }
        }
    }

    /** Every realised season at a position within a given distance of a rank, in one contract year. */
    private List<RealisedSeason> seasonsWithin(String position, int rank, int radius, int contractYear) {
        Map<Integer, List<RealisedSeason>> byRank = seasons.realised(contractYear)[position] ?: [:]
        byRank.findAll { int at, List<RealisedSeason> ignored -> Math.abs(at - rank) <= radius }
                .values().flatten() as List<RealisedSeason>
    }

    /**
     * Rate multipliers scaled so that the window's own mean rate is one.
     *
     * The mean is taken as total points over total games rather than as an average of per-season rates,
     * which is the same construction the curve levels a rank with — {@code seasonPoints / expectedGames} is
     * a ratio of means. Building the two differently would leave the multiplier centred somewhere other
     * than the level it multiplies.
     *
     * A season nobody played carries no rate and is kept at zero games, where it contributes nothing to the
     * value and everything to the average. Dropping those is what would make a bust free.
     */
    private static List<PointsCurve.Outcome> outcomesOf(List<RealisedSeason> pooled) {
        BigDecimal points = pooled.sum { it.points } as BigDecimal ?: 0.0g
        int games = pooled.sum { it.games } as int ?: 0
        if (!games || points <= 0) {
            return []
        }
        BigDecimal mean = points / games
        pooled.collect { RealisedSeason season ->
            double multiplier = season.games > 0 ? ((season.points / season.games) / mean).toDouble() : 0.0d
            new PointsCurve.Outcome(multiplier, season.games)
        }
    }

    /** Past this the window is the whole position, every rank the consensus carries. */
    private static final int MAX_RADIUS = 200
}
