package ff.projection

import ff.data.PlayerValuation
import spock.lang.Specification

import java.math.RoundingMode

/**
 * The constants this model divides money by are measurements, not choices, so they are checked against the
 * seasons they were measured from rather than left to drift.
 *
 * <b>The measuring itself is no longer done here.</b> It was, and that was the wrong place for it: the
 * figures the documentation quotes about what the league pays were computed by test code, which meant they
 * could not be generated, could not be cited, and could not be checked. {@link AuctionSpend} owns them now,
 * so the constants below and the tables in docs/figures come from one implementation reading one set of
 * committed seasons. See docs/PROJECTION.md.
 */
class AuctionValuationSpec extends Specification {

    /** The seasons the positional calibration is fitted over, which is what MARKET_SHARE is a mean of. */
    private static List<AuctionSpend.Season> calibrated() {
        AuctionSpend.CALIBRATED_SEASONS.collect { AuctionSpend.of(it) }
    }

    /**
     * The seasons played under superflex, which is what the rookie and spend constants are means of.
     *
     * Not the same span as {@link #calibrated}, and the difference matters. A share of the pot is only
     * comparable between seasons starting the same lineup, so the positional calibration throws 2022 away;
     * a spend rate and a rookie budget are not positional and keep it. These used to be measured over the
     * calibration's three seasons while their constants documented four, so each assertion was checking a
     * narrower claim than the one beside it in prose.
     */
    private static List<AuctionSpend.Season> superflex() {
        AuctionSpend.SUPERFLEX_SEASONS.collect { AuctionSpend.of(it) }
    }

    def "the spend rate is what the four superflex seasons actually spent"() {
        given:
        List<AuctionSpend.Season> seasons = superflex()

        when:
        BigDecimal measured = seasons.collect { it.spendRate }.sum() / seasons.size()

        then:
        (measured - AuctionValuation.SPEND_RATE).abs() < 0.01
    }

    /**
     * The range is stated over the measurable record, so it is checked over the measurable record.
     *
     * It used to be asserted over the calibration's three seasons against bounds of 0.65 and 0.90, which
     * matched neither the span nor the figures the documentation gives. The eight seasons that can be
     * measured run 0.697 to 0.868, which is the 70% to 87% the prose states once each is read to the whole
     * percentage point it is rounded to.
     *
     * Both bounds moved when the pot began counting the veterans the auction signs from outside the
     * pre-draft rosters. That is money the league plainly spent, so every season spends a little more of its
     * cap than it used to appear to.
     */
    def "no season in the record spent outside the range the model claims"() {
        expect:
        AuctionSpend.RECORD_SEASONS.collect { AuctionSpend.of(it).spendRate }.every {
            BigDecimal rounded = it.setScale(2, RoundingMode.HALF_UP)
            rounded >= 0.70 && rounded <= 0.87
        }
    }

    /**
     * A spend rate above one is not a high number, it is a broken measurement.
     *
     * The league cannot spend more than the cap it has free, so any season measuring over 1.0 is one where
     * what counts as an auction signing has gone wrong — a roster snapshot taken on the wrong side of a
     * structural change, most likely, so that players who were never bid on look like they were. Asserted
     * separately from the range above and before it, because the range is a claim about the league's
     * behaviour and this is a claim about the measurement being a measurement at all.
     *
     * It is the assertion that decides which seasons {@link AuctionSpend#RECORD_SEASONS} can hold.
     */
    def "no season measures as spending more than the cap it had free"() {
        expect:
        AuctionSpend.RECORD_SEASONS.every { AuctionSpend.of(it).spendRate <= 1.0 }
    }

    def "five rounds times teams is what the rookie draft actually puts on rosters"() {
        expect: 'within a couple of picks every season, since rookies are almost always kept'
        superflex().every {
            Math.abs(it.rookiesRostered - AuctionValuation.ROOKIE_ROUNDS * it.teams) <= 3
        }
    }

