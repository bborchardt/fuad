package ff.projection

/**
 * What a roster scores once it has to field a lineup, which is not the sum of what its players are worth.
 *
 * The auction board prices every player alone, against a league-wide replacement. That is the right way to
 * price a market and it cannot see the three things that make a set of players worth more than its parts.
 *
 * <b>Byes.</b> A player is worth nothing in the week he is off, and what covers him is whoever else is on
 * the roster that week. Three quarterbacks off in weeks 5, 10 and 13 cover a season between them in a way
 * that no per-player number reports.
 *
 * <b>Absence.</b> The same again for the weeks he misses to injury, except that nobody knows in advance
 * which weeks those are. A roster that can cover them is worth more than one that cannot.
 *
 * <b>Optionality.</b> Where a team starts two of a position and holds three, it starts the best two it
 * turns out to have. Only the weeks a player is good need him to be started, so a wide outcome spread is
 * worth more to a team holding a spare than to one holding exactly its starters. Under superflex, with two
 * quarterback slots and quarterback seasons the widest of any position, this is where it bites hardest.
 *
 * All three are properties of a <b>roster</b>, so none can live on a board that prices players one at a
 * time.
 *
 * <b>A replayed season is a rate and a set of weeks, not one number stretched over the calendar.</b> This
 * used to draw a blended season multiplier and apply it to every week alike, which is the smear
 * {@link AuctionValuation} was fixed for and this was not: a draw of 0.3 is almost always a player who
 * missed nine games, and modelling him as playing all fourteen weeks at a third of his rate makes a
 * roster's depth look useless. The backup replaced him all season instead of covering the ten weeks he was
 * actually out, and the four weeks the starter was himself and excellent were never scored. So the draw is
 * now the paired outcome — how he played, and how much he played — and the weeks he misses are weeks he
 * genuinely is not there.
 *
 * <b>The lineup is set two ways, and the answer is the pair.</b> Set on expectation, a team starts whoever
 * its ranks say is best and lives with the draw. Set with hindsight, it starts whoever turned out best.
 * Neither is true: nobody knows in advance, and nobody is still guessing by week four.
 *
 * <b>Both of them can see who is playing.</b> What a manager does not know is how good a player will turn
 * out; he can always see who is hurt. Nobody starts a player who is not on the field, so availability binds
 * both readings and only form is bracketed between them.
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

    /** A rostered player, reduced to what a lineup needs. */
    static class Rostered {
        final String position
        /**
         * What he scores in a week he plays.
         *
         * A rate rather than an expectation, because how much football he plays is now drawn rather than
         * averaged in. Taken as the levelled season over the games behind it, so that a player who plays
         * his expected number of weeks scores his expected season and nothing is lost to the anchor.
         */
        final double rate
        /** The weeks he could play, his bye excepted. */
        final int[] playable
        /** How many of those weeks this rank has historically played. */
        final double expectedGames

        Rostered(String position, double rate, int[] playable, double expectedGames) {
            this.position = position
            this.rate = rate
            this.playable = playable
            this.expectedGames = expectedGames
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

    /** The two things a replayed season needs drawn: which season a player has, and when he misses weeks. */
    private static final long FORM = 0L
    private static final long TIMING = 1L

    private final PointsCurve curve
    private final ByeWeeks byes
    private final Map<String, Integer> minimums
    private final Map<String, Integer> maximums
    private final int slots
    private final int lastWeek
    private final Map<String, Double> meanGames = [:]

    LineupValue(PointsCurve curve, ByeWeeks byes, StarterRequirements requirements) {
        this.curve = curve
        this.byes = byes
        this.minimums = requirements.perTeamMinimums()
        this.maximums = requirements.perTeamMaximums()
        this.slots = requirements.perTeamStarters()
        this.lastWeek = byes.lastWeek
    }

    /**
     * The draw for one player, in one replayed season, of one of the two things a season is made of.
     *
     * <b>Computed rather than stored, and that is the whole point.</b> These used to be a table of random
     * numbers sized to a full roster and indexed {@code slot = index % width}, which meant a roster longer
     * than the table wrapped silently and handed the extra players the seasons of the first few — the same
     * form, the same weeks missed. A player who is out exactly when an existing player is out diversifies
     * nothing, so depth measured past the end of the table came back worth about a third of its real value,
     * with nothing to say it had happened. The depth report walks a roster plus four, where the table was
     * sized for a roster plus one, so a full enough roster reached it.
     *
     * A function of the sample and the position on the roster has no length to overrun. It keeps what the
     * table was for — a marginal is the difference between two rosters, and drawing each its own seasons
     * would give that difference the noise of both — while removing the size it was for.
     *
     * The key is {@code (sample, slot, stream)} packed injectively and put through SplitMix64's finaliser,
     * which is a bijection with good avalanche: neighbouring slots come back uncorrelated, which is what
     * the table got from being drawn in sequence and what this has to get from mixing.
     */
    private static double draw(int sample, int slot, long stream) {
        long key = ((sample as long) * 1024L + slot) * 2L + stream
        long z = key + 0x9E3779B97F4A7C15L
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL
        z = z ^ (z >>> 31)
        // The top 53 bits are the ones worth having, and 53 is what a double holds.
        ((z ^ SEED) >>> 11) * (1.0d / (1L << 53))
    }

    /**
     * The positions this lineup can field, in the order the league lists them.
     *
     * Read from the requirements rather than listed anywhere, so a report walking "every position" walks
     * the ones the league actually starts. A hand-kept list has to be told when the model gains a position
     * and was not: kickers were levelled and the depth report went on covering four.
     */
    Set<String> positions() { new LinkedHashSet<>(maximums.keySet()) }

    /** A player as a lineup sees him, or null where no rank means no points to bring. */
    Rostered rostered(String position, Integer rank) {
        if (rank == null) {
            return null
        }
        BigDecimal games = curve.expectedGames(position, rank)
        BigDecimal rate = curve.levelledRate(position, rank)
        List<Integer> playable = PointsCurve.playableWeeks(byes.of(position, rank), lastWeek)
        if (!rate || !games || !playable) {
            return null
        }
        new Rostered(position, rate.toDouble(), playable as int[], games.toDouble())
    }

    /** What this roster scores, averaged over the seasons it is replayed against. */
    Bracket evaluate(List<Rostered> roster) {
        if (!roster) {
            return new Bracket(0.0, 0.0)
        }
        // Chosen on the ranks alone, so the order is the same in every week of every season. Only which of
        // them is playing changes, and dropping a player from an order leaves the rest of it in order.
        List<Integer> expected = (0..<roster.size()).toList().sort { -roster[it].rate }

        double onExpectation = 0.0
        double withHindsight = 0.0
        double[] multipliers = new double[roster.size()]
        boolean[][] playing = new boolean[roster.size()][lastWeek + 1]
        for (int sample = 0; sample < SAMPLES; sample++) {
            seasonOf(roster, sample, multipliers, playing)
            // Hindsight knows the season each player is having, which is one number for the whole year, so
            // its order is a per-season fact rather than a per-week one.
            List<Integer> hindsight = (0..<roster.size()).toList()
                    .sort { -(roster[it].rate * multipliers[it]) }
            for (int week = 1; week <= lastWeek; week++) {
                for (int index : select(roster, expected, week, playing)) {
                    onExpectation += roster[index].rate * multipliers[index]
                }
                for (int index : select(roster, hindsight, week, playing)) {
                    withHindsight += roster[index].rate * multipliers[index]
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

    /**
     * The season each player on this roster is having: how he played, and which weeks he was there for.
     *
     * The rate and the games come from one player's actual year and are kept paired, since a season that
     * ended early is also, often enough, one that was going badly. Drawing them apart would lose whatever
     * relation they have.
     *
     * <b>How much he plays is his rank's, not the position's.</b> The outcome list is pooled across the
     * ranks that carry money, so its games are the position's. Taken raw they would hand a backup
     * quarterback the availability of a starter, which is exactly the thing rank does predict at that
     * position, where it predicts it nowhere else. So the draw is read as a multiplier against the position's
     * own mean and applied to what this rank has historically played. See docs/figures.
     */
    private void seasonOf(List<Rostered> roster, int sample, double[] multipliers, boolean[][] playing) {
        for (int index = 0; index < roster.size(); index++) {
            Rostered player = roster[index]
            List<PointsCurve.Outcome> outcomes = curve.outcomeSeasons(player.position)
            int weeks
            if (!outcomes) {
                multipliers[index] = 1.0
                weeks = Math.round(player.expectedGames) as int
            } else {
                PointsCurve.Outcome outcome = outcomes[Math.min(outcomes.size() - 1,
                        (draw(sample, index, FORM) * outcomes.size()) as int)]
                multipliers[index] = outcome.rateMultiplier
                double mean = meanGamesOf(player.position, outcomes)
                weeks = Math.round(mean > 0 ? player.expectedGames * outcome.games / mean
                        : player.expectedGames) as int
            }
            play(player, playing[index], Math.max(0, Math.min(player.playable.length, weeks)),
                    draw(sample, index, TIMING))
        }
    }

    /** The games a position's replayed seasons average, which is what a drawn one is a multiple of. */
    private double meanGamesOf(String position, List<PointsCurve.Outcome> outcomes) {
        Double known = meanGames[position]
        if (known != null) {
            return known
        }
        double mean = outcomes ? outcomes.collect { it.games }.sum() / outcomes.size() : 0.0d
        meanGames[position] = mean
        mean
    }

    /**
     * Mark which weeks he is there for, the ones he misses taken as one run rather than scattered.
     *
     * Absence comes in blocks: a player is hurt and then he is back, and the weeks in between are
     * consecutive. Scattering them would hand every week its own small independent chance of being short a
     * player, which a single spare covers far too easily and which is not the shape a bench actually has to
     * cover.
     *
     * Where the block falls is drawn and wrapped around the season, since nothing here knows when an injury
     * lands and every week should be as likely to be missed as any other. That is the same assumption the
     * auction path makes when it charges {@code g/W} of a season uniformly; the difference is that a lineup
     * has to know <i>which</i> weeks, because that is what decides who gets started.
     */
    private static void play(Rostered player, boolean[] playing, int weeks, double placement) {
        Arrays.fill(playing, false)
        int total = player.playable.length
        int missed = total - weeks
        boolean[] out = new boolean[total]
        if (missed > 0) {
            int start = Math.min(total - 1, (placement * total) as int)
            for (int i = 0; i < missed; i++) {
                out[(start + i) % total] = true
            }
        }
        for (int i = 0; i < total; i++) {
            if (!out[i]) {
                playing[player.playable[i]] = true
            }
        }
    }

    /**
     * The best lineup this roster can field in one week, given an order to prefer players in.
     *
     * Minimums first, taking the best at each position, then the flex spots go to the highest in the order
     * still under their position's cap. Greedy is exact here: the minimums have to be met by someone and
     * the best available is never the wrong choice for them, and what is left is a free pick under
     * per-position caps.
     *
     * A player who is not playing this week is not selectable at all, on either reading. Starting him would
     * score nothing and would hold a slot that somebody else can fill, which is not what a team does with a
     * player it can see is out.
     */
    private int[] select(List<Rostered> roster, List<Integer> order, int week, boolean[][] playing) {
        Map<String, Integer> taken = [:].withDefault { 0 }
        Set<Integer> chosen = new LinkedHashSet<>()

        // Minimums, which a team fields whether or not the player is any good.
        order.each { int index ->
            if (!playing[index][week]) {
                return
            }
            String position = roster[index].position
            if (taken[position] < (minimums[position] ?: 0)) {
                taken[position] = taken[position] + 1
                chosen << index
            }
        }
        // Then the flex, to whoever is highest in the order and still under his position's ceiling. Counted
        // separately from the minimums rather than up to the lineup size, because a slot a team cannot fill
        // is a slot it goes without: no kicker on the roster means nine starters, not a fourth receiver.
        int flex = slots - (minimums.values().sum() as int ?: 0)
        int filled = 0
        order.each { int index ->
            if (!playing[index][week] || chosen.contains(index)) {
                return
            }
            String position = roster[index].position
            if (filled < flex && taken[position] < (maximums[position] ?: 0)) {
                taken[position] = taken[position] + 1
                filled++
                chosen << index
            }
        }
        chosen as int[]
    }
}
