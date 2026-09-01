package ff.load.fuad

import ff.data.fantasypros.FpRankedPlayer
import ff.load.fantasypros.FantasyProsLoader
import ff.load.util.LoadUtils

/**
 * What the dynasty ranking knows about a rookie that his rookie rank does not already say.
 *
 * <b>The rookie ranking orders a class and starts again the next year, so it cannot say whether the class
 * is any good.</b> The dynasty ranking places the same players against the whole league, which is the
 * cross-class comparison the rookie ranking refuses to make. The question is how to use it, and two
 * obvious answers are both wrong.
 *
 * Reading a rookie's dynasty rank off the <b>veteran</b> curve mixes populations: a veteran ranked 28th at
 * quarterback who plays is a backup in relief at 13 points a game, and a rookie ranked 28th who plays has
 * won a job. Building a rookie curve <b>indexed</b> by dynasty rank runs out of data instead — nine classes
 * spread over forty dynasty ranks left 27 of 117 rookies with any level at all, and every rookie worth
 * drafting fell through.
 *
 * <b>So it is used as a relative adjustment rather than as a level.</b> What the record supports is an
 * ordering claim: holding rookie rank fixed, the rookies the dynasty ranking rates above their peers go on
 * to score more. That needs no per-rank sample, because every rookie contributes to one pooled relationship.
 *
 * <b>And it is tapered, because the claim is only true at the top of a class.</b> Measured in a sliding
 * window down the rookie ranks, the signal is 0.42 at ranks two to four, 0.33 at five, 0.23 at six, 0.09 at
 * eight and gone by ten. That is a shape the data supplied rather than one chosen: below, a plateau and an
 * exponential fitted to it, so there is no rank at which the adjustment jumps. A cutoff would put a cliff on
 * the board, which this project has already paid for once.
 *
 * See docs/fuad/PROJECTION.md.
 */
class RookieDynastyIndex {

    /** Classes the relationship is measured over: every one with three seasons of outcomes behind it. */
    private static final List<String> SEASONS =
            (2017..2023).collect { it as String }.asImmutable() as List<String>

    /**
     * Rookie ranks over which the dynasty index is trusted in full.
     *
     * The measured signal is flat across the top four of a position — 0.418, 0.418, 0.428 in sliding windows
     * — and starts falling at five. A plateau rather than a decay from rank one, because that is what the
     * measurement shows.
     */
    private static final int PLATEAU = 4

    /**
     * How fast the trust decays past the plateau, in ranks.
     *
     * Three, which is what the measured signal does: normalised against its own plateau it runs 0.79, 0.54,
     * 0.38, 0.22, 0.12 and zero at ranks five to ten, against 0.72, 0.51, 0.37, 0.26, 0.19 for this decay.
     * It never quite reaches zero, which is the point of using it — there is no rank where a rookie's
     * treatment changes abruptly. By rank fifteen it is worth two per cent of the adjustment.
     */
    private static final double DECAY = 3.0d

    /**
     * How much a rookie's rate rises per unit of log dynasty residual, where the ranking rates him <b>up</b>.
     *
     * Fitted over the 57 rookie seasons at ranks one to four whose residual is positive, regressing log rate
     * on the log ratio of the dynasty rank he was expected to hold against the one he holds. The slope is
     * 0.258 with a correlation of 0.242: a rookie the dynasty ranking places twice as high as his rookie
     * rank implies scores about 20% more per game.
     *
     * <b>One fitted number, in a model that already carries several.</b> The auction board's market shares,
     * price steepness, spend rate and availability are all fitted the same way and checked the same way.
     * What this must never become is a knob: it is measured, it is reported as a figure, and a spec holds it
     * to the seasons it was fitted on.
     */
    private static final double SENSITIVITY = 0.258d

    /**
     * The adjustment is one sided, because only one side of it is there.
     *
     * Binned by residual, the rookies the dynasty ranking rates <b>below</b> their peers score exactly what
     * the ones it agrees about score — mean rates of 8.73 and 8.70 either side of zero — while the ones it
     * rates well above score 11.62. There is no penalty in the record, so none is applied. Charging one
     * would be inventing the half of the relationship the data declines to show, and it is the half that
     * would have marked down a quarterback this board is already least sure about.
     *
     * The claim this leaves is narrow and is the one the record supports: <b>the dynasty ranking picks out
     * the exceptional prospects, and says nothing useful about the rest.</b>
     */
    private static final double NO_PENALTY = 0.0d

