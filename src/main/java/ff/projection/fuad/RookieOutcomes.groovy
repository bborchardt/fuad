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
 * <b>Banded by rank, because a distribution needs more seasons than a mean does.</b> A single rookie rank
 * has nine observations at best. The bands are the coarsest split that keeps the shape the measurement
 * found, and they are a stated pooling unit: a deep rookie is given the spread of deep rookies at his
 * position, which is a claim about that group and not about him.
 *
 * See docs/fuad/PROJECTION.md.
 */
class RookieOutcomes {

    /**
     * Rank bands, as the last rank in each.
     *
     * Chosen where the measurement changes rather than for roundness: the top five of a position are
     * established prospects who play, six to ten already carry a tenth who never appear, and past twenty the
     * majority of seasons never happen. See docs/fuad/PROJECTION.md.
     */
    private static final List<Integer> BANDS = [5, 10, 20, Integer.MAX_VALUE].asImmutable()

    /** Below this many seasons a band is pooled into the whole position rather than measured alone. */
    private static final int MINIMUM_OBSERVATIONS = 20

    private final RookieSeasons seasons
    private final Map<List, List<PointsCurve.Outcome>> byBand = [:]

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
        byBand.computeIfAbsent([position, bandOf(rank), contractYear]) { List key ->
            List<RealisedSeason> banded = seasonsIn(position, bandOf(rank), contractYear)
            banded.size() >= MINIMUM_OBSERVATIONS ?
                    outcomesOf(banded) : outcomesOf(seasonsIn(position, null, contractYear))
        }
    }

    /**
     * Whether a band was too thin to measure and fell back to the whole position.
     *
     * True at quarterback and tight end past rank ten, where nine classes do not rank enough of them to make
     * a distribution. The fallback is the right answer — a spread over the whole position beats a spread
     * over eleven seasons — but it means two bands report identical figures, and a reader seeing that
     * without being told would reasonably suspect a bug.
     */
    boolean isPooled(String position, int rank, int contractYear) {
        seasonsIn(position, bandOf(rank), contractYear).size() < MINIMUM_OBSERVATIONS
    }

    /**
     * Rate multipliers scaled so that the band's own mean rate is one.
     *
     * The mean is taken as total points over total games rather than as an average of per-season rates,
     * which is the same construction the curve levels a rank with — {@code seasonPoints / expectedGames} is
     * a ratio of means. Building the two differently would leave the multiplier centred somewhere other
     * than the level it multiplies.
     *
     * A season nobody played carries no rate and is kept at zero games, where it contributes nothing to the
     * value and everything to the average. Dropping those is what would make a bust free.
     */
    private static List<PointsCurve.Outcome> outcomesOf(List<RealisedSeason> banded) {
        BigDecimal points = banded.sum { it.points } as BigDecimal ?: 0.0g
        int games = banded.sum { it.games } as int ?: 0
        if (!games || points <= 0) {
            return []
        }
        BigDecimal mean = points / games
        banded.collect { RealisedSeason season ->
            double multiplier = season.games > 0 ? ((season.points / season.games) / mean).toDouble() : 0.0d
            new PointsCurve.Outcome(multiplier, season.games)
        }
    }

    /** Every realised season at a position in a contract year, in a band or across all of them. */
    private List<RealisedSeason> seasonsIn(String position, Integer band, int contractYear) {
        Map<Integer, List<RealisedSeason>> byRank = seasons.realised(contractYear)[position] ?: [:]
        byRank.findAll { int rank, List<RealisedSeason> ignored -> band == null || bandOf(rank) == band }
                .values().flatten() as List<RealisedSeason>
    }

    private static int bandOf(int rank) {
        BANDS.find { rank <= it }
    }
}
