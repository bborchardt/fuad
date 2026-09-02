package ff.projection.fuad

import ff.data.PlayerValuation

/**
 * How much steeper this league bids than value, fitted from what it actually paid.
 *
 * This is where {@link AuctionValuation#PRICE_STEEPNESS} comes from, and it exists because that constant had
 * nowhere to come from. It was fitted once, by hand, against a value column the model has since changed
 * twice, and nothing recomputed it or could have noticed — {@code check_docs.sh} holds the prose to the
 * figures and {@link AuctionAccuracy} now holds the board to the record, but a number nothing regenerates is
 * outside both. See docs/TODO.md.
 *
 * <b>The estimator is the model's own arithmetic read backwards, not a choice.</b> {@link AuctionValuation}
 * bends a position's shares to {@code share_i ∝ value_i^gamma}, renormalises them to that position's own
 * total, and prices each player at {@code 1 + rate * share_i}. Taking logs of the part above the reserved
 * minimum bid:
 *
 * <pre>
 *     log(paid - 1) = a + gamma * log(value)
 * </pre>
 *
 * where {@code a} absorbs the clearing rate, the normaliser and the positional calibration together. The
 * slope of that line <b>is</b> gamma. Nothing has to be searched for, and nothing depends on the model's own
 * pot — which matters, because the board's pot runs six to eleven per cent under what the league spent, and
 * an estimator that minimised dollar error would quietly bend gamma to absorb that. Level is not gamma's
 * job.
 *
 * <b>A dollar signing is a censored observation, not a cheap one.</b> Half the kickers and a quarter of the
 * tight ends go at the minimum bid, which says only that the market cleared them somewhere below it. Fitting
 * the line over the rest alone is selection on the outcome and biases the slope toward zero — measurably, by
 * 0.2 to 0.3 at every position that carries money. So the minimum-bid signings are kept and enter the
 * likelihood as what they are, an upper bound. See {@link #logLikelihood}.
 *
 * <b>What it does not settle.</b> The fit is over the same seasons {@link AuctionSpend#CALIBRATED_SEASONS}
 * covers, which are the seasons a refitted constant is then scored on, so it is no more out of sample than
 * anything else here. It also cannot see the tag: the best players at a position are held below open bidding
 * and never appear as a signing at all, so the steepest part of every curve is fitted from where the market
 * was allowed to operate and extrapolated over where it was not.
 */
class PriceSteepness {

    /** One player somebody bid on, and what the board said he was worth. */
    static class Observation {
        final String position
        /** Value over replacement, which is the quantity {@code steepen} raises to the power of gamma. */
        final BigDecimal value
        /** What he actually went for. */
        final BigDecimal paid

        Observation(String position, BigDecimal value, BigDecimal paid) {
            this.position = position
            this.value = value
            this.paid = paid
        }

        /** Whether the minimum bid is all the record says about him. */
        boolean isCensored() { paid < MINIMUM_BID + 1 }
    }

    /** What one position's bidding came out at. */
    static class Fit {
        final String position
        /** Signings behind it, the censored ones included. */
        final int signings
        /** How many of those went at the minimum bid and so carry only an upper bound. */
        final int censored
        /** The exponent itself: above one the league pays more for the best than value warrants. */
        final BigDecimal gamma
        /** The scatter of log price about the fitted line, which says how much of a claim gamma is. */
        final BigDecimal sigma

        Fit(String position, int signings, int censored, BigDecimal gamma, BigDecimal sigma) {
            this.position = position
            this.signings = signings
            this.censored = censored
            this.gamma = gamma
            this.sigma = sigma
        }
    }

    /**
     * The reserved dollar every roster spot carries, which is the floor a price is measured above.
     *
     * {@link AuctionValuation} prices at {@code 1 + rate * share}, so a signing at one dollar is a share the
     * market valued at less than a whole dollar and nothing more precise than that.
     */
    private static final BigDecimal MINIMUM_BID = 1.0

    /** Below this many signings a position is not fitted at all, and keeps whatever it was given. */
    static final int MINIMUM_SIGNINGS = 12

    /**
     * Every signing of the fitted seasons, joined to what the board said that player was worth.
     *
     * Joined on the MFL id like {@link AuctionAccuracy}, and for the same reason: a board row and a roster
     * row carry the same identifier, so no name matching stands between the record and the fit.
     */
    static List<Observation> observationsFrom(Map<String, List<PlayerValuation>> boardsBySeason) {
        boardsBySeason.collectMany { String season, List<PlayerValuation> board ->
            Map<String, BigDecimal> worth = board.collectEntries { [(it.playerId): it.valueOverReplacement] }
            AuctionSpend.signings(season).findAll { worth[it.playerId] > 0 }
                    .collect { new Observation(it.position, worth[it.playerId], it.paid) }
        }
    }

    /** One fit per position that has enough signings to make a claim. */
    static List<Fit> of(List<Observation> observations) {
        observations.groupBy { it.position }
                .findAll { String position, List<Observation> at -> at.size() >= MINIMUM_SIGNINGS }
                .collect { String position, List<Observation> at -> fitOf(position, at) }
                .sort { AuctionSpend.POSITIONS.indexOf(it.position) }
    }

    private static Fit fitOf(String position, List<Observation> at) {
        double[] start = leastSquares(at)
        double[] fitted = maximise(at, start)
        new Fit(position, at.size(), at.count { it.censored }, fitted[1] as BigDecimal,
                fitted[2] as BigDecimal)
    }

