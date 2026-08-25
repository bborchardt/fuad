package ff.data.greenfield

/**
 * One keeper decision, and what it is worth against the pick it costs.
 *
 * The surplus is <b>marginal</b>: what this owner gains by keeping, holding every other owner's keepers
 * fixed. It is not a figure that can be added up across the league, because two owners forfeiting adjacent
 * picks are both measured against the same next-best player and only one of them could have had him. That is
 * the right question for one owner deciding, and the wrong one for valuing the rule.
 */
class KeeperSurplus {

    String owner
    String player
    String position
    Integer positionRank

    /** The round of pick surrendered: 2 or 8. */
    int costRound

    /** Which pick of the draft that is, once the snake has been worked out. */
    int costPick

    /** Points over replacement the kept player is expected to return. */
    BigDecimal keeperValue

    /** The player consensus says would still be there at that pick, and what he is worth. */
    String alternative
    BigDecimal alternativeValue

    /** What keeping gains over spending the pick, if the pick is spent in consensus order. */
    BigDecimal surplus() { keeperValue - alternativeValue }

    /**
     * The best player this league has actually left on the board at that pick, over nine drafts.
     *
     * Null where no draft reached the pick. See {@code DraftHistory}.
     */
    BigDecimal measuredAlternativeValue

    /**
     * What keeping gains against the pick's measured worth rather than its assumed worth.
     *
     * <b>This is the lower of the two and the one an owner using this board should act on.</b> The consensus
     * reading asks what a drafter who follows the rankings would get; this asks what was actually still
     * there. They differ most in the late rounds, where this league reliably leaves a startable quarterback
     * sitting until round eight, so an eighth round pick is worth far more than its number suggests.
     */
    BigDecimal measuredSurplus() {
        measuredAlternativeValue == null ? null : keeperValue - measuredAlternativeValue
    }

    /** Whether the prior season's draft round permits this player to be kept at this price. */
    boolean eligible

    /** The round he was drafted in last season, or null where he was not drafted at all. */
    Integer priorRound
}
