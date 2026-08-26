package ff.projection

import ff.data.RealisedSeason
import spock.lang.Specification

import java.math.RoundingMode

/**
 * The curve takes its order from the consensus ranking and its level from what those ranks have historically
 * been worth. Nothing about a particular player enters it. See docs/fuad/PROJECTION.md.
 */
class PointsCurveSpec extends Specification {

    /** Nine seasons of one position, rank k scoring exactly 300 - 5k every time. */
    private static Map<Integer, List<BigDecimal>> steady() {
        (1..30).collectEntries { int rank -> [(rank): (1..9).collect { (300 - rank * 5) as BigDecimal }] }
    }

    def "levels a rank at what the ranks around it have actually scored"() {
        given:
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(steady())])

        expect: 'the middle of the range, where a rank has neighbours either side, comes back'
        (curve.seasonPoints('WR', 15) - 225.0).abs() < 0.001
        curve.depth('WR') == 30

        and: 'as a rate times an availability, which is what the level now is'
        (curve.pointsPerGame('WR', 15) * curve.expectedGames('WR', 15) - 225.0).abs() < 0.001
        curve.expectedGames('WR', 15) == TestSeasons.FULL
    }

    def "tells a player who missed games from one who played badly"() {
        given: 'two ranks with the same season total, reached in opposite ways'
        Map<Integer, List<RealisedSeason>> byRank = (1..30).collectEntries { int rank ->
            [(rank): (1..9).collect {
                // rank 15 plays half a season at a good rate; everyone else plays all of it at half that
                rank == 15 ? new RealisedSeason(points: 100.0, games: 6)
                        : new RealisedSeason(points: 100.0, games: 12)
            }]
        }
        PointsCurve curve = PointsCurve.of([WR: byRank])

        expect: 'the rate sees the difference a season total cannot: same points, half the football'
        curve.pointsPerGame('WR', 15) > curve.pointsPerGame('WR', 25)

        and: 'while availability is smoothed widely enough to absorb one deviant rank almost entirely'
        (curve.expectedGames('WR', 15) - 12.0).abs() < 1.0
        (curve.expectedGames('WR', 15) - curve.expectedGames('WR', 25)).abs() < 0.5

        and: 'so the rank that played less is levelled above the one that played more'
        curve.seasonPoints('WR', 15) > curve.seasonPoints('WR', 25)
    }

    def "follows a real decline in availability instead of flattening it away"() {
        given: 'a position with 20 starting jobs: full seasons to rank 20, then backups who rarely play'
        Map<Integer, List<RealisedSeason>> byRank = (1..60).collectEntries { int rank ->
            int games = rank <= 20 ? 12 : Math.max(2, 12 - (rank - 20) / 3 as int)
            [(rank): (1..9).collect { new RealisedSeason(points: games * 10.0, games: games) }]
        }
        PointsCurve curve = PointsCurve.of([QB: byRank])

        expect: 'the starters are held near a full season'
        (curve.expectedGames('QB', 10) - 12.0).abs() < 1.0

        and: 'and the fall past the last starting job is carried rather than smoothed flat'
        curve.expectedGames('QB', 45) < curve.expectedGames('QB', 10) * 0.7

        and: 'without a cliff anywhere: no single rank drops availability by a tenth of a game'
        (21..55).every {
            (curve.expectedGames('QB', it) - curve.expectedGames('QB', it + 1)).abs() < 0.5
        }
    }

    def "a lost season is availability, not a rate of zero"() {
        given: 'eight seasons at a steady rate and one that never happened'
        Map<Integer, List<RealisedSeason>> byRank = (1..30).collectEntries { int rank ->
            [(rank): (1..8).collect { new RealisedSeason(points: 120.0, games: 12) } +
                    [new RealisedSeason(points: 0.0, games: 0)]]
        }
        PointsCurve curve = PointsCurve.of([WR: byRank])

        expect: 'the rate is what he does when he plays, untouched by the year he did not'
        (curve.pointsPerGame('WR', 15) - 10.0).abs() < 0.001

        and: 'the lost year lands on availability, and pulls the level down through it'
        (curve.expectedGames('WR', 15) - (12 * 8 / 9)).abs() < 0.001
        curve.seasonPoints('WR', 15) < 120.0
    }

    def "smooths towards the ranks it has, so the ends pull inward"() {
        given:
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(steady())])

        expect: 'rank one has only lower ranks to average against, so it lands below its own 295'
        curve.seasonPoints('WR', 1) < 295.0
        curve.seasonPoints('WR', 1) > 280.0
    }

    def "reports nothing where too few seasons have held a rank to say anything"() {
        given: 'one season, so five smoothed ranks yield five observations against a minimum of six'
        Map realised = [WR: (1..3).collectEntries { [(it): [100.0 as BigDecimal]] }]

        expect:
        PointsCurve.of([WR: TestSeasons.byRank(realised.WR)]).seasonPoints('WR', 1) == 0.0
        PointsCurve.of([WR: TestSeasons.byRank(realised.WR)]).positions() == [] as Set
    }

    def "counts a ranked season that never happened as a zero, which is what pulls a curve down"() {
        given: 'the same nine seasons, but two of every rank lost entirely'
        Map<Integer, List<BigDecimal>> withBusts = steady().collectEntries { int rank, List<BigDecimal> scored ->
            [(rank): scored.take(7) + [0.0 as BigDecimal, 0.0 as BigDecimal]]
        }

        expect: 'seven ninths of the level, exactly, rather than the untouched level a dropped zero gives'
        PointsCurve.of([WR: TestSeasons.byRank(withBusts)]).seasonPoints('WR', 15)
                .setScale(1, RoundingMode.HALF_UP) == 175.0
    }

    def "outcome multipliers average one, so carrying the spread moves no expected points"() {
        given: 'ranks that realise anywhere from nothing to half again as much as expected'
        Map<Integer, List<BigDecimal>> uneven = (1..30).collectEntries { int rank ->
            BigDecimal expected = (300 - rank * 5) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5, 0.0 as BigDecimal] * 2]
        }

        when:
        List<Double> multipliers = PointsCurve.of([WR: TestSeasons.byRank(uneven)]).outcomeMultipliers('WR')

        then:
        multipliers.size() >= 20
        Math.abs(multipliers.sum() / multipliers.size() - 1.0d) < 0.001d

        and: 'the lost seasons are in there as zeros, which is the left tail a bench is priced against'
        multipliers.count { it == 0.0d } == multipliers.size() / 4
    }

    def "reports the range a position's seasons run to, low below expectation and high above"() {
        given: 'a quarter of seasons lost entirely, a quarter at half, a quarter half again as much'
        Map<Integer, List<BigDecimal>> uneven = (1..30).collectEntries { int rank ->
            BigDecimal expected = (300 - rank * 5) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5, 0.0 as BigDecimal] * 2]
        }
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(uneven)])

        expect:
        curve.outcomePercentile('WR', 0.10) < 1.0
        curve.outcomePercentile('WR', 0.90) > 1.0
        curve.outcomePercentile('WR', 0.10) < curve.outcomePercentile('WR', 0.90)
    }

    private static int tierCount(PointsCurve curve) {
        (1..curve.depth('WR')).collect { curve.tier('WR', it) }.toSet().size()
    }

    def "separates every rank when the seasons behind them are tight"() {
        given: 'rank k scores exactly 300 - 5k every year, so a rank is known almost precisely'
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(steady())])

        expect: 'the error is far smaller than the gap between ranks, so nothing needs grouping'
        curve.standardError('WR', 15) < 2.0
        tierCount(curve) == curve.depth('WR')
    }

    def "groups ranks it cannot tell apart when the seasons behind them scatter"() {
        given: 'the same ranks, but realised anywhere from nothing to half again as much'
        Map<Integer, List<BigDecimal>> uneven = (1..30).collectEntries { int rank ->
            BigDecimal expected = (300 - rank * 5) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5, 0.0 as BigDecimal] * 2]
        }
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(uneven)])

        expect: 'error now swamps the gap between neighbouring ranks, so they collapse together'
        curve.standardError('WR', 15) > 10.0
        tierCount(curve) < curve.depth('WR') / 2

        and: 'while ranks far enough apart still separate'
        curve.tier('WR', 1) < curve.tier('WR', 30)
    }

    def "tiers follow the level and never the rank"() {
        given: 'a curve with enough scatter to be non-monotone, which is the case that broke this'
        Map<Integer, List<BigDecimal>> lumpy = (1..30).collectEntries { int rank ->
            BigDecimal expected = (300 - rank * 5) as BigDecimal
            [(rank): [expected * (rank % 4 == 0 ? 0.4 : 1.3), expected * 0.6, expected * 1.4,
                      0.0 as BigDecimal] * 2]
        }
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(lumpy)])
        List<Integer> ranks = (1..curve.depth('WR')).toList()

        expect: 'a rank levelling higher is never put in a worse tier than one levelling lower'
        ranks.every { int a ->
            ranks.every { int b ->
                curve.seasonPoints('WR', a) <= curve.seasonPoints('WR', b) ||
                        curve.tier('WR', a) <= curve.tier('WR', b)
            }
        }

        and: 'the curve really is non-monotone here, so that invariant was worth asserting'
        ranks.any { int r -> r < curve.depth('WR') && curve.seasonPoints('WR', r + 1) > curve.seasonPoints('WR', r) }
    }

    def "falls back to expectation where a position has no spread to report"() {
        given: 'three ranks over six seasons: enough to level a rank, too few to describe a distribution'
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(
                (1..3).collectEntries { int rank -> [(rank): (1..6).collect { 200.0 as BigDecimal }] })])

        expect: 'a range would be invented rather than measured, so none is claimed'
        curve.outcomeMultipliers('WR') == []
        curve.outcomePercentile('WR', 0.10) == 1.0
        curve.outcomePercentile('WR', 0.90) == 1.0
    }

    def "reports nothing for a rank deeper than the record goes"() {
        expect:
        PointsCurve.of([WR: TestSeasons.byRank(steady())]).weeklyRate('WR', 90, null, 14) == [:]
    }

    def "counts what it was built from, so a figure about the sample comes from the sample"() {
        given: 'thirty ranks over nine seasons, two of every rank lost outright'
        Map<Integer, List<RealisedSeason>> byRank = (1..30).collectEntries { int rank ->
            [(rank): (1..7).collect { new RealisedSeason(points: (300 - rank * 5) as BigDecimal, games: 13) } +
                    [new RealisedSeason(points: 0.0, games: 0), new RealisedSeason(points: 0.0, games: 0)]]
        }
        PointsCurve curve = PointsCurve.of([WR: byRank])

        when:
        PointsCurve.Census census = curve.census('WR')

        then: 'the seasons behind the ranks that carry money, and how many never happened'
        census.seasons == curve.pricedDepth('WR') * 9
        census.lost == curve.pricedDepth('WR') * 2
    }

    /**
     * The factor that puts a position's level back after the two halves are averaged apart, reported so the
     * documentation can cite it rather than remember it — which is what two javadoc comments were doing,
     * having drifted apart from each other and from the model alike.
     */
    def "reports the anchor it scaled the position's shape by"() {
        given: 'seasons where missing games and playing badly go together, which is what the anchor is for'
        Map<Integer, List<RealisedSeason>> byRank = (1..30).collectEntries { int rank ->
            BigDecimal rate = (30 - rank * 0.5) as BigDecimal
            [(rank): (1..9).collect { int season ->
                // a good year is a full one at the rank's rate; a bad year is short *and* at half the rate.
                // That covariance is what averaging the halves apart drops, and what the anchor puts back.
                season % 3 == 0 ? new RealisedSeason(points: rate * 5 / 2, games: 5)
                        : new RealisedSeason(points: rate * 13, games: 13)
            }]
        }
        PointsCurve curve = PointsCurve.of([WR: byRank])

        expect: 'above one, the covariance having been dropped by averaging the halves apart'
        curve.census('WR').anchor > 1.0

        and: 'and it is exactly what reconciles the raw halves with the level the board reports'
        (curve.pointsPerGame('WR', 15) * curve.expectedGames('WR', 15) * curve.census('WR').anchor
                - curve.seasonPoints('WR', 15)).abs() < 0.001
    }

    def "a position whose seasons never vary needs no anchor at all"() {
        given: 'rank k scores 300 - 5k over a full season every year, so there is no covariance to drop'
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(steady())])

        expect: 'one, and the level is the raw product untouched'
        (curve.census('WR').anchor - 1.0).abs() < 0.001
    }

    /**
     * The measurement that chose {@link PointsCurve#AVAILABILITY_SMOOTHING_RADIUS}, reported so the argument
     * for smoothing availability five times wider than the rate can be held to it rather than remembered.
     */
    def "measures how weakly rank predicts availability"() {
        given: 'a position where how much football a rank plays has nothing to do with the rank'
        Map<Integer, List<RealisedSeason>> flat = (1..30).collectEntries { int rank ->
            [(rank): (1..9).collect { int season ->
                // games cycle on the season and ignore the rank entirely
                new RealisedSeason(points: ((300 - rank * 5) * (7 + season % 7) / 13) as BigDecimal,
                        games: 7 + season % 7)
            }]
        }

        and: 'and one where the deep ranks plainly play less, as quarterback really does'
        Map<Integer, List<RealisedSeason>> falling = (1..30).collectEntries { int rank ->
            int games = Math.max(2, 13 - rank / 3 as int)
            [(rank): (1..9).collect { new RealisedSeason(points: (10 * games) as BigDecimal, games: games) }]
        }

        expect: 'near nothing where rank says nothing, and strongly negative where it says something'
        PointsCurve.of([WR: flat]).census('WR').gamesCorrelation.abs() < 0.2
        PointsCurve.of([QB: falling]).census('QB').gamesCorrelation < -0.8
    }

    /**
     * The pair the whole split rests on: a season is two things, and they scatter independently. Where the
     * two are comparable, as much of the variation in a season total is absence as is form.
     */
    def "measures how widely each half of a season scatters"() {
        given: 'a position whose rate never varies and whose availability varies a lot'
        Map<Integer, List<RealisedSeason>> byRank = (1..30).collectEntries { int rank ->
            [(rank): (1..9).collect { int season ->
                int games = [13, 13, 13, 13, 13, 13, 7, 4, 0][season - 1]
                new RealisedSeason(points: (12 * games) as BigDecimal, games: games)
            }]
        }
        PointsCurve curve = PointsCurve.of([RB: byRank])

        expect: 'the rate is a constant, so it does not scatter at all'
        curve.census('RB').rateVariation < 0.001

        and: 'while availability does, the lost season included, which is where it belongs'
        curve.census('RB').gamesVariation > 0.3
    }

    def "calls a curve that never goes backwards perfectly monotone"() {
        given: 'rank k scores exactly 300 - 5k every year, so nothing ever dips'
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(steady())])

        expect:
        curve.census('WR').backward == 0.0
    }

    /**
     * The claim splitting a season in two exists to make: the curve comes out more monotone.
     *
     * Availability is the noisy half here — every rank plays at the same rate and the games alternate — so
     * levelling the totals carries that noise straight into the level, while smoothing availability over ten
     * ranks either side takes it out. This is the measurement behind the comparison in docs/fuad/PROJECTION.md,
     * which is why it is computed rather than remembered.
     */
    def "travels backwards less than levelling the season totals does"() {
        given: 'the rate declining cleanly with rank, and availability sawtoothing rank by rank'
        Map<Integer, List<RealisedSeason>> byRank = (1..40).collectEntries { int rank ->
            BigDecimal rate = (30.0 - rank * 0.5) as BigDecimal
            int games = rank % 2 == 0 ? 12 : 8
            [(rank): (1..9).collect { new RealisedSeason(points: rate * games, games: games) }]
        }
        PointsCurve curve = PointsCurve.of([WR: byRank])

        when:
        PointsCurve.Census census = curve.census('WR')

        then: 'the totals carry the sawtooth and the split does not'
        census.backwardOfTotals > 0.25
        census.backward < census.backwardOfTotals / 2
    }
}