    /**
     * <b>Held to two tenths of a point, not to one.</b> A tolerance of 0.01 on a constant of 0.033 is a
     * third of the constant, which no drift the data could produce would ever breach — the reserve moved
     * when the pot began counting the veterans signed from outside the pre-auction rosters, and this went on
     * passing throughout. A tolerance wide enough to admit any answer is not a check.
     */
    def "rookies cost about the share of the pot the model reserves for them"() {
        given:
        List<BigDecimal> shares = superflex().collect { it.rookieShare }

        expect:
        (shares.sum() / shares.size() - AuctionValuation.ROOKIE_BUDGET_SHARE).abs() < 0.002
    }

    /**
     * Every position is calibrated on its share of the whole auction, kickers in the denominator with
     * everyone else.
     *
     * <b>One basis, and it has to be one.</b> The four scoring positions used to carry shares of what those
     * four took, alongside a kicker entry on the whole-pot basis, and the map summed to 1.009. That cost
     * nothing while it lasted: a kicker had no curve and so no value over replacement, which kept him out
     * of the pool {@link AuctionValuation#calibrate} normalises over, and his entry was never read at all.
     * Levelling kickers put them in that pool and made the mixed basis a real error.
     */
    def "the market shares are what the calibrated seasons actually paid each position"() {
        given:
        Map<String, BigDecimal> paid = AuctionSpend.shareByPosition(calibrated())

        expect:
        AuctionSpend.POSITIONS.every {
            (paid[it] - AuctionValuation.MARKET_SHARE[it]).abs() < 0.005
        }
    }

    def "the shares are shares, so they sum to one"() {
        expect: 'which the mixed basis did not, and could not have been noticed from the map alone'
        ((AuctionValuation.MARKET_SHARE.values().sum() as BigDecimal) - 1.0).abs() < 0.002
    }

    /**
     * The price half of the finding that made levelling kickers worth doing.
     *
     * <b>Asserted over the record rather than against the constant.</b> This used to check that each of the
     * four scoring positions is paid within a fifth of {@link AuctionValuation#MARKET_SHARE} and that the
     * kicker entry is under 0.02 — but {@code MARKET_SHARE} <i>is</i> the measured paid share, so the first
     * was the constant against itself and a restatement of the test above, and the second was a constant
     * against a literal. Neither could fail for any reason the data could supply.
     *
     * What the finding actually rests on is that kicker takes almost nothing in every auction the league
     * has ever held, which the seasons can answer and a constant cannot. Nine seasons run 0.48% to 1.61%.
     *
     * The other half — that the curve puts kicker's share of <i>value</i> near 6% — is a property of a
     * board rather than of the record, so it is reported as {@code VORSHARE} in docs/figures and the
     * documentation is held to it there. It was prose that nothing recomputed, which is the arrangement
     * this class's own history is a warning about.
     */
    def "kicker takes almost none of any auction the league has held"() {
        given:
        List<BigDecimal> shares = AuctionSpend.RECORD_SEASONS.collect {
            AuctionSpend.shareByPosition([AuctionSpend.of(it)]).PK
        }

        expect: 'never as much as a fiftieth of the pot, in any season, under either lineup'
        shares.every { it > 0 && it < 0.02 }

        and: 'which is what the pooled constant the calibration uses is a mean of'
        AuctionValuation.MARKET_SHARE.PK < 0.02
    }

