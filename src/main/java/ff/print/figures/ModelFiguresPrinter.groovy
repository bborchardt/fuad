package ff.print.figures

import ff.data.FranchiseTag
import ff.data.PlayerValuation
import ff.projection.AuctionSpend
import ff.projection.AuctionValuation
import ff.projection.ByeWeeks
import ff.projection.PointsCurve
import ff.projection.StarterRequirements
import ff.projection.TagHistory

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
 * Six tables, because they have six shapes.
 *
 * <b>curve.tsv</b> is per position and rank — everything the curve says about a rank.
 * <b>positions.tsv</b> is per position — the scalars a position carries, including what it was built from.
 * <b>board.tsv</b> is one row for the season, being the figures about the priced board as a whole.
 * <b>tags.tsv</b> is per season and player — every franchise tag the model recovers, being the one figure
 * here that is an inference rather than arithmetic on something the league published.
 * <b>rates.tsv</b> is per season and position — what a tag cost, set by rule off the previous season.
 * <b>spend.tsv</b> is per season and position — what the league actually paid, which is the one thing here
 * that describes the league rather than the model, and is here because it is what the model's calibration
 * is fitted to and the only evidence for which seasons it throws away.
 *
 * Written under docs/figures rather than into reports, deliberately. A report describes one auction and is
 * not committed; this describes the model, so it belongs beside the prose that cites it and in the same
 * commit as the change that moves it.
 */
class ModelFiguresPrinter {

