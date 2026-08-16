package ff.projection

import ff.data.RealisedSeason

/**
 * What a player at a given consensus rank is worth, and how widely that turns out.
 *
 * <b>Order comes from the FantasyPros consensus, level from history.</b> The player ranked WR1 is valued at
 * what preseason WR1s have actually scored, restated under the rules being priced. No projection of any
 * particular player enters anywhere: a ranking is a judgement about who is better, which is what consensus
 * is for, and history says what that judgement has been worth.
 *
 * This is deliberately not built from projections. Doing so made the level of every rank somebody's opinion
 * of this year's specific players, so that a source rating one quarterback far above consensus dragged the
 * whole rank with him.
 *
 * <b>A season is a rate multiplied by an availability, and the two are levelled separately.</b> How good a
 * player is when he plays and how much football he plays are differently caused and differently variable —
 * for ranked quarterbacks the rate scatters with a coefficient of variation of 0.25 and games played with
 * 0.25, so about half the variation in a season total is availability rather than production. Levelling the
 * product directly lets one unlucky year of injuries at one rank masquerade as a judgement about talent:
 * it is why the consensus best running back appeared to be outplayed by RB5, when in fact he has the
 * highest points per game at the position and the fewest games of the top eight.
 *
 * Multiplying two separately averaged halves is also the better estimator. Rate is much the smoother of the
 * two — running back's curve travels backwards a third as often measured per game as measured per season —
 * so the product carries less noise than the mean of the products does.
 *
 * Nine seasons are pooled flat. Restating 2017-19 and 2022-24 under one rule set brings them to within a
 * few per cent at every position, so there is no era effect left to weight against. That gives about 45
 * observations a rank against 15 from three seasons, which is what the smoothing has to work with.
 *
 * Players ranked before a season who never played are in the sample as {@code games = 0}. They carry no
 * rate, being no evidence about one, and count in the availability half. Dropping them would bias the curve
 * upward and flatten the left tail a bench is priced against.
 *
 * See docs/PROJECTION.md.
 */
class PointsCurve {

    /** Ranks either side of each one that are averaged in, to get a usable sample out of nine seasons. */
    private static final int SMOOTHING_RADIUS = 2

    /**
     * Ranks levelled below this share of the position's best do not get a say in the outcome spread.
     *
     * Not because they are uninteresting but because a ratio taken against a very small number is not a
     * ratio of the same thing. The consensus ranks receivers a hundred and forty deep, and by the bottom of
     * that list a rank is worth three or four points a season, so one player who turns out to be a starter
     * comes back as sixteen times expectation. Those are not seasons that beat their projection, they are
     * seasons the consensus was not really making a claim about, and letting them in invents exactly the
     * multipliers above three that this distribution is kept empirical to avoid.
     */
    private static final double RELEVANT_FRACTION = 0.25d

    private static final int MINIMUM_OBSERVATIONS = 6

    /** One season as the spread sees it: how the player scored when he played, and how often he played. */
    static class Outcome {
        final double rateMultiplier
        final int games

        Outcome(double rateMultiplier, int games) {
            this.rateMultiplier = rateMultiplier
            this.games = games
        }
    }

    private final Map<String, Map<Integer, BigDecimal>> rateByPosition
    private final Map<String, Map<Integer, BigDecimal>> gamesByPosition
    private final Map<String, Map<Integer, BigDecimal>> levelByPosition
    private final Map<String, Map<Integer, BigDecimal>> errorByPosition
    private final Map<String, Map<Integer, Integer>> tierByPosition
    private final Map<String, List<Double>> multipliersByPosition
    private final Map<String, List<Outcome>> outcomesByPosition
    private final Map<String, Integer> depthByPosition

