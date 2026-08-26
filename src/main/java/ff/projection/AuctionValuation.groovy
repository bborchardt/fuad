package ff.projection

import ff.data.PlayerValuation

/**
 * Price an auction: turn expected points into dollars, subject to the money that actually exists.
 *
 * The chain is points to value over replacement to dollars, and only the last step is this class's.
 * {@link ExpectedValue} carries the first two, which describe the football and not the market: what a rank
 * is worth over the player who would replace it is the same question whatever a league bids with. What
 * follows here is the part that is true of this auction and of nothing else — a cap, a spend rate, the
 * shares this league's bidding has actually landed on, and franchise tags to settle.
 *
 * <b>Replacement is worked out week by week, not over the season.</b> A team has to field a starting lineup
 * every week, so the player it would otherwise start is the best one available <i>that week</i>, and in a
 * week when six teams are on bye that is a worse player than the season table suggests. Season totals hide
 * this. Pricing it week by week moves about a point and a half of the league's total value onto
 * quarterbacks and running backs, whose replacements thin out most on bye weeks.
 *
 * <b>Dollars come from dividing a known pot.</b> Teams spend a fairly steady share of the cap they have
 * free, 70 to 87 per cent across the measurable record, so the pot is knowable in advance. Every player who must be
 * signed is reserved a dollar, and what is left is shared out in proportion to value over replacement.
 * Prices therefore sum to the money available, which no curve fitted player by player will do.
 *
 * <b>Then it is pulled towards how this league actually bids.</b> Pure value over replacement puts far more
 * of the pot on running backs than this league spends, and far less on wide receivers.
 * {@link #MARKET_WEIGHT} decides how far to trust the theory against the record; it is 1.0, so
 * {@link #calibrate} forces the positional shares in {@link #MARKET_SHARE} exactly, and {@link #steepen}
 * bends each position's curve without disturbing its total.
 *
 * <b>What a position finally holds is a little off that target, and predictably so.</b> Every roster spot
 * still to be filled is reserved a dollar before anything is priced, and that reservation is handed out by
 * headcount rather than by worth, spread evenly over players whose value is anything but even. A position
 * carrying many cheap players therefore lands above its target and one carrying fewer dearer players below
 * it. Kicker is the clearest case, holding many of the board's players for very little of its money. The
 * figures are in docs/figures/&lt;year&gt;/positions.tsv as PLAYERS and RESERVE against SHARE and
 * TARGETSHARE.
 *
 * Franchise tags are settled last and change the answer, so the whole thing is iterated. See
 * docs/PROJECTION.md.
 */
class AuctionValuation {

    /**
     * Share of the pot each position has taken since the league repriced, 2023-2025.
     *
     * 2022 is deliberately excluded. Superflex arrived that year and the league had not adjusted to it yet:
     * wide receivers took far more of that auction than of any since, and quarterbacks far less. Averaging
     * it in drags wide receiver up and holds quarterback down. What each position took, season by season, is
     * in docs/figures/&lt;year&gt;/spend.tsv as SHARE and SHAREXPK — generated from the committed seasons and
     * checked against docs/PROJECTION.md, so the case for dropping the year is evidence a reader can check
     * rather than a number repeated here.
     *
     * The repricing is real and not a stock of old contracts running off. Money already committed tells the
     * same story from the other side, and it is generated alongside the auction shares as COMMITTEDSHARE on
     * docs/figures/&lt;year&gt;/spend.tsv: quarterback climbs across the same seasons the auction turns
     * towards it, running back falls, and receiver stays where it was. Nothing is expiring away, the league
     * is simply paying for different positions.
     *
     * <b>Shares of the whole auction, kickers included.</b> They used to be shares of what the four scoring
     * positions took, with a kicker entry on the other basis, so the map did not sum to one — which cost
     * nothing while it lasted, because a kicker had no curve, no value over replacement, and so never
     * entered the pool {@link #calibrate} normalises over. His entry was never read. Now that kickers are
     * levelled they are in that pool, the basis has to be one basis, and the map sums to one, which the
     * spec checks. What it summed to before was recorded in a commit this repository no longer has, the history
     * having been rewritten since.
     */
    static final Map<String, BigDecimal> MARKET_SHARE =
            [QB: 0.237, RB: 0.323, WR: 0.335, TE: 0.094, PK: 0.010].asImmutable() as Map<String, BigDecimal>

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
     *
     * <b>Of the contracts somebody re-signed, not of the contracts that expired.</b> That denominator is the
     * whole meaning of these numbers and went unwritten, which made the last band read as an anomaly: taken
     * against every expiring contract, availability falls away steadily with rank, because most deep players
     * are re-signed by nobody at all. Taken against the ones somebody did sign — which is the question a
     * bidder is actually asking — the deep band comes back up. Both readings are in
     * docs/figures/&lt;year&gt;/retention.tsv, and {@code AuctionSpend.retention} measures them from the
     * committed seasons so these four numbers cannot drift from the record they came from.
     */
    static final List<List> AVAILABILITY = [[12, 0.30], [24, 0.47], [40, 0.58], [Integer.MAX_VALUE, 0.46]]

