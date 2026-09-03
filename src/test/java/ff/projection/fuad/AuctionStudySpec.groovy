package ff.projection.fuad

import spock.lang.Shared
import spock.lang.Specification

/** The held-out decision gates behind the production market signal and rejected rank-price blends. */
class AuctionStudySpec extends Specification {

    @Shared
    List<AuctionStudy.Fit> fits = AuctionStudy.of()

    /**
     * The seasons the study actually folds over, which is not every measured season.
     *
     * {@link AuctionStudy#of} filters by {@link AuctionSpend#isMeasurable}, so a season added to the
     * superflex set before its post-draft rosters land has no fold and no fit. Iterating the unfiltered
     * list found that out by dereferencing a null.
     */
    @Shared
    List<String> folded = AuctionAccuracy.MEASURED_SEASONS.findAll { AuctionSpend.isMeasurable(it) }

    private AuctionStudy.Fit fit(String season, String position, String model) {
        fits.find { it.season == season && it.position == position && it.model == model }
    }

    def "a points-shaped price improves every held-out auction"() {
        expect:
        folded.every { String season ->
            fit(season, AuctionAccuracy.ALL, 'POINTS').meanAbsolute <
                    fit(season, AuctionAccuracy.ALL, 'VOR').meanAbsolute
        }
    }

    def "points beat either production-compatible endpoint and keep quarterback's rank advantage"() {
        expect:
        fit('POOLED', AuctionAccuracy.ALL, 'POINTS').meanAbsolute <
                fit('POOLED', AuctionAccuracy.ALL, 'VOR').meanAbsolute
        fit('POOLED', AuctionAccuracy.ALL, 'POINTS').meanAbsolute <
                fit('POOLED', AuctionAccuracy.ALL, 'RANK_SHAPE').meanAbsolute

        and: 'quarterback remains substantially better than the raw rank median'
        fit('POOLED', 'QB', 'POINTS').meanAbsolute + 1.0 <
                fit('POOLED', 'QB', 'RANK_MEDIAN_RAW').meanAbsolute
    }

    def "the outcome distribution rescues much of what replacement costs, but not all of it"() {
        expect:
        fit('POOLED', AuctionAccuracy.ALL, 'VOR').meanAbsolute <
                fit('POOLED', AuctionAccuracy.ALL, 'EXPECTED_VOR').meanAbsolute
        fit('POOLED', AuctionAccuracy.ALL, 'POINTS').meanAbsolute <
                fit('POOLED', AuctionAccuracy.ALL, 'VOR').meanAbsolute
    }

    def "no rank blend beats points in every season"() {
        given:
        List<String> blends = ['BLEND_25', 'BLEND_50', 'BLEND_75',
                               'POINTS_BLEND_25', 'POINTS_BLEND_50', 'POINTS_BLEND_75']

        expect:
        blends.every { String model ->
            folded.any { String season ->
                fit(season, AuctionAccuracy.ALL, model).meanAbsolute >=
                        fit(season, AuctionAccuracy.ALL, 'POINTS').meanAbsolute
            }
        }
    }
}
