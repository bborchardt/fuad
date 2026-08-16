package ff.projection

import java.math.RoundingMode

/**
 * What releasing a player costs the team that signed him.
 *
 * The greater of <b>40% of the dollars left on the contract</b> and <b>$1 per year remaining</b>, rounded
 * up, charged against the current season's cap and cleared at the end of it. Nothing carries forward: this
 * league has no dead money, so a bad contract is a cash-flow problem for one season rather than an
 * obligation that follows a team around.
 *
 * <b>The floor is the half that shapes behaviour.</b> Below $2.50 a year the percentage is worth less than
 * a dollar and the minimum takes over, so a cheap long contract is proportionally the most expensive thing
 * in the league to escape: a five-year dollar player costs $5 to walk away from, five times what he costs
 * to keep for a year, while a $50 one-year deal costs $20, or 0.4 times. The two ends of the board are
 * constrained differently rather than one of them being free.
 *
 * Confirmed against every penalty the league has charged: 383 of 384 adjustments across 2017-2025 come back
 * exactly, and the one that does not is a six-cut batch a dollar light. See docs/LEAGUE_RULES.md.
 */
class CutPenalty {

    /** Share of the remaining contract charged on release. */
    static final BigDecimal RATE = 0.40

    /** The least a released year can cost, whatever the salary. Binds below $2.50 a year and nowhere else. */
    static final BigDecimal MINIMUM_PER_YEAR = 1.0

    /** The salary per year at which the rate overtakes the floor. */
    static final BigDecimal FLOOR_BINDS_BELOW = MINIMUM_PER_YEAR / RATE

    /**
     * The penalty for releasing one contract.
     *
     * @param yearsRemaining years left to run, the whole contract if it has not started
     * @param salary         the annual salary
     */
    static int of(int yearsRemaining, BigDecimal salary) {
        if (yearsRemaining <= 0) {
            return 0
        }
        BigDecimal rated = RATE * yearsRemaining * salary
        BigDecimal floor = MINIMUM_PER_YEAR * yearsRemaining
        (rated.max(floor)).setScale(0, RoundingMode.CEILING) as int
    }

    /** True where the minimum rather than the rate decides what a contract costs to escape. */
    static boolean floorGoverns(BigDecimal salary) {
        salary < FLOOR_BINDS_BELOW
    }
}
