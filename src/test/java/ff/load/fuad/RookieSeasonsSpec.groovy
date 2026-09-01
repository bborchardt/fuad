package ff.load.fuad

import ff.projection.PointsCurve
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The rookie curves: the same levelling the auction board uses, run against a shifted ranking.
 *
 * What is worth asserting is not a level — those move whenever a season is added, and the figures under
 * docs/figures hold them instead — but the shape of the thing: that each contract year is levelled off the
 * class that far behind it, that the deeper years rest on fewer classes, and that asking for a sixth year of
 * a five year contract is refused rather than answered.
 */
class RookieSeasonsSpec extends Specification {

    private static final RookieSeasons SEASONS = new RookieSeasons()

    /**
     * Nine classes have a first season in the record and five have a fifth.
     *
     * This is the honest denominator behind every level the later years carry, and it is the reason the
     * fifth year of a contract is a weaker claim than the first rather than an equal one.
     */
    @Unroll
    def "contract year #year is levelled off #classes rookie classes"() {
        expect:
        SEASONS.classesBehind(year) == classes

        where:
        year | classes
        1    | 9
        2    | 8
        3    | 7
        4    | 6
        5    | 5
    }

    @Unroll
    def "there is no year #year of a contract the league cannot write"() {
        when:
        SEASONS.curve(year)

        then:
        thrown(IllegalArgumentException)

        where:
        year << [0, 6, -1]
    }

    def "levels a rookie at his rookie rank, not at a rank the whole league is ordered by"() {
        given: 'the first year curve'
        PointsCurve curve = SEASONS.curve(1)

        expect: 'rookie RB1 is levelled well below what the consensus RB1 of a whole league scores'
        curve.seasonPoints('RB', 1) > 0
        curve.seasonPoints('RB', 1) < 200
    }

    def "builds each contract year once, however often it is asked for"() {
        given:
        RookieSeasons seasons = new RookieSeasons()

        expect:
        seasons.curve(2).is(seasons.curve(2))
    }

    /**
     * The finding the whole rookie board rests on, and it is not the same at every position.
     *
     * <b>Quarterbacks and receivers are worth substantially more in their second season than their first;
     * running backs are not.</b> The best rookie quarterback of a class has levelled at 90 points in the
     * year he was drafted and 155 in the year after, and the best receiver at 99 and 122. The best running
     * back levels at 131 and then 127 — he arrives finished, and a pick spent on him is a win-now pick in a
     * way a pick spent on a quarterback is not.
     *
     * That difference is the argument for pricing a rookie over his contract rather than over his first
     * season, so it is asserted rather than described. If it ever stopped holding, a rookie pick would be an
     * auction lot with a fixed price and most of this could be deleted.
     */
    def "quarterbacks and receivers grow into their second season, and running backs do not"() {
        given:
        PointsCurve first = SEASONS.curve(1)
        PointsCurve second = SEASONS.curve(2)

        expect: 'a fifth again at quarterback, a fifth at receiver'
        second.seasonPoints('QB', 1) > first.seasonPoints('QB', 1) * 1.2
        second.seasonPoints('WR', 1) > first.seasonPoints('WR', 1) * 1.2

        and: 'and a running back who is already what he will be'
        second.seasonPoints('RB', 1) < first.seasonPoints('RB', 1)
    }
}
