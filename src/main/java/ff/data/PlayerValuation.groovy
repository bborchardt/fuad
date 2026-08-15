package ff.data

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/** What a player is worth in an auction, and what the league's rules let a team pay instead. */
@CompileStatic
@Immutable(copyWith = true)
class PlayerValuation {

    String playerId
    String playerName
    String position
    /** Consensus positional rank going into the season. */
    int positionRank
    /** Expected points over the regular season, after the realisation correction. */
    BigDecimal points
    /** Points above the player who would start in their place, summed week by week. */
    BigDecimal valueOverReplacement
    /**
     * What the player is worth: value over replacement priced against the cap, with no adjustment for how
     * this league behaves. What a rational league would pay.
     */
    int value
    /** What open bidding here is expected to pay, whether or not the player reaches it. */
    int marketSalary
    /**
     * What it takes to prise the player off the team that holds him.
     *
     * An expiring contract is a restricted free agent: the incumbent may match the winning bid, so an
     * outside bidder has to clear what the player is worth to them, not merely what the market clears at.
     * For a player nobody holds this is just the market price.
     */
    int acquisitionSalary
    /** Rough chance this player reaches another team at all, from how often his tier has changed hands. */
    BigDecimal availability
    /** What their team will actually pay: the tag price if tagged, otherwise the market price. */
    int salary
    /** What this position's franchise tag costs, which is a ceiling on what a tag holder need pay. */
    int franchiseSalary
    /** The team holding the expiring contract, or null for a player nobody rosters. */
    String franchiseId
    /** True when the model expects this player to be tagged rather than reach open bidding. */
    boolean franchiseTagged

    /**
     * What a tag saves against open bidding. Positive means the tag is the cheaper way to keep them.
     *
     * Measured against the market price rather than what they end up costing, since a tagged player's cost
     * is the tag price by definition and comparing that to itself would always come out at zero.
     */
    int getTagSurplus() { marketSalary - franchiseSalary }

    /**
     * Value less the price the market is expected to settle at: is this player worth what he will cost.
     *
     * This is the reading to use on a player already yours, where the choice is to re-sign at the market
     * price or let him go. For someone else's restricted free agent, compare against
     * {@link #acquisitionSalary} instead, and expect the answer to be no.
     *
     * Right of first refusal makes that a near certainty rather than an opinion. A player worth more than
     * he clears at is simply matched by the team holding him, so prising him loose costs his full worth and
     * the surplus stays with them. A player worth less is let go, and you have overpaid. Positive edge on
     * another team's restricted free agent is not hard to find, it is arithmetically unavailable.
     */
    int getEdge() { value - marketSalary }

    /** What the incumbent's right to match costs an outside bidder, over the market price. */
    int getRestrictionPremium() { acquisitionSalary - marketSalary }

    /**
     * The edge as a band rather than a number. It is the difference of two noisy estimates, so its error is
     * larger than either, and a dollar figure would imply a precision that is not there.
     */
    String getEdgeBand() {
        int threshold = Math.max(5, (marketSalary * 0.25) as int)
        edge >= threshold ? 'BARGAIN' : edge <= -threshold ? 'OVERPRICED' : 'fair'
    }
}
