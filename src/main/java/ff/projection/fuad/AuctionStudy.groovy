package ff.projection.fuad

import ff.data.PlayerValuation
import ff.data.fuad.FuadData
import ff.league.League
import ff.load.fuad.FuadLoader
import ff.load.fuad.FuadValuationLoader
import ff.projection.ExpectedValue
import ff.projection.PointsCurve

/**
 * The open auction question evaluated with one season absent from every model input that can contain it.
 *
 * Each fold removes its target from the realised seasons behind the points curve and from the auctions that
 * supply spend, positional share, steepness, and the rank-price prior.  The remaining auctions may be later
 * than the target: this is leave-one-season-out model comparison rather than a claim that four auctions can
 * support a rolling production fit.  Its purpose is to compare signals on equal evidence without allowing
 * the result being scored to help build any of them.
 */
class AuctionStudy {

    /** One model in one held-out season, or its pooled row. */
    static class Fit {
        final String season
        final String position
        final String model
        final int priced
        final BigDecimal meanAbsolute
        final BigDecimal bias

        Fit(String season, String position, String model, int priced,
            BigDecimal meanAbsolute, BigDecimal bias) {
            this.season = season
            this.position = position
            this.model = model
            this.priced = priced
            this.meanAbsolute = meanAbsolute
            this.bias = bias
        }
    }

    /**
     * Every completed superflex auction, followed by two weighted pooled rows for each model and position.
     *
     * <b>The second pool drops the earliest auction, and it is reported because the first one flatters the
     * board.</b> That season is the league's first under superflex and it had not yet adjusted — quarterback
     * took 13.7% of the money against 23.5%, 17.3% and 29.3% after. Every model is hurt by that break, but a
     * rank median built from the other seasons is hurt most, having nothing but those seasons to go on. So
     * including it at full weight quietly advantages every model over the yardstick they are all measured
     * against, and the difference is not small: pooled over four folds the board is a tenth behind the rank
     * median and over three it is a third of a dollar behind.
     *
     * It is not dropped, only reported twice. One fold in four is a lot to discard, and it is the only fold
     * that asks whether a signal survives a change of regime.
     */
    static List<Fit> of() {
        List<String> seasons = AuctionAccuracy.MEASURED_SEASONS.findAll { AuctionSpend.isMeasurable(it) }
        List<Fit> folds = seasons.collectMany { String target -> fold(target, seasons) }
        String earliest = seasons.min()
        folds + pooled(folds, POOLED) +
                pooled(folds.findAll { it.season != earliest }, POOLED + 'EX' + earliest)
    }

    /** The season label a pooled row carries, so a reader can tell it from a fold. */
    static final String POOLED = 'POOLED'

    /**
     * What a position's steepness is before the fold's own signings have spoken, which is no bending at all.
     *
     * <b>It used to be the production constants, and that was a leak.</b> Those are fitted over all four
     * auctions including the one being held out, and {@link PriceSteepness#of} silently drops a position
     * that falls under its minimums — so a thin fold at a thin position would keep the target-contaminated
     * exponent and the study would go on claiming the answer was absent from every fitted input. One is the
     * honest seed: it says the fold has no evidence about that position and declines to invent any.
     */
    private static final Map<String, BigDecimal> NEUTRAL_STEEPNESS =
            AuctionSpend.POSITIONS.collectEntries { [(it): 1.0 as BigDecimal] }

