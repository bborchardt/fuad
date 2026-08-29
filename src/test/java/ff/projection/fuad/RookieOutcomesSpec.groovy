package ff.projection.fuad

import ff.load.fuad.RookieSeasons
import ff.projection.PointsCurve
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The spread a rookie rank is given, measured on rookies instead of borrowed from the veterans.
 *
 * What matters here is not a cell but four properties, each of which was wrong at some point in this
 * board's history: that a multiplier is a ratio of rate and never of a season total, that a season nobody
 * played is kept rather than dropped, that the spread widens down the board — which is the whole reason for
 * measuring it — and that it widens <b>smoothly</b>, since an edge in the pooling near replacement is worth
 * tens of dollars to the player either side of it.
 */
class RookieOutcomesSpec extends Specification {

    @Shared
    RookieSeasons seasons = new RookieSeasons()
    @Shared
    RookieOutcomes outcomes = new RookieOutcomes(seasons)

    /**
     * The mean multiplier, weighted by games, computed in a helper rather than inline.
     *
     * Spock reads {@code a * b} in a spec body as a mock interaction, so the product has to live somewhere
     * it does not rewrite. Weighted by games because that is how the level it multiplies is built: the
     * curve's rate is total points over total games, a ratio of means rather than a mean of ratios.
     */
    private static double weightedMean(List<PointsCurve.Outcome> played) {
        double weighted = 0.0d
        int games = 0
        for (PointsCurve.Outcome outcome : played) {
            weighted += outcome.rateMultiplier * outcome.games
            games += outcome.games
        }
        games ? weighted / games : 0.0d
    }

    @Unroll
    def "#position multipliers are centred on the window's own mean rate"() {
        given:
        List<PointsCurve.Outcome> played = outcomes.of(position, 3, 1).findAll { it.games > 0 }
        double weighted = weightedMean(played)

        expect: 'a rate ratio averages about one by construction, weighted as the level is'
        played.size() > 20
        weighted > 0.95
        weighted < 1.05

        where:
        position << ['QB', 'RB', 'WR', 'TE']
    }

    /**
     * A season that never happened is an observation, not an absence.
     *
     * Dropping them would make a bust free, which is the whole thing a deep pick is being weighed against.
     * They are kept at zero games, where they contribute nothing to the value and everything to the average.
     */
    def "keeps the seasons nobody played, at no games and no rate"() {
        given:
        List<PointsCurve.Outcome> deep = outcomes.of('WR', 22, 1)

        expect:
        deep.count { it.games == 0 } > 0
        deep.findAll { it.games == 0 }.every { it.rateMultiplier == 0.0d }
    }

    /**
     * The finding the whole change rests on: a rookie's outcomes widen down the board.
     *
     * At the top of a class a rookie is a known quantity who plays, and his spread is <b>narrower</b> than
     * an established player's at the same position — which is why lending rookies the veteran spread was
     * close to right early and badly wrong deep. By rank 21 the same measurement is half again as wide and
     * most of the seasons never happen at all.
     */
    @Unroll
    def "#position outcomes widen from the top of a class to the bottom"() {
        given:
        def spreadOf = { int rank ->
            List<PointsCurve.Outcome> played = outcomes.of(position, rank, 2).findAll { it.games > 0 }
            List<Double> sorted = played*.rateMultiplier.sort()
            sorted[(sorted.size() * 0.9) as int]
        }

        double deepMissing = outcomes.of(position, 25, 2).count { it.games == 0 } /
                (double) outcomes.of(position, 25, 2).size()
        double topMissing = outcomes.of(position, 3, 2).count { it.games == 0 } /
                (double) outcomes.of(position, 3, 2).size()

        expect: 'the ninetieth percentile multiplier is larger deep than shallow'
        spreadOf(25) > spreadOf(3)

        and: 'and most of a deep rank\'s seasons never happen, where almost none of a shallow one\'s do'
        deepMissing > 0.4
        topMissing < 0.2

        where:
        position << ['RB', 'WR']
    }

    def "gives every rank enough seasons to be a distribution rather than an anecdote"() {
        expect:
        ['QB', 'RB', 'WR', 'TE'].every { String position ->
            [1, 8, 15, 30].every { int rank -> outcomes.of(position, rank, 1).size() >= 20 }
        }
    }

    def "measures each rank once, however often it is asked for"() {
        expect:
        outcomes.of('WR', 3, 1).is(outcomes.of('WR', 3, 1))
    }

    /**
     * The defect this replaced, asserted so it cannot come back.
     *
     * Fixed bands put an edge between rank five and rank six, and near replacement an edge is not a rounding
     * difference: Omar Cooper at WR5 and Denzel Boston at WR6 have blended rates within one per cent of each
     * other and were priced $52 and $85, entirely because one fell in the top band and the other did not. A
     * rookie levelled just below replacement is worth nothing at his mean, so all of his value comes from the
     * right tail, and a tenth more tail is most of a doubling.
     *
     * A sliding window has no edges, so neighbouring ranks differ by about as much as their levels do.
     *
     * Checked over the first twenty ranks, which is where the money is. Past there the consensus ranks few
     * enough backs that the window's composition turns over quickly from one rank to the next, and the
     * levels are far enough below replacement that what the spread does to them costs nothing.
     */
    @Unroll
    def "#position spreads move smoothly from one rank to the next"() {
        given:
        def p90 = { int rank ->
            List<Double> played = outcomes.of(position, rank, 2).findAll { it.games > 0 }*.rateMultiplier.sort()
            played[(played.size() * 0.9) as int]
        }

        expect: 'no pair of neighbours jumps the way a band edge did, over the ranks that carry money'
        (2..20).every { int rank -> Math.abs(p90(rank) - p90(rank - 1)) < 0.15d }

        where:
        position << ['RB', 'WR']
    }
}
