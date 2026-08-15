package ff.projection

/**
 * What a roster scores once it has to field a lineup, which is not the sum of what its players are worth.
 *
 * The auction board prices every player alone, against a league-wide replacement. That is the right way to
 * price a market and it cannot see the two things that make a set of players worth more than its parts.
 *
 * <b>Byes.</b> A player is worth nothing in the week he is off, and what covers him is whoever else is on
 * the roster that week. Three quarterbacks off in weeks 5, 10 and 13 cover a season between them in a way
 * that no per-player number reports.
 *
 * <b>Optionality.</b> Where a team starts two of a position and holds three, it starts the best two it
 * turns out to have. Only the weeks a player is good need him to be started, so a wide outcome spread is
 * worth more to a team holding a spare than to one holding exactly its starters. Under superflex, with two
 * quarterback slots and quarterback seasons the widest of any position, this is where it bites hardest.
 *
 * Both are properties of a <b>roster</b>, so neither can live on a board that prices players one at a time.
 *
 * <b>The lineup is set two ways, and the answer is the pair.</b> Set on expectation, a team starts whoever
 * its ranks say is best and lives with the draw. Set with hindsight, it starts whoever turned out best.
 * Neither is true: nobody knows in advance, and nobody is still guessing by week four. The truth is
 * bracketed, and reporting one number would be choosing which lie to tell.
 *
 * See docs/STRATEGY.md.
 */
class LineupValue {

    /**
     * Seasons replayed per evaluation.
     *
     * Enough that the bracket is stable to about a point, which is well inside what it is trying to
     * measure. Every draw is taken from a fixed seed, so a report re-run against the same board is the same
     * report: a figure that moved because of sampling is a figure a plan cannot be held to.
     */
    static final int SAMPLES = 400

    private static final long SEED = 20260815L

    /** A rostered player, reduced to what a lineup needs: where he plays and what he scores each week. */
    static class Rostered {
        final String position
        final double[] weekly

        Rostered(String position, double[] weekly) {
            this.position = position
            this.weekly = weekly
        }
    }

    /** What a roster is expected to score, with the lineup set two ways. */
    static class Bracket {
        final BigDecimal onExpectation
        final BigDecimal withHindsight

        Bracket(BigDecimal onExpectation, BigDecimal withHindsight) {
            this.onExpectation = onExpectation
            this.withHindsight = withHindsight
        }

        Bracket minus(Bracket other) {
            new Bracket(onExpectation - other.onExpectation, withHindsight - other.withHindsight)
        }
    }

    private final PointsCurve curve
    private final ByeWeeks byes
    private final Map<String, Integer> minimums
    private final Map<String, Integer> maximums
    private final int slots
    private final int lastWeek

    /**
     * Draws shared across every roster this evaluates, indexed by sample and by position on the roster.
     *
     * Shared deliberately. A marginal value is the difference between two rosters, and if each were drawn
     * its own seasons the difference would carry the noise of both. Holding the draws fixed means the only
     * thing that changes between them is the player being added.
     */
    private final double[][] draws

    LineupValue(PointsCurve curve, ByeWeeks byes, StarterRequirements requirements, int maxRoster) {
        this.curve = curve
        this.byes = byes
        this.minimums = requirements.perTeamMinimums()
        this.maximums = requirements.perTeamMaximums()
        this.slots = requirements.perTeamStarters()
        this.lastWeek = byes.lastWeek
        Random random = new Random(SEED)
        // One more than a full roster, since every evaluation asks what one further player would add.
        int width = maxRoster + 1
        this.draws = (0..<SAMPLES).collect { (0..<width).collect { random.nextDouble() } as double[] }
                as double[][]
    }

    /** A player as a lineup sees him, or null where no rank means no points to bring. */
    Rostered rostered(String position, Integer rank) {
        if (rank == null) {
            return null
        }
        Map<Integer, BigDecimal> weekly = curve.weeklyPoints(position, rank, byes.of(position, rank), lastWeek)
        if (!weekly) {
            return null
        }
        double[] points = new double[lastWeek + 1]
        weekly.each { int week, BigDecimal value -> points[week] = value.toDouble() }
        new Rostered(position, points)
    }

