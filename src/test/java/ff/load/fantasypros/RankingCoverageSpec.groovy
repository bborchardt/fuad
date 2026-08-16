package ff.load.fantasypros

import ff.data.fantasypros.FpRankedPlayer
import ff.load.util.LoadUtils
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A ranking set that quietly loses a position is invisible until somebody needs that position.
 *
 * Fantasypros does not carry kickers in a superflex ranking, which is the format this league needs, so
 * 2026's redraft export arrived without a single one — and nothing noticed until six teams needed a kicker
 * and the board had none to show them. Kickers now come as their own single position export, merged in by
 * {@link FantasyProsLoader#loadRedraftRankedPlayers}. This asserts the result, which is what the model
 * actually reads, rather than any one file. See docs/DATA.md.
 */
class RankingCoverageSpec extends Specification {

    /** Every position the league starts, and so every position a board has to be able to price. */
    private static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE', 'PK'].asImmutable()

    @Unroll
    def "#year's redraft ranking carries every position the league starts"() {
        given:
        Collection<FpRankedPlayer> ranked = new FantasyProsLoader().loadRedraftRankedPlayers(year).values()

        expect:
        POSITIONS.every { String position -> ranked.any { it.player.position == position } }

        where:
        year << LoadUtils.YEARS
    }

    @Unroll
    def "#year ranks its kickers from one, however they were exported"() {
        given: 'a single position export has no POS column, so the rank has to come from the row'
        List<Integer> ranks = new FantasyProsLoader().loadRedraftRankedPlayers(year).values()
                .findAll { it.player.position == 'PK' }
                .collect { it.rank.positionRank }
                .sort()

        expect: 'the best kicker is PK1 and the ranks run without gaps or repeats'
        ranks.first() == 1
        ranks == (1..ranks.size()).toList()

        where:
        year << LoadUtils.YEARS
    }

    def "kickers merged from their own file do not land among the best players overall"() {
        given: 'their file numbers from one, so the overall rank has to be offset past the main set'
        Collection<FpRankedPlayer> ranked = new FantasyProsLoader().loadRedraftRankedPlayers('2026').values()
        int bestKicker = ranked.findAll { it.player.position == 'PK' }.collect { it.rank.overallRank }.min()

        expect:
        bestKicker > ranked.findAll { it.player.position != 'PK' }.size()
    }
}