    private static List<Fit> fold(String target, List<String> measured) {
        List<String> training = measured.findAll { it != target }
        List<String> curveSeasons = League.FUAD.seasons.findAll { it != target }
        PointsCurve curve = FuadValuationLoader.overSeasons(curveSeasons, AuctionValuation.DEFAULT_SETTINGS).curve()

        List<AuctionSpend.Season> auctions = training.collect { AuctionSpend.of(it) }
        Map<String, BigDecimal> shares = AuctionSpend.shareByPosition(auctions)
        BigDecimal spendRate = auctions.collect { it.spendRate }.sum() / auctions.size()
        BigDecimal rookieShare = auctions.collect { it.rookieShare }.sum() / auctions.size()
        AuctionValuation.Settings preliminary = new AuctionValuation.Settings(
                shares, AuctionValuation.VOR_STEEPNESS, spendRate, rookieShare)

        FuadValuationLoader trainingLoader = FuadValuationLoader.withCurve(curve, preliminary)
        Map<String, List<PlayerValuation>> trainingBoards = training.collectEntries { String season ->
            [(season): trainingLoader.valuations(season, new FuadLoader().loadData(season))]
        }
        Map<AuctionValuation.PriceSignal, Map<String, BigDecimal>> steepness =
                AuctionValuation.PriceSignal.values().collectEntries { AuctionValuation.PriceSignal signal ->
                    Map<String, BigDecimal> fitted = NEUTRAL_STEEPNESS +
                            PriceSteepness.of(observationsFor(
                                    signal, training, trainingBoards, trainingLoader, curve))
                                    .collectEntries { [(it.position): it.gamma] }
                    [(signal): fitted]
                }
        Map<String, Map<Integer, BigDecimal>> rankPrice = AuctionRankPrices.of(training)

        List<Map> candidates = [
                [name: 'VOR', signal: AuctionValuation.PriceSignal.VOR, weight: 0.0, prior: [:]],
                [name: 'EXPECTED_VOR', signal: AuctionValuation.PriceSignal.EXPECTED_VOR,
                 weight: 0.0, prior: [:]],
                [name: 'POINTS', signal: AuctionValuation.PriceSignal.POINTS, weight: 0.0, prior: [:]],
                [name: 'BLEND_25', signal: AuctionValuation.PriceSignal.VOR, weight: 0.25, prior: rankPrice],
                [name: 'BLEND_50', signal: AuctionValuation.PriceSignal.VOR, weight: 0.50, prior: rankPrice],
                [name: 'BLEND_75', signal: AuctionValuation.PriceSignal.VOR, weight: 0.75, prior: rankPrice],
                [name: 'POINTS_BLEND_25', signal: AuctionValuation.PriceSignal.POINTS,
                 weight: 0.25, prior: rankPrice],
                [name: 'POINTS_BLEND_50', signal: AuctionValuation.PriceSignal.POINTS,
                 weight: 0.50, prior: rankPrice],
                [name: 'POINTS_BLEND_75', signal: AuctionValuation.PriceSignal.POINTS,
                 weight: 0.75, prior: rankPrice],
                [name: 'RANK_SHAPE', signal: AuctionValuation.PriceSignal.VOR, weight: 1.0, prior: rankPrice],
                [name: 'RB_BLEND_50', signal: AuctionValuation.PriceSignal.VOR, weight: 0.50,
                 prior: rankPrice.findAll { it.key == 'RB' }],
                [name: 'RB_RANK_SHAPE', signal: AuctionValuation.PriceSignal.VOR, weight: 1.0,
                 prior: rankPrice.findAll { it.key == 'RB' }],
                [name: 'WR_BLEND_50', signal: AuctionValuation.PriceSignal.VOR, weight: 0.50,
                 prior: rankPrice.findAll { it.key == 'WR' }],
                [name: 'WR_RANK_SHAPE', signal: AuctionValuation.PriceSignal.VOR, weight: 1.0,
                 prior: rankPrice.findAll { it.key == 'WR' }],
        ]

        // Loop-invariant: every candidate prices the same target season, so it is loaded once rather than
        // once per candidate — fourteen times a fold, fifty-six a run, on the test path as well.
        FuadData targetData = new FuadLoader().loadData(target)

        Map<String, List<PlayerValuation>> boards = [:]
        List<Fit> results = candidates.collectMany { Map candidate ->
            AuctionValuation.Settings settings = new AuctionValuation.Settings(
                    shares, steepness[candidate.signal as AuctionValuation.PriceSignal], spendRate, rookieShare,
                    candidate.signal as AuctionValuation.PriceSignal,
                    candidate.prior as Map<String, Map<Integer, BigDecimal>>,
                    candidate.weight as BigDecimal)
            List<PlayerValuation> board = FuadValuationLoader.withCurve(curve, settings)
                    .valuations(target, targetData)
            boards[candidate.name as String] = board
            AuctionAccuracy.of(target, board).collect { AuctionAccuracy.Fit fit ->
                new Fit(target, fit.position, candidate.name as String, fit.priced,
                        fit.meanAbsolute, fit.bias)
            }
        }

        // The raw rank median is useful as a yardstick, but is not a production-compatible board: unlike
        // RANK_SHAPE it neither clears the current pot nor participates in tag settlement.
        List<AuctionAccuracy.Fit> raw = AuctionAccuracy.of(target, boards.VOR, trainingBoards)
        results + raw.collect { AuctionAccuracy.Fit fit ->
            new Fit(target, fit.position, 'RANK_MEDIAN_RAW', fit.priced, fit.benchmark, null)
        }
    }

