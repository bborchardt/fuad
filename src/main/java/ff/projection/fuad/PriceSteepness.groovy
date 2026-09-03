package ff.projection.fuad

import ff.data.PlayerValuation

/**
 * How much the league stretches a supplied positive signal, fitted from what it actually paid.
 *
 * This is where {@link AuctionValuation#PRICE_STEEPNESS} comes from, and it exists because that constant had
 * nowhere to come from. Its VOR predecessor was fitted once, by hand, against a column the model later changed
 * twice, and nothing recomputed it or could have noticed — {@code check_docs.sh} holds the prose to the
 * figures and {@link AuctionAccuracy} now holds the board to the record, but a number nothing regenerates is
 * outside both. See docs/TODO.md.
 *
 * <b>The estimator is the model's own arithmetic read backwards, not a choice.</b> {@link AuctionValuation}
 * bends a position's shares to {@code share_i ∝ signal_i^gamma}, renormalises them to that position's own
 * total, and prices each player at {@code 1 + rate * share_i}. Taking logs of the part above the reserved
 * minimum bid:
 *
 * <pre>
 *     log(paid - 1) = a + gamma * log(signal)
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

    /** One player somebody bid on, and the positive model signal being fitted. */
    static class Observation {
        final String position
        /** The quantity {@code steepen} raises to the power of gamma. */
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
        /**
         * The bottom of the profile-likelihood interval on gamma, which is how much of a claim it really is.
         *
         * {@code SIGMA} says how far the signings scatter about the line; this says how far the line itself
         * could be moved and still describe them. They are different questions and only the second bears on
         * whether a steepness is worth acting on. See {@link #profileInterval}.
         */
        final BigDecimal gammaLow
        /** The top of the same interval. */
        final BigDecimal gammaHigh

        Fit(String position, int signings, int censored, BigDecimal gamma, BigDecimal sigma,
            BigDecimal gammaLow, BigDecimal gammaHigh) {
            this.position = position
            this.signings = signings
            this.censored = censored
            this.gamma = gamma
            this.sigma = sigma
            this.gammaLow = gammaLow
            this.gammaHigh = gammaHigh
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
     * And below this many that cleared the reserve, whatever the total.
     *
     * A position bought entirely at the minimum bid says nothing about how steeply it is bid, and the
     * likelihood knows it: with no interval bounded above, it climbs without limit as the line is pushed
     * down, and the search stops wherever it ran out of steps. Twenty signings all at a dollar returned a
     * steepness of -0.5 and eighteen of twenty returned 4.41, neither of them a measurement. Kicker is
     * already half censored, so this is a floor the record could reach rather than a theoretical one.
     */
    static final int MINIMUM_CLEARING_RESERVE = 8

    /**
     * The seasons the steepness is fitted over: the calibrated ones the record can price and score.
     *
     * The same set wherever it is asked for. The figure and the spec that holds the constant to it used to
     * derive it separately — one from the boards a figures run happened to have priced, the other from
     * {@link AuctionSpend#CALIBRATED_SEASONS} entire — so a season present to one and absent from the other
     * would have left GAMMA and INFORCE describing two different fits while agreeing to the hundredth.
     */
    static List<String> fittedSeasons() {
        AuctionSpend.CALIBRATED_SEASONS.findAll { AuctionSpend.isMeasurable(it) }
    }

    /**
     * Every signing of the fitted seasons, joined to what the board said that player was worth.
     *
     * Joined on the MFL id like {@link AuctionAccuracy}, and for the same reason: a board row and a roster
     * row carry the same identifier, so no name matching stands between the record and the fit.
     *
     * <b>A signing the board valued at nothing is left out, and that is not the selection it looks like.</b>
     * Eleven of the 257 are, and two thirds of those went at the minimum bid, so dropping them by any rule
     * that noticed their price would bias the fit exactly as truncation does. This rule does not notice
     * their price: a player worth nothing has a share of {@code 0^gamma}, which is zero at every steepness,
     * so he says nothing about the slope and cannot be made to. The log of his value is undefined for the
     * same reason it is uninformative.
     */
    static List<Observation> observationsFrom(Map<String, List<PlayerValuation>> boardsBySeason,
                                              Collection<String> seasons = fittedSeasons(),
                                              Closure<BigDecimal> valueOf =
                                                      { PlayerValuation it -> it.valueOverReplacement }) {
        boardsBySeason.findAll { seasons.contains(it.key) }
                .collectMany { String season, List<PlayerValuation> board ->
                    Map<String, BigDecimal> worth = board.collectEntries {
                        [(it.playerId): valueOf(it)]
                    }
                    AuctionSpend.signings(season)
                            .findAll { worth[it.playerId] != null && worth[it.playerId] > 0 }
                            .collect { new Observation(it.position, worth[it.playerId], it.paid) }
                }
    }

    /**
     * One fit per position that has enough signings, and enough of them priced, to make a claim.
     *
     * <b>{@code profiled} is off by default because the interval costs a few hundred ascents a position and
     * nothing that prices a board wants it.</b> {@link AuctionStudy} refits the steepness once per candidate
     * per fold and reads only {@code gamma}; the interval is a claim about the constants themselves, so it
     * is computed where they are reported and nowhere else.
     */
    static List<Fit> of(List<Observation> observations, boolean profiled = false) {
        observations.groupBy { it.position }
                .findAll { String position, List<Observation> at ->
                    at.size() >= MINIMUM_SIGNINGS && at.count { !it.censored } >= MINIMUM_CLEARING_RESERVE
                }
                .collect { String position, List<Observation> at -> fitOf(position, at, profiled) }
                .sort { AuctionSpend.POSITIONS.indexOf(it.position) }
    }

    private static Fit fitOf(String position, List<Observation> at, boolean profiled) {
        double[] start = leastSquares(at)
        double[] fitted = maximise(at, start)
        double[] interval = profiled ? profileInterval(at, fitted) : null
        new Fit(position, at.size(), at.count { it.censored }, fitted[1] as BigDecimal,
                fitted[2] as BigDecimal,
                interval == null ? null : interval[0] as BigDecimal,
                interval == null ? null : interval[1] as BigDecimal)
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
     * The likelihood of a set of signings under one line, every one of them read as the interval it is.
     *
     * <b>A price is a whole number of dollars, and treating it as a point on a continuous line is what
     * biases this.</b> An auction settles somewhere and the league writes down the dollar; a contract at $7
     * says the market cleared between seven and eight, not at seven exactly. Taken as a point that
     * understates every price by part of a dollar, and understates the cheap ones by proportionally far
     * more, which tilts the line and reads a market as steeper than it is — measured against synthetic
     * signings priced at a known steepness and then rounded, by 0.02 to 0.06, which is larger than the
     * sampling error of the seasons behind it.
     *
     * So every signing contributes the mass between the two prices it could have been, and the minimum bid
     * is not a special case but the lowest such interval with nothing under it: a dollar signing says the
     * market cleared below the reserved dollar and no more than that. One formulation covers both, which is
     * the point — the censored term was right and the uncensored one was the approximation.
     */
    private static double logLikelihood(List<Observation> at, double intercept, double gamma, double sigma) {
        if (sigma <= 0) {
            return -Double.MAX_VALUE
        }
        double total = 0.0d
        for (Observation observation : at) {
            double mean = intercept + gamma * Math.log(observation.value.toDouble())
            double paid = observation.paid.toDouble()
            // Above the reserved dollar the price is somewhere in [paid, paid + 1), so what is above the
            // reserve is in [paid - 1, paid) and its log in [log(paid - 1), log(paid)).
            double upper = standardNormalBelow((Math.log(paid) - mean) / sigma)
            double lower = observation.censored ? 0.0d
                    : standardNormalBelow((Math.log(paid - MINIMUM_BID.toDouble()) - mean) / sigma)
            total += Math.log(Math.max(1e-300d, upper - lower))
        }
        total
    }

    /**
     * Coordinate ascent with a halving step, which is enough for a surface this well behaved.
     *
     * A search rather than a formula because an interval likelihood has no closed form. It is started from
     * the least-squares line through the signings that clear the reserve, which is near enough that the
     * ascent has a short way to travel. The surface is not concave in these coordinates — a Tobit
     * likelihood is concave only after reparameterising, and this is not that — so what stands behind the
     * answer is the check rather than the shape: {@code PriceSteepnessSpec} holds it to a steepness it is
     * given by construction, over synthetic signings censored and rounded the same way the record is.
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
     * Half of the 95% chi-square point on one degree of freedom, which is what a profile interval drops by.
     *
     * The interval is every gamma the record cannot reject against the one it likes best: hold gamma at a
     * trial value, let the intercept and the scatter go wherever they like underneath it, and keep the trial
     * if the best likelihood it can reach is within this of the unrestricted maximum.
     */
    private static final double PROFILE_DROP = 1.920729d

    /** How far either side of the estimate the search will look before reporting the bound as unreached. */
    private static final double PROFILE_REACH = 8.0d

    /**
     * The first step out from the estimate, doubled until the drop is passed.
     *
     * Geometric rather than fixed because the widths this has to cover span two orders of magnitude — a
     * well-fitted position bounds gamma inside a tenth, and a position bought at the minimum bid does not
     * bound it at all — and every step out costs a full ascent over the nuisance parameters.
     */
    private static final double PROFILE_STEP = 0.125d

    /**
     * How far gamma could be moved and still describe these signings, which is not what {@code SIGMA} says.
     *
     * <b>A steepness with no interval on it reads as a measurement when it may be an artefact of the range
     * the market was allowed to operate over.</b> The point estimate is the top of a surface, and the
     * surface can be almost flat: a position whose signings span a narrow band of the fitted signal
     * constrains the slope through that band hardly at all, and the estimator will still return a number to
     * two decimal places. Receiver is the case that motivated this — no signing at the top rank in four
     * seasons, eight of ninety-nine inside the top five, and the largest exponent on the board.
     *
     * The bound is a bisection on the profile rather than a curvature approximation, because the surface is
     * not symmetric in gamma and the asymmetry is the informative part: the record bounds a steepness from
     * below far better than from above, the observations that would bound it from above being exactly the
     * ones the franchise tag removes.
     *
     * A bound that reaches {@link #PROFILE_REACH} is returned at the reach and means what it says — that the
     * signings do not bound gamma on that side at all, and no width should be read into the figure.
     */
    private static double[] profileInterval(List<Observation> at, double[] fitted) {
        double target = logLikelihood(at, fitted[0], fitted[1], fitted[2]) - PROFILE_DROP
        [profileBound(at, fitted, target, -1.0d), profileBound(at, fitted, target, 1.0d)] as double[]
    }

    private static double profileBound(List<Observation> at, double[] fitted, double target, double towards) {
        double inside = fitted[1]
        double outside = Double.NaN
        for (double reach = PROFILE_STEP; reach <= PROFILE_REACH; reach *= 2.0d) {
            double trial = fitted[1] + towards * Math.min(reach, PROFILE_REACH)
            if (profileAt(at, fitted, trial) < target) {
                outside = trial
                break
            }
            inside = trial
        }
        if (Double.isNaN(outside)) {
            return inside
        }
        for (int halving = 0; halving < PROFILE_HALVINGS; halving++) {
            double middle = 0.5d * (inside + outside)
            if (profileAt(at, fitted, middle) < target) {
                outside = middle
            } else {
                inside = middle
            }
        }
        0.5d * (inside + outside)
    }

    private static final int PROFILE_HALVINGS = 24

    /**
     * How closely the nuisance ascent is run, which is looser than the fit itself and deliberately so.
     *
     * The profile is only ever compared against a target, so it needs enough precision to place the bound
     * to a hundredth of a gamma and no more. Held to the fit's own tolerance it costs several times as much
     * and moves no reported figure.
     */
    private static final double PROFILE_TOLERANCE = 1e-4d

    /** The best this set of signings can do with gamma held where it is put. */
    private static double profileAt(List<Observation> at, double[] fitted, double gamma) {
        double[] best = [fitted[0], gamma, fitted[2]] as double[]
        double[] step = [1.0d, 0.0d, 0.5d] as double[]
        double value = logLikelihood(at, best[0], gamma, best[2])
        for (int round = 0; round < MAXIMUM_ROUNDS; round++) {
            boolean improved = false
            for (int parameter : [0, 2]) {
                for (double direction : [step[parameter], -step[parameter]]) {
                    double[] trial = best.clone()
                    trial[parameter] += direction
                    if (trial[2] <= 0) {
                        continue
                    }
                    double candidate = logLikelihood(at, trial[0], gamma, trial[2])
                    if (candidate > value) {
                        value = candidate
                        best = trial
                        improved = true
                    }
                }
            }
            if (!improved) {
                [0, 2].each { step[it] *= 0.5d }
                if (Math.max(step[0], step[2]) < PROFILE_TOLERANCE) {
                    return value
                }
            }
        }
        value
    }

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
