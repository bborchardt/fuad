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
                .findAll { it.vor != null }
                .sort { -(it.vor as BigDecimal) }

        out.println(['TAKEN', 'VORRANK', 'POS', 'RANK', 'PLAYER', 'TEAM', 'BYE', 'VOR', 'ADP', 'EDGE',
                     'TIER', 'PTS'].join(','))
        rows.eachWithIndex { Map row, int i ->
            FpRankedPlayer player = row.player as FpRankedPlayer
            String position = row.position as String
            int rank = row.rank as int
            int vorRank = i + 1
            Integer adp = row.adp as Integer
            out.println([
                    quote(keptBy[player.player.name] ?: ''),
                    vorRank,
                    position,
                    rank,
                    quote(player.player.name),
                    quote(player.player.team ?: ''),
                    player.bye ?: '',
                    (row.vor as BigDecimal).setScale(1, RoundingMode.HALF_UP),
                    adp ?: '',
                    adp ? adp - vorRank : '',
                    board.curve.tier(position, rank),
                    board.curve.seasonPoints(position, rank).setScale(1, RoundingMode.HALF_UP),
            ].join(','))
        }
    }

    /** Team names and player names carry commas and apostrophes; the sheet is comma separated. */
    private static String quote(String value) {
        value.contains(',') || value.contains('"') ? '"' + value.replace('"', '""') + '"' : value
    }
}
