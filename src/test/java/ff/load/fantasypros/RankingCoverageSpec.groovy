package ff.load.fantasypros

import ff.data.fantasypros.FpRankedPlayer
import ff.load.util.LoadUtils
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A ranking set that quietly loses a position is invisible until somebody needs that position.
 *
 * 2026's was fetched with {@code position=OP}, which is every offensive player and no kickers, and nothing
 * noticed until six teams needed a kicker and the board had none to show them. The fetcher now asks for
 * kickers separately; this is what stops the next one going the same way. See docs/DATA.md.
 */
class RankingCoverageSpec extends Specification {

    /** Every position the league starts, and so every position a board has to be able to price. */
    private static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE', 'PK'].asImmutable()

    /**
     * 2026's kickers cannot be recovered: the API key has since dropped to the free tier, which truncates
     * every set to ten players, and the fetcher refuses to overwrite good data with that. Remove this the
     * moment a full set can be fetched again.
     */
    private static final List<String> MISSING_KICKERS = ['2026'].asImmutable()

    @Unroll
    def "#year's redraft ranking carries every position the league starts"() {
        given:
        Collection<FpRankedPlayer> ranked = new FantasyProsLoader()
                .loadRankedPlayers(LoadUtils.fpRedraftRankingsHalfPprResourcePath(year)).values()

        expect:
        POSITIONS.findAll { it != 'PK' || !MISSING_KICKERS.contains(year) }.every { String position ->
            ranked.any { it.player.position == position }
        }

        where:
        year << LoadUtils.YEARS
    }

    def "the only ranking set missing a position is the one that cannot be refetched"() {
        expect: 'so that the exclusion above stays a record of a known gap rather than a place to hide new ones'
        MISSING_KICKERS == ['2026']
    }
}