    /** Positions in the order the documentation reads them. */
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
     * Kickers carry a full row now. They used to sit here with almost every column empty, because the
     * statistics this project kept carried no kicking and so no rank at the position could be levelled.
     * They can, and the row is worth reading: {@code SHARE} against {@code TARGETSHARE} is where the
     * league's pricing of the position parts company with what the curve says it is worth.
     *
     * {@code ANCHOR} is here for the same reason the rest of the row is. It is the factor
     * {@link PointsCurve} scales a position's shape by to put its level back where its seasons actually
     * were, it differs by position, and it was quoted from memory in two javadoc comments that had drifted
     * apart from each other and from the model alike.
     *
     * {@code GAMESCORR}, {@code RATECV} and {@code GAMESCV} are the three measurements the rate-against-
     * availability split rests on: how weakly rank predicts how much football a player plays, and how widely
     * each half scatters. They decided {@link PointsCurve#AVAILABILITY_SMOOTHING_RADIUS} and they are the
     * evidence for treating a season as two things rather than one, so they belong where the argument that
     * cites them can be held to them.
     */
    void printPositions(PrintWriter out) {
        out.println(['POS', 'DEPTH', 'PRICEDDEPTH', 'STARTED', 'REPLRANK', 'GAMMA', 'PLAYERS', 'RESERVE',
                     'SHARE', 'TARGETSHARE', 'VORSHARE', 'P10', 'P90', 'SEASONS', 'LOST', 'BACKWARD',
                     'BACKWARDTOTALS', 'ANCHOR', 'GAMESCORR', 'RATECV', 'GAMESCV'].join('\t'))
        Map<String, BigDecimal> share = pricedShareByPosition()
        Map<String, BigDecimal> reserve = reservedShareByPosition()
        Map<String, BigDecimal> worth = valueShareByPosition()
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
                    valuations.count { it.position == position },
                    percent(reserve[position]),
                    percent(share[position]),
                    percent(AuctionValuation.MARKET_SHARE[position]),
                    percent(worth[position]),
                    levelled ? curve.outcomePercentile(position, AuctionValuation.LOW_OUTCOME)
                            .setScale(2, RoundingMode.HALF_UP) : '',
                    levelled ? curve.outcomePercentile(position, AuctionValuation.HIGH_OUTCOME)
                            .setScale(2, RoundingMode.HALF_UP) : '',
                    levelled ? census.seasons : '',
                    levelled ? census.lost : '',
                    levelled ? percent(census.backward) : '',
                    levelled ? percent(census.backwardOfTotals) : '',
                    levelled ? census.anchor.setScale(3, RoundingMode.HALF_UP) : '',
                    levelled ? census.gamesCorrelation.setScale(2, RoundingMode.HALF_UP) : '',
                    levelled ? census.rateVariation.setScale(2, RoundingMode.HALF_UP) : '',
                    levelled ? census.gamesVariation.setScale(2, RoundingMode.HALF_UP) : '',
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

    /**
     * What the league actually paid each position, season by season, which is what the calibration is fitted to.
     *
     * <b>The one table here that is about the league rather than about the model.</b> It earns its place
     * because the case for {@link AuctionValuation#MARKET_SHARE} — and in particular the case for throwing
     * 2022 away — is these figures and nothing else, and an exclusion whose evidence cannot be checked is an
     * exclusion a reader has to take on trust. The documentation quoted all sixteen of them and claimed a
     * spec recomputed them, which no spec did.
     *
     * <b>{@code COMMITTED} is the other side of the same question.</b> A league can look as though it has
     * changed what it pays for when all that has happened is a stock of old contracts expiring. If the
     * auction is buying more quarterback and the money already on the books is also becoming more
     * quarterback, then nothing is running off and the repricing is real.
     *
     * <b>Both bases, because the two are genuinely different questions.</b> {@code SHARE} is the share of
     * every auction dollar; {@code SHAREXPK} leaves kickers out of the denominator, which is how the
     * positional comparison reads and is what the prose tabulates. They differ by a few tenths at every
     * position, which is small enough to be mistaken for rounding and large enough to matter to a constant
     * that gets compared against them.
     *
     * 2022 is reported and not calibrated on. The pooled row is the span that is.
     */
    void printSpend(PrintWriter out) {
        out.println(['SEASON', 'POS', 'DOLLARS', 'SHARE', 'SHAREXPK', 'COMMITTED', 'COMMITTEDSHARE'].join('\t'))
        List<AuctionSpend.Season> measured = AuctionSpend.SUPERFLEX_SEASONS.collect { AuctionSpend.of(it) }
        measured.each { AuctionSpend.Season season -> printSpendRow(out, season.season, [season]) }
        List<AuctionSpend.Season> calibrated =
                measured.findAll { AuctionSpend.CALIBRATED_SEASONS.contains(it.season) }
        printSpendRow(out, AuctionSpend.CALIBRATED_SEASONS.first() + '-' +
                AuctionSpend.CALIBRATED_SEASONS.last(), calibrated)
    }

    /**
     * How often an expiring contract of each band actually changed hands, which is what `AVAIL` carries.
     *
     * <b>Both denominators, because the constant uses one and the prose kept reaching for the other.</b>
     * {@code MOVEDSHARE} is of the contracts somebody re-signed and is what
     * {@link AuctionValuation#AVAILABILITY} holds; {@code MOVEDOFEXPIRING} is of every contract that
     * expired. They tell opposite stories about the deepest band, and the difference between them is
     * {@code SIGNEDSHARE} — how often a band is re-signed at all, which collapses with rank.
     */
    void printRetention(PrintWriter out) {
        out.println(['BAND', 'THROUGHRANK', 'EXPIRING', 'SIGNED', 'MOVED', 'MOVEDSHARE', 'MOVEDOFEXPIRING',
                     'SIGNEDSHARE'].join('\t'))
        List<Integer> boundaries = AuctionValuation.AVAILABILITY.collect { it[0] as int }
        int from = 1
        AuctionSpend.retention(AuctionSpend.SUPERFLEX_SEASONS, boundaries).each { AuctionSpend.Retention band ->
            // The last band runs to the end of the ranking rather than to a rank anybody would recognise.
            String label = band.throughRank == Integer.MAX_VALUE ? "$from+" : "$from-$band.throughRank"
            out.println([
                    label,
                    band.throughRank == Integer.MAX_VALUE ? '' : band.throughRank,
                    band.expiring,
                    band.signed,
                    band.moved,
                    band.movedShare.setScale(2, RoundingMode.HALF_UP),
                    band.movedShareOfExpiring.setScale(2, RoundingMode.HALF_UP),
                    band.signedShare.setScale(2, RoundingMode.HALF_UP),
            ].join('\t'))
            from = band.throughRank + 1
        }
    }

    /**
     * How deep the league actually signs, against how deep the board prices.
     *
     * {@code PRICEDDEPTH} is a claim the curve makes and this is the record it is answerable to. The four
     * hand-set depths it replaced were too shallow against {@code DEEPEST} at every position, and kicker —
     * the one position the relevance floor cannot bound — is capped by hand at exactly the deepest rank the
     * league has ever paid for. See {@link PointsCurve#DEPTH_CAP}.
     */
    void printDepth(PrintWriter out) {
        out.println(['POS', 'PRICEDDEPTH', 'SIGNINGS', 'DEEPEST', 'MEDIANRANK', 'P90RANK', 'ROSTERED',
                     'WITHINDEPTH'].join('\t'))
        Map<String, AuctionSpend.Depth> measured = AuctionSpend.depth(AuctionSpend.SUPERFLEX_SEASONS)
        POSITIONS.each { String position ->
            AuctionSpend.Depth depth = measured[position]
            int priced = curve.pricedDepth(position)
            out.println([
                    position,
                    priced ?: '',
                    depth.signed.size(),
                    depth.deepest,
                    depth.rankAt(0.5),
                    depth.rankAt(0.9),
                    depth.rostered.size(),
                    priced ? percent(depth.rosteredWithin(priced)) : '',
            ].join('\t'))
        }
    }

    private static void printSpendRow(PrintWriter out, String label, List<AuctionSpend.Season> seasons) {
        Map<String, BigDecimal> share = AuctionSpend.shareByPosition(seasons)
        Map<String, BigDecimal> excludingKickers =
                AuctionSpend.shareByPosition(seasons, AuctionSpend.EXCLUDING_KICKERS)
        // Pooled by dollars like the auction shares, so a season with more money on the books counts for
        // more of the answer, which is what a share of committed salary means.
        BigDecimal committedTotal =
                (seasons.collect { it.committedTotal }.sum() ?: 0.0) as BigDecimal
        Map<String, BigDecimal> committedShare = POSITIONS.collectEntries { String position ->
            BigDecimal held = (seasons.collect { it.committed[position] ?: 0.0 }.sum() ?: 0.0) as BigDecimal
            [(position): committedTotal > 0 ? held / committedTotal : 0.0 as BigDecimal]
        }
        AuctionSpend.POSITIONS.each { String position ->
            out.println([
                    label,
                    position,
                    (seasons.collect { it.dollars[position] ?: 0.0 }.sum() ?: 0.0)
                            .setScale(0, RoundingMode.HALF_UP),
                    percent(share[position]),
                    // A kicker has no share of a total he is not in, which is not the same as a share of nil.
                    excludingKickers.containsKey(position) ? percent(excludingKickers[position]) : '',
                    (seasons.collect { it.committed[position] ?: 0.0 }.sum() ?: 0.0)
                            .setScale(0, RoundingMode.HALF_UP),
                    percent(committedShare[position]),
            ].join('\t'))
        }
    }

    /**
     * Every franchise tag the model recovers, which is the one table here reporting an inference.
     *
     * Everything else in the figures is arithmetic on data the league published. A tag is not published at
     * all — it is reconstructed from a wiped contract that came back at exactly the rate, or from a first
     * round pick moving with nothing against it — so of all the figures the documentation quotes, these are
     * the ones where being wrong is both likeliest and least visible. They were quoted in a table of 46
     * rows that nothing checked.
     *
     * Keyed by season and player together, since neither alone picks out a row: a season holds several tags
     * and a player is tagged in several seasons.
     */
    void printTags(PrintWriter out) {
        out.println(['SEASON', 'PLAYER', 'POS', 'SALARY', 'RATE', 'BASIS', 'STATUS'].join('\t'))
        TagHistory.tagsBySeason().each { String season, List<FranchiseTag> tags ->
            tags.findAll { it.status == FranchiseTag.Status.CONFIRMED }
                    .sort { a, b -> a.position <=> b.position ?: a.playerName <=> b.playerName }
                    .each { FranchiseTag tag ->
                        out.println([season, TagHistory.readableName(tag), tag.position, tag.salary,
                                     tag.franchiseSalary, TagHistory.basisOf(tag), tag.status].join('\t'))
                    }
        }
    }

    /**
     * What tagging a player at each position cost, coming into each season's auction.
     *
     * Set by rule off the previous season's top five salaries, so it is the one auction price in the record
     * that was not bid. It runs a season further than the tags do: the season being priced has a rate,
     * known from salaries already paid, and no auction yet to use it in.
     */
    void printRates(PrintWriter out) {
        out.println(['SEASON', 'POS', 'RATE'].join('\t'))
        TagHistory.RATE_SEASONS.each { String season ->
            Map<String, Integer> rates = TagHistory.franchiseSalaries(season)
            POSITIONS.each { String position ->
                if (rates.containsKey(position)) {
                    out.println([season, position, rates[position]].join('\t'))
                }
            }
        }
    }

    /**
     * What share of the board's money a position gets from the minimum bid alone.
     *
     * <b>This is why {@code SHARE} and {@code TARGETSHARE} do not agree, and the difference is not an
     * error.</b> {@link AuctionValuation#calibrate} hits the target exactly and {@code steepen} preserves
     * it exactly; then every roster spot still to be filled is reserved a dollar, and <i>that</i> is handed
     * out by headcount rather than by worth. A position holding many cheap players collects more of it than
     * its share of the money would suggest, and one holding fewer dearer players collects less.
     *
     * Kicker shows it most plainly: a sixth of the board's players for under a hundredth of its money.
     */
    private Map<String, BigDecimal> reservedShareByPosition() {
        BigDecimal total = (valuations.collect { it.marketSalary }.sum() ?: 0) as BigDecimal
        POSITIONS.collectEntries { String position ->
            [(position): total > 0 ? (valuations.count { it.position == position } as BigDecimal) / total : 0.0]
        }
    }

    /**
     * What share of the board's <b>worth</b> each position holds, against {@code TARGETSHARE}'s share of its
     * money.
     *
     * <b>The one comparison on this row that is not the model against itself.</b> {@code SHARE} and
     * {@code TARGETSHARE} both describe what the board charges, and the calibration forces the second onto
     * the first, so their agreement says only that the forcing worked. This is value over replacement before
     * any of that — what the curve says a position is worth — so the gap between it and {@code TARGETSHARE}
     * is the gap between what the league pays and what the model thinks it is buying.
     *
     * Kicker is why it is here. It takes a fraction of the auction and holds a multiple of that fraction in
     * value — a disagreement of a different kind from receiver's or tight end's, which are out by a margin
     * rather than by a factor. The four numbers that say so are the row this method writes, and the
     * documentation cites them from it: they were prose that nothing recomputed, which is the very
     * arrangement that put the league's spending into a spec and out of reach of a citation. See
     * {@link ff.projection.AuctionSpend}.
     */
    private Map<String, BigDecimal> valueShareByPosition() {
        BigDecimal total = (valuations.collect { it.valueOverReplacement }.sum() ?: 0.0) as BigDecimal
        POSITIONS.collectEntries { String position ->
            BigDecimal worth = (valuations.findAll { it.position == position }
                    .collect { it.valueOverReplacement }.sum() ?: 0.0) as BigDecimal
            [(position): total > 0 ? worth / total : 0.0]
        }
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
