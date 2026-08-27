package ff.projection.greenfield

/**
 * Which position to take at each of a slot's picks, chosen for the whole draft rather than one pick at a
 * time.
 *
 * <b>Taking whichever position falls furthest before the next pick is not the same as drafting well, and
 * the difference is measurable.</b> That rule looks one gap ahead, so it cannot see that a position it
 * defers will be far worse by the time it comes back. Against slot 13's real board it gave up thirteen
 * points of starting value: it took the third best quarterback in round three, which pushed the third
 * receiver out to round seven where receivers are worth a third of what they are in round five. The
 * quarterback it deferred instead would have cost twenty five and the receiver gained twenty, and no
 * comparison of one gap could tell.
 *
 * So the plan is solved rather than walked. Every ordering of positions across the picks is scored on what
 * it starts, subject to what the league lets a team start, and the best is taken.
 *
 * <b>Solved by working backwards, because the choices are not independent but the states are few.</b> What
 * the rest of a draft is worth depends only on which pick it is and how many of each position are already
 * held — never on the order they arrived in — so the same position is reached by many orderings and needs
 * solving once. Fifteen picks against the counts this league permits is a few thousand states, where the
 * orderings run past a hundred thousand.
 *
 * <b>It optimises against typical availability, so it is a prior and not a script.</b> Every value here is
 * what the board is expected to hold at a pick, taken from what this league has actually left there. What is
 * really there is the draft's business, and the answer to a pick is always the board in front of you.
 */
class DraftPlan {

    /**
     * The position to take at each pick, by pick.
     *
     * A pick with no entry is one where nothing can still improve a starting lineup: everything is full and
     * what is left is bench, which this does not price.
     *
     * @param picks       the slot's own picks, in order, less any it has given up
     * @param held        what it already holds, keepers included
     * @param maxima      the most of each position the league lets a team start
     * @param positions   the positions the league prices
     * @param bestRankAt  position to pick to the best rank expected to be there
     * @param value       position to rank to what that rank is worth over replacement
     */
    static Map<Integer, String> best(List<Integer> picks, Map<String, Integer> held,
                                     Map<String, Integer> maxima, List<String> positions,
                                     Map<String, Map<Integer, Integer>> bestRankAt,
                                     Map<String, Map<Integer, BigDecimal>> value) {
        Map<String, Integer> start = positions.collectEntries { [(it): held[it] ?: 0] }
        Map<List, List> solved = [:]
        solve(0, new LinkedHashMap<>(start), start, picks, maxima, positions, bestRankAt, value, solved)
        Map<Integer, String> plan = [:]
        Map<String, Integer> counts = new LinkedHashMap<>(start)
        picks.eachWithIndex { int pick, int i ->
            String position = solved[[i, counts.values().toList()]]?.getAt(1) as String
            if (position) {
                plan[pick] = position
                counts[position] = counts[position] + 1
            }
        }
        plan
    }

    /**
     * What the rest of the draft is worth from here, and which position gets there.
     *
     * Memoised on the pick and the counts, those being the whole of the state: two orderings arriving at the
     * same pick holding the same players face the same remaining draft.
     */
    private static List solve(int i, Map<String, Integer> counts, Map<String, Integer> start,
                              List<Integer> picks, Map<String, Integer> maxima, List<String> positions,
                              Map<String, Map<Integer, Integer>> bestRankAt,
                              Map<String, Map<Integer, BigDecimal>> value, Map<List, List> solved) {
        if (i == picks.size()) {
            return [0.0g, null]
        }
        List key = [i, counts.values().toList()]
        if (solved.containsKey(key)) {
            return solved[key]
        }
        int pick = picks[i]
        BigDecimal bestTotal = null
        String bestPosition = null
        positions.each { String position ->
            int have = counts[position]
            if (have >= (maxima[position] ?: 0)) {
                return
            }
            Integer expected = bestRankAt[position]?.get(pick)
            if (expected == null) {
                return
            }
            // Each player this slot has already taken at the position is one it removed from the board
            // itself, which the room's own average cannot know about — so the next is that much deeper.
            BigDecimal worth = value[position]?.get(expected + have - (start[position] ?: 0))
            if (worth == null) {
                return
            }
            counts[position] = have + 1
            BigDecimal total = worth + (solve(i + 1, counts, start, picks, maxima, positions, bestRankAt,
                    value, solved)[0] as BigDecimal)
            counts[position] = have
            if (bestTotal == null || total > bestTotal) {
                bestTotal = total
                bestPosition = position
            }
        }
        if (bestPosition == null) {
            // Nothing left that can start: the rest is bench, which is not this sheet's question.
            List rest = solve(i + 1, counts, start, picks, maxima, positions, bestRankAt, value, solved)
            solved[key] = [rest[0], null]
            return solved[key]
        }
        solved[key] = [bestTotal, bestPosition]
        solved[key]
    }
}
