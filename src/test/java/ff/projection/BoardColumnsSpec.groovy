package ff.projection

import ff.data.PlayerValuation
import ff.data.RealisedSeason
import spock.lang.Specification

/**
 * The board is the only thing a draft plan may reason from, so what a plan needs has to be on it.
 *
 * These are the columns that exist because leaving them off sent a plan behind the model for them: the bye
 * week, which decides how a set of players covers a season between them, and the range a position's seasons
 * run to either side of expectation. See docs/STRATEGY.md.
 */
class BoardColumnsSpec extends Specification {

    private static final int LAST_WEEK = 14

    /**
     * Ranks levelled 204 down, realising at half, level and half again.
     *
     * Deliberately without a lost season: the real left tail reaches zero at every position, which puts the
     * tenth percentile at zero for everyone and makes a bad season the same number for the best player and
     * the worst. True of the league, and useless for testing that the range scales with the player.
     */
    private static Map<Integer, List<BigDecimal>> uneven() {
        (1..30).collectEntries { int rank ->
            BigDecimal expected = (210 - rank * 6) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5] * 3]
        }
    }

    private static List<PlayerValuation> value(ByeWeeks byes) {
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(uneven())])
        StarterRequirements requirements = new StarterRequirements(
                [WR: 2], [WR: 4], 3, 10)
        Map<String, List> available = (1..20).collectEntries { int rank ->
            [("p$rank" as String): ["Player $rank" as String, 'WR', rank, null, rank + 3]]
        }
        AuctionValuation.value(curve, requirements, available, [WR: 40], 300.0, 20, byes)
    }

    def "carries each player's bye week onto the board"() {
        given: 'rank 3 is off in week 9, and the rest of the pool has no bye recorded'
        List<PlayerValuation> valuations = value(new ByeWeeks([WR: [3: 9]], LAST_WEEK))

        expect:
        valuations.find { it.positionRank == 3 }.bye == 9
        valuations.find { it.positionRank == 4 }.bye == null
    }

    def "carries the dynasty rank through without letting it touch a price"() {
        given: 'every player ranked three worse for the long run than for this season'
        List<PlayerValuation> valuations = value(new ByeWeeks([:], LAST_WEEK))

        expect: 'it reaches the board'
        valuations.find { it.positionRank == 5 }.dynastyRank == 8

        and: 'and nothing is priced off it: value and price follow the redraft rank, which is monotone'
        valuations.sort { it.positionRank }.collect { it.value } ==
                valuations.sort { it.positionRank }.collect { it.value }.sort { -it }
    }

    def "leaves the dynasty rank empty where the ranking does not carry a player"() {
        given:
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(uneven())])

        when: 'a pool whose entries stop at the franchise, as a caller that has no dynasty ranking would give'
        List<PlayerValuation> valuations = AuctionValuation.value(curve,
                new StarterRequirements([WR: 2], [WR: 4], 3, 10),
                [p1: ['Player 1', 'WR', 2, null]], [WR: 40], 300.0, 20, new ByeWeeks([:], LAST_WEEK))

        then: 'absent rather than invented'
        valuations[0].dynastyRank == null
    }

    def "brackets every player between a bad season and a good one"() {
        given:
        List<PlayerValuation> valuations = value(new ByeWeeks([:], LAST_WEEK))

        expect:
        valuations.every { it.pointsLow < it.points && it.pointsHigh > it.points }
    }

    def "scales the range with the player, but keeps its shape the position's"() {
        given:
        List<PlayerValuation> valuations = value(new ByeWeeks([:], LAST_WEEK))
        PlayerValuation best = valuations.find { it.positionRank == 5 }
        PlayerValuation worse = valuations.find { it.positionRank == 15 }

        expect: 'the better player has more points in a bad season than the worse one does'
        best.pointsLow > worse.pointsLow

        and: 'but the same proportional range, since realised variation is measured per position, not per player'
        ((best.pointsHigh / best.points) - (worse.pointsHigh / worse.points)).abs() < 0.001
        ((best.pointsLow / best.points) - (worse.pointsLow / worse.points)).abs() < 0.001
    }

    /**
     * The season is carried as its two halves as well as its total, and the three have to agree.
     *
     * A plan reads these side by side and will multiply two of them; landing anywhere but the third would
     * be a column that quietly lies. It is not free — the level is anchored back to the mean season the
     * position actually had, some five per cent above the product of the two separate means — so the rate
     * reported is the one implied by the level rather than the raw mean behind it.
     */
    def "carries the rate and the availability a season is the product of"() {
        given:
        List<PlayerValuation> valuations = value(new ByeWeeks([:], LAST_WEEK))

        expect: 'both halves reach the board'
        valuations.every { it.pointsPerGame > 0 && it.expectedGames > 0 }

        and: 'and they multiply back out to the season beside them'
        valuations.every {
            ((it.pointsPerGame * it.expectedGames) - it.points).abs() < it.points * 0.001
        }
    }

    /**
     * Availability is the one thing on the board that separates two players a tier cannot.
     *
     * The outcome range is the position's, so it is the same proportion for everybody at it. Games played
     * is not: it is levelled per rank, and it is why a fragile high-rate player and a durable moderate one
     * are no longer the same row with different names.
     */
    def "reports availability per rank, where the outcome range is only per position"() {
        given: 'a position whose deeper ranks miss half the season'
        Map<Integer, List<BigDecimal>> shallow = (1..10).collectEntries { int rank ->
            [(rank): [180.0, 180.0, 180.0] * 3]
        }
        Map<Integer, List<RealisedSeason>> byRank = TestSeasons.byRank(shallow)
        (11..30).each { int rank ->
            byRank[rank] = (1..9).collect { new RealisedSeason(points: 90.0, games: 6) }
        }
        PointsCurve curve = PointsCurve.of([WR: byRank])
        Map<String, List> available = [deep: ['Deep', 'WR', 25, null], top: ['Top', 'WR', 3, null]]

        when:
        List<PlayerValuation> valuations = AuctionValuation.value(curve,
                new StarterRequirements([WR: 2], [WR: 4], 3, 10), available, [WR: 40], 300.0, 20,
                new ByeWeeks([:], LAST_WEEK))

        then: 'the deep rank is reported as playing materially less football'
        valuations.find { it.playerName == 'Deep' }.expectedGames <
                valuations.find { it.playerName == 'Top' }.expectedGames * 0.85
    }

    def "leaves the range at expectation when a position has no measured spread"() {
        given: 'three ranks over six seasons: enough to level a rank, too few to describe a distribution'
        PointsCurve curve = PointsCurve.of([WR: TestSeasons.byRank(
                (1..3).collectEntries { int rank -> [(rank): (1..6).collect { 200.0 as BigDecimal }] })])
        Map<String, List> available = [p1: ['Player 1', 'WR', 2, null, 5]]

        when:
        List<PlayerValuation> valuations = AuctionValuation.value(curve,
                new StarterRequirements([WR: 2], [WR: 4], 3, 10), available, [WR: 40], 300.0, 20,
                new ByeWeeks([:], LAST_WEEK))

        then: 'a range would be invented rather than measured, so none is claimed'
        valuations[0].pointsLow == valuations[0].points
        valuations[0].pointsHigh == valuations[0].points
    }
}