    /** What this roster scores, averaged over the seasons it is replayed against. */
    Bracket evaluate(List<Rostered> roster) {
        if (!roster) {
            return new Bracket(0.0, 0.0)
        }
        // The lineup set on expectation does not depend on the draw, so it is chosen once and then scored
        // against every season. Only hindsight has to re-choose, since it is choosing on the outcome.
        List<int[]> expectedLineups = (1..lastWeek).collect { int week ->
            select(roster, week, null)
        }

        double onExpectation = 0.0
        double withHindsight = 0.0
        for (int sample = 0; sample < SAMPLES; sample++) {
            double[] multipliers = multipliersFor(roster, sample)
            for (int week = 1; week <= lastWeek; week++) {
                for (int index : expectedLineups[week - 1]) {
                    onExpectation += roster[index].weekly[week] * multipliers[index]
                }
                for (int index : select(roster, week, multipliers)) {
                    withHindsight += roster[index].weekly[week] * multipliers[index]
                }
            }
        }
        new Bracket(
                (onExpectation / SAMPLES) as BigDecimal,
                (withHindsight / SAMPLES) as BigDecimal)
    }

    /** What one more player adds to this roster, which is the only question a plan is really asking. */
    Bracket marginal(List<Rostered> roster, Rostered candidate) {
        if (candidate == null) {
            return new Bracket(0.0, 0.0)
        }
        evaluate(roster + candidate).minus(evaluate(roster))
    }

    private double[] multipliersFor(List<Rostered> roster, int sample) {
        double[] multipliers = new double[roster.size()]
        for (int index = 0; index < roster.size(); index++) {
            List<Double> outcomes = curve.outcomeMultipliers(roster[index].position)
            if (!outcomes) {
                multipliers[index] = 1.0
                continue
            }
            // The draw belongs to the slot on the roster, not to the player, so adding a candidate at the
            // end leaves everyone already there with exactly the season they had.
            double draw = draws[sample][index % draws[sample].length]
            multipliers[index] = outcomes[Math.min(outcomes.size() - 1, (draw * outcomes.size()) as int)]
        }
        multipliers
    }

    /**
     * The best lineup this roster can field in one week.
     *
     * Minimums first, taking the best at each position, then the flex spots go to the highest scorers still
     * under their position's cap. Greedy is exact here: the minimums have to be met by someone and the best
     * available is never the wrong choice for them, and what is left is a free pick under per-position caps.
     *
     * @param multipliers the season each player is having, or null to choose on expectation alone
     */
    private int[] select(List<Rostered> roster, int week, double[] multipliers) {
        List<Integer> order = (0..<roster.size()).toList().sort { int index ->
            -scoreOf(roster, index, week, multipliers)
        }
        Map<String, Integer> taken = [:].withDefault { 0 }
        Set<Integer> chosen = new LinkedHashSet<>()

        // Minimums, which a team fields whether or not the player is any good.
        order.each { int index ->
            String position = roster[index].position
            if (taken[position] < (minimums[position] ?: 0)) {
                taken[position] = taken[position] + 1
                chosen << index
            }
        }
        // Then the flex, to whoever scores most and is still under his position's ceiling. Counted
        // separately from the minimums rather than up to the lineup size, because a slot a team cannot fill
        // is a slot it goes without: no kicker on the roster means nine starters, not a fourth receiver.
        int flex = slots - (minimums.values().sum() as int ?: 0)
        int filled = 0
        order.each { int index ->
            String position = roster[index].position
            if (filled < flex && !chosen.contains(index) && taken[position] < (maximums[position] ?: 0)) {
                taken[position] = taken[position] + 1
                filled++
                chosen << index
            }
        }
        chosen as int[]
    }

    private static double scoreOf(List<Rostered> roster, int index, int week, double[] multipliers) {
        roster[index].weekly[week] * (multipliers == null ? 1.0d : multipliers[index])
    }
}
