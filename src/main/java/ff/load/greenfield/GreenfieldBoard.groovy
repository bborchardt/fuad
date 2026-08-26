package ff.load.greenfield

import ff.data.fantasypros.FpRankedPlayer
import ff.data.greenfield.KeeperSurplus
import ff.league.League
import ff.load.util.LoadUtils
import ff.projection.ByeWeeks
import ff.projection.ExpectedValue
import ff.projection.PointsCurve
import ff.projection.greenfield.KeeperValuation

/**
 * Everything a Greenfield sheet is written from, assembled once.
 *
 * The curve costs nine seasons of statistics and the measured pick values cost nine drafts walked against
 * it, so both the reports and the committed figures want the same instance rather than the same work twice.
 *
 * It also keeps the two in step. A figure is only worth committing if it describes the model that wrote the
 * report beside it, and the surest way to have them disagree is to assemble each from its own copy of the
 * loading.
 */
class GreenfieldBoard {

    final String year
    final GreenfieldValuationLoader loader
    final PointsCurve curve
    final ByeWeeks byes
    final Map<String, Map<Integer, BigDecimal>> replacement
    final Collection<FpRankedPlayer> ranked

    private Map<Integer, BigDecimal> pickValues
    private List<KeeperSurplus> keeperSurpluses

    GreenfieldBoard(String year, GreenfieldValuationLoader loader = new GreenfieldValuationLoader()) {
        this.year = year
        this.loader = loader
        this.curve = loader.curve()
        this.byes = loader.byes(year)
        this.replacement = loader.replacement(year)
        this.ranked = loader.ranked(year)
                .findAll { League.GREENFIELD.scoredPositions.contains(it.player.position) }
    }

    Map<String, Integer> starters() { loader.starters() }

    /** What the best player still on the board has been worth at each pick, over every collected draft. */
    Map<Integer, BigDecimal> pickValues() {
        pickValues ?: (pickValues =
                new DraftHistory(curve, loader.requirements(), League.GREENFIELD).bestAvailableByPick())
    }

    /** Points over replacement for a named player, or null where the board makes no claim about him. */
    BigDecimal valueOf(String name) {
        FpRankedPlayer player = byName()[name]
        player ? ExpectedValue.expectedValueOverReplacement(
                curve, replacement, player.player.position, player.rank.positionRank, byes) : null
    }

    /** Each declared keeper against the pick it costs, both readings. */
    List<KeeperSurplus> keepers() {
        keeperSurpluses ?: (keeperSurpluses = KeeperValuation.value(
                declaredKeepers(), slots(), this.&valueOf, consensusOrder(), priorRounds(),
                League.GREENFIELD.teams, pickValues()))
    }

    /** Who has already taken each player off the board. */
    Map<String, String> keptBy() {
        declaredKeepers().collectEntries { [(it.player as String): it.owner] }
    }

    private Map<String, FpRankedPlayer> byName() {
        ranked.collectEntries { [(it.player.name): it] }
    }

    private List<String> consensusOrder() {
        ranked.sort { a, b ->
            (a.rank.overallRank ?: Integer.MAX_VALUE) <=> (b.rank.overallRank ?: Integer.MAX_VALUE)
        }.collect { it.player.name }
    }

    private List<Map> declaredKeepers() {
        Map<String, FpRankedPlayer> byName = byName()
        rows("/ff/greenfield/data/$year/keepers.tsv").collect { List<String> row ->
            FpRankedPlayer player = byName[row[1]]
            [owner       : row[0], player: row[1], costRound: row[2] as int,
             position    : player?.player?.position, positionRank: player?.rank?.positionRank]
        }
    }

    private Map<String, Integer> slots() {
        rows("/ff/greenfield/data/$year/draft_order.tsv").collectEntries { [(it[1]): it[0] as int] }
    }

    /**
     * The round each player was drafted in last season, which is what decides whether he may be kept.
     *
     * A player absent from it went undrafted and qualifies at either price, which is why this is a lookup
     * that may miss rather than one that must hit.
     */
    private Map<String, Integer> priorRounds() {
        rows("/ff/greenfield/data/${(year as int) - 1}/draft.tsv").collectEntries { [(it[2]): it[0] as int] }
    }

    private static List<List<String>> rows(String resource) {
        LoadUtils.loadCsvResource(resource).drop(1).collect { it.split('\t') as List<String> }
    }
}