    /**
     * The property that makes the scale a curve is measured on somebody else's business.
     *
     * Value over replacement is computed inside a position and then summed across all of them to divide the
     * pot, so any factor applied to one position's whole curve is a thumb on that sum. It ought not to be:
     * {@link AuctionValuation#calibrate} forces each position's share of the money to what the league
     * actually pays, and a share is a ratio, so multiplying a position's points by anything must come out
     * the other side unchanged.
     *
     * <b>That is what was quietly not true.</b> Rate and availability are averaged apart and multiplied,
     * which loses their covariance, so each position's level is anchored back to the season it really had —
     * by a different factor at each position, reported as {@code ANCHOR} in docs/figures. Value over
     * replacement was taken on the unanchored rate and {@code PTS} on the anchored one, so the two were on
     * different scales and {@code VALUE} carried the difference between positions. Prices never did, which
     * is why it went unseen until the kicker market turned on {@code VALUE}.
     *
     * Asserted with the flex pinned shut, since allocating it compares positions against each other and
     * would legitimately move if one of them doubled.
     */
    def "doubling a position's points changes no price, a share being a ratio"() {
        given: 'two positions, and the same two with every receiver season worth twice as much'
        Map<Integer, List<BigDecimal>> shape = (1..12).collectEntries { int rank ->
            BigDecimal expected = (240 - rank * 12) as BigDecimal
            [(rank): [expected, expected * 0.5, expected * 1.5] * 3]
        }
        Map<Integer, List<BigDecimal>> doubled = shape.collectEntries { int rank, List<BigDecimal> seasons ->
            [(rank): seasons.collect { it * 2 }]
        }
        PointsCurve plain = PointsCurve.of([QB: TestSeasons.byRank(shape), WR: TestSeasons.byRank(shape)])
        PointsCurve scaled = PointsCurve.of([QB: TestSeasons.byRank(shape), WR: TestSeasons.byRank(doubled)])

        and: 'a lineup with no flex, so nothing reallocates a starting slot between the two'
        StarterRequirements fixed = new StarterRequirements([QB: 1, WR: 2], [QB: 1, WR: 2], 3, 10)
        Map<String, List> pool = (1..6).collectMany { int rank ->
            [["q$rank" as String, ["QB $rank" as String, 'QB', rank, null]],
             ["w$rank" as String, ["WR $rank" as String, 'WR', rank, null]]]
        }.collectEntries { [(it[0]): it[1]] }

        when:
        List<PlayerValuation> before = AuctionValuation.value(plain, fixed, pool, [QB: 20, WR: 20],
                400.0, 12, new ByeWeeks([:], 14))
        List<PlayerValuation> after = AuctionValuation.value(scaled, fixed, pool, [QB: 20, WR: 20],
                400.0, 12, new ByeWeeks([:], 14))

        then: 'the receivers really are worth twice as much, so there is something here that could leak'
        scaled.seasonPoints('WR', 3) > plain.seasonPoints('WR', 3) * 1.9

        and: 'and every price is the same to the dollar, at both positions'
        before.every { PlayerValuation player ->
            player.marketSalary == after.find { it.playerId == player.playerId }.marketSalary
        }
    }

    /**
     * The last constant on this class that nothing recomputed, and it had drifted.
     *
     * It is a measurement like the rest — how often an expiring contract of a given band actually changed
     * hands — so it is checked against the seasons it was measured from. The top band was carried at 0.26
     * against a record of 0.30, which is the difference between saying a top-twelve player is retained
     * three times in four and seven times in ten.
     */
    def "availability is what the record says an expiring contract of each band did"() {
        given:
        List<Integer> boundaries = AuctionValuation.AVAILABILITY.collect { it[0] as int }
        List<AuctionSpend.Retention> measured =
                AuctionSpend.retention(AuctionSpend.SUPERFLEX_SEASONS, boundaries)

        expect: 'every band within a point of what the seasons behind it actually did'
        measured.every { AuctionSpend.Retention band ->
            BigDecimal carried = AuctionValuation.AVAILABILITY.find { it[0] == band.throughRank }[1]
            (band.movedShare - carried).abs() < 0.01
        }

        and: 'each band resting on enough contracts to be a rate rather than an anecdote'
        measured.every { it.signed >= 40 }
    }

    /**
     * Why the deepest band does not continue the fall, which read as an anomaly while the denominator went
     * unstated.
     *
     * Availability is measured over the contracts somebody re-signed, because that is the question a bidder
     * asks. Measured over every contract that expired it falls away steadily with rank instead — and the
     * difference between the two readings is that a deep contract is usually re-signed by nobody at all.
     */
    def "the deep band comes back up only because most of it is never signed at all"() {
        given:
        List<AuctionSpend.Retention> measured = AuctionSpend.retention(AuctionSpend.SUPERFLEX_SEASONS,
                AuctionValuation.AVAILABILITY.collect { it[0] as int })

        expect: 'on the constant\'s own denominator the deepest band is no more available than the middle'
        measured.last().movedShare < measured[2].movedShare

        and: 'while against every expiring contract it is much the least available of the four'
        measured.last().movedShareOfExpiring < measured.collect { it.movedShareOfExpiring }.max() / 2

        and: 'which is that reading collapsing: the top band is nearly always re-signed and the last rarely'
        measured.first().signedShare > 0.9
        measured.last().signedShare < 0.5
    }

