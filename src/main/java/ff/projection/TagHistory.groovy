package ff.projection

import ff.data.FranchiseTag
import ff.load.util.LoadUtils

/**
 * Every franchise tag the league has used, recovered season by season.
 *
 * <b>This is the one thing in the repository that is reconstructed rather than read.</b> The league records
 * a tag nowhere, so {@link FranchiseTagIdentifier} infers all 46 of them from what they leave behind — a
 * contract wiped and re-signed at exactly the rate, or a first round pick moving with nothing coming back.
 * Every other figure in docs/LEAGUE_RULES.md is a rule that could be looked up; these are the model's own
 * account of what happened, which makes them the figures most worth checking and the ones hardest to check
 * by eye.
 *
 * The per-season loading lived in two specs, which is why none of it could be generated into a figure. It
 * lives here so the documentation and the specs read one implementation. See docs/LEAGUE_RULES.md.
 */
class TagHistory {

    /**
     * Seasons a tag can be recovered for.
     *
     * From 2018, because the rate is the average of the previous season's top five and 2017 has no 2016
     * behind it. To 2025, because recovering a tag needs an auction that has been held: the season being
     * priced has no post-draft rosters and no transactions yet.
     */
    static final List<String> SEASONS =
            (2018..2025).collect { it as String }.asImmutable() as List<String>

    /**
     * Seasons a franchise salary can be computed for, which runs one further than the tags do.
     *
     * The season being priced has a rate — it is set by the previous season's salaries, which are known —
     * even though its auction has not happened and so carries no tags.
     */
    static final List<String> RATE_SEASONS =
            (2018..2026).collect { it as String }.asImmutable() as List<String>

    /**
     * What tagging a player at each position cost, coming into this season's auction.
     *
     * Read from the previous season's end-of-year rosters and that season's player list, since a player's
     * position has to be taken from the year the salary was paid in. See {@link FranchiseSalaryCalculator}.
     */
    static Map<String, Integer> franchiseSalaries(String season) {
        String prior = ((season as int) - 1) as String
        FranchiseSalaryCalculator.franchiseSalaries(
                LoadUtils.loadJsonResource(LoadUtils.mflEndOfYearRostersResourcePath(prior)) as Map,
                LoadUtils.loadJsonResource(LoadUtils.mflPlayersResourcePath(prior)) as Map)
    }

    /** Every tag inferred for one season, in whatever order the identifier found them. */
    static List<FranchiseTag> tags(String season) {
        FranchiseTagIdentifier.tags(
                LoadUtils.loadJsonResource(LoadUtils.mflRostersResourcePath(season)) as Map,
                LoadUtils.loadJsonResource(LoadUtils.mflPostDraftRostersResourcePath(season)) as Map,
                LoadUtils.loadJsonResource(LoadUtils.mflTransactionsResourcePath(season)) as Map,
                LoadUtils.loadJsonResource(LoadUtils.mflPlayersResourcePath(season)) as Map,
                franchiseSalaries(season))
    }

    /** Every season's tags, keyed by season, in the order the seasons were played. */
    static Map<String, List<FranchiseTag>> tagsBySeason() {
        SEASONS.collectEntries { [(it): tags(it)] } as Map<String, List<FranchiseTag>>
    }

    /**
     * How the documentation words a tag's basis, which is not how the model names it.
     *
     * The prose says <i>exact</i>, or <i>bid away, 0010 to 0005</i>, because that is what a reader wants to
     * know: whether the team kept him at the rate or somebody paid picks to take him. The model names the
     * evidence rather than the outcome. Rendering it here keeps the prose readable and still checkable —
     * written without the comma, since a figure is compared after the punctuation a document dresses it in
     * has been stripped.
     */
    static String basisOf(FranchiseTag tag) {
        tag.basis == FranchiseTag.Basis.PICK_COMPENSATED ?
                "bid away $tag.taggingFranchiseId to $tag.signingFranchiseId" : 'exact'
    }

    /** The player as a reader writes him, the league data holding him the other way round. */
    static String readableName(FranchiseTag tag) {
        LoadUtils.nameFirstThenLast(tag.playerName)
    }
}
