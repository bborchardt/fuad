package ff.projection

import ff.data.PlayerValuation

/**
 * Price an auction: turn expected points into dollars, subject to the money that actually exists.
 *
 * The chain is points to value over replacement to dollars.
 *
 * <b>Replacement is worked out week by week, not over the season.</b> A team has to field a starting lineup
 * every week, so the player it would otherwise start is the best one available <i>that week</i>, and in a
 * week when six teams are on bye that is a worse player than the season table suggests. Season totals hide
 * this. Pricing it week by week moves about a point and a half of the league's total value onto
 * quarterbacks and running backs, whose replacements thin out most on bye weeks.
 *
 * <b>Dollars come from dividing a known pot.</b> Teams spend a fairly steady share of the cap they have
 * free, 65 to 85 per cent across the record, so the pot is knowable in advance. Every player who must be
 * signed is reserved a dollar, and what is left is shared out in proportion to value over replacement.
 * Prices therefore sum to the money available, which no curve fitted player by player will do.
 *
 * <b>Then it is pulled towards how this league actually bids.</b> Pure value over replacement puts 42 per
 * cent of the pot on running backs where the league has historically spent 31, and 26 per cent on wide
 * receivers where it has spent 39. {@link #MARKET_WEIGHT} decides how far to trust the theory against the
 * record; halfway, by default, because four seasons of shares are noisy enough that neither deserves the
 * whole vote.
 *
 * Franchise tags are settled last and change the answer, so the whole thing is iterated. See
 * docs/PROJECTION.md.
 */
class AuctionValuation {

    /**
     * Share of the pot each position has taken since the league repriced, 2023-2025.
     *
     * 2022 is deliberately excluded. Superflex arrived that year and the league had not adjusted to it yet:
     * wide receivers took 56.2% of the auction against 30.4% to 37.8% in every season since, and
     * quarterbacks 13.9% against 16.7% to 29.8%. Averaging it in drags wide receiver up by four points of
     * the pot and holds quarterback down.
     *
     * The repricing is real and not a stock of old contracts running off. Money already committed tells the
     * same story from the other side: quarterback contracts have gone 16% to 30% of committed salary since
     * 2022 and running back 43% to 20%, while wide receiver has stayed flat around 36-42%. Nothing is
     * expiring away, the league is simply paying for different positions.
     */
    static final Map<String, BigDecimal> MARKET_SHARE =
            [QB: 0.233, RB: 0.330, WR: 0.344, TE: 0.093, PK: 0.009].asImmutable() as Map<String, BigDecimal>

    /** 0 leaves value over replacement alone, 1 forces the historical shares exactly. */
    static final BigDecimal MARKET_WEIGHT = 1.0

    /**
     * How much steeper this league's prices run than value, within a position.
     *
     * Fitted as `price ~ value^gamma` across signings by consensus rank, over the same repriced seasons. Above one the market
     * pays more for the best at a position and less for the rest than their value warrants; below one it
     * spreads money more evenly than value does. Quarterback is much the steepest, which is the league
     * paying a premium for an elite starter that value over replacement will not produce on its own, since
     * superflex starts twenty of them and so sets a high replacement.
     *
     * This belongs to price and never to value: it is a description of behaviour, not of worth.
     */
    static final Map<String, BigDecimal> PRICE_STEEPNESS =
            [QB: 1.44, RB: 1.13, WR: 1.07, TE: 1.51, PK: 1.00].asImmutable() as Map<String, BigDecimal>

    /**
     * How often a tier of expiring contract has actually changed hands, 2022-2025.
     *
     * Expiring players are restricted: their team may match, and for the best players it usually does.
     * Retention runs at 74% inside the top twelve at a position against 41% in the twenties, so the
     * stickiness the rule creates lands almost entirely on the players worth wanting.
     */
    static final List<List> AVAILABILITY = [[12, 0.26], [24, 0.46], [40, 0.59], [Integer.MAX_VALUE, 0.47]]

    /** Roughly how often a franchised player has been prised away with pick compensation: six of 46. */
    static final BigDecimal TAGGED_AVAILABILITY = 0.13

    /**
     * Share of the pot that goes on rookies, who are drafted separately after the auction.
     *
     * Their prices are set by rule off the previous year's positional prices, and they come out very low:
     * the whole league spent $42, $52, $69 and $76 on them across 2022-2025, which is 3.0% to 4.0% of the
     * auction pot each year. That money is committed before any bidding and has to come off the top.
     */
    static final BigDecimal ROOKIE_BUDGET_SHARE = 0.035