    /** Roughly how often a franchised player has been prised away with pick compensation: six of 46. */
    static final BigDecimal TAGGED_AVAILABILITY = 0.13

    /**
     * Share of the pot that goes on rookies, who are drafted separately after the auction.
     *
     * Their prices are set by rule off the previous year's positional prices, and they come out very low —
     * a few per cent of the auction pot each year. That money is committed before any bidding and has to
     * come off the top. {@code AuctionValuationSpec} recomputes the share from the committed seasons, so
     * this constant cannot drift from what the league has actually spent on a rookie class.
     */
    static final BigDecimal ROOKIE_BUDGET_SHARE = 0.033

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
     * The fifth held back is deliberate, not slack to be bid away. Cap is what pays for in-season signings,
     * and what a team releasing a bad contract eats the penalty out of — charged to the current year and
     * cleared at the end of it, so unspent cap is also how a mistake is stopped from reaching next season.
     * A model assuming teams bid to the cap would price the whole board too high.
     *
     * <b>Counted over every player the auction paid for</b>, which is expiring contracts that came back and
     * also veterans who were on no pre-draft roster. The second group used to be left out, so the pot was
     * measured without money the board was nevertheless dividing among them — a small share of an auction
     * and a different one every year, but never nothing, and the model prices those players.
     * {@link AuctionSpend} tells them from an in-season pickup by the transaction log rather than by their
     * salary, and carries what they came to as {@code freeAgentShare} so the identification is checked
     * rather than trusted.
     *
     * Counted over distinct players, never over roster rows. The week 1 snapshots repeat a handful of rows
     * verbatim, same franchise and same salary, and summing rows counts those contracts twice; Cooper Kupp's
     * 94 in 2022 is one of them. <b>That double count also lands near 0.83 and has nothing to do with this
     * figure</b> — two different mistakes reaching one number, which is worth saying so nobody reads the
     * agreement as confirmation.
     *
     * `AuctionValuationSpec` recomputes it from the committed seasons so it cannot drift. See
     * docs/LEAGUE_RULES.md.
     */
    static final BigDecimal SPEND_RATE = 0.83

    private static final int MAX_TAG_ITERATIONS = 10

