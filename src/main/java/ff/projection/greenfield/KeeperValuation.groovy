package ff.projection.greenfield

import ff.data.greenfield.KeeperSurplus

/**
 * What each keeper is worth against the pick it costs.
 *
 * <b>The decision is a subtraction, and a snake draft makes it a clean one.</b> Forfeiting a pick costs
 * exactly the player who would have been taken with it and nothing else — no other pick moves, no budget is
 * disturbed. So keeping is worth {@code value(kept) - value(whoever would have been there)}, both in points
 * over replacement, and the two are read off the same board.
 *
 * That is cleaner than the auction's equivalent, where every dollar spent on one player changes what is left
 * for all the others and prices have to be settled against a pot.
 *
 * <b>Who would have been there is the assumption this rests on.</b> It is taken as consensus order: the
 * board minus everyone already kept, drafted top down, skipping the picks nobody owns any more. Real drafts
 * do not run in consensus order, and this league's own nine drafts are the evidence for how far it departs —
 * which is a better basis and is not built yet. Until it is, the alternative is the player the consensus
 * says is there, and a surplus of a few points is inside what that assumption can carry.
 *
 * <b>Eligibility is a rule about last season, not about this one.</b> A player drafted in round six or later
 * may be kept for an eighth, one drafted in round three or later for a second, and a player who went
 * undrafted for either. It says nothing about who drafted him, so a player traded midseason is keepable by
 * whoever holds him — which is why 21 of the 98 keepers in the record were kept by an owner who did not
 * draft them. See docs/GREENFIELD.md.
 */
class KeeperValuation {

    /**
     * The earliest prior-season round a player may have been drafted in and still be kept at each price.
     *
     * An undrafted player qualifies for either, which is what {@code null} prior round means below. This is
     * confirmed against every keeper in the record: 98 of them over eight seasons, none in violation.
     */
    static final Map<Integer, Integer> MINIMUM_PRIOR_ROUND = [2: 3, 8: 6].asImmutable()

    /**
     * Every keeper valued against the pick it costs.
     *
     * @param keepers      owner, player and cost round, as the league recorded them
     * @param slots        each owner's draft slot
     * @param valueOf      points over replacement for a named player
     * @param board        every draftable player, in consensus order
     * @param priorRounds  the round each player was drafted in last season, absent where he was not
     * @param teams        how many teams are in the snake
     */
    static List<KeeperSurplus> value(List<Map> keepers, Map<String, Integer> slots,
                                     Closure<BigDecimal> valueOf, List<String> board,
                                     Map<String, Integer> priorRounds, int teams) {
        Set<String> kept = keepers.collect { it.player as String } as Set
        List<String> pool = board.findAll { !kept.contains(it) }

        Set<Integer> forfeited = keepers.collect {
            SnakeDraft.overallPick(it.costRound as int, slots[it.owner as String], teams)
        } as Set

        keepers.collect { Map keeper ->
            String player = keeper.player as String
            int costRound = keeper.costRound as int
            int pick = SnakeDraft.overallPick(costRound, slots[keeper.owner as String], teams)
            String alternative = availableAt(pick, pool, forfeited)
            Integer prior = priorRounds[player]
            new KeeperSurplus(
                    owner: keeper.owner as String,
                    player: player,
                    position: keeper.position as String,
                    positionRank: keeper.positionRank as Integer,
                    costRound: costRound,
                    costPick: pick,
                    keeperValue: valueOf(player) ?: 0.0,
                    alternative: alternative,
                    alternativeValue: alternative ? (valueOf(alternative) ?: 0.0) : 0.0,
                    priorRound: prior,
                    eligible: eligible(costRound, prior))
        }.sort { -it.surplus() }
    }

    /**
     * Whether the prior season's draft round permits this price.
     *
     * A null prior round is a player who went undrafted, who may be kept at either price.
     */
    static boolean eligible(int costRound, Integer priorRound) {
        Integer minimum = MINIMUM_PRIOR_ROUND[costRound]
        minimum != null && (priorRound == null || priorRound >= minimum)
    }

    /**
     * The best player left when a pick comes round.
     *
     * A forfeited pick is not skipped over — nobody takes a player there, so the next pick inherits whoever
     * would have gone. That is why several owners surrendering adjacent picks are all measured against the
     * same player, and why these surpluses are marginal rather than additive.
     */
    private static String availableAt(int pick, List<String> pool, Set<Integer> forfeited) {
        int taken = (1..<pick).count { !forfeited.contains(it) }
        taken < pool.size() ? pool[taken] : null
    }
}
