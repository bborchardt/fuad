package ff.projection

/**
 * When the player holding each consensus rank is off, and how long the season being priced runs.
 *
 * A bye is the one thing about a particular player's season that the model is willing to know. It is a fact
 * of the schedule rather than a judgement about how good he is, so taking it from the ranking source costs
 * nothing that {@link PointsCurve} exists to protect. Everything else about him comes from his rank.
 *
 * It has to be carried for the whole ranked pool and not only for the players up for auction, because
 * replacement level is the best player <i>not</i> started in a given week, and in a week when six teams are
 * off that is a worse player than the season table suggests. Knowing the byes of the players being priced
 * without knowing the byes of the players they would be replaced by would take the effect out of exactly
 * half the calculation.
 */
class ByeWeeks {

    private final Map<String, Map<Integer, Integer>> byPositionAndRank

    /** The last week a salary buys. The league plays a regular season and pays for nothing beyond it. */
    final int lastWeek

    ByeWeeks(Map<String, Map<Integer, Integer>> byPositionAndRank, int lastWeek) {
        this.byPositionAndRank = byPositionAndRank
        this.lastWeek = lastWeek
    }

    /** The week this rank is off, or null where the source does not say. */
    Integer of(String position, int rank) {
        byPositionAndRank[position]?.get(rank)
    }
}