    /**
     * Roster spots the rookie draft fills, as rounds times teams.
     *
     * Five rounds, and the picks are free to drop, but nearly all are kept: 38, 46, 52 and 49 rookies were
     * rostered at week 1 across 2022-2025 against 40, 45, 50 and 50 picks. Close enough to treat every
     * pick as a roster spot the auction does not have to fill.
     */
    static final int ROOKIE_ROUNDS = 5

    /**
     * Share of free cap the league spends at auction, the mean of the four superflex seasons.
     *
     * Counted over distinct players. The week 1 snapshots repeat a handful of roster rows verbatim, same
     * franchise and same salary, and summing rows rather than players counts those contracts twice and puts
     * this at 0.83. Cooper Kupp's 94 in 2022 is one of them. `AuctionValuationSpec` recomputes it from the
     * committed seasons so it cannot drift.
     */
    static final BigDecimal SPEND_RATE = 0.80

    private static final int MAX_TAG_ITERATIONS = 10

    /**
     * @param curve          expected points by position and consensus rank
     * @param requirements   the league's starting requirements, which set replacement level
     * @param available      players up for auction: id to [name, position, rank, franchiseId]
     * @param franchiseSalary  the franchise tag price at each position
     * @param freeCap        cap space the league has left after contracts already running
     * @param byes           when each rank is off, which is what makes replacement move week to week
     */
    static List<PlayerValuation> value(PointsCurve curve, StarterRequirements requirements,
                                       Map<String, List> available, Map<String, Integer> franchiseSalary,
                                       BigDecimal freeCap, int slotsToFill, ByeWeeks byes) {
        Map<String, Integer> starters = requirements.startersByPosition(
                curve.positions().collectEntries { String position ->
                    [(position): (1..curve.depth(position)).collect { curve.seasonPoints(position, it) }]
                })
        Map<String, Map<Integer, BigDecimal>> replacement = replacementByPosition(curve, starters, byes)

        Set<String> tagged = []
        List<PlayerValuation> valuations = []
        for (int i = 0; i < MAX_TAG_ITERATIONS; i++) {
            valuations = price(curve, replacement, available, franchiseSalary, freeCap, slotsToFill, tagged, byes)
            Set<String> next = predictTags(valuations)
            if (next == tagged) {
                break
            }
            tagged = next
        }
        valuations.collect { it.copyWith(franchiseTagged: tagged.contains(it.playerId)) }
                .sort { a, b -> b.salary <=> a.salary ?: a.playerName <=> b.playerName }
    }

    /**
     * One team, one tag, used on whichever expiring player it saves most against. A tag is only worth using
     * when the player would cost more in the auction than the tag does.
     */
    static Set<String> predictTags(List<PlayerValuation> valuations) {
        valuations.findAll { it.franchiseId && it.tagSurplus > 0 }
                .groupBy { it.franchiseId }
                .collect { franchise, held -> held.max { it.tagSurplus }.playerId }
                .toSet()
    }