    private PointsCurve(Map<String, Map<Integer, BigDecimal>> rateByPosition,
                        Map<String, Map<Integer, BigDecimal>> gamesByPosition,
                        Map<String, Map<Integer, BigDecimal>> levelByPosition,
                        Map<String, Map<Integer, BigDecimal>> errorByPosition,
                        Map<String, Map<Integer, Integer>> tierByPosition,
                        Map<String, List<Double>> multipliersByPosition,
                        Map<String, List<Outcome>> outcomesByPosition,
                        Map<String, Integer> depthByPosition) {
        this.rateByPosition = rateByPosition
        this.gamesByPosition = gamesByPosition
        this.levelByPosition = levelByPosition
        this.errorByPosition = errorByPosition
        this.tierByPosition = tierByPosition
        this.multipliersByPosition = multipliersByPosition
        this.outcomesByPosition = outcomesByPosition
        this.depthByPosition = depthByPosition
    }

    /**
     * @param realised per position, the seasons had by players holding each preseason rank, over as many
     *                 seasons as are available, all restated under one set of rules
     */
    static PointsCurve of(Map<String, Map<Integer, List<RealisedSeason>>> realised) {
        Map<String, Map<Integer, BigDecimal>> rates = [:]
        Map<String, Map<Integer, BigDecimal>> games = [:]
        Map<String, Map<Integer, BigDecimal>> levels = [:]
        Map<String, Map<Integer, BigDecimal>> errors = [:]
        Map<String, Map<Integer, Integer>> tiers = [:]
        Map<String, List<Double>> multipliers = [:]
        Map<String, List<Outcome>> outcomes = [:]
        Map<String, Integer> depths = [:]

        realised.each { String position, Map<Integer, List<RealisedSeason>> byRank ->
            int deepest = byRank.keySet() ? byRank.keySet().max() as int : 0
            Map<Integer, BigDecimal> rate = [:]
            Map<Integer, BigDecimal> played = [:]
            Map<Integer, BigDecimal> level = [:]
            Map<Integer, BigDecimal> error = [:]
            (1..deepest).each { int rank ->
                List<RealisedSeason> around = smoothed(byRank, rank)
                // Availability counts every ranked season, including the ones that never happened. Rate
                // counts only the seasons with football in them, a lost year being no evidence about form.
                List<BigDecimal> observedRates = around.findAll { it.games > 0 }.collect { it.rate }
                if (around.size() >= MINIMUM_OBSERVATIONS && observedRates.size() >= MINIMUM_OBSERVATIONS) {
                    BigDecimal meanRate = mean(observedRates)
                    BigDecimal meanGames = mean(around.collect { it.games as BigDecimal })
                    rate[rank] = meanRate
                    played[rank] = meanGames
                    level[rank] = meanRate * meanGames
                    error[rank] = levelErrorOf(observedRates, around, meanRate, meanGames)
                }
            }
            if (level) {
                // Availability is flat across the ranks that carry money, so it is estimated once for the
                // position rather than per rank. Levelling it rank by rank fits noise and puts it straight
                // back into the product this split exists to take it out of.
                Map<Integer, BigDecimal> flattened = flattenAvailability(byRank, level, played)
                Map<Integer, BigDecimal> settled = level.collectEntries { int rank, BigDecimal points ->
                    [(rank): rate[rank] * flattened[rank]]
                }
                Map<Integer, BigDecimal> settledError = error.collectEntries { int rank, BigDecimal e ->
                    [(rank): level[rank] > 0 ? e * settled[rank] / level[rank] : e]
                }
                rates[position] = rate
                games[position] = flattened
                levels[position] = settled
                errors[position] = settledError
                tiers[position] = tiersOf(settled, settledError)
                depths[position] = settled.keySet().max() as int
                multipliers[position] = spread(byRank, settled)
                outcomes[position] = outcomesOf(byRank, rate, settled)
            }
        }
        new PointsCurve(rates, games, levels, errors, tiers, multipliers, outcomes, depths)
    }

    Set<String> positions() { levelByPosition.keySet().asImmutable() }

    int depth(String position) { depthByPosition[position] ?: 0 }

    /** Expected points over the season for the player holding this rank: his rate times his availability. */
    BigDecimal seasonPoints(String position, int rank) {
        levelByPosition[position]?.get(rank) ?: 0.0
    }

    /** What this rank scores in a game he plays, which is the half of a season that is about ability. */
    BigDecimal pointsPerGame(String position, int rank) {
        rateByPosition[position]?.get(rank) ?: 0.0
    }

