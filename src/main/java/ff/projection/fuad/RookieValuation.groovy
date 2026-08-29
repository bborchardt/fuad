package ff.projection.fuad

import ff.data.PlayerValuation
import ff.data.fuad.FuadPlayer
import ff.data.fuad.RookieValue
import ff.load.fuad.RookieDynastyIndex
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
     * Standard errors either side of a level, and how much of a normal sits at each, for integrating over it.
     *
     * <b>What a rookie is worth is not what his level's midpoint is worth.</b> Value over replacement is
     * convex — it is {@code max(0, points - replacement)} summed weekly, so the upside of a level being
     * higher than estimated outruns the downside of it being lower. Pricing at the point estimate therefore
     * understates the expectation, and understates it in proportion to how badly the level is pinned down.
     *
     * That bias is not spread evenly. It is worth a per cent or two at running back and receiver, where nine
     * classes level a rank tightly, and 17% to 25% at quarterback and tight end, where they do not — which
     * is to say it was quietly marking down exactly the positions the board is least sure about, and a
     * reader sorting on the number would have dropped them.
     *
     * Five points weighted by the normal density at each, which is enough for a smooth convex function and
     * cheap enough to run five times a contract year.
     */
    private static final List<List<BigDecimal>> LEVEL_ERRORS =
            [[-2.0g, 0.055g], [-1.0g, 0.244g], [0.0g, 0.402g], [1.0g, 0.244g], [2.0g, 0.055g]].asImmutable()

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
     * Every ranked rookie, valued over the contract the league would give him.
     *
     * @param seasons      the rookie curves, one per contract year
     * @param outcomes     how widely a rookie rank's seasons run, measured on rookies
     * @param dynasty      what the dynasty ranking adds to a rookie's level, where it adds anything
     * @param replacement  weekly replacement level per position, from the veteran board's own curve
     * @param board        this season's priced board, which turns value over replacement into dollars
     * @param baselines    the position baselines bylaw 8.3 decays from, for the season being drafted
     * @param expectedPick rookie overall rank to the pick the league's drafts say it goes at
     * @param rookies      the ranked rookies themselves, in consensus order
     * @param byeByTeam    which week each NFL team is off, since a rookie ranking does not carry it
     */
    static List<RookieValue> value(RookieSeasons seasons,
                                   RookieOutcomes outcomes,
                                   RookieDynastyIndex dynasty,
                                   Map<String, Map<Integer, BigDecimal>> replacement,
                                   List<PlayerValuation> board,
                                   Map<String, Integer> baselines,
                                   Map<Integer, Integer> expectedPick,
                                   Collection<FuadPlayer> rookies,
                                   Map<String, Integer> byeByTeam,
                                   int lastWeek) {
        Map<String, List<List<BigDecimal>>> priceCurve = pricesByPosition(board)
        ByeWeeks byes = rookieByes(rookies, byeByTeam, lastWeek)

        List<RookieValue> valued = rookies
                .findAll { FuadPlayer rookie -> rookie.rookieRank && replacement.containsKey(rookie.player.position) }
                .sort { FuadPlayer rookie -> rookie.rookieRank.overallRank }
                .collect { FuadPlayer rookie ->
                    String position = rookie.player.position
                    int rank = rookie.rookieRank.positionRank

                    // One year's worth at a given number of standard errors on the level.
                    def priced = { int contractYear, BigDecimal errors ->
                        ExpectedValue.expectedValueOverReplacement(
                                rateOf(rookie, seasons, dynasty, contractYear) *
                                        errorFactor(seasons, position, rank, contractYear, errors),
                                outcomes.of(position, rank, contractYear),
                                replacement[position] ?: [:],
                                byes.of(position, rank), byes.lastWeek)
                    }
                    // Integrated over the level's own error rather than taken at its midpoint. See
                    // LEVEL_ERRORS: the two are not the same number where the function is convex.
                    List<Integer> value = (1..RookieSeasons.CONTRACT_YEARS).collect { int contractYear ->
                        LEVEL_ERRORS.sum { List<BigDecimal> point ->
                            point[1] * priceOf(priced(contractYear, point[0]), priceCurve[position])
                        } as int
                    }
                    // The same contract at a standard error either side, which is the band it carries.
                    def bounded = { BigDecimal direction ->
                        (1..RookieSeasons.CONTRACT_YEARS)
                                .collect { int contractYear -> priceOf(priced(contractYear, direction), priceCurve[position]) }
                                .sum() as int
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
                            valueByYear: value,
                            expectedPick: pick,
                            salary: salary,
                            contractLength: length,
                            surplus: (0..<length).sum { int year -> value[year] - salary } as int,
                            valueLow: bounded(-1.0g),
                            valueHigh: bounded(1.0g))
                }
        tiered(valued)
    }

    /**
     * Group each position's rookies into bands the evidence cannot tell apart.
     *
     * The same rule the auction board tiers ranks by: walk in order of what they are worth, keep a rookie in
     * the current tier while he sits within one standard error of the <b>best</b> in it, and open a new tier
     * when he falls further. Measured against the tier's best rather than its previous member, so a chain of
     * individually small steps cannot drift a tier arbitrarily wide.
     *
     * Tiered on {@link RookieValue#getContractValue} rather than on surplus, because surplus carries the
     * salary and the salary is a fact about a pick. Two rookies the model cannot separate as players should
     * read as ties whatever they will cost.
     *
     * The error compared against is the <b>upside</b> one: a rookie stays in the tier while his own high
     * bound reaches the best value in it, which is the reading that asks whether he could be as good.
     */
    private static List<RookieValue> tiered(List<RookieValue> valued) {
        Map<String, Integer> tiers = [:]
        valued.groupBy { it.position }.each { String position, List<RookieValue> atPosition ->
            int tier = 0
            Integer best = null
            atPosition.sort { RookieValue a, RookieValue b ->
                (b.contractValue <=> a.contractValue) ?: (a.overallRank <=> b.overallRank)
            }.each { RookieValue rookie ->
                if (best == null || best - rookie.contractValue > rookie.valueHigh - rookie.contractValue) {
                    tier++
                    best = rookie.contractValue
                }
                tiers[rookie.playerId] = tier
            }
        }
        valued.collect { RookieValue rookie -> rookie.copyWith(tier: tiers[rookie.playerId]) }
    }

    /**
     * What a rookie scores in a game he plays: his rookie rank's level, moved by what the dynasty index adds.
     *
     * <b>The rookie index sets the level and the dynasty index only moves it.</b> That division is what the
     * record supports. Holding rookie rank fixed, the dynasty ranking still predicts which rookies do better
     * — at the top of a class, strongly — but it cannot supply a level of its own, because nine classes
     * spread across forty dynasty ranks do not fill one. So it adjusts, in proportion to how far above or
     * below its usual place it has put him, and it is trusted less at every rank down the board.
     *
     * Class quality enters here and nowhere else: a weak class's best back carries a poor dynasty rank
     * against the rookies who have held that rank before him, and is levelled down without anybody grading
     * the class. See {@link RookieDynastyIndex}.
     *
     * Rate rather than season points, so that availability is left entirely to the outcomes.
     */
    private static BigDecimal rateOf(FuadPlayer rookie, RookieSeasons seasons, RookieDynastyIndex dynasty,
                                     int contractYear) {
        String position = rookie.player.position
        BigDecimal rate = seasons.curve(contractYear).levelledRate(position, rookie.rookieRank.positionRank)
        rate * dynasty.adjustment(position, rookie.rookieRank.positionRank,
                rookie.dynastyRank?.positionRank)
    }

    /**
     * How much a rank's level could be out by, as a factor on its rate.
     *
     * The curve's standard error is on season points and the rate is those points over expected games, so a
     * one error rise in the level is the same proportional rise in the rate. Where a rank carries no level
     * at all the factor is one, there being nothing to be uncertain about.
     */
    private static BigDecimal errorFactor(RookieSeasons seasons, String position, int rank, int contractYear,
                                          BigDecimal direction) {
        BigDecimal points = seasons.curve(contractYear).seasonPoints(position, rank)
        if (points <= 0) {
            return 1.0g
        }
        BigDecimal moved = 1.0g + direction * seasons.curve(contractYear).standardError(position, rank) / points
        moved > 0 ? moved : 0.0g
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
