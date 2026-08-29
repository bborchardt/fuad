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
 * <b>A rookie's level comes from two indices, not one.</b> The rookie ranking orders a class and says
 * nothing about how good the class is: "rookie RB1" is the best back of that year whether that is a
 * generational prospect or a committee back in a bare year, and nine of them are levelled as one object. The
 * dynasty ranking makes exactly the comparison the rookie ranking refuses to, placing a rookie against
 * established players, so it carries class quality — and it carries it per position, which is what a year
 * thin at one position and deep at another requires. The two order rookie outcomes about equally well, so
 * they are blended rather than one being chosen. See {@link #RATE_CALIBRATION}.
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
     * What a rookie scores per game against what the veteran curve gives his dynasty rank, by contract year.
     *
     * <b>A dynasty rank is a claim about a career and this board buys seasons</b>, so the two need joining
     * before they can be blended. Measured over 424 rookie seasons: a rookie plays at 79% of his dynasty
     * rank's rate in the year he is drafted and about 95% of it in every year after, which is the ranking
     * saying he will be good and being early rather than wrong.
     *
     * <b>Measured on rate and never on season totals.</b> The same figures taken over totals run 0.74, 0.91,
     * 0.89, 0.80 and 0.70 — the fall away in the last two years is rookies leaving the league, which is
     * availability and belongs to the rookie curve. Calibrating a rate with a number that already contains
     * availability, and then applying availability again, is the double count this model has made once
     * already. See {@link ff.projection.fuad.RookieOutcomes}.
     */
    static final List<BigDecimal> RATE_CALIBRATION =
            [0.79g, 0.95g, 0.97g, 0.94g, 0.93g].asImmutable() as List<BigDecimal>

    /**
     * How far the dynasty index counts against the rookie index in a rookie's level.
     *
     * Half, because neither is better. Over 462 rookies carrying both, they order the first season at
     * Spearman 0.631 and 0.626 and the best of the first three at 0.623 and 0.629 — a tie either way round.
     * Two estimators that are equally good and imperfectly correlated average to something better than
     * either, which is the second reason for a blend and the answer to a rookie rank varying more from year
     * to year than a veteran rank does.
     *
     * A rookie the dynasty ranking does not carry is levelled on the rookie index alone rather than being
     * dropped or given a default: not being ranked among the top few hundred dynasty assets is a fact about
     * a deep rookie, not a missing measurement, and the rookie index still has him.
     */
    static final BigDecimal DYNASTY_WEIGHT = 0.5g

    /**
     * Every ranked rookie, valued over the contract the league would give him.
     *
     * @param seasons      the rookie curves, one per contract year
     * @param outcomes     how widely a rookie rank's seasons run, measured on rookies
     * @param veteran      the board's own curve, which levels a rookie's dynasty rank against the league
     * @param replacement  weekly replacement level per position, from the veteran board's own curve
     * @param board        this season's priced board, which turns value over replacement into dollars
     * @param baselines    the position baselines bylaw 8.3 decays from, for the season being drafted
     * @param expectedPick rookie overall rank to the pick the league's drafts say it goes at
     * @param rookies      the ranked rookies themselves, in consensus order
     * @param byeByTeam    which week each NFL team is off, since a rookie ranking does not carry it
     */
    static List<RookieValue> value(RookieSeasons seasons,
                                   RookieOutcomes outcomes,
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
                                rateOf(rookie, seasons, veteran, contractYear),
                                outcomes.of(position, rank, contractYear),
                                replacement[position] ?: [:],
                                byes.of(position, rank), byes.lastWeek)
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
                            nflDraft: rookie.draft,
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
     * What a rookie scores in a game he plays, blended across the two indices that order him.
     *
     * The rookie index says what rookies at his rank in his class have scored; the dynasty index says what
     * the consensus thinks he is worth against the whole league, calibrated by
     * {@link #RATE_CALIBRATION} for the fact that it is pricing a career and this is one year of it. Class
     * quality enters here and nowhere else: a weak class's best back carries a poor dynasty rank and is
     * levelled down without anybody grading the class.
     *
     * Rate rather than season points, so that availability is left entirely to the outcomes.
     */
    private static BigDecimal rateOf(FuadPlayer rookie, RookieSeasons seasons, PointsCurve veteran,
                                     int contractYear) {
        String position = rookie.player.position
        BigDecimal rookieRate = seasons.curve(contractYear).levelledRate(position, rookie.rookieRank.positionRank)
        if (!rookie.dynastyRank) {
            return rookieRate
        }
        BigDecimal dynastyRate = veteran.levelledRate(position, rookie.dynastyRank.positionRank) *
                RATE_CALIBRATION[contractYear - 1]
        rookieRate * (1.0g - DYNASTY_WEIGHT) + dynastyRate * DYNASTY_WEIGHT
    }

    /**
     * The same rookie priced at a particular pick rather than at the one he is expected to go at.
     *
     * <b>A rookie's cost is a fact about the pick, not about him.</b> Bylaw 8.3 decays the baseline by every
     * selection already made, so taking the same player at 1.02 and at 2.02 are different transactions, and
     * a team weighing whether to reach has to see both. The board reports him at his expected pick because
     * that is the neutral reading; an outlook for one team reports him at that team's own picks.
     *
     * The contract length is recomputed too, since a year worth committing to at a dollar may not be at
     * twelve.
     */
    static RookieValue at(RookieValue rookie, int overallPick, Map<String, Integer> baselines) {
        int salary = RookieSalary.salary(baselines[rookie.position] ?: RookieSalary.MINIMUM_SALARY,
                overallPick - 1)
        int length = contractLength(rookie.valueByYear, salary)
        rookie.copyWith(
                expectedPick: overallPick,
                salary: salary,
                contractLength: length,
                surplus: (0..<length).sum { int year -> rookie.valueByYear[year] - salary } as int)
    }

    /**
     * How many years are worth signing: the length that leaves the most value over its cost.
     *
     * <b>Taken as the best total rather than as a run that stops at the first bad year.</b> That was the
     * first rule here and it is exactly backwards for the shape a rookie has. A rookie quarterback is worth
     * about what he costs in the season he is drafted and three or four times that in the two after it, so
     * stopping at a break-even first year signed Fernando Mendoza for one year and reported his contract as
     * worth nothing — against $172 of surplus sitting in years two to five. The stopping rule suits an asset
     * that declines, and this is an asset that grows.
     *
     * The choice is still made once, before any of it is known, because bylaw 12.4 wants the length by the
     * cut down date of the year he is drafted. At a dollar it almost always says five, and cutting later
     * costs a dollar a year remaining by bylaw 9.1, so the downside of being wrong is five dollars.
     */
    static int contractLength(List<Integer> valueByYear, int salary) {
        int best = 1
        int bestSurplus = valueByYear ? valueByYear[0] - salary : 0
        int running = bestSurplus
        for (int year = 1; year < valueByYear.size(); year++) {
            running += valueByYear[year] - salary
            if (running > bestSurplus) {
                bestSurplus = running
                best = year + 1
            }
        }
        best
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