    /** How many games this rank has historically played, out of the thirteen a season holds. */
    BigDecimal expectedGames(String position, int rank) {
        gamesByPosition[position]?.get(rank) ?: 0.0
    }

    /**
     * How precisely a rank is levelled: the standard error of the mean behind {@link #seasonPoints}.
     *
     * Combined from the two halves, since the level is their product. That it is smaller than the error on
     * the season totals themselves is the point of splitting them: averaging rate and availability apart
     * and multiplying is a lower variance estimator than averaging the product.
     */
    BigDecimal standardError(String position, int rank) {
        errorByPosition[position]?.get(rank) ?: 0.0
    }

    /**
     * Which band of indistinguishable ranks this one falls in, 1 being the best.
     *
     * <b>The curve resolves QB2 from QB17 and cannot resolve QB10 from QB14.</b> Levels are a mean of about
     * 45 realised seasons and carry a standard error to match, so across the flat middle of a position the
     * rank-to-rank ordering is noise. Reporting those as different prices claims a resolution the evidence
     * does not have.
     *
     * A tier holds every rank levelling within one standard error of the best rank in it. Ranks in the same
     * tier at the same position should be read as ties and chosen between on price, bye or roster fit —
     * never on the order they happen to fall in. Tiers are per position: a QB tier 3 and an RB tier 3 have
     * nothing to do with each other.
     */
    int tier(String position, int rank) {
        tierByPosition[position]?.get(rank) ?: 0
    }

    /**
     * What this rank scores in each week he plays, which is not the same as each week of the season.
     *
     * Evenly across the weeks he is not on bye, because there is nothing here to say otherwise: a matchup
     * by matchup shape would have to come from a projection, which is the thing this curve exists to avoid.
     *
     * <b>This is a rate, not an expectation.</b> It says what a week looks like when he plays, and says
     * nothing about how many such weeks there are — that is {@link #expectedGames}, and the two are kept
     * apart so that a season lost to injury is modelled as absence rather than as thirteen bad weeks.
     */
    Map<Integer, BigDecimal> weeklyRate(String position, int rank, Integer byeWeek, int lastWeek) {
        BigDecimal rate = pointsPerGame(position, rank)
        List<Integer> playing = playableWeeks(byeWeek, lastWeek)
        if (!rate || !playing) {
            return [:]
        }
        (1..lastWeek).collectEntries { [(it): it == byeWeek ? 0.0 as BigDecimal : rate] }
    }

    /** The weeks of the season this rank could play, the bye excepted. */
    static List<Integer> playableWeeks(Integer byeWeek, int lastWeek) {
        (1..lastWeek).findAll { it != byeWeek }
    }

    /**
     * Expected points in a given week, availability included: the season spread over the weeks it could
     * fall in.
     *
     * The counterpart to {@link #weeklyRate}, and the right reading wherever the question is what a player
     * brings on average rather than what a particular week looks like. A lineup compared against another
     * lineup wants this; value over replacement, which has to know whether he cleared the bar in the weeks
     * he actually played, wants the rate.
     */
    Map<Integer, BigDecimal> weeklyPoints(String position, int rank, Integer byeWeek, int lastWeek) {
        BigDecimal season = seasonPoints(position, rank)
        List<Integer> playing = playableWeeks(byeWeek, lastWeek)
        if (!season || !playing) {
            return [:]
        }
        BigDecimal perWeek = season / playing.size()
        (1..lastWeek).collectEntries { [(it): it == byeWeek ? 0.0 as BigDecimal : perWeek] }
    }

    /**
     * Every ratio of a realised season to what the rank predicted, scaled to average one.
     *
     * The whole season, rate and availability together, which is what the board reports a range from. Kept
     * as observed rather than fitted to a distribution, because it is badly lopsided: nearly all the
     * variance is a left tail of seasons lost to injury, reaching zero, while the upside stops not far
     * above one and a half times expectation. A lognormal matched to that variance mirrors the left tail
     * into a right one and invents multipliers over three.
     */
    List<Double> outcomeMultipliers(String position) { multipliersByPosition[position] ?: [] }

