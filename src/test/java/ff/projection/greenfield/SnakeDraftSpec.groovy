package ff.projection.greenfield

import spock.lang.Specification
import spock.lang.Unroll

class SnakeDraftSpec extends Specification {

    @Unroll
    def "slot #slot picks number #pick in round #round of a #teams team snake"() {
        expect:
        SnakeDraft.overallPick(round, slot, teams) == pick

        where:
        round | slot | teams || pick
        1     | 1    | 14    || 1
        1     | 14   | 14    || 14
        2     | 14   | 14    || 15      // the turn: last in round one picks first in round two
        2     | 1    | 14    || 28
        8     | 1    | 14    || 112     // round eight is even, so slot one picks last
        8     | 14   | 14    || 99
        15    | 1    | 14    || 197
    }

    def "every pick of a full draft belongs to exactly one slot, and the round trip holds"() {
        given:
        int teams = 14
        int rounds = 15

        expect:
        (1..rounds * teams).every { int pick ->
            SnakeDraft.overallPick(SnakeDraft.roundOf(pick, teams), SnakeDraft.slotOf(pick, teams), teams) == pick
        }

        and: 'and every slot gets exactly one pick a round'
        (1..rounds).every { int round ->
            (1..teams).collect { SnakeDraft.overallPick(round, it, teams) }.toSet().size() == teams
        }
    }

    def "a slot outside the league is refused rather than wrapped around"() {
        when:
        SnakeDraft.overallPick(1, 15, 14)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('slot must be within 1..14')
    }
}
