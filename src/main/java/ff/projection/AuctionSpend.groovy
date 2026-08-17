package ff.projection

import ff.load.util.LoadUtils

/**
 * What the league actually paid at auction, season by season and position by position.
 *
 * This is where {@link AuctionValuation#MARKET_SHARE} and {@link AuctionValuation#SPEND_RATE} come from.
 * They are measurements rather than choices, so the measurement belongs in the model beside the constants
 * it produced, and not in a spec that happens to check them — it was in a spec, which meant the figures the
 * documentation quoted about the league's spending were computed by test code and could not be generated,
 * cited or checked. See docs/PROJECTION.md.
 *
 * <b>A signing is a wiped contract that came back.</b> An expiring player carries {@link #WIPED_SALARY} on
 * the pre-draft roster; if he is on the post-draft roster too, what he carries there is what somebody paid
 * for him. Everything else on the pre-draft roster is a contract already running, and is committed money
 * rather than auction money.
 *
 * <b>Counted over distinct players, never over roster rows.</b> The week 1 snapshots repeat a handful of
 * rows verbatim — same franchise, same salary — and summing rows counts those contracts twice. Cooper
 * Kupp's 94 in 2022 is one of them, and it is worth about three points of the spend rate.
 */
class AuctionSpend {

    /** What an expiring contract is written down to before the auction, which is what marks it expiring. */
    private static final BigDecimal WIPED_SALARY = new BigDecimal('0.01')

    /** Positions the auction spends on, kickers included, which is the basis a share of the pot is taken on. */
    static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE', 'PK'].asImmutable() as List<String>

    /**
     * The same without kickers, which is the basis the positional comparison is read on.
     *
     * A kicker takes well under 1% of any auction and has no curve behind him, so leaving him in the
     * denominator moves every other position by a few tenths for no reason anybody is interested in. The
     * two bases differ, and carrying both is what stops a table on one being read as a figure on the other.
     */
    static final List<String> EXCLUDING_KICKERS = ['QB', 'RB', 'WR', 'TE'].asImmutable() as List<String>

    /** Every season played under superflex, which is as far back as a share of this pot means anything. */
    static final List<String> SUPERFLEX_SEASONS = ['2022', '2023', '2024', '2025'].asImmutable() as List<String>

    /**
     * The seasons the calibration is actually fitted over.
     *
     * 2022 is excluded deliberately: superflex arrived that year and the league had not adjusted to it. It
     * is measured and reported all the same, because the case for dropping it is the figures themselves and
     * a reader cannot check an exclusion whose evidence is not shown.
     */
    static final List<String> CALIBRATED_SEASONS = ['2023', '2024', '2025'].asImmutable() as List<String>

    /** One season's auction, as the money actually moved. */
    static class Season {
        final String season
        /** Auction dollars by position. */
        final Map<String, BigDecimal> dollars
        /** Cap the league had free once contracts already running were paid for. */
        final BigDecimal freeCap
        /** What the rookie draft cost, which is spoken for before any bidding and comes off the top. */
        final BigDecimal rookieDollars
        /** Rookies actually on a week 1 roster, against the picks the draft had to give out. */
        final int rookiesRostered
        /** Teams that season, the league having contracted to eight and expanded back to ten. */
        final int teams

        Season(String season, Map<String, BigDecimal> dollars, BigDecimal freeCap, BigDecimal rookieDollars,
               int rookiesRostered, int teams) {
            this.season = season
            this.dollars = dollars
            this.freeCap = freeCap
            this.rookieDollars = rookieDollars
            this.rookiesRostered = rookiesRostered
            this.teams = teams
        }

        BigDecimal getSpent() { (dollars.values().sum() ?: 0.0) as BigDecimal }

        /** Share of the free cap the league spent, which is what {@code SPEND_RATE} averages. */
        BigDecimal getSpendRate() { freeCap > 0 ? spent / freeCap : 0.0 }

        /** What rookies cost as a share of the auction, which is what {@code ROOKIE_BUDGET_SHARE} reserves. */
        BigDecimal getRookieShare() { spent > 0 ? rookieDollars / spent : 0.0 }
    }

    /** What one season's auction paid each position, and what it had to spend. */
    static Season of(String season) {
        Map<String, List> preDraft = byPlayer(LoadUtils.mflRostersResourcePath(season))
        Map<String, List> postDraft = byPlayer(LoadUtils.mflPostDraftRostersResourcePath(season))
        List<Map> players = LoadUtils.loadJsonResource(
                LoadUtils.mflPlayersResourcePath(season)).players.player as List<Map>
        Map<String, String> positionById = players.collectEntries { [(it.id as String): it.position as String] }
        Map<String, String> statusById = players.collectEntries { [(it.id as String): (it.status ?: '') as String] }

        Map<String, BigDecimal> dollars = [:].withDefault { 0.0 as BigDecimal }
        preDraft.each { String id, List held ->
            String position = positionById[id]
            if (expiring(held) && postDraft.containsKey(id) && POSITIONS.contains(position)) {
                dollars[position] += postDraft[id][1] as BigDecimal
            }
        }

        // A rookie was on no pre-draft roster, so his contract is never an auction signing. It is money the
        // league has committed all the same, and the auction has that much less to divide.
        Map<String, List> rookies = postDraft.findAll { String id, List held -> statusById[id] == 'R' }
        BigDecimal rookieDollars =
                (rookies.values().collect { it[1] as BigDecimal }.sum() ?: 0.0) as BigDecimal

        Map league = LoadUtils.loadJsonResource(LoadUtils.mflLeagueResourcePath(season)) as Map
        int teams = (league.league.franchises.franchise as List).size()
        BigDecimal cap = (league.league.salaryCapAmount as String as BigDecimal) * teams
        BigDecimal committed = (preDraft.values().findAll { !expiring(it) }
                .collect { it[1] as BigDecimal }.sum() ?: 0.0) as BigDecimal

        new Season(season, POSITIONS.collectEntries { [(it): dollars[it]] }, cap - committed,
                rookieDollars, rookies.size(), teams)
    }

    /**
     * Share of the auction each position took, over however many seasons are pooled.
     *
     * Pooled by dollars rather than by averaging each season's share, so a season with a larger auction
     * counts for more of the answer — which is what a share of the pot means.
     */
    static Map<String, BigDecimal> shareByPosition(List<Season> seasons, List<String> positions = POSITIONS) {
        Map<String, BigDecimal> dollars = positions.collectEntries { String position ->
            [(position): (seasons.collect { it.dollars[position] ?: 0.0 }.sum() ?: 0.0) as BigDecimal]
        }
        BigDecimal total = (dollars.values().sum() ?: 0.0) as BigDecimal
        dollars.collectEntries { String position, BigDecimal paid ->
            [(position): total > 0 ? paid / total : 0.0 as BigDecimal]
        }
    }

    /** Player id to [franchise, salary], keeping the first row for each player. */
    private static Map<String, List> byPlayer(String resourcePath) {
        Map<String, List> held = [:]
        (LoadUtils.loadJsonResource(resourcePath).rosters.franchise as List<Map>).each { Map franchise ->
            def rostered = franchise.player ?: []
            ((rostered instanceof List ? rostered : [rostered]) as List<Map>).each { Map player ->
                held.putIfAbsent(player.id as String,
                        [franchise.id as String, new BigDecimal(player.salary as String)])
            }
        }
        held
    }

    /** Compared rather than equated, so a salary written 0.010 is the same wiped contract as 0.01. */
    private static boolean expiring(List held) {
        (held[1] as BigDecimal).compareTo(WIPED_SALARY) == 0
    }
}