    /**
     * The line through the uncensored signings alone, which is where the search starts.
     *
     * Biased toward flat, being a selection on the outcome, and reported by nothing — it is a starting point
     * near enough the answer that the ascent below has a short way to travel, and no more than that.
     */
    private static double[] leastSquares(List<Observation> at) {
        List<Observation> paid = at.findAll { !it.censored }
        if (paid.size() < 3) {
            return [0.0d, 1.0d, 1.0d] as double[]
        }
        List<Double> xs = paid.collect { Math.log(it.value.toDouble()) }
        List<Double> ys = paid.collect { Math.log(it.paid.toDouble() - MINIMUM_BID.toDouble()) }
        double meanX = xs.sum() / xs.size(), meanY = ys.sum() / ys.size()
        double sxy = 0.0d, sxx = 0.0d
        xs.eachWithIndex { double x, int i -> sxy += (x - meanX) * (ys[i] - meanY); sxx += (x - meanX)**2 }
        double gamma = sxx > 0 ? sxy / sxx : 1.0d
        double intercept = meanY - gamma * meanX
        double variance = 0.0d
        xs.eachWithIndex { double x, int i -> variance += (ys[i] - intercept - gamma * x)**2 }
        [intercept, gamma, Math.max(1e-3d, Math.sqrt(variance / Math.max(1, xs.size() - 2)))] as double[]
    }

    /**
     * The likelihood of a set of signings under one line, with the minimum bid treated as a bound.
     *
     * An uncensored signing contributes the density of its own residual. One at the minimum bid contributes
     * the probability that the line would have put it there at all — the mass below the floor — which is
     * what lets it inform the slope without pretending to a price it never revealed.
     */
    private static double logLikelihood(List<Observation> at, double intercept, double gamma, double sigma) {
        if (sigma <= 0) {
            return -Double.MAX_VALUE
        }
        double total = 0.0d
        for (Observation observation : at) {
            double x = Math.log(observation.value.toDouble())
            if (observation.censored) {
                // Everything the record says is that the market cleared him under the reserved dollar,
                // which in these logs is everything below zero.
                total += Math.log(Math.max(1e-300d, standardNormalBelow((0.0d - intercept - gamma * x) / sigma)))
            } else {
                double z = (Math.log(observation.paid.toDouble() - MINIMUM_BID.toDouble())
                        - intercept - gamma * x) / sigma
                total += -Math.log(sigma) - 0.5d * Math.log(2 * Math.PI) - 0.5d * z * z
            }
        }
        total
    }

    /**
     * Coordinate ascent with a halving step, which is enough for a surface this well behaved.
     *
     * The censored likelihood is concave in these three parameters, so there is one maximum and no way to
     * climb to a false one. A search rather than a formula because the censored term has no closed form;
     * {@code PriceSteepnessSpec} holds it to a gamma it is given by construction, over synthetic signings
     * censored the same way the record is.
     */
    private static double[] maximise(List<Observation> at, double[] start) {
        double[] best = start.clone()
        double[] step = [1.0d, 0.5d, 0.5d] as double[]
        double value = logLikelihood(at, best[0], best[1], best[2])
        for (int round = 0; round < MAXIMUM_ROUNDS; round++) {
            boolean improved = false
            for (int parameter = 0; parameter < 3; parameter++) {
                for (double direction : [step[parameter], -step[parameter]]) {
                    double[] trial = best.clone()
                    trial[parameter] += direction
                    if (trial[2] <= 0) {
                        continue
                    }
                    double candidate = logLikelihood(at, trial[0], trial[1], trial[2])
                    if (candidate > value) {
                        value = candidate
                        best = trial
                        improved = true
                    }
                }
            }
            if (!improved) {
                (0..<3).each { step[it] *= 0.5d }
                if (step.max() < TOLERANCE) {
                    return best
                }
            }
        }
        best
    }

    private static final int MAXIMUM_ROUNDS = 2000
    private static final double TOLERANCE = 1e-6d

    /**
     * The standard normal below a point, which is the whole of what a censored signing contributes.
     *
     * Visible rather than private because the censored term is only as good as this is, and
     * {@code PriceSteepnessSpec} holds it to values that are known rather than fitted.
     */
    static double standardNormalBelow(double z) {
        0.5d * erfc(-z / Math.sqrt(2.0d))
    }

    /**
     * The complementary error function, to about seven figures.
     *
     * Chebyshev fit of the form Numerical Recipes gives, carried here because the platform has none and this
     * needs one. Its accuracy is checked against known values rather than assumed.
     */
    private static double erfc(double x) {
        double z = Math.abs(x)
        double t = 2.0d / (2.0d + z)
        double ty = 4.0d * t - 2.0d
        double[] coefficients = [-1.3026537197817094d, 6.4196979235649026e-1d, 1.9476473204185836e-2d,
                                 -9.561514786808631e-3d, -9.46595344482036e-4d, 3.66839497852761e-4d,
                                 4.2523324806907e-5d, -2.0278578112534e-5d, -1.624290004647e-6d,
                                 1.303655835580e-6d, 1.5626441722e-8d, -8.5238095915e-8d,
                                 6.529054439e-9d, 5.059343495e-9d, -9.91364156e-10d,
                                 -2.27365122e-10d, 9.6467911e-11d, 2.394038e-12d,
                                 -6.886027e-12d, 8.94487e-13d, 3.13092e-13d,
                                 -1.12708e-13d, 3.81e-16d, 7.106e-15d] as double[]
        double d = 0.0d, dd = 0.0d
        for (int j = coefficients.length - 1; j > 0; j--) {
            double tmp = d
            d = ty * d - dd + coefficients[j]
            dd = tmp
        }
        double answer = t * Math.exp(-z * z + 0.5d * (coefficients[0] + ty * d) - dd)
        x >= 0.0d ? answer : 2.0d - answer
    }
}
