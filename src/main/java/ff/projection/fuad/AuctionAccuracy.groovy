package ff.projection.fuad

import ff.data.PlayerValuation

/**
 * How close the board came to what the league actually paid, season by season and position by position.
 *
 * <b>This is the only check the model has that reaches outside itself.</b> {@code check_docs.sh} holds the
 * prose to the figures and {@code check_strategy.sh} holds a plan to the board it was written from; both ask
 * whether the model is consistent with itself. Nothing asked whether the board resembles an auction, and the
 * cost of that showed up twice in one afternoon: a repricing that made the board measurably worse went
 * unnoticed until somebody thought to look, and {@link AuctionValuation#PRICE_STEEPNESS} — a constant fitted
 * once, offline, against a value column the model has since changed twice — turned out to be worth more
 * accuracy than the repricing cost. See docs/TODO.md.
 *
 * <b>Joined on the MFL id, never on a name.</b> A board row and a roster row carry the same identifier, so
 * the two are the same player by construction. Every other join in this project matches consensus names to
 * statistics names and has to work at it; this one does not, and a fall in {@link Fit#priced} therefore means
 * the pool has stopped covering the auction rather than that the matching has drifted.
 *
 * <b>Measured on {@code COST} rather than {@code PRICE}</b>, which is what a team actually pays once the
 * franchise tag has held the very best players below open bidding. It is the column the record can speak to;
 * {@code PRICE} is what the auction would have settled at for players it never got to bid on.
 *
 * <b>None of this is out of sample, and no arrangement of nine seasons would make it so.</b> The curve is
 * built from every season the statistics cover whichever season is being priced, so the level leaks;
 * {@link AuctionSpend#CALIBRATED_SEASONS} are fitted on and then scored on. It measures fit, not prediction,
 * and a comparison between two models is fair where an absolute figure is flattered. 2022 is the one season
 * held out of the calibration and it is also the season the league had not yet adjusted to superflex, so it
 * is a weak test rather than a clean one.
 */
class AuctionAccuracy {

    /** Seasons the board can be held to: superflex, which is as far back as this lineup has existed. */
    static final List<String> MEASURED_SEASONS = AuctionSpend.SUPERFLEX_SEASONS

    /** The row a position, or a whole season, comes out at. */
    static final String ALL = 'ALL'

    /** How one position of one season came out. */
    static class Fit {
        final String season
        /** The position, or {@link #ALL} for the season entire. */
        final String position
        /**
         * Signings the record holds, which is the denominator {@link #priced} is read against.
         *
         * <b>The only figure here counted over the whole record.</b> Everything below is over {@link #priced}
         * instead, so this is the one column that answers what the auction was rather than what the board was
         * scored on.
         */
        final int signings
        /** How many of them the board also priced, and so how many every figure below is computed over. */
        final int priced
        /**
         * What the priced signings actually cost, in dollars.
         *
         * Over {@link #priced} and not {@link #signings}, because a dollar the board never quoted a price for
         * cannot be an error in it. So this runs below the same season's total in
         * {@link AuctionSpend.Season#dollars} by whatever the pool did not cover, and the two are not the
         * same quantity even though both are money the league spent.
         */
        final BigDecimal paid
        /** What the board said those same players would cost. */
        final BigDecimal cost
        /** Mean absolute error in dollars, which is the headline. */
        final BigDecimal meanAbsolute
        /**
         * Mean signed error, model less paid.
         *
         * Carried beside {@link #meanAbsolute} because the two say different things and only the pair is
         * diagnostic: a board that is uniformly low is mis-levelled, which is a question for the pot, and one
         * that is right on average and wrong player by player is mis-shaped, which is a question for the
         * curve and the steepness.
         */
        final BigDecimal bias
        /**
         * Rank correlation against what was paid, which asks about the ordering rather than the dollars.
         *
         * Null where there is no ordering to score rather than zero, which is a real answer meaning the
         * board ordered the auction no better than chance. A position bought entirely at the minimum bid has
         * no variance to correlate with and would otherwise report as though it had failed.
         */
        final BigDecimal correlation

