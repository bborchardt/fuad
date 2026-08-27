package ff.load.greenfield

import ff.data.fantasypros.FpRankedPlayer
import ff.data.greenfield.KeeperSurplus
import ff.league.League
import ff.load.util.LoadUtils
import ff.projection.ByeWeeks
import ff.projection.ExpectedValue
import ff.projection.PointsCurve
import ff.projection.greenfield.KeeperValuation
import ff.projection.greenfield.SnakeDraft

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

    private PositionDemand demand
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

    /** When each position actually comes off the board here, which is not when it is worth taking. */
    PositionDemand demand() { demand ?: (demand = new PositionDemand(League.GREENFIELD)) }

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

    /**
     * What the best usable player at a position is worth when a given pick comes round.
     *
     * <b>Best available and best available <i>at a position</i> are different questions, and the second is
     * usually the one being asked.</b> This league caps a team at one quarterback, two tight ends, one
     * kicker and one defence, so the best player left on the board is frequently one the asker cannot field.
     * Pricing a forfeited pick at him overstates what the pick was worth by the whole difference.
     *
     * Returns null where the position has nothing left the drafts can speak to, or nothing the curve still
     * prices.
     */
    BigDecimal positionalValueAt(String position, int pick) {
        Integer rank = demand().bestRankAvailableAt(position, pick)
        rank && rank <= curve.pricedDepth(position)
                ? ExpectedValue.expectedValueOverReplacement(curve, replacement, position, rank, byes)
                : null
    }

    /** What one rank at a position is worth, which is the same question without a pick attached. */
    BigDecimal valueOfRank(String position, int rank) {
        rank <= curve.pricedDepth(position)
                ? ExpectedValue.expectedValueOverReplacement(curve, replacement, position, rank, byes)
                : null
    }

    /** The rank behind {@link #positionalValueAt}, for a sheet that has to say who it means. */
    Integer positionalRankAt(String position, int pick) {
        demand().bestRankAvailableAt(position, pick)
    }

    /** Each declared keeper against the pick it costs, both readings. */
    List<KeeperSurplus> keepers() {
        keeperSurpluses ?: (keeperSurpluses = KeeperValuation.value(
                declaredKeepers(), slots(), this.&valueOf, consensusOrder(), priorRounds(),
                League.GREENFIELD.teams, pickValues(), this.&positionalValueAt, this.&positionalRankAt))
    }

    /**
     * The picks a slot no longer owns, having spent them on keepers.
     *
     * Empty for a slot that kept nobody, which is a real answer rather than a missing one.
     */
    Set<Integer> forfeitedBy(int slot) {
        declaredKeepers().findAll { slots()[it.owner as String] == slot }
                .collect { SnakeDraft.overallPick(it.costRound as int, slot, League.GREENFIELD.teams) } as Set
    }

    /**
     * What a slot already holds: its keepers, plus anything taken since the draft started.
     *
     * Keepers are counted without being asked for, being a matter of record. Everything else has to be told,
     * since nothing here watches a draft happen.
     */
    Map<String, Integer> heldBy(int slot, Map<String, Integer> alsoHeld) {
        Map<String, FpRankedPlayer> byName = byName()
        Map<String, Integer> held = new LinkedHashMap<>(alsoHeld)
        declaredKeepers().findAll { slots()[it.owner as String] == slot }.each { Map keeper ->
            String position = byName[keeper.player as String]?.player?.position
            if (position) {
                held[position] = (held[position] ?: 0) + 1
            }
        }
        held
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
