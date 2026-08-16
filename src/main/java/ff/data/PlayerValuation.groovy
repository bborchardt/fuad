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
    /** Consensus positional rank going into the season, from the redraft ranking the model is built on. */
    int positionRank
    /**
     * Consensus positional rank for the long run, or null where the dynasty ranking does not carry him.
     *
     * Reported and never priced. It says nothing about this season, which is what a salary buys, and the
     * model deliberately levels every rank on the redraft ranking alone. It is here because the length of a
     * contract is a second decision taken at the same moment as the price, and nothing else on the board
     * speaks to it. See docs/LEAGUE_RULES.md#contract-length.
     */
    Integer dynastyRank
    /** Expected points over the regular season: what this rank has historically been worth. */
    BigDecimal points
    /**
     * Points in a bad season and a good one, at the 10th and 90th percentile of realised outcomes.
     *
     * The spread is the <b>position's</b>, applied to this player's level: the ratios behind it are pooled
     * across ranks, so every quarterback gets the same proportional range around his own expectation. It
     * says how wide outcomes at this position run, never that this player is the risky one.
     */
    BigDecimal pointsLow
    BigDecimal pointsHigh
    /** The week this player is off, which decides how a set of players covers a season between them. */
    Integer bye
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