    /**
     * @param curve          expected points by position and consensus rank
     * @param requirements   the league's starting requirements, which set replacement level
     * @param available      players up for auction: id to [name, position, rank, franchiseId] and
     *                       optionally a dynasty rank, which is carried to the board and never priced
     * @param franchiseSalary  the franchise tag price at each position
     * @param freeCap        cap space the league has left after contracts already running
     * @param byes           when each rank is off, which is what makes replacement move week to week
     */
    static List<PlayerValuation> value(PointsCurve curve, StarterRequirements requirements,
                                       Map<String, List> available, Map<String, Integer> franchiseSalary,
                                       BigDecimal freeCap, int slotsToFill, ByeWeeks byes) {
        Map<String, Map<Integer, BigDecimal>> replacement =
                ExpectedValue.replacementLevels(curve, requirements, byes)

        // The set the board is priced with is the set the board must report. They come apart when the loop
        // runs out of rounds mid-cycle, and re-stamping the tags from the round after the last pricing left
        // a team told to tag one player while every price on the board assumed it had tagged another.
        Set<String> pricedWith = [] as Set<String>
        Set<String> tagged = [] as Set<String>
        List<PlayerValuation> valuations = []
        for (int i = 0; i < MAX_TAG_ITERATIONS; i++) {
            pricedWith = tagged
            valuations = price(curve, replacement, available, franchiseSalary, freeCap, slotsToFill, tagged, byes)
            tagged = predictTags(valuations)
            if (tagged == pricedWith) {
                break
            }
        }
        if (tagged != pricedWith) {
            warnUnsettled(pricedWith, tagged, valuations)
        }
        valuations.sort { a, b -> b.salary <=> a.salary ?: a.playerName <=> b.playerName }
    }

    /**
     * Say so when the tags never settle, rather than picking a round and letting it read as an answer.
     *
     * Two expiring players on one roster can each be the better tag once the other is tagged: taking one out
     * of the bidding puts his tag price back in the pot and lifts what the other would fetch, which flips
     * the saving back the other way. The cycle is real rather than a rounding artefact, and the surpluses
     * driving it sit inside the standard error the levels carry, so the model genuinely cannot separate
     * them.
     *
     * The board stays priced with the tags it reports. What it cannot do is claim the set is settled.
     */
    private static void warnUnsettled(Set<String> pricedWith, Set<String> next, List<PlayerValuation> valuations) {
        Map<String, PlayerValuation> byId = valuations.collectEntries { [(it.playerId): it] }
        List<String> contested = ((pricedWith - next) + (next - pricedWith)).collect { String id ->
            PlayerValuation player = byId[id]
            player ? "$player.playerName ($player.franchiseId)" as String : id
        }.sort()
        System.err.println("Franchise tags did not settle in $MAX_TAG_ITERATIONS rounds. The board is priced " +
                "with the tags it reports; these are the ones it cannot choose between: ${contested.join(', ')}")
    }

    /**
     * One team, one tag, used on whichever expiring player it saves most against. A tag is only worth using
     * when the player would cost more in the auction than the tag does.
     *
     * <b>A tie goes to the more valuable player.</b> Surpluses are whole dollars off levels carrying a
     * standard error of seven points or so, so two players tying is not rare and means only that the model
     * cannot separate what the tag saves on them. It can still separate what they are worth: {@code value}
     * is worth priced against the cap, with no adjustment for how this league bids, so the tie is broken on
     * the better contract rather than on the larger saving.
     *
     * This used to fall out of the order {@code available} happened to iterate in, which is neither a reason
     * nor stable. Where surplus and value both tie the id decides, so that the same board always predicts
     * the same tags — an arbitrary rule, but arbitrary and fixed beats arbitrary and varying.
     */
    static Set<String> predictTags(List<PlayerValuation> valuations) {
        valuations.findAll { it.franchiseId && it.tagSurplus > 0 }
                .groupBy { it.franchiseId }
                .collect { franchise, held ->
                    held.max { PlayerValuation a, PlayerValuation b ->
                        (a.tagSurplus <=> b.tagSurplus) ?: (a.value <=> b.value) ?: (a.playerId <=> b.playerId)
                    }.playerId
                }
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
            [(id): ExpectedValue.expectedValueOverReplacement(curve, replacement, p[1] as String, p[2] as int, byes)]
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
                    dynastyRank: p.size() > 4 ? p[4] as Integer : null,
                    points: curve.seasonPoints(position, rank),
                    pointsPerGame: curve.levelledRate(position, rank),
                    expectedGames: curve.expectedGames(position, rank),
                    tier: curve.tier(position, rank),
                    pointsLow: curve.seasonPoints(position, rank) * curve.outcomePercentile(position, ExpectedValue.LOW_OUTCOME),
                    pointsHigh: curve.seasonPoints(position, rank) * curve.outcomePercentile(position, ExpectedValue.HIGH_OUTCOME),
                    bye: byes.of(position, rank),
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

}
