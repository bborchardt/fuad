package ff.load.fuad

import ff.data.PlayerValuation
import ff.data.RealisedSeason
import ff.data.fantasypros.FpRankedPlayer
import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.load.fantasypros.FantasyProsLoader
import ff.load.nflverse.NflverseStatsLoader
import ff.load.nflverse.ScoringRules
import ff.load.util.LoadUtils
import ff.projection.AuctionValuation
import ff.projection.ByeWeeks
import ff.projection.FranchiseSalaryCalculator
import ff.projection.LineupValue
import ff.projection.PointsCurve
import ff.projection.StarterRequirements

/**
 * Assemble everything an auction valuation needs for a season and run it.
 *
 * Three things have to come together: what a consensus rank has historically been worth, which players are
 * up for auction, and how much cap the league has left. See docs/PROJECTION.md.
 */
class FuadValuationLoader {

    /**
     * Finished seasons the curve is built from, every one nflverse statistics are held for.
     *
     * Pooled flat rather than weighted towards the recent ones. Restating 2017-19 and 2022-24 under a single
     * rule set leaves them within a few per cent at every position, so there is no era left to correct for,
     * and nine seasons is what gives a rank about 45 observations instead of 15.
     */
    private static final List<String> REALISED_SEASONS =
            (2017..2025).collect { it as String }.asImmutable()

    private static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE', 'PK'].asImmutable()

    /**
     * Positions the statistics carry, which are the ones a curve can be built for.
     *
     * Kicking is not in the nflverse data and is not worth adding: kickers have taken under one per cent of
     * the auction in every season on record, so they price out at the minimum bid either way.
     */
    private static final List<String> SCORED_POSITIONS = ['QB', 'RB', 'WR', 'TE'].asImmutable()

    /**
     * Prefix lengths tried in turn when a ranked name has no exact match in the statistics.
     *
     * Longest first, and a name is taken out of the pool once it is claimed, so the specific matches are
     * made before the loose ones get a chance to go wrong. This is what tells Gabe from Gabriel and Kenny
     * from Kenneth. A rank left unmatched after all three is scored as a zero, not dropped.
     */
    private static final List<Integer> MATCH_LENGTHS = [10, 5, 3].asImmutable()

    private static final String WIPED_SALARY = '0.01'

    /** A full roster, from the league bylaws. See docs/LEAGUE_RULES.md. */
    private static final int MAX_ROSTER = 30

    /**
     * Kickers are cut off by hand, having no curve to derive a depth from.
     *
     * Every other position is bounded by {@link PointsCurve#pricedDepth}, the point below which the curve
     * says a rank is no longer really a claim. Kicking is not in the statistics at all, so there is no
     * level to compare against a floor and this stands in for one. It costs nothing: the league has spent
     * under one per cent of its auction on the position in every season on record.
     */
    private static final int KICKER_DEPTH = 12

    /**
     * The curve, built once per loader.
     *
     * Nine seasons of statistics are read and restated to make it, which is much the most expensive thing
     * here, and the salaries, teams and roster reports all want the same one.
     */
    private PointsCurve curve

    private final Map<String, List<PlayerValuation>> valuationsByYear = [:]

    PointsCurve curve() {
        curve ?: (curve = PointsCurve.of(realisedByRank()))
    }

    /**
     * How a roster's lineup scores, for reports that ask what a player adds to one team rather than to the
     * market. Shares the curve and the byes the board was priced from, so the two are answers about the
     * same season.
     */
    LineupValue lineups(String year) {
        Map league = LoadUtils.loadJsonResource(LoadUtils.mflLeagueResourcePath(year)) as Map
        new LineupValue(curve(), byeWeeks(year, league.league.lastRegularSeasonWeek as String as int),
                requirements(year), MAX_ROSTER)
    }

    /** What a team has to field each week, which is also what tells it where its roster is short. */
    StarterRequirements requirements(String year) {
        Map league = LoadUtils.loadJsonResource(LoadUtils.mflLeagueResourcePath(year)) as Map
        StarterRequirements.fromLeague(league, (league.league.franchises.franchise as List).size())
    }