    private static List<PlayerValuation> price(PointsCurve curve, Map<String, Map<Integer, BigDecimal>> replacement,
                                               Map<String, List> available, Map<String, Integer> franchiseSalary,
                                               BigDecimal freeCap, int slotsToFill, Set<String> tagged,
                                               ByeWeeks byes) {
        // Tagged players never reach the bidding, and their price leaves the pot with them.
        BigDecimal spentOnTags = available.findAll { id, p -> tagged.contains(id) }
                .collect { id, p -> franchiseSalary[p[1] as String] ?: 0 }
                .sum() ?: 0.0 as BigDecimal
        Map<String, List> bidFor = available.findAll { id, p -> !tagged.contains(id) }

        // Valued for everyone, including the tagged. What a tagged player would have gone for is what says
        // whether tagging them was worth it, and it is the only thing that can: their actual cost is the
        // tag price, which compared against itself would make every tag look pointless and none stable.
        Map<String, BigDecimal> vor = available.collectEntries { String id, List p ->
            [(id): expectedValueOverReplacement(curve, replacement, p[1] as String, p[2] as int, byes)]
        }
        Map<String, BigDecimal> priceShares = steepen(calibrate(vor, available), available)

        // What the auction pays, once the tagged are gone and their tag prices have left the pot with them.
        // Rookies never reach the auction, but their contracts and their roster spots are spoken for, so
        // both come off before anything is priced.
        BigDecimal pot = freeCap * SPEND_RATE * (1.0 - ROOKIE_BUDGET_SHARE) - spentOnTags
        int slots = Math.max(1, slotsToFill - tagged.size())
        BigDecimal biddingRate = clearingRate(pot, slots, bidFor.keySet(), priceShares)
        BigDecimal biddingShare = (bidFor.keySet().collect { priceShares[it] ?: 0.0 }.sum() ?: 0.0) as BigDecimal

        // Value is the same money divided by worth alone, with no regard for how this league behaves.
        BigDecimal valueRate = clearingRate(freeCap * SPEND_RATE * (1.0 - ROOKIE_BUDGET_SHARE),
                slotsToFill, available.keySet(), vor)

        available.collect { String id, List p ->
            String position = p[1] as String
            int rank = p[2] as int
            BigDecimal share = priceShares[id] ?: 0.0
            // A tagged player's market price is what he would have cost had his own team not tagged him,
            // with everyone else's tag still standing. That puts him back in the bidding and puts the money
            // his team would have spent tagging him back in the pot. Pricing him instead at a rate set by a
            // pool he was removed from, against a pot his tag had already been taken out of, inflates the
            // very best by a quarter: after the tagged are gone the top of the board is a large share of
            // what is left.
            BigDecimal rate = tagged.contains(id) && biddingShare + share > 0 ?
                    (pot + (franchiseSalary[position] ?: 0) - (slots + 1)) / (biddingShare + share) :
                    biddingRate
            int market = Math.max(1, (1 + rate * share) as int)
            int worth = Math.max(1, (1 + valueRate * (vor[id] ?: 0.0)) as int)
            String holder = p[3] as String
            // Restricted: the team holding an expiring contract may match, so an outside bid has to clear
            // what the player is worth to them, not just what the market would otherwise settle at.
            int acquire = holder ? Math.max(market, worth + 1) : market
            new PlayerValuation(
                    playerId: id,
                    playerName: p[0] as String,
                    position: position,
                    positionRank: rank,
                    points: curve.seasonPoints(position, rank),
                    valueOverReplacement: vor[id] ?: 0.0,
                    value: worth,
                    marketSalary: market,
                    acquisitionSalary: acquire,
                    availability: tagged.contains(id) ? TAGGED_AVAILABILITY :
                            holder ? availabilityFor(rank) : 1.0,
                    salary: tagged.contains(id) ? (franchiseSalary[position] ?: 1) : market,
                    franchiseSalary: franchiseSalary[position] ?: 0,
                    franchiseId: p[3] as String,
                    franchiseTagged: tagged.contains(id))
        }
    }

    /** How often a player of this rank actually reaches another team. */
    private static BigDecimal availabilityFor(int rank) {
        (AVAILABILITY.find { rank <= (it[0] as int) }[1]) as BigDecimal
    }

    /**
     * Bend each position's price curve to the steepness the league actually bids at, keeping that
     * position's total spend where the share calibration put it.
     */
    private static Map<String, BigDecimal> steepen(Map<String, BigDecimal> shares, Map<String, List> available) {
        Map<String, BigDecimal> bent = shares.collectEntries { String id, BigDecimal share ->
            BigDecimal gamma = PRICE_STEEPNESS[available[id][1] as String] ?: 1.0
            [(id): share > 0 ? Math.pow(share.toDouble(), gamma.toDouble()) as BigDecimal : 0.0]
        }
        Map<String, BigDecimal> before = [:].withDefault { 0.0 as BigDecimal }
        Map<String, BigDecimal> after = [:].withDefault { 0.0 as BigDecimal }
        shares.each { id, share ->
            String position = available[id][1] as String
            before[position] += share
            after[position] += bent[id]
        }
        bent.collectEntries { String id, BigDecimal share ->
            String position = available[id][1] as String
            [(id): after[position] > 0 ? share * before[position] / after[position] : share]
        }
    }