    /**
     * The same seasons, but split into how he played and how often, and kept paired.
     *
     * Paired deliberately: a season's rate and its games came from one player having one year, and pricing
     * them as independent draws would lose whatever relation they have. This is what value over replacement
     * is averaged across, and it is why an injury-shortened season is now worth something — a starter who
     * played six games at his own rate cleared replacement in six weeks, where smearing the same total over
     * thirteen made him look like a player who never cleared it at all.
     */
    List<Outcome> outcomeSeasons(String position) { outcomesByPosition[position] ?: [] }

    /**
     * The multiplier a given share of this position's seasons came in under.
     *
     * <b>It is a property of the position, not of the player.</b> The ratios are pooled across every rank
     * that carries enough money to be a ratio of the same thing, so two players at the same position get
     * the same spread around their own different levels. That is deliberate — realised variation cannot
     * tell an erratic player from one the consensus misjudged — and it is why nothing here should be read
     * as this player being the risky one.
     */
    BigDecimal outcomePercentile(String position, double percentile) {
        List<Double> sorted = (multipliersByPosition[position] ?: []).sort()
        if (!sorted) {
            return 1.0
        }
        int index = (Math.ceil(percentile * sorted.size()) as int) - 1
        sorted[Math.min(sorted.size() - 1, Math.max(0, index))] as BigDecimal
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        (values.sum() as BigDecimal) / values.size()
    }

    /**
     * Hold availability flat across the ranks that carry money, and let it fall away below them.
     *
     * How many games a player misses is essentially unrelated to where the consensus ranked him: across
     * nine seasons the correlation between rank and games played is -0.04 at running back, -0.09 at
     * receiver and -0.14 at tight end. Estimating a separate figure for each rank therefore fits noise, and
     * multiplying it into the level puts back exactly the scatter that splitting rate from availability was
     * meant to remove — measured per rank the level came out <i>less</i> monotone than the season totals it
     * replaced.
     *
     * Below the money the picture changes and the flat figure would be wrong. Quarterback is the clear
     * case: ranks 1 to 24 average 11.3 games and ranks 25 and beyond only 8.4, because those are backups
     * who do not play rather than starters who get hurt. Deep ranks keep their own estimate for that
     * reason, and they price at the minimum bid either way.
     */
    private static Map<Integer, BigDecimal> flattenAvailability(Map<Integer, List<RealisedSeason>> byRank,
                                                                Map<Integer, BigDecimal> level,
                                                                Map<Integer, BigDecimal> played) {
        BigDecimal floor = relevanceFloor(level)
        List<Integer> priced = level.keySet().findAll { level[it] > floor }.toList()
        if (!priced) {
            return played
        }
        List<BigDecimal> observed = priced.collectMany { int rank ->
            (byRank[rank] ?: []).collect { it.games as BigDecimal }
        }
        if (!observed) {
            return played
        }
        BigDecimal flat = mean(observed)
        played.collectEntries { int rank, BigDecimal own ->
            [(rank): level[rank] > floor ? flat : own.min(flat)]
        }
    }

    /**
     * The standard error of a level, propagated from the two halves it is a product of.
     *
     * Rate and availability are averaged over different samples — a lost season is in one and not the
     * other — so their errors are combined rather than taken from the season totals directly.
     */
    private static BigDecimal levelErrorOf(List<BigDecimal> rates, List<RealisedSeason> around,
                                           BigDecimal meanRate, BigDecimal meanGames) {
        double rateError = standardErrorOf(rates, meanRate).toDouble()
        double gamesError = standardErrorOf(around.collect { it.games as BigDecimal }, meanGames).toDouble()
        double level = (meanRate * meanGames).toDouble()
        if (level == 0.0d) {
            return 0.0
        }
        double relative = Math.sqrt(
                Math.pow(rateError / meanRate.toDouble(), 2) + Math.pow(gamesError / meanGames.toDouble(), 2))
        (level * relative) as BigDecimal
    }

    /** The standard error of a mean, over the sample it was taken from. */
    private static BigDecimal standardErrorOf(List<BigDecimal> values, BigDecimal mean) {
        if (values.size() < 2) {
            return 0.0
        }
        double variance = values.collect { double d = (it - mean).toDouble(); d * d }.sum() / (values.size() - 1)
        Math.sqrt(variance / values.size()) as BigDecimal
    }