    List<PlayerValuation> valuations(String year, FuadData fuadData) {
        if (valuationsByYear.containsKey(year)) {
            return valuationsByYear[year]
        }
        PointsCurve curve = curve()

        Map league = LoadUtils.loadJsonResource(LoadUtils.mflLeagueResourcePath(year)) as Map
        int teams = (league.league.franchises.franchise as List).size()
        StarterRequirements requirements = StarterRequirements.fromLeague(league, teams)
        ByeWeeks byes = byeWeeks(year, league.league.lastRegularSeasonWeek as String as int)

        String priorYear = (year as int) - 1 as String
        Map<String, Integer> franchiseSalary = FranchiseSalaryCalculator.franchiseSalaries(
                LoadUtils.loadJsonResource(LoadUtils.mflEndOfYearRostersResourcePath(priorYear)) as Map,
                LoadUtils.loadJsonResource(LoadUtils.mflPlayersResourcePath(priorYear)) as Map)

        valuationsByYear[year] = AuctionValuation.value(curve, requirements, available(year, fuadData),
                franchiseSalary, freeCap(year, league), slotsToFill(year, teams), byes)
    }

    /**
     * Roster spots the auction has to fill: what a full roster holds, less contracts still running and less
     * the spots the rookie draft takes.
     *
     * Checks out against what the league has actually signed. 2022 predicts 65 against 71 signings, 2023
     * 90 against 93, 2024 103 against 96, and 2025 92 against 92.
     */
    private static int slotsToFill(String year, int teams) {
        Map rosters = LoadUtils.loadJsonResource(LoadUtils.mflRostersResourcePath(year)) as Map
        int underContract = (rosters.rosters.franchise as List<Map>).sum { Map franchise ->
            def held = franchise.player ?: []
            ((held instanceof List ? held : [held]) as List<Map>).count { it.salary != WIPED_SALARY }
        } as int
        Math.max(1, teams * MAX_ROSTER - underContract - AuctionValuation.ROOKIE_ROUNDS * teams)
    }

    /**
     * Everyone who can be bid on: players whose contracts have expired, and players nobody holds.
     *
     * The two are not the same kind of free agent. An expiring contract is restricted, so its team may
     * match; a player nobody holds is unrestricted and simply goes to the highest bid. Only the second can
     * ever be a bargain, which is reason enough to carry them.
     *
     * Rookies are left out entirely. They are drafted separately after the auction and cannot be bid on,
     * and their cost and roster spots are taken off the top instead. Unrostered veterans are cut off at
     * roughly the depth the league rosters, since the rest would never be signed and would only dilute the
     * board.
     */
    private Map<String, List> available(String year, FuadData fuadData) {
        Map rosters = LoadUtils.loadJsonResource(LoadUtils.mflRostersResourcePath(year)) as Map
        Map<String, String> franchiseByPlayer = [:]
        (rosters.rosters.franchise as List<Map>).each { Map franchise ->
            def held = franchise.player ?: []
            ((held instanceof List ? held : [held]) as List<Map>).each { Map player ->
                if (player.salary == WIPED_SALARY) {
                    franchiseByPlayer[player.id as String] = franchise.id as String
                }
            }
        }
        Set<String> rostered = (rosters.rosters.franchise as List<Map>).collectMany { Map franchise ->
            def held = franchise.player ?: []
            ((held instanceof List ? held : [held]) as List<Map>).collect { it.id as String }
        } as Set

        fuadData.playerByNameMap.values().findAll { FuadPlayer player ->
            if (!player.redraftRank || !POSITIONS.contains(player.player.position) || player.rookie) {
                return false
            }
            // One depth for both kinds of free agent. An expiring contract does not have to be re-signed:
            // if nobody bids, the player goes back into the pool like anyone else, so a rank too deep to
            // be worth bidding on is too deep whoever happens to hold it.
            int depth = 'PK' == player.player.position ? KICKER_DEPTH
                    : curve().pricedDepth(player.player.position)
            (franchiseByPlayer.containsKey(player.mflId) || !rostered.contains(player.mflId)) &&
                    player.redraftRank.positionRank <= depth
        }.collectEntries { FuadPlayer player ->
            // The dynasty rank rides along to the board and is priced by nothing: a salary buys one
            // season, and the model levels every rank on the redraft ranking alone. It is carried because
            // a contract's length is decided at the same moment as its price. See docs/LEAGUE_RULES.md.
            [(player.mflId): [player.player.name, player.player.position,
                              player.redraftRank.positionRank, franchiseByPlayer[player.mflId],
                              player.dynastyRank?.positionRank]]
        }
    }