        Fit(String season, String position, int signings, int priced, BigDecimal paid, BigDecimal cost,
            BigDecimal meanAbsolute, BigDecimal bias, BigDecimal correlation) {
            this.season = season
            this.position = position
            this.signings = signings
            this.priced = priced
            this.paid = paid
            this.cost = cost
            this.meanAbsolute = meanAbsolute
            this.bias = bias
            this.correlation = correlation
        }
    }

    /**
     * One season's board against one season's signings.
     *
     * @param season the season priced, whose signings are the record it is held to
     * @param board  what the model said, as {@link AuctionValuation} priced it for that same season
     */
    static List<Fit> of(String season, List<PlayerValuation> board) {
        Map<String, PlayerValuation> priced = board.collectEntries { [(it.playerId): it] }
        List<AuctionSpend.Signing> signings = AuctionSpend.signings(season)
        Map<String, List<List<BigDecimal>>> joined = [:].withDefault { [] }
        Map<String, Integer> counted = [:].withDefault { 0 }
        signings.each { AuctionSpend.Signing signing ->
            counted[signing.position] += 1
            PlayerValuation valuation = priced[signing.playerId]
            if (valuation != null) {
                joined[signing.position] << [valuation.salary as BigDecimal, signing.paid]
            }
        }
        List<Fit> fits = AuctionSpend.POSITIONS.findAll { counted[it] > 0 }.collect { String position ->
            fitOf(season, position, counted[position], joined[position])
        }
        // collectMany rather than flatten, which is deep: a pair is a list and would be flattened away too.
        fits + [fitOf(season, ALL, (counted.values().sum() ?: 0) as int,
                joined.values().collectMany { it } as List<List<BigDecimal>>)]
    }

    private static Fit fitOf(String season, String position, int signings, List<List<BigDecimal>> pairs) {
        if (!pairs) {
            return new Fit(season, position, signings, 0, 0.0, 0.0, 0.0, 0.0, null)
        }
        BigDecimal paid = pairs.collect { it[1] }.sum() as BigDecimal
        BigDecimal cost = pairs.collect { it[0] }.sum() as BigDecimal
        BigDecimal absolute = (pairs.collect { (it[0] - it[1]).abs() }.sum() as BigDecimal) / pairs.size()
        BigDecimal bias = (pairs.collect { it[0] - it[1] }.sum() as BigDecimal) / pairs.size()
        new Fit(season, position, signings, pairs.size(), paid, cost, absolute, bias, rankCorrelationOf(pairs))
    }

    /**
     * Spearman's correlation: Pearson's over the ranks rather than the values.
     *
     * Over ranks because a handful of very expensive players would otherwise decide the figure between them,
     * and because the question this column asks — did the board order the auction correctly — is about
     * ordering and not about dollars. Ties share their average rank, which matters here: most of a board is
     * at the minimum bid and pretending those are ordered would invent agreement.
     */
    private static BigDecimal rankCorrelationOf(List<List<BigDecimal>> pairs) {
        if (pairs.size() < 2) {
            return null
        }
        List<Double> model = ranked(pairs.collect { it[0] })
        List<Double> actual = ranked(pairs.collect { it[1] })
        double meanModel = model.sum() / model.size()
        double meanActual = actual.sum() / actual.size()
        double covariance = 0.0, varianceModel = 0.0, varianceActual = 0.0
        model.eachWithIndex { double m, int i ->
            double dm = m - meanModel, da = actual[i] - meanActual
            covariance += dm * da
            varianceModel += dm * dm
            varianceActual += da * da
        }
        varianceModel > 0 && varianceActual > 0 ?
                (covariance / Math.sqrt(varianceModel * varianceActual)) as BigDecimal : null
    }

    /** Ranks of a list, ties sharing the average of the places they cover. */
    private static List<Double> ranked(List<BigDecimal> values) {
        List<Integer> order = (0..<values.size()).toList().sort(false) { values[it] }
        List<Double> ranks = new ArrayList<Double>(Collections.nCopies(values.size(), 0.0d))
        int i = 0
        while (i < order.size()) {
            int j = i
            while (j + 1 < order.size() && values[order[j + 1]] == values[order[i]]) {
                j++
            }
            double shared = (i + j) / 2.0d + 1.0d
            (i..j).each { ranks[order[it]] = shared }
            i = j + 1
        }
        ranks
    }
}
