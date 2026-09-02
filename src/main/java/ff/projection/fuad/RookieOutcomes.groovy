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
            windowAround(position, rank, contractYear)
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
        outcomesWithin(position, rank, SMOOTHING_RADIUS, contractYear).size() < MINIMUM_OBSERVATIONS
    }

    /**
     * The seasons pooled for one rank: its neighbours, widened until there are enough of them.
     *
     * Widening rather than falling back in one step, so a rank that is nearly well enough observed keeps
     * most of its locality instead of being handed the whole position.
     */
    private List<PointsCurve.Outcome> windowAround(String position, int rank, int contractYear) {
        for (int radius = SMOOTHING_RADIUS; ; radius += WIDENING_STEP) {
            List<PointsCurve.Outcome> within = outcomesWithin(position, rank, radius, contractYear)
            if (within.size() >= MINIMUM_OBSERVATIONS || radius > MAX_RADIUS) {
                return within
            }
        }
    }

    /**
     * Every season within a distance of a rank, each expressed against the level of <b>its own</b> rank.
     *
     * <b>This is the normalisation that has to be right, and was not.</b> A multiplier is applied to the
     * level of the rank being valued, so it has to be a ratio against the level of the rank it came from.
     * Dividing instead by the mean of the whole window overstates every rank whose level sits above its
     * neighbours' and understates every rank below: at rookie QB1 the rank's rate is 25.6 against a window
     * mean of 15.3, so each realised season arrived 68% too large, and at QB8 it arrived 21% too small.
     *
     * Normalised this way, {@code level(rank) x multiplier} is a season from somewhere in the window scaled
     * from its own rank onto this one, which is exactly what pooling neighbours is for. It is what
     * {@link PointsCurve#outcomeSeasons} now does for veterans, and for the same reason — this was the
     * first place the argument was made, and the veteran curve was pooling a whole position when it was.
     */
    private List<PointsCurve.Outcome> outcomesWithin(String position, int rank, int radius, int contractYear) {
        Map<Integer, List<RealisedSeason>> realised = seasons.realised(contractYear)[position] ?: [:]
        PointsCurve curve = seasons.curve(contractYear)
        realised.findAll { int at, List<RealisedSeason> ignored -> Math.abs(at - rank) <= radius }
                .collectMany { int at, List<RealisedSeason> seasonsAt ->
                    BigDecimal level = curve.levelledRate(position, at)
                    level > 0 ? outcomesOf(seasonsAt, level) : []
                } as List<PointsCurve.Outcome>
    }

    /**
     * One rank's seasons, as rate multipliers against that rank's own level, paired with their games.
     *
     * A season nobody played carries no rate and is kept at zero games, where it contributes nothing to the
     * value and everything to the average. Dropping those is what would make a bust free, and a bust being
     * free is the whole thing a late pick is weighed against.
     */
    private static List<PointsCurve.Outcome> outcomesOf(List<RealisedSeason> atRank, BigDecimal level) {
        atRank.collect { RealisedSeason season ->
            double multiplier = season.games > 0 ? ((season.points / season.games) / level).toDouble() : 0.0d
            new PointsCurve.Outcome(multiplier, season.games)
        }
    }

    /** Past this the window is the whole position, every rank the consensus carries. */
    private static final int MAX_RADIUS = 200
}
