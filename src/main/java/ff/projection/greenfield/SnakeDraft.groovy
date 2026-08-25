package ff.projection.greenfield

/**
 * Which pick of the draft belongs to which slot, when the order reverses every round.
 *
 * Three lines of arithmetic that are easy to get backwards and expensive to get backwards quietly: an
 * off-by-one in the reversal moves a keeper's cost to the wrong end of a round, which changes what the pick
 * would have returned and so changes every surplus computed against it. Nothing here notices being wrong,
 * so it is asserted directly.
 *
 * Rounds and slots are one-based, as the league's own draft exports write them.
 */
class SnakeDraft {

    /** Where a slot picks in a given round: forward in odd rounds, back in even ones. */
    static int overallPick(int round, int slot, int teams) {
        require(round >= 1, "round must be at least 1, was $round")
        require(slot >= 1 && slot <= teams, "slot must be within 1..$teams, was $slot")
        (round - 1) * teams + (round % 2 == 1 ? slot : teams + 1 - slot)
    }

    /** Which round an overall pick falls in. */
    static int roundOf(int overallPick, int teams) {
        require(overallPick >= 1, "pick must be at least 1, was $overallPick")
        (overallPick - 1).intdiv(teams) + 1
    }

    /** Which slot owns an overall pick, which is the inverse of {@link #overallPick}. */
    static int slotOf(int overallPick, int teams) {
        int round = roundOf(overallPick, teams)
        int within = overallPick - (round - 1) * teams
        round % 2 == 1 ? within : teams + 1 - within
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message)
        }
    }
}
