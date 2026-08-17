package ff.print.figures

import ff.data.PlayerValuation
import ff.projection.AuctionValuation
import ff.projection.ByeWeeks
import ff.projection.PointsCurve
import ff.projection.StarterRequirements

import java.math.RoundingMode

/**
 * The model's own account of itself, written out so the documentation can cite it instead of quoting it.
 *
 * <b>Why this exists.</b> docs/PROJECTION.md carries something like 221 hard figures — what each rank levels
 * at, how much football it plays, how deep the curve still makes a claim, how many players the league
 * starts. Every one was a number somebody read off a run and typed into prose, and nothing checked them
 * afterwards, so they drifted: the per-game table stopped multiplying out to the season beside it, the
 * running back example came to say the opposite of what the curve shows, and four different places went on
 * quoting an availability range the curve had moved off.
 *
 * That is the same failure docs/STRATEGY.md exists to prevent in a draft plan, and it was prevented there by
 * making the plan cite a generated board rather than remember it. This is that discipline turned on the
 * documentation: the figures are generated, committed, and checked, and a number that moves shows up as a
 * diff in the commit that moved it rather than as prose that quietly went stale.
 *
 * Three tables, because they have three shapes.
 *
 * <b>curve.tsv</b> is per position and rank — everything the curve says about a rank.
 * <b>positions.tsv</b> is per position — the scalars a position carries, including what it was built from.
 * <b>board.tsv</b> is one row for the season, being the figures about the priced board as a whole.
 *
 * Written under docs/figures rather than into reports, deliberately. A report describes one auction and is
 * not committed; this describes the model, so it belongs beside the prose that cites it and in the same
 * commit as the change that moves it.
 */
class ModelFiguresPrinter {

    /** Positions in the order the documentation reads them, kickers last as the one with no curve. */
    private static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE', 'PK'].asImmutable()

    private static final int TOP_CONCENTRATION = 40

    private final PointsCurve curve
    private final StarterRequirements requirements
    private final ByeWeeks byes
    private final List<PlayerValuation> valuations
    private final BigDecimal freeCap
    private final Map<String, Integer> starters
    private final Map<String, Map<Integer, BigDecimal>> replacement

    ModelFiguresPrinter(PointsCurve curve, StarterRequirements requirements, ByeWeeks byes,
                        List<PlayerValuation> valuations, BigDecimal freeCap) {
        this.curve = curve
        this.requirements = requirements
        this.byes = byes
        this.valuations = valuations
        this.freeCap = freeCap
        this.starters = AuctionValuation.startersOf(curve, requirements)
        this.replacement = AuctionValuation.replacementLevels(curve, requirements, byes)
    }

    /**
     * Everything the curve says about one rank, down to the depth it still makes a claim at.
     *
     * Stopped at the priced depth rather than run to the bottom of the ranking, since a rank the curve has
     * given up on is not a figure anything should be citing. {@code PPG} is the levelled rate, so
     * {@code PPG * G} lands on {@code PTS} — the board reports it that way and a reader who multiplies two
     * columns has to arrive at the third.
     */
    void printCurve(PrintWriter out) {
        out.println(['POS', 'RANK', 'PTS', 'PPG', 'G', 'SE', 'TIER', 'VOR', 'VOREXP'].join('\t'))
        POSITIONS.findAll { curve.pricedDepth(it) > 0 }.each { String position ->
            (1..curve.pricedDepth(position)).each { int rank ->
                out.println([
                        position,
                        rank,
                        curve.seasonPoints(position, rank).setScale(1, RoundingMode.HALF_UP),
                        curve.levelledRate(position, rank).setScale(2, RoundingMode.HALF_UP),
                        curve.expectedGames(position, rank).setScale(2, RoundingMode.HALF_UP),
                        curve.standardError(position, rank).setScale(1, RoundingMode.HALF_UP),
                        curve.tier(position, rank),
                        AuctionValuation.expectedValueOverReplacement(curve, replacement, position, rank, byes)
                                .setScale(1, RoundingMode.HALF_UP),
                        AuctionValuation.valueOverReplacementAtExpectation(curve, replacement, position, rank, byes)
                                .setScale(1, RoundingMode.HALF_UP),
                ].join('\t'))
            }
        }
    }