    /**
     * The largest residual the adjustment will act on, being the ninetieth percentile of the fitted sample.
     *
     * <b>A guard against extrapolating a weak fit off the end of its own data.</b> Uncapped, this year's
     * best back — placed at dynasty RB4 where the first back of a class usually sits at RB11 — carries a
     * residual past the 95th percentile of everything the slope was fitted on, and a linear rule read there
     * lifted his rate 42% and his contract from $233 to $491. Value over replacement is convex, so a rate
     * moved by two fifths moves a price by rather more, and a fit with a correlation of 0.24 should not be
     * asked to carry that.
     */
    private static final double MAXIMUM_RESIDUAL = 0.875d

    /** Below this many observations a rookie rank has no expected dynasty rank and gets no adjustment. */
    private static final int MINIMUM_OBSERVATIONS = 6

    private Map<List, Integer> expectedByPositionAndRank

    /**
     * What to multiply a rookie's rate by, given where the dynasty ranking put him.
     *
     * Above one where the dynasty ranking rates him higher than rookies at his rank usually are, and exactly
     * one where it agrees, rates him lower, or has nothing to say. See {@link #NO_PENALTY}.
     */
    BigDecimal adjustment(String position, int rookieRank, Integer dynastyRank) {
        Integer expected = expectedDynastyRank(position, rookieRank)
        if (!dynastyRank || !expected || dynastyRank <= 0) {
            return 1.0g
        }
        double residual = Math.min(MAXIMUM_RESIDUAL,
                Math.max(NO_PENALTY, Math.log(expected / (dynastyRank as double))))
        new BigDecimal(Math.exp(SENSITIVITY * taper(rookieRank) * residual))
    }

    /**
     * How far the dynasty index is trusted at a rookie rank: all of it at the top of a class, none deep.
     *
     * Continuous everywhere, so two adjacent ranks are always treated almost identically. That is the whole
     * requirement — the alternative, trusting it fully to rank five and not at all at rank six, would price
     * two players the record cannot separate as though it could.
     */
    static double taper(int rookieRank) {
        rookieRank <= PLATEAU ? 1.0d : Math.exp(-(rookieRank - PLATEAU) / DECAY)
    }

    /**
     * Where the dynasty ranking usually puts the rookie holding a given rank at his position.
     *
     * The median over the collected classes, pooled with the ranks either side to steady it. This is the
     * baseline a rookie is measured against: the consensus first receiver of a class sits around dynasty
     * WR24, so one sitting at WR15 is being told something, and one at WR31 is being told the opposite.
     */
    Integer expectedDynastyRank(String position, int rookieRank) {
        if (expectedByPositionAndRank == null) {
            expectedByPositionAndRank = measure()
        }
        expectedByPositionAndRank[[position, rookieRank]]
    }

    /** Every rookie of the collected classes that both rankings carry, as position, rookie rank, dynasty rank. */
    private static Map<List, Integer> measure() {
        Map<List, List<Integer>> observed = [:].withDefault { [] }
        SEASONS.each { String season ->
            Map<String, FpRankedPlayer> dynasty =
                    new FantasyProsLoader().loadRankedPlayers(LoadUtils.fpDynastyRankingsPprResourcePath(season))
            new FantasyProsLoader().loadRankedPlayers(LoadUtils.fpRookieRankingsPprResourcePath(season))
                    .values().each { FpRankedPlayer rookie ->
                FpRankedPlayer ranked = dynasty[rookie.player.name] ?:
                        dynasty.values().find { LoadUtils.isNameMatch(it.player.name, rookie.player.name, 5) }
                if (ranked) {
                    observed[[rookie.player.position, rookie.rank.positionRank]] << ranked.rank.positionRank
                }
            }
        }
        // Copied out of the withDefault map before anything reads it: a lookup there creates the entry it
        // fails to find, so pooling neighbours while iterating would extend the collection being iterated.
        Map<List, List<Integer>> counts = new LinkedHashMap<List, List<Integer>>(observed)
        Map<List, Integer> median = [:]
        counts.keySet().toList().each { List key ->
            String position = key[0] as String
            int rank = key[1] as int
            List<Integer> pooled = ((rank - 1)..(rank + 1))
                    .collectMany { int at -> counts[[position, at]] ?: [] }.sort()
            if (pooled.size() >= MINIMUM_OBSERVATIONS) {
                median[key] = pooled[(pooled.size() / 2) as int]
            }
        }
        median
    }
}
