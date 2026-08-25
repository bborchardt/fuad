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

    /** What keeping gains over spending the pick. Negative means the pick is worth more than the player. */
    BigDecimal surplus() { keeperValue - alternativeValue }

    /** Whether the prior season's draft round permits this player to be kept at this price. */
    boolean eligible

    /** The round he was drafted in last season, or null where he was not drafted at all. */
    Integer priorRound
}
