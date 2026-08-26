package ff.load.fuad

import ff.data.PlayerValuation
import ff.data.RealisedSeason
import ff.data.fantasypros.FpRankedPlayer
import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.league.League
import ff.load.fantasypros.FantasyProsLoader
import ff.load.RealisedSeasons
import ff.load.util.LoadUtils
import ff.projection.fuad.AuctionValuation
import ff.projection.ByeWeeks
import ff.projection.fuad.FranchiseSalaryCalculator
import ff.projection.LineupValue
import ff.projection.PointsCurve
import ff.projection.StarterRequirements

/**
 * Assemble everything an auction valuation needs for a season and run it.
 *
 * Three things have to come together: what a consensus rank has historically been worth, which players are
 * up for auction, and how much cap the league has left. See docs/fuad/PROJECTION.md.
 */
class FuadValuationLoader {

    /** Which league is being priced, and so how every season is restated before it is levelled. */
    private static final League LEAGUE = League.FUAD

    private static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE', 'PK'].asImmutable()

    /**
     * Positions the statistics carry, which are the ones a curve can be built for.
     *
     * Kicking is among them now. It was left out on the belief that nflverse does not carry it, and the
     * consequence was that no kicker could be levelled, every one priced at the minimum bid, and none added
     * anything to any lineup. See docs/fuad/PROJECTION.md.
     */
    private static final List<String> SCORED_POSITIONS = LEAGUE.scoredPositions.asImmutable()

    private static final String WIPED_SALARY = '0.01'

    /** A full roster, from the league bylaws. See docs/fuad/LEAGUE_RULES.md. */
    private static final int MAX_ROSTER = 30


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
                requirements(year))
    }

    /**
     * When each rank is off, and how long the season being priced runs.
     *
     * Public for the same reason as {@link #requirements}: anything recomputing what the board was priced
     * from has to use the same byes it was priced with, or it is describing a different season.
     */
    ByeWeeks byes(String year) {
        Map league = LoadUtils.loadJsonResource(LoadUtils.mflLeagueResourcePath(year)) as Map
        byeWeeks(year, league.league.lastRegularSeasonWeek as String as int)
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
        requirePriorSeason(year, priorYear)
        Map<String, Integer> franchiseSalary = FranchiseSalaryCalculator.franchiseSalaries(
                LoadUtils.loadJsonResource(LoadUtils.mflEndOfYearRostersResourcePath(priorYear)) as Map,
                LoadUtils.loadJsonResource(LoadUtils.mflPlayersResourcePath(priorYear)) as Map)

        valuationsByYear[year] = AuctionValuation.value(curve, requirements, available(year, fuadData),
                franchiseSalary, freeCapOf(year, league), slotsToFill(year, teams), byes)
    }

    /**
     * Refuse a season whose franchise tag rate cannot be computed, rather than failing on a null stream.
     *
     * The tag is the average of the top five salaries at a position the <b>previous</b> season, so the
     * earliest season a board can be priced for is the second one collected. 2017 has no 2016 behind it and
     * never will. Every report carrying a dollar goes through here, so a year that cannot be priced has to
     * say which year it is short of and why.
     */
    private static void requirePriorSeason(String year, String priorYear) {
        if (!LoadUtils.hasResource(LoadUtils.mflEndOfYearRostersResourcePath(priorYear))) {
            throw new IllegalArgumentException("Cannot price $year: the franchise tag is the average of the " +
                    "top five salaries at each position in $priorYear, and no $priorYear end of year " +
                    'rosters are held. See docs/fuad/LEAGUE_RULES.md.')
        }
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
            // be worth bidding on is too deep whoever happens to hold it. And one depth for every position,
            // kicker included: it used to be capped here instead, which left the board priced over 25 ranks
            // against a spread the curve had taken over 42.
            (franchiseByPlayer.containsKey(player.mflId) || !rostered.contains(player.mflId)) &&
                    player.redraftRank.positionRank <= curve().pricedDepth(player.player.position)
        }.collectEntries { FuadPlayer player ->
            // The dynasty rank rides along to the board and is priced by nothing: a salary buys one
            // season, and the model levels every rank on the redraft ranking alone. It is carried because
            // a contract's length is decided at the same moment as its price. See docs/fuad/LEAGUE_RULES.md.
            [(player.mflId): [player.player.name, player.player.position,
                              player.redraftRank.positionRank, franchiseByPlayer[player.mflId],
                              player.dynastyRank?.positionRank]]
        }
    }

    /** Every ranked season the curve is built from, restated under this league's rules. */
    private Map<String, Map<Integer, List<RealisedSeason>>> realisedByRank() {
        RealisedSeasons.byRank(LEAGUE, this.&ranked)
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
        new FantasyProsLoader().loadRedraftRankedPlayers(year).values()
    }

    /**
     * Cap space the league has left, which is what the pot is a share of.
     *
     * Public because it is the one figure behind a board that no report carries: `teams` reports it a team
     * at a time and nothing adds it up. See docs/figures.
     */
    BigDecimal freeCap(String year) {
        freeCapOf(year, LoadUtils.loadJsonResource(LoadUtils.mflLeagueResourcePath(year)) as Map)
    }

    /** Cap space not already committed to contracts still running. */
    private static BigDecimal freeCapOf(String year, Map league) {
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
