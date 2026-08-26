package ff.print.greenfield

import ff.data.fantasypros.FpRankedPlayer
import ff.projection.ByeWeeks
import ff.projection.ExpectedValue
import ff.projection.PointsCurve

import java.math.RoundingMode

/**
 * Every player the board makes a claim about, by position and in consensus order, with what he is worth.
 *
 * <b>{@code VOR} is the column to draft from, not {@code PTS}.</b> Points say how much a player scores;
 * value over replacement says how much better he is than the player who would take his slot otherwise, which
 * is the only thing a draft pick can buy. The two rank positions very differently here — a quarterback is
 * measured against a rank 15 replacement in a fourteen team single quarterback league, and a receiver
 * against a rank 43 one, because every flex in this league goes to a receiver.
 *
 * A player past the depth the curve still makes a claim at is blank rather than zero, as on the auction
 * board: a rank nothing is known about is not a rank known to be worthless.
 *
 * <b>{@code ADP} is when this league has taken that rank, not when it should.</b> Beside {@code VOR} it is
 * the whole draft decision: worth says who, demand says when. A rank that goes far later than it is worth is
 * one to wait on, and one that goes earlier has to be reached for or done without. Blank where nine drafts
 * have too little to say about the rank.
 *
 * {@code KEPT} names the owner who has already taken the player off the board. He is shown rather than
 * dropped, because what a keeper is worth is exactly what the owner who does not hold him is bidding
 * against, and because a board that silently omitted twelve of its best players would be read as a board
 * with nothing at those ranks.
 */
class GreenfieldBoardPrinter {

    private final PointsCurve curve
    private final Map<String, Map<Integer, BigDecimal>> replacement
    private final ByeWeeks byes
    private final Collection<FpRankedPlayer> ranked
    private final Map<String, String> keptBy
    private final Map<String, Map<Integer, Integer>> adp

    GreenfieldBoardPrinter(Collection<FpRankedPlayer> ranked, PointsCurve curve,
                           Map<String, Map<Integer, BigDecimal>> replacement, ByeWeeks byes,
                           Map<String, String> keptBy, Map<String, Map<Integer, Integer>> adp) {
        this.ranked = ranked
        this.curve = curve
        this.replacement = replacement
        this.byes = byes
        this.keptBy = keptBy
        this.adp = adp
    }

    void print(PrintWriter out) {
        out.println(['POS', 'RANK', 'PLAYER', 'TEAM', 'BYE', 'PTS', 'PPG', 'G', 'VOR', 'TIER', 'ADP', 'KEPT'].join('\t'))
        ranked.sort { a, b ->
            (a.player.position <=> b.player.position) ?: (a.rank.positionRank <=> b.rank.positionRank)
        }.each { FpRankedPlayer player ->
            String position = player.player.position
            int rank = player.rank.positionRank
            boolean priced = rank <= curve.pricedDepth(position)
            out.println([
                    position,
                    rank,
                    player.player.name,
                    player.player.team ?: '',
                    player.bye ?: '',
                    priced ? curve.seasonPoints(position, rank).setScale(1, RoundingMode.HALF_UP) : '',
                    priced ? curve.levelledRate(position, rank).setScale(2, RoundingMode.HALF_UP) : '',
                    priced ? curve.expectedGames(position, rank).setScale(2, RoundingMode.HALF_UP) : '',
                    priced ? ExpectedValue.expectedValueOverReplacement(curve, replacement, position, rank, byes)
                            .setScale(1, RoundingMode.HALF_UP) : '',
                    priced ? curve.tier(position, rank) : '',
                    adp[position]?.get(rank) ?: '',
                    keptBy[player.player.name] ?: '',
            ].join('\t'))
        }
    }
}
