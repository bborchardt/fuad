package ff.data

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * A signing that the franchise tag priced, or may have.
 *
 * Nothing in the league data flags a tag, so every one of these is inferred. {@link #status} says how far
 * the evidence goes and {@link #basis} says what the evidence was. See docs/fuad/LEAGUE_RULES.md.
 */
@CompileStatic
@Immutable(copyWith = true)
class FranchiseTag {

    /** How much the evidence supports this being a tag. */
    static enum Status {
        /** Priced by the rule, or paid for with a draft pick. Safe to treat as a tag. */
        CONFIRMED,
        /** One of several signings on a team that cannot all be its one tag. Exactly one of them is. */
        UNCERTAIN,
        /** Above the rate on a team that tagged nobody else, so it could be a tag bid up. Unresolvable. */
        CANDIDATE
    }

    /** What made this look like a tag. */
    static enum Basis {
        /** Re-signed by its own team, one year, at exactly the franchise salary. */
        EXACT_RATE,
        /** Signed away above the rate, with a first round rookie pick going back the other way. */
        PICK_COMPENSATED,
        /** Above the rate with no compensation recorded, which an ordinary auction win also looks like. */
        ABOVE_RATE
    }

    String playerId
    String playerName
    String position
    /** The team holding the expiring contract, which is the team whose one tag would have been used. */
    String taggingFranchiseId
    /** The team that ended up with the player, the same as the tagging team unless they were bid away. */
    String signingFranchiseId
    int salary
    int contractYears
    int franchiseSalary
    Status status
    Basis basis

    boolean isBidAway() { taggingFranchiseId != signingFranchiseId }
}