    /**
     * The one depth in the model set by hand, held to the record it was set from.
     *
     * Every other position is bounded by the relevance floor, which never fires at kicker because the curve
     * there is nearly flat. So kicker is bounded by what the league actually pays for — and 25 is not a
     * round number chosen for looking sensible, it is exactly the deepest rank ever signed. Asserted as an
     * equality for that reason: a hand-set constant that merely happens to be near the record is the kind
     * that drifts, and this one is answerable to a number the seasons can produce.
     */
    def "the kicker depth is exactly the deepest kicker the league has ever paid for"() {
        given:
        AuctionSpend.Depth kickers = AuctionSpend.depth(AuctionSpend.SUPERFLEX_SEASONS).PK

        expect:
        kickers.deepest == PointsCurve.DEPTH_CAP.PK

        and: 'and it covers all but a handful of the kickers anybody actually rosters'
        kickers.rosteredWithin(PointsCurve.DEPTH_CAP.PK) > 0.95
    }

    /**
     * Why the four hand-set depths that preceded the relevance floor had to go.
     *
     * They were QB 30, RB 45, WR 50 and TE 25, and the league has signed deeper than every one of them —
     * which is the whole argument for taking the depth off the curve instead of writing it down.
     */
    def "the league signs deeper than the hand-set depths the curve replaced"() {
        given:
        Map<String, AuctionSpend.Depth> measured = AuctionSpend.depth(AuctionSpend.SUPERFLEX_SEASONS)

        expect:
        [QB: 30, RB: 45, WR: 50, TE: 25].every { String position, int wasCappedAt ->
            measured[position].deepest > wasCappedAt
        }
    }

    /**
     * The evidence for reporting team context rather than pricing it.
     *
     * {@code -t teams} deliberately moves no price. That decision rests on a correlation which was, until
     * this, a figure in two javadoc comments and a line of prose that nothing recomputed — and it is the
     * kind of figure a later reader would reasonably want to overturn, since a strong relation would mean a
     * team's situation belongs in the price after all. Asserted as weak rather than as any exact value: the
     * claim being made is that there is nothing here to price.
     */
    def "how stretched a team is barely predicts how much of its roster it keeps"() {
        given:
        List<AuctionSpend.TeamSeason> teams = AuctionSpend.teamSeasons(AuctionSpend.CALIBRATED_SEASONS)

        expect: 'a relation far too weak to price, over every team season in the calibrated span'
        AuctionSpend.stretchAgainstKept(teams).abs() < 0.25

        and: 'and the spread of team states is real, which is why it is worth reporting at all'
        List<BigDecimal> stretch = teams.findAll { it.freeCap > 0 }.collect { it.stretch }
        stretch.min() < 0.5
        stretch.max() > 1.5
    }

    /** Measured and reported, never calibrated on: the case for dropping it has to be checkable. */
    def "2022 is the outlier the calibration excludes, and by a distance"() {
        given:
        Map<String, BigDecimal> transition =
                AuctionSpend.shareByPosition([AuctionSpend.of('2022')], AuctionSpend.EXCLUDING_KICKERS)
        List<Map<String, BigDecimal>> since = AuctionSpend.CALIBRATED_SEASONS.collect {
            AuctionSpend.shareByPosition([AuctionSpend.of(it)], AuctionSpend.EXCLUDING_KICKERS)
        }

        expect: 'wide receiver took more of that auction than of any season since, by a wide margin'
        transition.WR > since.collect { it.WR }.max() + 0.15

        and: 'and quarterback less than any of them'
        transition.QB < since.collect { it.QB }.min()
    }
}