    /**
     * The scalars a position carries, including what its curve was built from and how it came out.
     *
     * Kickers are here with almost every column empty, which is the point: the statistics carry no kicking,
     * so the position has no curve, no depth and no level, and the report that says so should be the same
     * one that says everything else. {@code STARTED} it does have, since a lineup requires one.
     */
    void printPositions(PrintWriter out) {
        out.println(['POS', 'DEPTH', 'PRICEDDEPTH', 'STARTED', 'REPLRANK', 'GAMMA', 'SHARE', 'TARGETSHARE',
                     'P10', 'P90', 'SEASONS', 'LOST', 'BACKWARD', 'BACKWARDTOTALS'].join('\t'))
        Map<String, BigDecimal> share = pricedShareByPosition()
        POSITIONS.each { String position ->
            boolean levelled = curve.pricedDepth(position) > 0
            PointsCurve.Census census = curve.census(position)
            out.println([
                    position,
                    levelled ? curve.depth(position) : '',
                    levelled ? curve.pricedDepth(position) : '',
                    starters[position] ?: 0,
                    // Replacement is the best player who would not be started, so one past the last starter.
                    starters[position] ? (starters[position] as int) + 1 : '',
                    AuctionValuation.PRICE_STEEPNESS[position] ?: '',
                    percent(share[position]),
                    percent(AuctionValuation.MARKET_SHARE[position]),
                    levelled ? curve.outcomePercentile(position, AuctionValuation.LOW_OUTCOME)
                            .setScale(2, RoundingMode.HALF_UP) : '',
                    levelled ? curve.outcomePercentile(position, AuctionValuation.HIGH_OUTCOME)
                            .setScale(2, RoundingMode.HALF_UP) : '',
                    levelled ? census.seasons : '',
                    levelled ? census.lost : '',
                    levelled ? percent(census.backward) : '',
                    levelled ? percent(census.backwardOfTotals) : '',
            ].join('\t'))
        }
    }

    /**
     * The board as a whole, which is the one thing no per-player or per-position table can carry.
     *
     * Both `PRICE` and `COST` totals, because the two answer different questions and the documentation uses
     * each in different places: `PRICE` is what open bidding is expected to settle at, `COST` is what teams
     * actually pay once the tag holds the very best players below it. Concentration is reported on both for
     * the same reason.
     */
    void printBoard(PrintWriter out) {
        List<Integer> prices = valuations.collect { it.marketSalary }.sort().reverse()
        List<Integer> costs = valuations.collect { it.salary }.sort().reverse()
        List<PlayerValuation> tagged = valuations.findAll { it.franchiseTagged }
        // One figure per row rather than one wide row. The documentation reads these down a column against
        // what the league actually did, and a name in the first column is what lets a table be checked.
        out.println(['FIGURE', 'VALUE'].join('\t'))
        [
                PLAYERS         : valuations.size(),
                TOTALPRICE      : prices.sum() ?: 0,
                TOTALCOST       : costs.sum() ?: 0,
                TOPPRICE        : prices ? prices.first() : 0,
                TOPCOST         : costs ? costs.first() : 0,
                PLAYERSABOVE1   : costs.count { it > 1 },
                TOP40PRICE      : concentration(prices),
                TOP40COST       : concentration(costs),
                TAGS            : tagged.size(),
                TEAMSTAGGING    : tagged.collect { it.franchiseId }.toSet().size(),
                FREECAP         : freeCap.setScale(0, RoundingMode.HALF_UP),
                EXPECTEDSPEND   : (freeCap * AuctionValuation.SPEND_RATE).setScale(0, RoundingMode.HALF_UP),
        ].each { String figure, Object value -> out.println([figure, value].join('\t')) }
    }

    /** What share of the money each position ends up with, which is what the calibration is aiming at. */
    private Map<String, BigDecimal> pricedShareByPosition() {
        BigDecimal total = (valuations.collect { it.marketSalary }.sum() ?: 0) as BigDecimal
        POSITIONS.collectEntries { String position ->
            BigDecimal paid = (valuations.findAll { it.position == position }
                    .collect { it.marketSalary }.sum() ?: 0) as BigDecimal
            [(position): total > 0 ? paid / total : 0.0]
        }
    }

    /** The share of the board's money held by its most expensive forty, which is what can be checked. */
    private static BigDecimal concentration(List<Integer> descending) {
        BigDecimal total = (descending.sum() ?: 0) as BigDecimal
        total > 0 ? percent((descending.take(TOP_CONCENTRATION).sum() ?: 0) as BigDecimal / total) : 0.0
    }

    private static BigDecimal percent(BigDecimal share) {
        ((share ?: 0.0) * 100).setScale(1, RoundingMode.HALF_UP)
    }
}
