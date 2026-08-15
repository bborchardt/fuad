package ff.projection

import spock.lang.Specification

import java.math.RoundingMode

/**
 * The curve takes its order from the consensus ranking and its level from what those ranks have historically
 * been worth. Nothing about a particular player enters it. See docs/PROJECTION.md.
 */
class PointsCurveSpec extends Specification {

    /** Nine seasons of one position, rank k scoring exactly 300 - 5k every time. */
    private static Map<Integer, List<BigDecimal>> steady() {
        (1..30).collectEntries { int rank -> [(rank): (1..9).collect { (300 - rank * 5) as BigDecimal }] }
    }

    def "levels a rank at what the ranks around it have actually scored"() {
        given:
        PointsCurve curve = PointsCurve.of([WR: steady()])

        expect: 'the middle of the range, where a rank has neighbours either side, comes back exactly'
        curve.seasonPoints('WR', 15) == 225.0
        curve.depth('WR') == 30
    }

    def "smooths towards the ranks it has, so the ends pull inward"() {
        given:
        PointsCurve curve = PointsCurve.of([WR: steady()])

        expect: 'rank one has only lower ranks to average against, so it lands below its own 295'
        curve.seasonPoints('WR', 1) < 295.0
        curve.seasonPoints('WR', 1) > 280.0
    }

    def "reports nothing where too few seasons have held a rank to say anything"() {
        given: 'one season, so five smoothed ranks yield five observations against a minimum of six'
        Map realised = [WR: (1..3).collectEntries { [(it): [100.0 as BigDecimal]] }]

        expect:
        PointsCurve.of(realised).seasonPoints('WR', 1) == 0.0
        PointsCurve.of(realised).positions() == [] as Set
    }

    def "counts a ranked season that never happened as a zero, which is what pulls a curve down"() {
        given: 'the same nine seasons, but two of every rank lost entirely'
        Map<Integer, List<BigDecimal>> withBusts = steady().collectEntries { int rank, List<BigDecimal> scored ->
            [(rank): scored.take(7) + [0.0 as BigDecimal, 0.0 as BigDecimal]]
        }

        expect: 'seven ninths of the level, exactly, rather than the untouched level a dropped zero gives'
        PointsCurve.of([WR: withBusts]).seasonPoints('WR', 15)
                .setScale(1, RoundingMode.HALF_UP) == 175.0
    }

    def "spreads a season evenly over the weeks that are played, and none over the bye"() {
        given:
        PointsCurve curve = PointsCurve.of([WR: steady()])
        Map<Integer, BigDecimal> weekly = curve.weeklyPoints('WR', 15, 7, 14)

        expect: 'the season is intact to the rounding of one division, and none of it falls on the bye'
        weekly[7] == 0.0
        ((weekly.values().sum() as BigDecimal) - 225.0).abs() < 0.001
        weekly[1] == weekly[14]
        weekly.size() == 14
    }

    def "spreads over the whole season when a rank has no bye recorded"() {
        given:
        Map<Integer, BigDecimal> weekly = PointsCurve.of([WR: steady()]).weeklyPoints('WR', 15, null, 14)

        expect:
        weekly.size() == 14
        weekly.values().every { it > 0 }
        ((weekly.values().sum() as BigDecimal) - 225.0).abs() < 0.001
    }

    def "outcome multipliers average one, so carrying the spread moves no expected points"() {
        given: 'ranks that realise anywhere from nothing to half again as much as expected'
        Map<Integer, List<BigDecimal>> uneven = (1..30).collectEntries { int rank ->
            BigDecimal expected = (300 - rank * 5) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5, 0.0 as BigDecimal] * 2]
        }

        when:
        List<Double> multipliers = PointsCurve.of([WR: uneven]).outcomeMultipliers('WR')

        then:
        multipliers.size() >= 20
        Math.abs(multipliers.sum() / multipliers.size() - 1.0d) < 0.001d

        and: 'the lost seasons are in there as zeros, which is the left tail a bench is priced against'
        multipliers.count { it == 0.0d } == multipliers.size() / 4
    }

    def "reports nothing for a rank deeper than the record goes"() {
        expect:
        PointsCurve.of([WR: steady()]).weeklyPoints('WR', 90, null, 14) == [:]
    }
}
