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

    /**
     * Nothing varies: every season exactly on its level, and every rank on the same one.
     *
     * Flat across ranks as well as across seasons, which is stronger than it looks and has to be. The
     * outcome spread is pooled over the whole position and each rank's level is smoothed over its
     * neighbours, so on a sloped curve the ranks at either end realise a few per cent off their own
     * smoothed level. Pooled, that reaches every rank, and a few per cent is enough to reorder two players
     * a rank apart. A fixture meaning "nobody ever outperforms anybody" has to be flat for that to be true.
     */
    private static Map<Integer, List<BigDecimal>> certain(int level) {
        (1..30).collectEntries { int rank -> [(rank): (1..9).collect { level as BigDecimal }] }
    }

    /**
     * A position that loses half its seasons outright, at twice the rate in the ones it keeps.
     *
     * The same expected season as {@link #certain} delivered a different way, which is the whole point: one
     * player is there every week, the other is excellent and then absent. A roster is not indifferent
     * between them, and a model that averages the second into thirteen mediocre weeks cannot say so.
     */
    private static Map<Integer, List<BigDecimal>> fragile(int level) {
        // Four of the nine seasons lost outright, the other five carrying the whole level between them.
        (1..30).collectEntries { int rank ->
            [(rank): (1..9).collect { it % 2 == 0 ? 0.0 : (level * 9 / 5) as BigDecimal }]
        }
    }

    /**
     * Seasons in which nobody ever misses a week.
     *
     * Availability is drawn now rather than averaged in, so a fixture meaning to hold outcomes fixed has to
     * say so about both halves: seasons of thirteen games against fourteen playable weeks would have every
     * player sitting one week out, and a spare covering it is real value that these tests are not about.
     */
    private static PointsCurve curve(Map<Integer, List<BigDecimal>> qb, Map<Integer, List<BigDecimal>> rest) {
        PointsCurve.of([QB : TestSeasons.byRank(qb, LAST_WEEK),
                        RB : TestSeasons.byRank(rest, LAST_WEEK),
                        WR : TestSeasons.byRank(rest, LAST_WEEK)])
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

    /**
     * The fix this file exists to hold: a lost season is weeks missed, not a year of bad football.
     *
     * Smearing an injured starter's total across the calendar left him nominally in the lineup every week,
     * scoring a little. A backup behind him was then worth nothing at all on the reading a plan should lean
     * on, because a lineup set on the preseason ranks never reached past a starter who was, as far as the
     * model could see, playing. Give the weeks back and the backup is worth exactly the ones his starter is
     * not there for, which is what insurance is.
     */
    def "a backup is worth the weeks his starter misses, before anyone needs hindsight"() {
        given: 'one starting slot, at a position that loses half its seasons to injury'
        PointsCurve points = PointsCurve.of([QB: TestSeasons.byRank(fragile(210), LAST_WEEK)])
        StarterRequirements oneSlot = new StarterRequirements([QB: 1], [QB: 1], 1, 10)
        LineupValue lineups = new LineupValue(points, new ByeWeeks([:], LAST_WEEK), oneSlot, 30)

        when: 'a second quarterback is added behind the first'
        LineupValue.Bracket added = lineups.marginal([lineups.rostered('QB', 1)], lineups.rostered('QB', 2))

        then: 'he is worth real points without anybody having to have guessed right'
        added.onExpectation > 0.0

        and: 'the share of the season his starter is absent for, of what he himself brings'
        LineupValue.Rostered starter = lineups.rostered('QB', 1)
        BigDecimal absent = 1.0 - (starter.expectedGames / starter.playable.length) as BigDecimal
        BigDecimal expected = absent * points.seasonPoints('QB', 2)
        added.onExpectation > expected * 0.8
        added.onExpectation < expected * 1.2
    }

    def "the same expected season is worth less spread thin than delivered and then missed"() {
        given: 'two positions levelled alike, one of them never absent and the other absent half the time'
        PointsCurve points = PointsCurve.of([QB: TestSeasons.byRank(certain(210), LAST_WEEK),
                                             RB: TestSeasons.byRank(fragile(210), LAST_WEEK)])
        StarterRequirements oneEach = new StarterRequirements([QB: 1, RB: 1], [QB: 1, RB: 1], 2, 10)
        LineupValue lineups = new LineupValue(points, new ByeWeeks([:], LAST_WEEK), oneEach, 30)

        expect: 'the two starters are levelled at the same season'
        (points.seasonPoints('QB', 1) - points.seasonPoints('RB', 1)).abs() < 1.0

        when: 'a spare is added behind each'
        LineupValue.Bracket behindDurable =
                lineups.marginal([lineups.rostered('QB', 1)], lineups.rostered('QB', 2))
        LineupValue.Bracket behindFragile =
                lineups.marginal([lineups.rostered('RB', 1)], lineups.rostered('RB', 2))

        then: 'behind the durable starter a spare is worth nothing: there is never a week to cover'
        behindDurable.onExpectation == 0.0

        and: 'behind the fragile one he is worth a large part of a season, on the same levelled points'
        behindFragile.onExpectation > points.seasonPoints('RB', 2) * 0.3
    }

    def "a lone player brings the season his rank is levelled at"() {
        given: 'a single roster spot and nobody to compete for it'
        PointsCurve points = PointsCurve.of([QB: TestSeasons.byRank(uneven(210), LAST_WEEK)])
        StarterRequirements oneSlot = new StarterRequirements([QB: 1], [QB: 1], 1, 10)
        LineupValue lineups = new LineupValue(points, new ByeWeeks([:], LAST_WEEK), oneSlot, 30)

        expect: 'splitting the season into a rate and a set of weeks gives the same season back'
        BigDecimal levelled = points.seasonPoints('QB', 5)
        (lineups.evaluate([lineups.rostered('QB', 5)]).onExpectation - levelled).abs() < levelled * 0.02
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
