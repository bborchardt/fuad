package ff.projection.fuad

import spock.lang.Specification

/**
 * The fit behind {@link AuctionValuation#PRICE_STEEPNESS}, held to a steepness it is given rather than to
 * one it reports.
 *
 * A number produced by a search is only worth what the search is worth, and the search here is over a
 * likelihood with a censored term that has no closed form to check it against. So it is checked the other
 * way round: signings are generated at a known gamma, then censored and rounded to the dollar exactly as
 * the record is, and the fit has to find the gamma they were made with. That is the test that would fail if the ascent stopped
 * early, if the censored term were wrong, or if the error function under it were inaccurate.
 *
 * <b>Averaged over draws rather than taken from one.</b> A single set of eighty signings recovers gamma to
 * about 0.06, which is the sampling error of the thing and not a fault in it, so a test written against one
 * draw would either be loose enough to catch nothing or tight enough to fail on an unlucky seed. Taking the
 * mean over thirty draws instead tests what is actually being claimed — that the estimator is unbiased —
 * and would catch a systematic error a tenth the size of what one draw could resolve.
 */
class PriceSteepnessSpec extends Specification {

    /** Draws averaged over, which brings the standard error of the mean to about 0.01. */
    private static final int DRAWS = 30

    /**
     * Signings at a known steepness, scattered about it the way the fit assumes they are.
     *
     * {@code paid = 1 + rate * value^gamma} with a lognormal disturbance, and anything the market would have
     * cleared under the reserved dollar recorded at the minimum bid, which is what makes the cheap ones
     * censored. The rate is set from the steepness so that the best player costs about the same whatever
     * gamma is, which keeps the share of censored signings roughly where the record has it rather than
     * letting it swing from none to most as gamma moves.
     *
     * <b>Rounded down to whole dollars, because that is how the record arrives</b>, and because taking a
     * price as a point on a continuous line rather than as the dollar it was written down to is what used to
     * bias this: measured this way the old point likelihood read a market as steeper than it was by 0.02 to
     * 0.06, which is more than the sampling error of the seasons behind the constants. A fixture that
     * handed the estimator continuous prices could not see that at all.
     */
    private static List<PriceSteepness.Observation> signings(double gamma, int count, long seed,
                                                             double sigma = 0.5d) {
        Random scatter = new Random(seed)
        double rate = 100.0d / Math.pow(120.0d, gamma)
        (1..count).collect { int rank ->
            BigDecimal value = (120.0 / rank) as BigDecimal
            double paid = 1.0d + rate * Math.pow(value.toDouble(), gamma) *
                    Math.exp(sigma * scatter.nextGaussian())
            new PriceSteepness.Observation('QB', value, Math.max(1, (int) paid) as BigDecimal)
        }
    }

    private static double fittedOver(double gamma, int draws = DRAWS) {
        (1..draws).collect { int draw ->
            PriceSteepness.of(signings(gamma, 80, draw * 7919L)).find { it.position == 'QB' }
                    .gamma.toDouble()
        }.sum() / draws
    }

    def "recovers the steepness its signings were made at"() {
        expect: 'flat, level and steep alike, so the answer is not the starting point or the middle'
        [0.7d, 1.0d, 1.4d].every { double gamma -> Math.abs(fittedOver(gamma) - gamma) < 0.03d }
    }

    def "counts what it fitted, the censored signings included"() {
        given:
        List<PriceSteepness.Observation> observations = signings(1.4d, 80, 7919L)

        when:
        PriceSteepness.Fit fit = PriceSteepness.of(observations).find { it.position == 'QB' }

        then: 'enough go at the minimum bid for their treatment to be the whole question'
        fit.censored >= 10
        fit.signings == 80
        fit.censored == observations.count { it.censored }
    }

    /**
     * Dropping the minimum-bid signings is what this fit exists not to do, and this says what it would cost.
     *
     * Selection on the outcome pulls a log-log slope toward zero, so a fit over the uncensored alone reads a
     * steep market as a flatter one. On the real signings it is worth 0.2 to 0.3 of gamma at every position
     * that carries money, which is larger than the gap between the fitted constants and the ones they
     * replaced.
     */
    def "reads a censored market as flatter than it is when the cheap signings are dropped"() {
        given: 'the same signings, fitted whole and fitted over the uncensored alone'
        double whole = fittedOver(1.4d)
        double truncated = (1..DRAWS).collect { int draw ->
            List<PriceSteepness.Observation> all = signings(1.4d, 80, draw * 7919L)
            PriceSteepness.of(all.findAll { !it.censored }).find { it.position == 'QB' }.gamma.toDouble()
        }.sum() / DRAWS

        expect: 'throwing them away costs real steepness, and keeping them is what is right'
        truncated < whole - 0.05d
        Math.abs(whole - 1.4d) < Math.abs(truncated - 1.4d)
    }

    def "declines to fit a position with too few signings to make a claim"() {
        expect: 'no row rather than a steepness invented from a handful of contracts'
        PriceSteepness.of(signings(1.2d, PriceSteepness.MINIMUM_SIGNINGS - 1, 7919L)).isEmpty()
        !PriceSteepness.of(signings(1.2d, PriceSteepness.MINIMUM_SIGNINGS, 7919L)).isEmpty()
    }

    /**
     * A position nobody bids over the minimum on says nothing about steepness, and must not be made to.
     *
     * With no interval bounded above, the likelihood climbs without limit as the line is pushed down and the
     * search stops wherever it ran out of steps — which is a number, and looks like a measurement, and is
     * neither. Kicker is already half censored, so this is a floor the record could reach.
     */
    def "declines to fit a position bought entirely at the minimum bid"() {
        given: 'every signing at a dollar, and then all but a handful'
        List<PriceSteepness.Observation> all = (1..20).collect { int rank ->
            new PriceSteepness.Observation('QB', (120.0 / rank) as BigDecimal, 1.0 as BigDecimal)
        }
        List<PriceSteepness.Observation> nearly = all.take(18) +
                (19..20).collect { int rank ->
                    new PriceSteepness.Observation('QB', (120.0 / rank) as BigDecimal, 9.0 as BigDecimal)
                }

        expect: 'neither is fitted, where both used to come back with a steepness'
        PriceSteepness.of(all).isEmpty()
        PriceSteepness.of(nearly).isEmpty()

        and: 'while enough of them clearing the reserve is fitted as usual'
        !PriceSteepness.of(signings(1.2d, 40, 7919L)).isEmpty()
    }

    def "puts the normal distribution where it belongs, which the censored term rests on"() {
        expect: 'the tail especially, which is where a censored signing contributes from'
        [[0.0d, 0.500000000000d], [1.0d, 0.841344746069d], [-1.0d, 0.158655253931d],
         [1.96d, 0.975002104852d], [-2.5758d, 0.005000423738d], [-3.0d, 0.001349898032d],
         [4.0d, 0.999968328758d]].every { List pair ->
            Math.abs(PriceSteepness.standardNormalBelow(pair[0] as double) - (pair[1] as double)) < 1e-9d
        }
    }
}
