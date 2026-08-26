package ff.print.figures.greenfield

import ff.data.fantasypros.FpRankedPlayer
import ff.data.greenfield.KeeperSurplus
import ff.league.League
import ff.projection.ByeWeeks
import ff.projection.ExpectedValue
import ff.projection.PointsCurve

import java.math.RoundingMode

/**
 * The Greenfield model's account of itself, for docs/greenfield/README.md to cite instead of quote.
 *
 * Same discipline as {@link ModelFiguresPrinter} and for the same reason: every number a document makes an
 * argument from is generated, committed, and checked, so one that moves fails the check in the commit that
 * moved it rather than sitting in prose looking exactly like a number that did not.
 *
 * Written into the same directory as the auction's figures, under names that carry the league, so one
 * {@code ./check_docs.sh} run holds both leagues' documentation to both leagues' models.
 *
 * <b>The keeper table is model output about league input.</b> It moves when the curve moves and also when an
 * owner declares a different keeper, and both should fail a document that has gone stale — the first because
 * the argument no longer holds, the second because it is about players nobody kept.
 */
class GreenfieldFiguresPrinter {

    private final PointsCurve curve
    private final Map<String, Map<Integer, BigDecimal>> replacement
    private final ByeWeeks byes
    private final Map<String, Integer> starters
    private final Collection<FpRankedPlayer> ranked

    GreenfieldFiguresPrinter(PointsCurve curve, Map<String, Map<Integer, BigDecimal>> replacement,
                             ByeWeeks byes, Map<String, Integer> starters,
                             Collection<FpRankedPlayer> ranked) {
        this.curve = curve
        this.replacement = replacement
        this.byes = byes
        this.starters = starters
        this.ranked = ranked
    }

    /** Everything the curve says about one rank, down to the depth it still makes a claim at. */
    void printCurve(PrintWriter out) {
        out.println(['POS', 'RANK', 'PTS', 'PPG', 'G', 'VOR'].join('\t'))
        League.GREENFIELD.scoredPositions.findAll { curve.pricedDepth(it) > 0 }.each { String position ->
            (1..curve.pricedDepth(position)).each { int rank ->
                out.println([position, rank,
                             curve.seasonPoints(position, rank).setScale(1, RoundingMode.HALF_UP),
                             curve.levelledRate(position, rank).setScale(2, RoundingMode.HALF_UP),
                             curve.expectedGames(position, rank).setScale(2, RoundingMode.HALF_UP),
                             ExpectedValue.expectedValueOverReplacement(curve, replacement, position, rank, byes)
                                     .setScale(1, RoundingMode.HALF_UP)].join('\t'))
            }
        }
    }

    /**
     * The scalars a position carries.
     *
     * {@code STARTED} against {@code REPLRANK} is the whole shape of this league: the flex is what decides
     * them, and where it goes decides what every position is worth.
     */
    void printPositions(PrintWriter out) {
        out.println(['POS', 'DEPTH', 'PRICEDDEPTH', 'STARTED', 'REPLRANK', 'BEST', 'REPLPTS'].join('\t'))
        League.GREENFIELD.scoredPositions.each { String position ->
            boolean levelled = curve.pricedDepth(position) > 0
            int started = starters[position] ?: 0
            out.println([position,
                         levelled ? curve.depth(position) : '',
                         levelled ? curve.pricedDepth(position) : '',
                         started,
                         started + 1,
                         levelled ? curve.seasonPoints(position, 1).setScale(1, RoundingMode.HALF_UP) : '',
                         levelled && started + 1 <= curve.pricedDepth(position)
                                 ? curve.seasonPoints(position, started + 1).setScale(1, RoundingMode.HALF_UP)
                                 : ''].join('\t'))
        }
    }

    /** Every keeper declared this season, against the pick it costs. */
    static void printKeepers(PrintWriter out, List<KeeperSurplus> keepers) {
        out.println(['PLAYER', 'POS', 'RANK', 'COSTROUND', 'VOR', 'MEASURED', 'POSVALUE',
                     'MEASUREDSURPLUS', 'POSSURPLUS'].join('\t'))
        keepers.sort { -(it.positionalSurplus() ?: it.measuredSurplus() ?: it.surplus()) }.each { KeeperSurplus k ->
            out.println([k.player, k.position ?: '', k.positionRank ?: '', k.costRound,
                         one(k.keeperValue), one(k.measuredAlternativeValue),
                         one(k.positionalAlternativeValue),
                         one(k.measuredSurplus()), one(k.positionalSurplus())].join('\t'))
        }
    }

    private static String one(BigDecimal value) {
        value == null ? '' : value.setScale(1, RoundingMode.HALF_UP) as String
    }
}
