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

    /** The best rank at the keeper's own position that the pick would typically have returned. */
    Integer positionalAlternativeRank

    /** What that rank is worth, which is the alternative when the slot has to be filled from the position. */
    BigDecimal positionalAlternativeValue

    /**
     * What keeping gains over the best player at the same position the pick would have returned.
     *
     * <b>This is the reading to use when the keeper is a starter.</b> The other two price the pick at the
     * best player available of any position, and this league caps a team at one quarterback, two tight ends,
     * one kicker and one defence — so the best player left is frequently one the owner cannot field. A
     * forfeited pick priced at a second quarterback nobody can start overstates what was given up by the
     * whole difference, and it is enough to turn a positive decision negative.
     *
     * It is the other end of a bracket rather than the answer. An owner who is set at the position really
     * would spend the pick elsewhere, and for him the measured reading is right.
     */
    BigDecimal positionalSurplus() {
        positionalAlternativeValue == null ? null : keeperValue - positionalAlternativeValue
    }

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