    /**
     * What players actually scored, restated under the rules being priced and indexed by the consensus rank
     * they held before that season.
     *
     * Indexing by rank rather than by player is what the whole model rests on. A ranking is a judgement
     * about who is better, which is what consensus is for; how much scoring that judgement has been worth is
     * a question only finished seasons can answer. Nothing here is a forecast of a particular player, so
     * nothing here can be dragged around by one source's opinion of one man.
     *
     * A preseason rank also cannot be revised after the fact, which a projection can. That is what keeps
     * this honest where comparing a finished season's projections against its own results would only
     * measure hindsight.
     */
    private Map<String, Map<Integer, List<RealisedSeason>>> realisedByRank() {
        Map<String, Map<Integer, List<RealisedSeason>>> realised = [:].withDefault { [:].withDefault { [] } }
        REALISED_SEASONS.each { String season ->
            Map<String, BigDecimal> scored = NflverseStatsLoader.seasonPoints(season, ScoringRules.CURRENT)
                    .collectEntries { String name, BigDecimal points -> [(LoadUtils.aliasedName(name)): points] }
            Map<String, Integer> games = NflverseStatsLoader.gamesPlayed(season)
                    .collectEntries { String name, Integer played -> [(LoadUtils.aliasedName(name)): played] }
            Set<String> unclaimed = NflverseStatsLoader.played(season).collect { LoadUtils.aliasedName(it) } as Set
            ranked(season).each { FpRankedPlayer player ->
                if (SCORED_POSITIONS.contains(player.player.position)) {
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
     * Null means he scored nothing and has to be carried as a zero rather than dropped. Ten ranked seasons
     * inside the depth this league rosters never happened — Andrew Luck's 2017 shoulder, Le'Veon Bell's
     * 2018 holdout, Gus Edwards' 2021 knee, Joe Mixon's 2025 foot — and they are exactly the seasons that
     * busted hardest. Leaving them out biases every curve upward and cuts off the left tail that a bench is
     * priced against.
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

    /**
     * Which week each rank is off, over the whole ranked pool rather than only the players up for auction.
     *
     * Replacement level is the best player a team would not otherwise start, so it needs the byes of the
     * players doing the replacing as much as of the players being priced. Taken from the same consensus
     * ranking the order comes from, since a bye is a fact of the schedule and not an opinion about anybody.
     */
    private static ByeWeeks byeWeeks(String year, int lastWeek) {
        Map<String, Map<Integer, Integer>> byes = [:].withDefault { [:] }
        ranked(year).each { FpRankedPlayer player ->
            if (POSITIONS.contains(player.player.position) && player.bye?.isInteger()) {
                byes[player.player.position][player.rank.positionRank] = player.bye as int
            }
        }
        new ByeWeeks(byes, lastWeek)
    }

    private static Collection<FpRankedPlayer> ranked(String year) {
        new FantasyProsLoader().loadRankedPlayers(LoadUtils.fpRedraftRankingsHalfPprResourcePath(year)).values()
    }

    /** Cap space not already committed to contracts still running. */
    private static BigDecimal freeCap(String year, Map league) {
        Map rosters = LoadUtils.loadJsonResource(LoadUtils.mflRostersResourcePath(year)) as Map
        BigDecimal committed = (rosters.rosters.franchise as List<Map>).collectMany { Map franchise ->
            def held = franchise.player ?: []
            ((held instanceof List ? held : [held]) as List<Map>)
                    .findAll { it.salary != WIPED_SALARY }
                    .collect { new BigDecimal(it.salary as String) }
        }.sum() ?: 0.0 as BigDecimal
        int teams = (league.league.franchises.franchise as List).size()
        (league.league.salaryCapAmount as String as BigDecimal) * teams - committed
    }
}