    /** Fit each candidate's exponent to that candidate rather than making VOR's exponent answer for it. */
    private static List<PriceSteepness.Observation> observationsFor(
            AuctionValuation.PriceSignal signal, List<String> seasons,
            Map<String, List<PlayerValuation>> boards, FuadValuationLoader loader, PointsCurve curve) {
        seasons.collectMany { String season ->
            Map<String, PlayerValuation> byId = boards[season].collectEntries { [(it.playerId): it] }
            def byes = loader.byes(season)
            Map<String, Map<Integer, BigDecimal>> replacement = ExpectedValue.replacementLevels(
                    curve, loader.requirements(season), byes)
            AuctionSpend.signings(season).findAll { byId[it.playerId] != null }.collect { signing ->
                PlayerValuation valuation = byId[signing.playerId]
                BigDecimal value = signal == AuctionValuation.PriceSignal.POINTS ? valuation.points :
                        signal == AuctionValuation.PriceSignal.EXPECTED_VOR ?
                                ExpectedValue.valueOverReplacementAtExpectation(
                                        curve, replacement, valuation.position, valuation.positionRank,
                                        byes) : valuation.valueOverReplacement
                value > 0 ? new PriceSteepness.Observation(signing.position, value, signing.paid) : null
            }.findAll()
        }
    }

    /** A signings-weighted mean over the folds that carry the figure at all, or null where none do. */
    private static BigDecimal weighted(List<Fit> at, Closure<BigDecimal> of) {
        if (at.isEmpty()) {
            return null
        }
        int count = at.collect { it.priced }.sum() as int
        count > 0 ? (at.collect { of(it) * it.priced }.sum() as BigDecimal) / count : null
    }

    private static List<Fit> pooled(List<Fit> folds, String label) {
        folds.groupBy { [it.model, it.position] }.collect { List key, List<Fit> at ->
            // Guarded like bias below, and for the same reason: RANK_MEDIAN_RAW takes its figure from
            // AuctionAccuracy.Fit.benchmark, which is null where a position had no naive observations or
            // nothing priced. Unguarded this pools a null into an arithmetic and takes the whole run down.
            // PRICED stays the count over every fold, so it is comparable between models; the mean is
            // weighted over the folds that actually carry one.
            int count = at.collect { it.priced }.sum() as int
            BigDecimal absolute = weighted(at.findAll { it.meanAbsolute != null }) { it.meanAbsolute }
            BigDecimal bias = weighted(at.findAll { it.bias != null }) { it.bias }
            new Fit(label, key[1] as String, key[0] as String, count, absolute, bias)
        }.sort { Fit a, Fit b ->
            a.model <=> b.model ?: AuctionSpend.POSITIONS.indexOf(a.position) <=>
                    AuctionSpend.POSITIONS.indexOf(b.position)
        }
    }
}
