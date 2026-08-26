package ff.projection.fuad

import ff.load.util.LoadUtils

/**
 * What the league actually paid at auction, season by season and position by position.
 *
 * This is where {@link AuctionValuation#MARKET_SHARE} and {@link AuctionValuation#SPEND_RATE} come from.
 * They are measurements rather than choices, so the measurement belongs in the model beside the constants
 * it produced, and not in a spec that happens to check them — it was in a spec, which meant the figures the
 * documentation quoted about the league's spending were computed by test code and could not be generated,
 * cited or checked. See docs/fuad/PROJECTION.md.
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

    /**
     * Transaction types that put a player on a roster without anybody bidding at the auction for him.
     *
     * The auction has no type of its own — see {@link #pickedUpAfterAuction} — so it is what remains after
     * these. A trade is in here because a traded player was paid for by whoever signed him originally, and
     * counting him again would count one contract twice.
     */
    private static final List<String> NON_AUCTION_MOVES =
            ['FREE_AGENT', 'BBID_WAIVER', 'BBID_AUTO_PROCESS_WAIVERS', 'TRADE'].asImmutable() as List<String>

    /** What an expiring contract is written down to before the auction, which is what marks it expiring. */
    private static final BigDecimal WIPED_SALARY = new BigDecimal('0.01')

    /** Positions the auction spends on, kickers included, which is the basis a share of the pot is taken on. */
    static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE', 'PK'].asImmutable() as List<String>

    /**
     * The same without kickers, which is the basis the positional comparison is read on.
     *
     * A kicker takes well under 1% of any auction, so leaving him in the denominator moves every other
     * position by a few tenths — small enough to be mistaken for rounding and large enough to matter to a
     * constant compared against them. Carrying both is what stops a table on one basis being read as a
     * figure on the other. {@link AuctionValuation#MARKET_SHARE} is on the whole-pot basis, kickers being
     * levelled and priced like everyone else; the documentation tabulates the four-position one.
     */
    static final List<String> EXCLUDING_KICKERS = ['QB', 'RB', 'WR', 'TE'].asImmutable() as List<String>

    /**
     * Every season whose auction can actually be measured, which is not quite every season on record.
     *
     * Wider than the calibration, deliberately. A share of the pot is only comparable across seasons with
     * the same lineup, so {@link AuctionValuation#MARKET_SHARE} is fitted on the superflex ones alone — but
     * the <b>spend rate</b> is a share of the cap rather than of a position, and the range the
     * documentation states for it is a claim about the whole record. A range stated over nine seasons and
     * checked over three is a range nothing checks.
     *
     * <b>2021 is out, and the spec is what put it out.</b> That season contracted the league from ten teams
     * to eight and its pre-draft snapshot was taken afterwards, so the 47 contracts the departing owners
     * released are on no pre-draft roster and every one of them reads as a player somebody bid on. Measured
     * that way the season spends 1.09 of the cap it had free, which is not a league bidding hard but a
     * measurement that has stopped measuring — see {@code AuctionValuationSpec}. Nothing here can tell those
     * contracts from genuine signings, so the season is excluded rather than guessed at.
     *
     * It is excluded from the <b>measurement</b> only. 2021 is a real season and its rosters, contracts and
     * statistics are used everywhere else.
     */
    static final List<String> RECORD_SEASONS =
            ((2017..2020) + (2022..2025)).collect { it as String }.asImmutable() as List<String>

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
        /**
         * Salary already running by position, which is the other side of the repricing.
         *
         * A league can look as though it has changed what it pays for when all that happened is a stock of
         * old contracts expiring. Committed money answers that: if the auction is buying more quarterback
         * and the contracts already on the books are also becoming more quarterback, nothing is running off.
         */
        final Map<String, BigDecimal> committed
        /** What the rookie draft cost, which is spoken for before any bidding and comes off the top. */
        final BigDecimal rookieDollars
        /**
         * Of {@link #dollars}, what went to veterans who were on no pre-draft roster.
         *
         * Carried so the transaction-log identification can be checked rather than trusted. It rests on
         * MFL's own type names — see {@link #NON_AUCTION_MOVES} — and if those ever change, this silently
         * goes to nothing or swallows every waiver pickup in the league. Neither would show up in a price.
         */
        final BigDecimal freeAgentDollars
        /** Rookies actually on a week 1 roster, against the picks the draft had to give out. */
        final int rookiesRostered
        /** Teams that season, the league having contracted to eight and expanded back to ten. */
        final int teams

        Season(String season, Map<String, BigDecimal> dollars, BigDecimal freeCap,
               Map<String, BigDecimal> committed, BigDecimal rookieDollars, BigDecimal freeAgentDollars,
               int rookiesRostered, int teams) {
            this.season = season
            this.dollars = dollars
            this.freeCap = freeCap
            this.committed = committed
            this.rookieDollars = rookieDollars
            this.freeAgentDollars = freeAgentDollars
            this.rookiesRostered = rookiesRostered
            this.teams = teams
        }

        BigDecimal getSpent() { (dollars.values().sum() ?: 0.0) as BigDecimal }

        /** Total salary already running, which is what free cap is the cap less. */
        BigDecimal getCommittedTotal() { (committed.values().sum() ?: 0.0) as BigDecimal }

        /** What share of money already committed each position holds. */
        Map<String, BigDecimal> getCommittedShare() {
            BigDecimal total = committedTotal
            committed.collectEntries { String position, BigDecimal held ->
                [(position): total > 0 ? held / total : 0.0 as BigDecimal]
            }
        }

        /** Share of the free cap the league spent, which is what {@code SPEND_RATE} averages. */
        BigDecimal getSpendRate() { freeCap > 0 ? spent / freeCap : 0.0 }

        /** What rookies cost as a share of the auction, which is what {@code ROOKIE_BUDGET_SHARE} reserves. */
        BigDecimal getRookieShare() { spent > 0 ? rookieDollars / spent : 0.0 }

        /** What share of the auction went to players who reached it from outside the pre-draft rosters. */
        BigDecimal getFreeAgentShare() { spent > 0 ? freeAgentDollars / spent : 0.0 }
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

        // And the veterans who were on no pre-draft roster at all, who are bid on at the same auction and
        // whose money the pot used to omit. Told from an in-season pickup by the transaction log rather
        // than by their salary: the auction arrives as a roster load and a waiver claim does not.
        Set<String> pickedUp = pickedUpAfterAuction(season)
        BigDecimal freeAgentDollars = 0.0
        postDraft.each { String id, List held ->
            String position = positionById[id]
            if (!preDraft.containsKey(id) && statusById[id] != 'R' && !pickedUp.contains(id) &&
                    POSITIONS.contains(position)) {
                dollars[position] += held[1] as BigDecimal
                freeAgentDollars += held[1] as BigDecimal
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

        // The same money split by position. Taken over the scoring positions only, so the shares are of
        // what the league commits to players it can start rather than of every contract on a roster.
        Map<String, BigDecimal> committedByPosition = [:].withDefault { 0.0 as BigDecimal }
        preDraft.each { String id, List held ->
            String position = positionById[id]
            if (!expiring(held) && POSITIONS.contains(position)) {
                committedByPosition[position] += held[1] as BigDecimal
            }
        }

        new Season(season, POSITIONS.collectEntries { [(it): dollars[it]] }, cap - committed,
                POSITIONS.collectEntries { [(it): committedByPosition[it]] },
                rookieDollars, freeAgentDollars, rookies.size(), teams)
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

    /**
     * How often an expiring contract in a band of ranks actually changed hands.
     *
     * <b>Of the contracts that were re-signed, not of the contracts that expired.</b> That denominator is
     * the whole of what {@link AuctionValuation#AVAILABILITY} means and it was never written down, which is
     * what made the deep band look anomalous: read against every expiring contract, availability falls away
     * steadily with rank, because most deep players are not re-signed by anybody. Read against the ones
     * somebody did sign — which is the question a bidder is asking — the deep band comes back up.
     *
     * Both counts are carried so the two readings can be told apart rather than argued about.
     */
    static class Retention {
        /** The deepest rank in this band, which is how {@link AuctionValuation#AVAILABILITY} is keyed. */
        final int throughRank
        /** Expiring contracts at these ranks, whether or not anybody re-signed them. */
        final int expiring
        /** Those of them somebody signed, which is the denominator the constant is measured on. */
        final int signed
        /** Those that went to a team other than the one holding them. */
        final int moved

        Retention(int throughRank, int expiring, int signed, int moved) {
            this.throughRank = throughRank
            this.expiring = expiring
            this.signed = signed
            this.moved = moved
        }

        /** How often a signed player of this band reached another team: what the constant carries. */
        BigDecimal getMovedShare() { signed > 0 ? (moved as BigDecimal) / signed : 0.0 }

        /** The same against every expiring contract, which is the reading that falls away with rank. */
        BigDecimal getMovedShareOfExpiring() { expiring > 0 ? (moved as BigDecimal) / expiring : 0.0 }

        /** How often a band was re-signed at all, which is what separates the two readings. */
        BigDecimal getSignedShare() { expiring > 0 ? (signed as BigDecimal) / expiring : 0.0 }
    }

    /**
     * Retention over the given seasons, banded by the rank boundaries the model prices with.
     *
     * The bands are a modelling choice and the rates are a measurement, so the boundaries are read off
     * {@link AuctionValuation#AVAILABILITY} rather than repeated here. A player the consensus did not rank
     * is skipped: he has no band to fall in, and the constant makes no claim about him.
     */
    static List<Retention> retention(List<String> seasons, List<Integer> boundaries) {
        Map<Integer, List<Integer>> tally = boundaries.collectEntries { [(it): [0, 0, 0]] }
        seasons.each { String season ->
            Map<String, List> preDraft = byPlayer(LoadUtils.mflRostersResourcePath(season))
            Map<String, List> postDraft = byPlayer(LoadUtils.mflPostDraftRostersResourcePath(season))
            Map<String, Integer> rankById = rankByPlayer(season)
            preDraft.each { String id, List held ->
                Integer rank = rankById[id]
                if (!expiring(held) || rank == null) {
                    return
                }
                List<Integer> counts = tally[boundaries.find { rank <= it }]
                counts[0]++
                if (postDraft.containsKey(id)) {
                    counts[1]++
                    if (postDraft[id][0] != held[0]) {
                        counts[2]++
                    }
                }
            }
        }
        boundaries.collect { int through ->
            new Retention(through, tally[through][0], tally[through][1], tally[through][2])
        }
    }

    /**
     * How deep into a ranking the league actually signs and rosters, against how deep the board prices.
     *
     * <b>A signing here is anyone the auction put on a week 1 roster</b>, which is a wider net than the one
     * {@link Season} casts for dollars: an expiring contract that came back, and also a veteran who was on
     * no pre-draft roster at all. Both are bid on and both are on the board, so both count towards how deep
     * bidding goes. Rookies are excluded, being drafted rather than bid on.
     */
    static class Depth {
        final String position
        /** Consensus rank of every player the auction signed, in no order. */
        final List<Integer> signed
        /** The same for everyone on a week 1 roster, which is the wider question of who is worth holding. */
        final List<Integer> rostered

        Depth(String position, List<Integer> signed, List<Integer> rostered) {
            this.position = position
            this.signed = signed
            this.rostered = rostered
        }

        /** The deepest rank the league has ever paid for at this position. */
        int getDeepest() { signed ? signed.max() : 0 }

        /** The rank a given share of signings came at or above. */
        int rankAt(double share) {
            if (!signed) {
                return 0
            }
            List<Integer> sorted = signed.sort(false)
            sorted[Math.min(sorted.size() - 1, Math.max(0, (Math.ceil(share * sorted.size()) as int) - 1))]
        }

        /** What share of week 1 rosters falls inside a given depth, which is what a cap has to cover. */
        BigDecimal rosteredWithin(int depth) {
            rostered ? (rostered.count { it <= depth } as BigDecimal) / rostered.size() : 0.0
        }
    }

    /** Signing and roster depth by position, over the seasons given. */
    static Map<String, Depth> depth(List<String> seasons) {
        Map<String, List<Integer>> signed = POSITIONS.collectEntries { [(it): []] }
        Map<String, List<Integer>> rostered = POSITIONS.collectEntries { [(it): []] }
        seasons.each { String season ->
            Map<String, List> preDraft = byPlayer(LoadUtils.mflRostersResourcePath(season))
            Map<String, List> postDraft = byPlayer(LoadUtils.mflPostDraftRostersResourcePath(season))
            Map<String, Integer> rankById = rankByPlayer(season)
            List<Map> players = LoadUtils.loadJsonResource(
                    LoadUtils.mflPlayersResourcePath(season)).players.player as List<Map>
            Map<String, String> positionById = players.collectEntries { [(it.id as String): it.position as String] }
            Map<String, String> statusById = players.collectEntries { [(it.id as String): (it.status ?: '') as String] }
            postDraft.each { String id, List held ->
                String position = positionById[id]
                Integer rank = rankById[id]
                if (!POSITIONS.contains(position) || rank == null) {
                    return
                }
                rostered[position] << rank
                boolean wasHeld = preDraft.containsKey(id)
                if (wasHeld ? expiring(preDraft[id]) : statusById[id] != 'R') {
                    signed[position] << rank
                }
            }
        }
        POSITIONS.collectEntries { [(it): new Depth(it, signed[it], rostered[it])] }
    }

    /**
     * One team's approach to one auction: how much of its roster was up, and what holding it would cost.
     *
     * <b>Exposure is what the expiring players actually fetched</b>, wherever they ended up. For the season
     * being priced {@code -t teams} uses model prices, which is the only thing available in advance; for a
     * finished season the record has the real number and there is no reason to model it. A player nobody
     * re-signed contributes nothing, which understates exposure a little at the teams holding the deepest
     * expiring contracts — those are exactly the players who go unsigned.
     */
    static class TeamSeason {
        final String season
        final String franchise
        /** Contracts the team had expiring, which is what it was choosing whether to keep. */
        final int expiring
        /** How many of them it kept. */
        final int kept
        /** What keeping all of them would have cost, at what they actually went for. */
        final BigDecimal exposure
        /** The cap it had left once the contracts already running were paid for. */
        final BigDecimal freeCap

        TeamSeason(String season, String franchise, int expiring, int kept, BigDecimal exposure,
                   BigDecimal freeCap) {
            this.season = season
            this.franchise = franchise
            this.expiring = expiring
            this.kept = kept
            this.exposure = exposure
            this.freeCap = freeCap
        }

        /** How stretched the team was: what holding its roster would cost against what it had to spend. */
        BigDecimal getStretch() { freeCap > 0 ? exposure / freeCap : 0.0 }

        /** How much of its expiring roster it actually held. */
        BigDecimal getKeptShare() { expiring > 0 ? (kept as BigDecimal) / expiring : 0.0 }
    }

    /** Every team's approach to every auction in the seasons given. */
    static List<TeamSeason> teamSeasons(List<String> seasons) {
        seasons.collectMany { String season ->
            Map<String, List> preDraft = byPlayer(LoadUtils.mflRostersResourcePath(season))
            Map<String, List> postDraft = byPlayer(LoadUtils.mflPostDraftRostersResourcePath(season))
            Map league = LoadUtils.loadJsonResource(LoadUtils.mflLeagueResourcePath(season)) as Map
            BigDecimal perTeamCap = league.league.salaryCapAmount as String as BigDecimal
            List<String> franchises = (league.league.franchises.franchise as List<Map>).collect { it.id as String }

            franchises.collect { String franchise ->
                List<String> held = preDraft.keySet().findAll { preDraft[it][0] == franchise }.toList()
                List<String> up = held.findAll { expiring(preDraft[it]) }
                BigDecimal committed = (held.findAll { !expiring(preDraft[it]) }
                        .collect { preDraft[it][1] as BigDecimal }.sum() ?: 0.0) as BigDecimal
                BigDecimal exposure = (up.collect { String id ->
                    postDraft.containsKey(id) ? postDraft[id][1] as BigDecimal : 0.0 as BigDecimal
                }.sum() ?: 0.0) as BigDecimal
                int kept = up.count { postDraft[it]?.getAt(0) == franchise }
                new TeamSeason(season, franchise, up.size(), kept, exposure, perTeamCap - committed)
            }
        }
    }

    /**
     * How strongly being stretched predicts letting players go, over the team seasons given.
     *
     * The number the decision to <b>report</b> team context rather than price it rests on. A strong
     * relation here would mean a team's situation belongs in the price; a weak one means it is real case by
     * case and invisible on average, which is the half of the problem worth handing to a reader.
     */
    static BigDecimal stretchAgainstKept(List<TeamSeason> teams) {
        List<TeamSeason> measurable = teams.findAll { it.expiring > 0 && it.freeCap > 0 }
        if (measurable.size() < 2) {
            return 0.0
        }
        double meanStretch = measurable.collect { it.stretch.toDouble() }.sum() / measurable.size()
        double meanKept = measurable.collect { it.keptShare.toDouble() }.sum() / measurable.size()
        double covariance = 0.0d, varianceStretch = 0.0d, varianceKept = 0.0d
        measurable.each { TeamSeason team ->
            double dx = team.stretch.toDouble() - meanStretch
            double dy = team.keptShare.toDouble() - meanKept
            covariance += dx * dy
            varianceStretch += dx * dx
            varianceKept += dy * dy
        }
        varianceStretch > 0 && varianceKept > 0 ?
                (covariance / Math.sqrt(varianceStretch * varianceKept)) as BigDecimal : 0.0
    }

    /** A season's consensus positional rank by MFL id, which is the join the bands are read through. */
    private static Map<String, Integer> rankByPlayer(String season) {
        Map<String, Integer> ranks = [:]
        new ff.load.fuad.FuadLoader().loadData(season).playerByNameMap.values().each { player ->
            if (player.redraftRank) {
                ranks[player.mflId] = player.redraftRank.positionRank
            }
        }
        ranks
    }

    /**
     * Players who reached a week 1 roster by some route other than the auction.
     *
     * <b>The auction is not itself a transaction type.</b> It arrives as {@code LOAD_ROSTERS}, the whole
     * result loaded at once, so there is no signing to look up player by player. What the log does record
     * individually is everything else that puts a player on a roster between the pre-draft snapshot and week
     * 1 — a free agent pickup, a blind bid waiver, a trade — so the auction is identified as what is left
     * once those are taken out.
     *
     * The two populations look nothing alike, which is the check on reading it this way: a waiver pickup is
     * almost always the minimum bid, while what remains carries real money and recognisable names.
     */
    private static Set<String> pickedUpAfterAuction(String season) {
        Set<String> moved = [] as Set<String>
        // Loaded rather than tolerated: with no log every newcomer would read as an auction signing, which
        // inflates the pot silently. A season whose transactions are missing should say so.
        def log = LoadUtils.loadJsonResource(LoadUtils.mflTransactionsResourcePath(season))
        List<Map> transactions = (log.transactions.transaction ?: []) as List<Map>
        transactions.each { Map transaction ->
            if (!NON_AUCTION_MOVES.contains(transaction.type as String)) {
                return
            }
            // The id lives in a different field per type, and more than one may be listed in each.
            String listed = [transaction.transaction, transaction.activated, transaction.deactivated]
                    .findAll { it }.join(',')
            (listed =~ /\d{4,}/).each { String id -> moved << id }
        }
        moved
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