    /**
     * Group the ranks into bands the evidence can actually tell apart.
     *
     * Walked in order of <b>level</b> rather than of rank, which matters because the curve is not monotone.
     * Sweeping by rank puts a tier boundary wherever the curve happens to dip and then recover, so two
     * ranks a point apart on estimates carrying ten points of error come out in different tiers because a
     * third rank sags between them. Ordering by level, they land together, which is what the evidence says.
     *
     * A rank joins the current tier while it levels within one standard error of the <b>best</b> rank in
     * that tier, and opens a new one when it falls further. Measuring against the tier's best rather than
     * its previous member is what stops a chain of individually-small steps from drifting a tier
     * arbitrarily wide.
     *
     * So a tier is a set of ranks, not a range of them, and it need not be contiguous.
     */
    private static Map<Integer, Integer> tiersOf(Map<Integer, BigDecimal> level, Map<Integer, BigDecimal> error) {
        List<Integer> byLevel = level.keySet().sort { a, b ->
            (level[b] <=> level[a]) ?: (a <=> b)
        }
        Map<Integer, Integer> tiers = [:]
        int tier = 0
        BigDecimal best = null
        byLevel.each { int rank ->
            BigDecimal points = level[rank]
            if (best == null || best - points > (error[rank] ?: 0.0)) {
                tier++
                best = points
            }
            tiers[rank] = tier
        }
        tiers
    }

    private static List<RealisedSeason> smoothed(Map<Integer, List<RealisedSeason>> byRank, int rank) {
        ((rank - SMOOTHING_RADIUS)..(rank + SMOOTHING_RADIUS))
                .collectMany { (byRank[it] ?: []) as List<RealisedSeason> }
    }

    /** Which ranks carry enough money for a ratio against them to mean anything. */
    private static BigDecimal relevanceFloor(Map<Integer, BigDecimal> level) {
        (level.values().max() ?: 0.0) * RELEVANT_FRACTION
    }

    private static List<Double> spread(Map<Integer, List<RealisedSeason>> byRank,
                                       Map<Integer, BigDecimal> level) {
        BigDecimal floor = relevanceFloor(level)
        List<Double> ratios = byRank.collectMany { int rank, List<RealisedSeason> seasons ->
            BigDecimal expected = level[rank]
            expected > floor && expected > 0 ? seasons.collect { (it.points / expected).toDouble() } : []
        }
        if (ratios.size() < 20) {
            return []
        }
        double mean = ratios.sum() / ratios.size()
        mean > 0 ? ratios.collect { it / mean } : []
    }

    /**
     * The same seasons split in two, rate against the rank's rate and games as they were.
     *
     * Rate multipliers are scaled to average one so that carrying the spread moves no expected points. A
     * lost season keeps its zero games and takes the average multiplier, which contributes nothing either
     * way: with no games there is no week for a rate to apply to.
     */
    private static List<Outcome> outcomesOf(Map<Integer, List<RealisedSeason>> byRank,
                                            Map<Integer, BigDecimal> rate,
                                            Map<Integer, BigDecimal> level) {
        BigDecimal floor = relevanceFloor(level)
        List<Outcome> raw = byRank.collectMany { int rank, List<RealisedSeason> seasons ->
            BigDecimal expected = level[rank]
            BigDecimal expectedRate = rate[rank]
            if (!expected || expected <= floor || !expectedRate) {
                return [] as List<Outcome>
            }
            seasons.collect { RealisedSeason season ->
                new Outcome(season.games > 0 ? (season.rate / expectedRate).toDouble() : 1.0d, season.games)
            }
        }
        if (raw.size() < 20) {
            return []
        }
        List<Double> played = raw.findAll { it.games > 0 }.collect { it.rateMultiplier }
        if (!played) {
            return []
        }
        double mean = played.sum() / played.size()
        mean > 0 ? raw.collect { new Outcome(it.rateMultiplier / mean, it.games) } : []
    }
}
