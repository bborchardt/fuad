package ff.projection

import spock.lang.Specification

import java.math.RoundingMode

/**
 * The curve keeps the projection's shape and the consensus ranking's order, then corrects both for what a
 * rank has historically been worth. See docs/PROJECTION.md.
 */
class PointsCurveSpec extends Specification {

    /** Three players at one position, projected 100, 60 and 20 a season over two weeks. */
    private static Map<Integer, Map<String, BigDecimal>> projections() {
        [1: [a: 50.0, b: 30.0, c: 10.0],
         2: [a: 50.0, b: 30.0, c: 10.0]]
    }

    private static Map<String, String> positions() { [a: 'WR', b: 'WR', c: 'WR'] }

    def "orders slots by projected total, so rank one is the best projection"() {
        given:
        PointsCurve curve = PointsCurve.of(projections(), positions(), [:])

        expect:
        curve.depth('WR') == 3
        curve.weeklyPoints('WR', 1).values().sum() == 100.0
        curve.weeklyPoints('WR', 2).values().sum() == 60.0
        curve.weeklyPoints('WR', 3).values().sum() == 20.0
    }

    def "leaves projections alone when there is too little realised scoring to fit"() {
        given: 'only three ranks of history, below the minimum the fit requires'
        Map realised = [WR: [1: [10.0], 2: [10.0], 3: [10.0]]]

        expect:
        PointsCurve.of(projections(), positions(), realised).weeklyPoints('WR', 1).values().sum() == 100.0
    }

    def "scales a position down when a rank has historically scored below its projection"() {
        given: 'thirty ranks that each realised exactly half of what was projected'
        Map<Integer, Map<String, BigDecimal>> projected = [1: (1..30).collectEntries { [("p$it".toString()): (300 - it * 5) as BigDecimal] }]
        Map<String, String> pos = (1..30).collectEntries { [("p$it".toString()): 'WR'] }
        Map realised = [WR: (1..30).collectEntries { [(it): [((300 - it * 5) / 2) as BigDecimal]] }]

        when:
        PointsCurve curve = PointsCurve.of(projected, pos, realised)

        then: 'the exponent is one, because halving every rank does not change the shape'
        // Not exactly one: the ranks at either end have neighbours on one side only, which tilts the
        // smoothed fit very slightly.
        Math.abs(curve.fits.WR[1] - 1.0d) < 0.05d

        and: 'a rank in the middle comes back halved, its neighbours averaging out either side'
        curve.weeklyPoints('WR', 15).values().sum().setScale(0, RoundingMode.HALF_UP) == 113

        and: 'the top rank comes back a shade under half, having only lower ranks to smooth against'
        curve.weeklyPoints('WR', 1).values().sum() < 148.0
        curve.weeklyPoints('WR', 1).values().sum() > 138.0
    }

    def "flattens a position whose projected curve is steeper than what it realises"() {
        given: 'realised scoring compressed towards the middle, so the top falls further than the bottom'
        Map<Integer, Map<String, BigDecimal>> projected = [1: (1..30).collectEntries { [("p$it".toString()): (300 - it * 5) as BigDecimal] }]
        Map<String, String> pos = (1..30).collectEntries { [("p$it".toString()): 'RB'] }
        Map realised = [RB: (1..30).collectEntries { [(it): [Math.sqrt((300 - it * 5) * 150) as BigDecimal]] }]

        when:
        PointsCurve curve = PointsCurve.of(projected, pos, realised)

        then: 'the fitted exponent is below one, which is what flattening means'
        curve.fits.RB[1] < 0.9d
        curve.weeklyPoints('RB', 1).values().sum() < 295.0
    }

    def "reports nothing for a rank deeper than the projections go"() {
        expect:
        PointsCurve.of(projections(), positions(), [:]).weeklyPoints('WR', 9) == [:]
    }
}
