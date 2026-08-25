package ff.load.greenfield

import ff.league.League
import spock.lang.Shared
import spock.lang.Specification

/**
 * The measured board, against the nine drafts it is measured from.
 *
 * What is asserted is the shape rather than the figures: a pick is worth less than the one before it, every
 * season contributes, and the late rounds do not collapse to nothing. The figures themselves move whenever
 * the curve does and belong in docs/figures rather than in a spec that would have to be edited to agree.
 */
class DraftHistorySpec extends Specification {

    @Shared
    GreenfieldValuationLoader loader = new GreenfieldValuationLoader()

    @Shared
    Map<Integer, BigDecimal> measured =
            new DraftHistory(loader.curve(), loader.requirements(), League.GREENFIELD).bestAvailableByPick()

    def "every pick of a full draft is covered, less the keepers nobody spent a pick on"() {
        expect: 'fifteen rounds of fourteen, short by the keepers that leave the board before it starts'
        measured.size() > 190
        measured.size() <= 15 * 14
        measured.keySet().min() == 1
    }

    def "the board is picked over as the draft runs, and never recovers"() {
        expect: 'later picks are worth no more than earlier ones, which a stalled name match would break'
        [1, 10, 20, 40, 60, 99, 140].collect { measured[it] }.every { it != null }
        measured[1] > measured[20]
        measured[20] > measured[60]
        measured[60] > measured[140]
    }

    def "a startable player survives into the eighth round, which is what makes that keeper slot cheap"() {
        expect: 'the eighth is picks 99 to 112, and what is left there is worth well over nothing'
        (99..112).every { measured[it] > 20 }
    }

    def "the first pick is worth the best player on the board, no draft having picked before it"() {
        given:
        Map<Integer, BigDecimal> firstSeason = new DraftHistory(
                loader.curve(), loader.requirements(), League.GREENFIELD).bestAvailable('2025')

        expect:
        firstSeason[1] == firstSeason.values().max()
    }
}
