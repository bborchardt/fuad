package ff.load

import ff.data.RealisedSeason
import ff.data.fantasypros.FpRankedPlayer
import ff.league.League
import ff.load.nflverse.NflverseStatsLoader
import ff.load.util.LoadUtils

/**
 * What players actually scored, restated under the rules being priced and indexed by the consensus rank they
 * held before that season.
 *
 * Indexing by rank rather than by player is what the whole model rests on. A ranking is a judgement about who
 * is better, which is what consensus is for; how much scoring that judgement has been worth is a question
 * only finished seasons can answer. Nothing here is a forecast of a particular player, so nothing here can be
 * dragged around by one source's opinion of one man.
 *
 * A preseason rank also cannot be revised after the fact, which a projection can. That is what keeps this
 * honest where comparing a finished season's projections against its own results would only measure hindsight.
 *
 * <b>Shared between leagues because none of it is about a league.</b> Which rules a season is restated under
 * and which ranking supplies the order are both parameters; everything else — matching a ranked name to a
 * statistics line, and what to do when it cannot be matched — is the same problem whoever is asking. It was
 * private to the auction loader until there was a second league that needed it, and the part worth not
 * copying is {@link #claim}, where the careless version of the same twenty lines is silently wrong.
 *
 * See docs/PROJECTION.md.
 */
class RealisedSeasons {

    /**
     * Prefix lengths tried in turn when a ranked name has no exact match in the statistics.
     *
     * Longest first, and a name is taken out of the pool once it is claimed, so the specific matches are made
     * before the loose ones get a chance to go wrong. This is what tells Gabe from Gabriel and Kenny from
     * Kenneth. A rank left unmatched after all three is scored as a zero, not dropped.
     */
    private static final List<Integer> MATCH_LENGTHS = [10, 5, 3].asImmutable()

    /**
     * Every ranked season the league's statistics can speak to, keyed by position and positional rank.
     *
     * @param league  whose scoring every season is restated under, and whose positions bound what is kept
     * @param ranked  the consensus ranking that gave each season its order, a season at a time
     */
    static Map<String, Map<Integer, List<RealisedSeason>>> byRank(
            League league, Closure<Collection<FpRankedPlayer>> ranked) {
        Map<String, Map<Integer, List<RealisedSeason>>> realised = [:].withDefault { [:].withDefault { [] } }
        league.seasons.each { String season ->
            Map<String, BigDecimal> scored = NflverseStatsLoader.seasonPoints(season, league.scoring)
                    .collectEntries { String name, BigDecimal points -> [(LoadUtils.aliasedName(name)): points] }
            Map<String, Integer> games = NflverseStatsLoader.gamesPlayed(season)
                    .collectEntries { String name, Integer played -> [(LoadUtils.aliasedName(name)): played] }
            Set<String> unclaimed = NflverseStatsLoader.played(season).collect { LoadUtils.aliasedName(it) } as Set
            ranked(season).each { FpRankedPlayer player ->
                if (league.scoredPositions.contains(player.player.position)) {
                    String name = claim(unclaimed, player.player.name)
                    // No stat line at all is a season that never happened: no points and no games, which is
                    // an observation about availability and none about how he plays.
                    realised[player.player.position][player.rank.positionRank] << new RealisedSeason(
                            points: name ? scored[name] : 0.0 as BigDecimal,
                            games: name ? (games[name] ?: 0) : 0)
                }
            }
        }
        realised
    }

    /**
     * The statistics line belonging to a ranked player, or null where he has none at all.
     *
     * Null means he scored nothing and has to be carried as a zero rather than dropped. Ranked seasons inside
     * the depth a league rosters do sometimes never happen — Andrew Luck's 2017 shoulder, Le'Veon Bell's 2018
     * holdout, Gus Edwards' 2021 knee, Joe Mixon's 2025 foot — and they are exactly the seasons that busted
     * hardest. Leaving them out biases every curve upward and cuts off the left tail that a bench is priced
     * against.
     *
     * Which is why the name matching has to be careful before it gives up: a name that failed to match looks
     * identical to a season that never happened, and quietly becomes a zero that never was.
     */
    private static String claim(Set<String> unclaimed, String name) {
        if (unclaimed.remove(name)) {
            return name
        }
        for (int length : MATCH_LENGTHS) {
            List<String> matches = unclaimed
                    .findAll { LoadUtils.isNameMatch(it.toUpperCase(), name.toUpperCase(), length) }.toList()
            if (matches.size() == 1) {
                unclaimed.remove(matches.first())
                return matches.first()
            }
            if (matches.size() > 1) {
                return null
            }
        }
        null
    }
}
