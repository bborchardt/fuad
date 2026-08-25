package ff.projection.greenfield

import ff.data.greenfield.KeeperSurplus
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The keeper decision, on a board small enough to check by hand.
 *
 * Fourteen named players in consensus order, each worth ten points less than the one above him, so what a
 * pick returns can be read straight off the position in the list and every surplus below is arithmetic a
 * reader can redo.
 */
class KeeperValuationSpec extends Specification {

    private static final int TEAMS = 4

    /** p1 is worth 140, p2 130, and so on down. */
    private static final List<String> BOARD = (1..14).collect { "p$it" as String }
    private static final Closure<BigDecimal> VALUE = { String name ->
        (150 - 10 * (name.substring(1) as int)) as BigDecimal
    }

    def "keeping is worth the player less whoever the pick would have returned"() {
        given: 'slot 2 keeps p3 for a second round pick, which in a four team snake is pick 7'
        List<Map> keepers = [[owner: 'b', player: 'p3', costRound: 2]]

        when:
        List<KeeperSurplus> valued = KeeperValuation.value(
                keepers, [a: 1, b: 2, c: 3, d: 4], VALUE, BOARD, [p3: 5], TEAMS)

        then:
        valued.size() == 1
        valued[0].costPick == 7

        and: 'p3 is off the board, so the six picks before this one take p1, p2, p4, p5, p6 and p7'
        valued[0].alternative == 'p8'

        and: 'p3 is worth 120 and p8, who keeping him gives up, is worth 70'
        valued[0].keeperValue == 120
        valued[0].alternativeValue == 70
        valued[0].surplus() == 50
    }

    def "a keeper worth less than the pick it costs comes out negative rather than being hidden"() {
        given: 'slot 1 keeps p9 -- a late player -- for a second round pick, which is pick 8'
        List<Map> keepers = [[owner: 'a', player: 'p9', costRound: 2]]

        when:
        List<KeeperSurplus> valued = KeeperValuation.value(
                keepers, [a: 1, b: 2, c: 3, d: 4], VALUE, BOARD, [p9: 4], TEAMS)

        then: 'the pick would have returned a better player than the one being kept'
        valued[0].surplus() < 0
        valued[0].keeperValue < valued[0].alternativeValue
    }

    def "two owners forfeiting adjacent picks are measured against the same player"() {
        given: 'picks 7 and 8 are both surrendered, so nobody is taken at either'
        List<Map> keepers = [[owner: 'b', player: 'p3', costRound: 2],
                             [owner: 'a', player: 'p4', costRound: 2]]

        when:
        List<KeeperSurplus> valued = KeeperValuation.value(
                keepers, [a: 1, b: 2, c: 3, d: 4], VALUE, BOARD, [p3: 5, p4: 5], TEAMS)

        then: 'each is asking what he alone would have got, and only one of them could have had it'
        valued.collect { it.alternative }.toSet().size() == 1

        and: 'which is why these do not add up across the league'
        valued.every { it.surplus() > 0 }
    }

    @Unroll
    def "a player drafted in round #priorRound #verdict be kept for a #costRound"() {
        expect:
        KeeperValuation.eligible(costRound, priorRound) == allowed

        where:
        costRound | priorRound || allowed
        8         | 6          || true
        8         | 12         || true
        8         | 5          || false     // too early: the cheap slot starts at the sixth
        2         | 3          || true
        2         | 5          || true      // the dead zone: keepable, but only at the dear price
        2         | 2          || false
        8         | null       || true      // undrafted qualifies for either slot
        2         | null       || true

        verdict = allowed ? 'may' : 'may not'
    }

    def "a cost round the rule does not name is refused rather than defaulted"() {
        expect: 'only a second and an eighth are keeper prices; a fifth is not a cheaper eighth'
        !KeeperValuation.eligible(5, 1)
        !KeeperValuation.eligible(1, 1)
    }

    def "the results come back worst last, so a plan reads the decisions in the order they matter"() {
        given:
        List<Map> keepers = [[owner: 'a', player: 'p9', costRound: 2],
                             [owner: 'b', player: 'p1', costRound: 2]]

        when:
        List<KeeperSurplus> valued = KeeperValuation.value(
                keepers, [a: 1, b: 2, c: 3, d: 4], VALUE, BOARD, [p1: 6, p9: 6], TEAMS)

        then:
        valued.collect { it.player } == ['p1', 'p9']
        valued[0].surplus() > valued[1].surplus()
    }

    def "the measured reading brackets the consensus one, and is the lower of the two here"() {
        given: 'the league has historically left a 100 point player on the board at pick 7'
        List<Map> keepers = [[owner: 'b', player: 'p3', costRound: 2]]

        when:
        List<KeeperSurplus> valued = KeeperValuation.value(
                keepers, [a: 1, b: 2, c: 3, d: 4], VALUE, BOARD, [p3: 5], TEAMS, [7: 100.0 as BigDecimal])

        then: 'consensus order says the pick returns p8 at 70; the record says it returns 100'
        valued[0].alternativeValue == 70
        valued[0].measuredAlternativeValue == 100

        and: 'so keeping is worth 50 on the assumption and 20 against what was really there'
        valued[0].surplus() == 50
        valued[0].measuredSurplus() == 20
    }

    def "a pick no draft ever reached has no measured reading rather than a zero"() {
        given:
        List<Map> keepers = [[owner: 'b', player: 'p3', costRound: 2]]

        when:
        List<KeeperSurplus> valued = KeeperValuation.value(
                keepers, [a: 1, b: 2, c: 3, d: 4], VALUE, BOARD, [p3: 5], TEAMS, [:])

        then: 'absent is not the same as worthless, and a null says which of the two it is'
        valued[0].measuredAlternativeValue == null
        valued[0].measuredSurplus() == null
        valued[0].surplus() == 50
    }
}
