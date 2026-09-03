package ff.projection.fuad

import ff.data.fuad.FuadPlayer
import ff.load.fuad.FuadLoader

/** What this league has paid around each consensus rank, expressed above the reserved minimum bid. */
class AuctionRankPrices {

    private static final int RADIUS = 6
    private static final int MAXIMUM_RANK = 250

    /**
     * A leave-one-season-out fit can supply its training seasons here without constructing a board first.
     * The join is on MFL id, as auction accuracy is; the redraft rank is the only board input this needs.
     */
    static Map<String, Map<Integer, BigDecimal>> of(Collection<String> seasons) {
        List<List> observations = seasons.collectMany { String season ->
            Map<String, FuadPlayer> byId = new FuadLoader().loadData(season).playerByNameMap.values()
                    .findAll { it.redraftRank }
                    .collectEntries { [(it.mflId): it] }
            AuctionSpend.signings(season).findAll { byId[it.playerId] != null }.collect {
                [it.position, byId[it.playerId].redraftRank.positionRank, it.paid]
            }
        }
        ofObservations(observations)
    }

    /** Package-visible for a small synthetic test of smoothing, fallback, and the reserved dollar. */
    static Map<String, Map<Integer, BigDecimal>> ofObservations(List<List> observations) {
        AuctionSpend.POSITIONS.collectEntries { String position ->
            List<List> atPosition = observations.findAll { it[0] == position }
            Map<Integer, BigDecimal> byRank = atPosition ? (1..MAXIMUM_RANK).collectEntries { int rank ->
                List<List> within = atPosition.findAll { Math.abs((it[1] as int) - rank) <= RADIUS }
                if (!within) {
                    int nearest = atPosition.collect { Math.abs((it[1] as int) - rank) }.min()
                    within = atPosition.findAll { Math.abs((it[1] as int) - rank) == nearest }
                }
                BigDecimal median = medianOf(within.collect { it[2] as BigDecimal })
                [(rank): median > 1 ? median - 1 : 0.0 as BigDecimal]
            } : [:]
            [(position): byRank.asImmutable()]
        }.asImmutable()
    }

    private static BigDecimal medianOf(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.sort(false)
        int size = sorted.size()
        size % 2 == 1 ? sorted[size.intdiv(2)] :
                (sorted[size.intdiv(2) - 1] + sorted[size.intdiv(2)]) / 2
    }
}
