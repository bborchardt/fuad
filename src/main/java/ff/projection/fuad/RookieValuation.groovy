package ff.projection.fuad

import ff.data.PlayerValuation
import ff.data.fuad.FuadPlayer
import ff.data.fuad.RookieValue
import ff.load.fuad.RookieSeasons
import ff.projection.ByeWeeks
import ff.load.util.NflTeams
import ff.projection.ExpectedValue
import ff.projection.PointsCurve

/**
 * Price a rookie draft: what each pick is worth over the contract it comes with, against what it costs.
 *
 * <b>This is the auction's arithmetic run over a longer horizon and a fixed price.</b> The board asks what a
 * player is worth for one season and what open bidding will charge for him. Neither half transfers: a rookie
 * is signed for up to five years by bylaw 2.2, and his salary is set by rule at the moment he is picked
 * rather than bid up by anyone. So the same points over replacement are computed five times against
 * {@link RookieSeasons}, and the cost comes from {@link RookieSalary}.
 *
 * Three things are assumed, none of them small, and all of them stated in docs/fuad/PROJECTION.md:
 *
 * <ul>
 * <li><b>A future season prices like this one.</b> Points over replacement in year four are converted to
 * dollars through the board being priced now, since nothing can know that year's cap, pool or ranking. What
 * this assumes is that the <i>price of a given amount of value</i> holds, not that any player or any rank
 * holds — and the league's cap has moved once in nine years.</li>
 * <li><b>Replacement is this season's.</b> The player a rookie would displace four years out is levelled
 * from the veteran curve as it stands, for the same reason.</li>
 * <li><b>No discount is applied to a later year.</b> A dollar of surplus in year four counts as a dollar,
 * because the league's own currency does not carry interest: cap space cannot be saved between seasons, so
 * a dollar next year is not a dollar this year invested. What the horizon <i>does</i> cost is that the later
 * years are levelled off fewer classes, which {@link RookieSeasons#classesBehind} reports rather than
 * discounts.</li>
 * </ul>
 */
class RookieValuation {

    /** Below this many dollars of value a year is not worth committing a contract year to. */
    private static final int WORTH_A_YEAR = 0

    /**
     * Every ranked rookie, valued over the contract the league would give him.
     *
     * @param seasons      the rookie curves, one per contract year
     * @param veteran      the board's own curve, which supplies each position's outcome spread
     * @param replacement  weekly replacement level per position, from the veteran board's own curve
     * @param board        this season's priced board, which turns value over replacement into dollars
     * @param baselines    the position baselines bylaw 8.3 decays from, for the season being drafted
     * @param expectedPick rookie overall rank to the pick the league's drafts say it goes at
     * @param rookies      the ranked rookies themselves, in consensus order
     * @param byeByTeam    which week each NFL team is off, since a rookie ranking does not carry it
     */
    static List<RookieValue> value(RookieSeasons seasons,
                                   PointsCurve veteran,
                                   Map<String, Map<Integer, BigDecimal>> replacement,
                                   List<PlayerValuation> board,
                                   Map<String, Integer> baselines,
                                   Map<Integer, Integer> expectedPick,
                                   Collection<FuadPlayer> rookies,
                                   Map<String, Integer> byeByTeam,
                                   int lastWeek) {
        Map<String, List<List<BigDecimal>>> priceCurve = pricesByPosition(board)
        ByeWeeks byes = rookieByes(rookies, byeByTeam, lastWeek)

        rookies.findAll { FuadPlayer rookie -> rookie.rookieRank && replacement.containsKey(rookie.player.position) }
                .sort { FuadPlayer rookie -> rookie.rookieRank.overallRank }
                .collect { FuadPlayer rookie ->
                    String position = rookie.player.position
                    int rank = rookie.rookieRank.positionRank

                    List<BigDecimal> vor = (1..RookieSeasons.CONTRACT_YEARS).collect { int contractYear ->
                        ExpectedValue.expectedValueOverReplacement(
                                seasons.curve(contractYear), veteran, replacement, position, rank, byes)
                    }
                    List<Integer> value = vor.collect { BigDecimal points ->
                        priceOf(points, priceCurve[position])
                    }

                    Integer pick = expectedPick[rookie.rookieRank.overallRank]
                    int salary = RookieSalary.salary(baselines[position] ?: RookieSalary.MINIMUM_SALARY,
                            (pick ?: 1) - 1)
                    int length = contractLength(value, salary)

                    new RookieValue(
                            playerId: rookie.mflId,
                            playerName: rookie.player.name,
                            position: position,
                            overallRank: rookie.rookieRank.overallRank,
                            positionRank: rank,
                            nflTeam: rookie.player.team,
                            bye: byeOf(rookie, byeByTeam),
                            pointsOverReplacement: vor,
                            valueByYear: value,
                            expectedPick: pick,
                            salary: salary,
                            contractLength: length,
                            surplus: (0..<length).sum { int year -> value[year] - salary } as int)
                }
    }