    /**
     * Dollars per unit of value, once each roster spot still to be filled has been reserved its minimum bid.
     *
     * Reserved per spot rather than per player in the pool, since the pool holds everyone who could be bid
     * on and only the spots actually get filled. Charging a dollar for every name on the board would take
     * hundreds off the pot for players nobody signs.
     */
    private static BigDecimal clearingRate(BigDecimal pot, int slots, Set<String> pool,
                                           Map<String, BigDecimal> shares) {
        BigDecimal totalShare = (pool.collect { shares[it] ?: 0.0 }.sum() ?: 0.0) as BigDecimal
        totalShare > 0 ? (pot - slots) / totalShare : 0.0
    }

    /** Pull each position's slice of value towards the slice the league has historically bought. */
    private static Map<String, BigDecimal> calibrate(Map<String, BigDecimal> vor, Map<String, List> available) {
        BigDecimal total = (vor.values().findAll { it > 0 }.sum() ?: 0.0) as BigDecimal
        if (total <= 0) {
            return vor
        }
        Map<String, BigDecimal> byPosition = [:].withDefault { 0.0 as BigDecimal }
        vor.each { id, v -> if (v > 0) byPosition[available[id][1] as String] += v }

        Map<String, BigDecimal> scale = byPosition.collectEntries { String position, BigDecimal positionVor ->
            BigDecimal modelShare = positionVor / total
            BigDecimal target = MARKET_SHARE[position]
            [(position): !target || modelShare <= 0 ? 1.0 :
                    (1.0 - MARKET_WEIGHT) + MARKET_WEIGHT * target / modelShare]
        }
        vor.collectEntries { String id, BigDecimal v ->
            [(id): v > 0 ? v * (scale[available[id][1] as String] ?: 1.0) : 0.0 as BigDecimal]
        }
    }

    /**
     * Value over replacement, averaged over how the season might actually go.
     *
     * A season is replayed against every realised-over-expected ratio the position has produced, and the
     * value over replacement averaged across them. Taking the expectation at the end rather than the start
     * is the whole point: a player projected level with replacement is worth nothing at his average and a
     * good deal in the seasons he beats it, and only the second reading gives a bench any value at all.
     *
     * Replacement itself is left at its expectation. It is the best of whoever is left at a position rather
     * than one player's season, so it moves far less than any individual does.
     */
    static BigDecimal expectedValueOverReplacement(PointsCurve curve,
                                                   Map<String, Map<Integer, BigDecimal>> replacement,
                                                   String position, int rank, ByeWeeks byes) {
        Map<Integer, BigDecimal> weekly = curve.weeklyPoints(position, rank, byes.of(position, rank), byes.lastWeek)
        Map<Integer, BigDecimal> against = replacement[position] ?: [:]
        List<Double> outcomes = curve.outcomeMultipliers(position)
        if (!weekly) {
            return 0.0
        }
        if (!outcomes) {
            return valueOverReplacement(weekly, against, 1.0d)
        }
        BigDecimal total = outcomes.collect { double multiplier ->
            valueOverReplacement(weekly, against, multiplier)
        }.sum() as BigDecimal
        total / outcomes.size()
    }

    private static BigDecimal valueOverReplacement(Map<Integer, BigDecimal> weekly,
                                                   Map<Integer, BigDecimal> against, double multiplier) {
        (weekly.collect { int week, BigDecimal points ->
            BigDecimal over = points * multiplier - (against[week] ?: 0.0)
            over > 0 ? over : 0.0
        }.sum() ?: 0.0) as BigDecimal
    }

    /** For each week, the points of the best player at a position who would not be started that week. */
    private static Map<String, Map<Integer, BigDecimal>> replacementByPosition(PointsCurve curve,
                                                                               Map<String, Integer> starters,
                                                                               ByeWeeks byes) {
        curve.positions().collectEntries { String position ->
            Map<Integer, List<BigDecimal>> weekly = [:].withDefault { [] }
            (1..curve.depth(position)).each { int rank ->
                curve.weeklyPoints(position, rank, byes.of(position, rank), byes.lastWeek).each { int week, BigDecimal points ->
                    if (points > 0) {
                        weekly[week] << points
                    }
                }
            }
            int started = starters[position] ?: 0
            [(position): weekly.collectEntries { int week, List<BigDecimal> points ->
                List<BigDecimal> sorted = points.sort(false).reverse()
                [(week): sorted.size() > started ? sorted[started] : 0.0 as BigDecimal]
            }]
        }
    }
}
