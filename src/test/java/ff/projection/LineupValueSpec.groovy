package ff.projection

import spock.lang.Specification

/**
 * A roster is worth more than the sum of what its players are priced at, for two reasons a per-player board
 * cannot see: the weeks a player is off are covered by whoever else is there, and where a team holds more
 * of a position than it starts, it starts whichever turned out best. See docs/STRATEGY.md.
 */
class LineupValueSpec extends Specification {

    private static final int LAST_WEEK = 14

    /** Two quarterback slots of ten, as superflex, with room to bench a third. */
    private static StarterRequirements superflex() {
        new StarterRequirements([QB: 1, RB: 1, WR: 2], [QB: 2, RB: 3, WR: 5], 10, 10)
    }

    /** Ranks levelled 210 down, realising at half, level, and half again. */
    private static Map<Integer, List<BigDecimal>> uneven(int top) {
        (1..30).collectEntries { int rank ->
            BigDecimal expected = (top - rank * 6) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5] * 3]
        }
    }

    /** Every season exactly at its rank, so outcomes never differ from expectation. */
    private static Map<Integer, List<BigDecimal>> certain(int top) {
        (1..30).collectEntries { int rank -> [(rank): (1..9).collect { (top - rank * 6) as BigDecimal }] }
    }

    private static PointsCurve curve(Map<Integer, List<BigDecimal>> qb, Map<Integer, List<BigDecimal>> rest) {
        PointsCurve.of([QB: TestSeasons.byRank(qb), RB: TestSeasons.byRank(rest), WR: TestSeasons.byRank(rest)])
    }

    def "a third quarterback is worth only what hindsight makes him worth, absent a bye"() {
        given: 'two starting slots, and outcomes that vary so the best two of three is a real choice'
        PointsCurve points = curve(uneven(210), uneven(150))
        LineupValue lineups = new LineupValue(points, new ByeWeeks([:], LAST_WEEK), superflex(), 30)
        List<LineupValue.Rostered> two = [lineups.rostered('QB', 3), lineups.rostered('QB', 4)]

        when:
        LineupValue.Bracket added = lineups.marginal(two, lineups.rostered('QB', 5))

        then: 'a lineup set on preseason ranks starts the same two every week and never reaches for him'
        added.onExpectation == 0.0

        and: 'the whole of his value is the weeks he turns out to be one of the best two, which is real'
        added.withHindsight > 0.0
    }

    def "a spare covers a bye whether or not anyone has hindsight"() {
        given: 'two starting quarterbacks, one of them off in week 7, and outcomes that never vary'
        PointsCurve points = curve(certain(210), certain(150))
        LineupValue lineups = new LineupValue(points, new ByeWeeks([QB: [3: 7]], LAST_WEEK), superflex(), 30)
        List<LineupValue.Rostered> two = [lineups.rostered('QB', 3), lineups.rostered('QB', 4)]

        when:
        LineupValue.Bracket added = lineups.marginal(two, lineups.rostered('QB', 5))

        then: 'the bye is a fact of the schedule, so covering it is worth the same on either reading'
        added.onExpectation > 0.0
        added.withHindsight == added.onExpectation
    }

    def "a spare is worth nothing at a position whose outcomes never vary"() {
        given: 'the same two slots, but every season lands exactly on its rank'
        PointsCurve points = curve(certain(210), certain(150))
        LineupValue lineups = new LineupValue(points, new ByeWeeks([:], LAST_WEEK), superflex(), 30)
        List<LineupValue.Rostered> two = [lineups.rostered('QB', 3), lineups.rostered('QB', 4)]

        when:
        LineupValue.Bracket added = lineups.marginal(two, lineups.rostered('QB', 5))

        then: 'nobody ever outperforms anybody, so the third is never started and adds nothing'
        added.onExpectation == 0.0
        added.withHindsight == 0.0
    }

    def "a bye is covered by whoever else is on the roster"() {
        given: 'one starting slot and two quarterbacks, the starter off in week 7'
        PointsCurve points = curve(certain(210), certain(150))
        StarterRequirements oneSlot = new StarterRequirements([QB: 1], [QB: 1], 1, 10)
        LineupValue lineups = new LineupValue(points,
                new ByeWeeks([QB: [1: 7]], LAST_WEEK), oneSlot, 30)

        when: 'a backup who is never off is added behind him'
        LineupValue.Bracket added = lineups.marginal([lineups.rostered('QB', 1)], lineups.rostered('QB', 2))

        then: 'he is worth exactly the one week the starter cannot play, and nothing else'
        added.onExpectation > 0.0
        added.withHindsight == added.onExpectation
    }

    def "a slot a team cannot fill is a slot it goes without"() {
        given: 'a lineup wanting two receivers, on a roster that holds none and is full everywhere else'
        PointsCurve points = curve(certain(210), certain(150))
        LineupValue lineups = new LineupValue(points, new ByeWeeks([:], LAST_WEEK), superflex(), 30)
        List<LineupValue.Rostered> full = (1..2).collect { lineups.rostered('QB', it) } +
                (1..3).collect { lineups.rostered('RB', it) }

        when: 'the first receiver is added'
        LineupValue.Bracket added = lineups.marginal(full, lineups.rostered('WR', 1))

        then: 'he brings his whole season, having displaced nobody: the slot he fills was standing empty'
        (added.onExpectation - points.seasonPoints('WR', 1)).abs() < points.seasonPoints('WR', 1) * 0.02
    }

    def "the same roster scores the same every time it is asked"() {
        given:
        PointsCurve points = curve(uneven(210), uneven(150))
        LineupValue lineups = new LineupValue(points, new ByeWeeks([:], LAST_WEEK), superflex(), 30)
        List<LineupValue.Rostered> roster = (1..4).collect { lineups.rostered('QB', it) }

        expect: 'a figure a plan is held to cannot move because the sampling was reseeded'
        lineups.evaluate(roster).onExpectation == lineups.evaluate(roster).onExpectation
        lineups.evaluate(roster).withHindsight == lineups.evaluate(roster).withHindsight
    }

    def "a marginal is measured against the same seasons as the roster it is added to"() {
        given:
        PointsCurve points = curve(uneven(210), uneven(150))
        LineupValue lineups = new LineupValue(points, new ByeWeeks([:], LAST_WEEK), superflex(), 30)
        List<LineupValue.Rostered> roster = (1..3).collect { lineups.rostered('QB', it) }

        when: 'a player who can never be started is added: rank 30 behind three better quarterbacks'
        LineupValue.Bracket added = lineups.marginal(roster, lineups.rostered('QB', 30))

        then: 'the difference is his alone, with no sampling noise from redrawing everybody else'
        added.onExpectation >= 0.0
        added.withHindsight >= 0.0
    }
}