    /**
     * How many years are worth signing: every year the player is expected to be worth more than his salary.
     *
     * <b>The choice is made once, before any of it is known, which is why it is made this way.</b> Bylaw
     * 12.4 wants a length by the cut down date of the year he is drafted, so a team commits to year five
     * without seeing year one. The rule here is the expectation and nothing cleverer, and at a dollar it
     * almost always says five — which is the finding rather than a defect of the rule. Cutting later costs a
     * dollar a year remaining by bylaw 9.1, so the downside of five years at the minimum is five dollars
     * against a surplus that runs to three figures.
     *
     * Years are taken in order and stop at the first that does not pay, since a contract is a run of years
     * and not a selection of them.
     */
    static int contractLength(List<Integer> valueByYear, int salary) {
        int length = 0
        for (int year = 0; year < valueByYear.size(); year++) {
            if (valueByYear[year] - salary <= WORTH_A_YEAR) {
                break
            }
            length = year + 1
        }
        Math.max(1, length)
    }

    /**
     * What the market charges for a given amount of value at a position, read off the board being priced.
     *
     * <b>Priced by equivalence rather than by a rate.</b> A rookie worth eleven points over replacement is
     * worth what a veteran worth eleven points over replacement costs, which is a reading the board already
     * makes and this need not repeat. A single dollars-per-point rate would be the same claim with the
     * board's shape thrown away, and the shape is the whole of what separates the top of a position from
     * its middle: the auction is steepened deliberately, so the top of the board is worth more per point
     * than the bottom.
     *
     * Between two priced players the price is interpolated. Above the most valuable, it is extended at the
     * rate of the top pair rather than held flat, since a rookie can be worth more than anyone the auction
     * has left — the very best players are under contract or tagged and never reach the board at all.
     */
    static int priceOf(BigDecimal pointsOverReplacement, List<List<BigDecimal>> priced) {
        if (!priced || pointsOverReplacement <= 0) {
            return 0
        }
        List<BigDecimal> below = null
        for (List<BigDecimal> point : priced) {
            if (point[0] >= pointsOverReplacement) {
                return interpolate(pointsOverReplacement, below, point)
            }
            below = point
        }
        // Past the top of the board: extend the last segment rather than flattening it.
        List<BigDecimal> top = priced.last()
        List<BigDecimal> next = priced.size() > 1 ? priced[priced.size() - 2] : null
        BigDecimal rate = next && top[0] > next[0] ?
                (top[1] - next[1]) / (top[0] - next[0]) : top[1] / (top[0] ?: 1.0g)
        Math.max(0, (top[1] + rate * (pointsOverReplacement - top[0])) as int)
    }

    private static int interpolate(BigDecimal points, List<BigDecimal> below, List<BigDecimal> above) {
        if (!below) {
            // Nothing on the board is worth this little, so scale the cheapest player down to it.
            return Math.max(0, (above[1] * (above[0] > 0 ? points / above[0] : 0.0g)) as int)
        }
        BigDecimal span = above[0] - below[0]
        BigDecimal share = span > 0 ? (points - below[0]) / span : 0.0g
        Math.max(0, (below[1] + share * (above[1] - below[1])) as int)
    }

    /**
     * The board's own value to price pairs at each position, in increasing order of value.
     *
     * Market salary rather than value, since the question a pick answers is what it would cost to buy this
     * production instead. Value is what a rational league would pay and market salary is what this one
     * does, and a rookie is being weighed against money that has to be spent here.
     */
    private static Map<String, List<List<BigDecimal>>> pricesByPosition(List<PlayerValuation> board) {
        board.groupBy { it.position }.collectEntries { String position, List<PlayerValuation> priced ->
            [(position): priced.collect { [it.valueOverReplacement, it.marketSalary as BigDecimal] }
                    .sort { it[0] }]
        } as Map<String, List<List<BigDecimal>>>
    }

    /**
     * When each rookie is off, keyed the way the rookie curve is levelled.
     *
     * <b>A rookie has no bye in any ranking that carries him.</b> The dynasty export writes 0 against every
     * one of them, which is not week zero but no answer, and passing it through leaves a rookie playing
     * seventeen weeks where every veteran he is priced against plays sixteen — six per cent of a season,
     * handed to one side of the comparison for no reason but a missing column.
     *
     * His team's bye is the answer, and the redraft ranking has it for every team. The same week is used in
     * every contract year: no schedule exists for a season four years out, and which week a team is off is
     * close to arbitrary anyway. What matters is that a rookie loses one, as everybody does.
     *
     * Keyed by positional rookie rank rather than by consensus rank, since that is what the rookie curve
     * levels — the veteran board's byes would hand rookie RB1 the bye of the best running back in football.
     */
    private static Integer byeOf(FuadPlayer rookie, Map<String, Integer> byeByTeam) {
        byeByTeam[NflTeams.abbreviationOf(rookie.player.team) ?: rookie.player.team]
    }

    private static ByeWeeks rookieByes(Collection<FuadPlayer> rookies, Map<String, Integer> byeByTeam,
                                       int lastWeek) {
        Map<String, Map<Integer, Integer>> byes = [:].withDefault { [:] }
        rookies.each { FuadPlayer rookie ->
            Integer bye = byeOf(rookie, byeByTeam)
            if (rookie.rookieRank && bye) {
                byes[rookie.player.position][rookie.rookieRank.positionRank] = bye
            }
        }
        new ByeWeeks(byes, lastWeek)
    }
}
