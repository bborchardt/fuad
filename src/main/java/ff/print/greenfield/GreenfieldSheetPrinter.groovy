package ff.print.greenfield

import ff.data.fantasypros.FpRankedPlayer
import ff.load.greenfield.GreenfieldBoard

import java.math.RoundingMode

/**
 * The board as one list in value order, for a spreadsheet to be kept during a draft.
 *
 * <b>Not a second board.</b> Every figure here is the board's, rearranged: one row per player across all
 * positions rather than a list per position, sorted by what he is worth rather than by what the consensus
 * thinks of him. A draft is one queue and reads best as one.
 *
 * <b>{@code EDGE} is the column this exists for.</b> {@code VORRANK} is where a player sits by worth;
 * {@code ADP} is the pick this league has typically taken him at. The difference is how far the room lets
 * him fall past his value — positive is a player worth reaching past, negative is one this room reaches for.
 * Josh Allen is worth less than the best back and goes thirty picks later, and no single column says that.
 *
 * <b>{@code TAKEN} is filled in for the keepers and left empty for everyone else</b>, which is the state the
 * draft actually starts in. Filter the blanks and the sheet is the live board.
 *
 * <b>Everyone ranked is on it, priced or not.</b> The curve stops where nine seasons stop having enough to
 * say about a rank, which at running back is rank 65 against the sixty this league drafts — five players of
 * margin, and a draft where backs go faster than usual would run off the end of it. So the ranks past that
 * are listed too, in consensus order, with {@code VOR} left blank.
 *
 * Blank rather than zero, and last rather than mixed in: a rank the curve declines to price is one nothing
 * is known about, which is not the same claim as one known to be worthless. Sorting them below every priced
 * player says the board has run out of opinions, not that these are the worst players available.
 *
 * Comma separated, unlike every other report here, because this one is opened by a spreadsheet rather than
 * pasted into one — the same reason the auction's schedule is.
 */
class GreenfieldSheetPrinter {

    private final GreenfieldBoard board
    private final List<String> positions

    GreenfieldSheetPrinter(GreenfieldBoard board, List<String> positions) {
        this.board = board
        this.positions = positions
    }

    void print(PrintWriter out) {
        Map<String, String> keptBy = board.keptBy()
        List<Map> rows = board.ranked
                .findAll { positions.contains(it.player.position) }
                .collect { FpRankedPlayer player ->
                    String position = player.player.position
                    int rank = player.rank.positionRank
                    boolean priced = rank <= board.curve.pricedDepth(position)
                    [player  : player,
                     position: position,
                     rank    : rank,
                     vor     : priced ? board.valueOf(player.player.name) : null,
                     adp     : board.demand().averageDraftPosition()[position]?.get(rank)]
                }
        // Priced first in value order, then the rest in consensus order: the sheet runs out of value
        // before it runs out of players, and says so by leaving the column empty.
                .sort { Map a, Map b ->
                    if (a.vor != null && b.vor != null) {
                        return -((a.vor as BigDecimal) <=> (b.vor as BigDecimal))
                    }
                    if (a.vor != null) return -1
                    if (b.vor != null) return 1
                    ((a.player as FpRankedPlayer).rank.overallRank ?: Integer.MAX_VALUE) <=>
                            ((b.player as FpRankedPlayer).rank.overallRank ?: Integer.MAX_VALUE)
                }

        out.println(['TAKEN', 'VORRANK', 'POS', 'RANK', 'PLAYER', 'TEAM', 'BYE', 'VOR', 'ADP', 'EDGE',
                     'TIER', 'PTS'].join(','))
        int priced = rows.count { it.vor != null }
        rows.eachWithIndex { Map row, int i ->
            FpRankedPlayer player = row.player as FpRankedPlayer
            String position = row.position as String
            int rank = row.rank as int
            // Only the priced part of the list has a value order, so only it carries a place in one.
            Integer vorRank = row.vor == null ? null : i + 1
            Integer adp = row.adp as Integer
            out.println([
                    quote(keptBy[player.player.name] ?: ''),
                    vorRank ?: '',
                    position,
                    rank,
                    quote(player.player.name),
                    quote(player.player.team ?: ''),
                    player.bye ?: '',
                    row.vor == null ? '' : (row.vor as BigDecimal).setScale(1, RoundingMode.HALF_UP),
                    adp ?: '',
                    adp && vorRank ? adp - vorRank : '',
                    row.vor == null ? '' : board.curve.tier(position, rank),
                    row.vor == null ? ''
                            : board.curve.seasonPoints(position, rank).setScale(1, RoundingMode.HALF_UP),
            ].join(','))
        }
    }

    /** Team names and player names carry commas and apostrophes; the sheet is comma separated. */
    private static String quote(String value) {
        value.contains(',') || value.contains('"') ? '"' + value.replace('"', '""') + '"' : value
    }
}
