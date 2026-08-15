package ff.load.fuad

import ff.data.PlayerValuation
import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.load.mfl.MflWeeklyScoresLoader
import ff.load.util.LoadUtils
import ff.projection.AuctionValuation
import ff.projection.FranchiseSalaryCalculator
import ff.projection.PointsCurve
import ff.projection.StarterRequirements

/**
 * Assemble everything an auction valuation needs for a season and run it.
 *
 * Four things have to come together: what the league is projected to score, what a consensus rank has
 * historically been worth, which players are up for auction, and how much cap the league has left. See
 * docs/PROJECTION.md.
 */
class FuadValuationLoader {

    /** Seasons whose scoring matches the current rules closely enough to measure realisation against. */
    private static final List<String> REALISED_SEASONS = ['2023', '2024', '2025'].asImmutable()

    private static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE', 'PK'].asImmutable()

    private static final String WIPED_SALARY = '0.01'

    /** A full roster, from the league bylaws. See docs/LEAGUE_RULES.md. */
    private static final int MAX_ROSTER = 30

    /** How deep at each position the league actually rosters, which bounds who is worth pricing. */
    private static final Map<String, Integer> ROSTERED_DEPTH =
            [QB: 30, RB: 45, WR: 50, TE: 25, PK: 12].asImmutable()

    List<PlayerValuation> valuations(String year, FuadData fuadData) {
        Map players = LoadUtils.loadJsonResource(LoadUtils.mflPlayersResourcePath(year)) as Map
        Map<String, String> positionById = (players.players.player as List<Map>)
                .collectEntries { [(it.id as String): it.position as String] }

        PointsCurve curve = PointsCurve.of(
                MflWeeklyScoresLoader.weeklyScores(LoadUtils.mflProjectedScoresResourcePath(year)),
                positionById,
                realisedByRank())

        Map league = LoadUtils.loadJsonResource(LoadUtils.mflLeagueResourcePath(year)) as Map
        int teams = (league.league.franchises.franchise as List).size()
        StarterRequirements requirements = StarterRequirements.fromLeague(league, teams)

        String priorYear = (year as int) - 1 as String
        Map<String, Integer> franchiseSalary = FranchiseSalaryCalculator.franchiseSalaries(
                LoadUtils.loadJsonResource(LoadUtils.mflEndOfYearRostersResourcePath(priorYear)) as Map,
                LoadUtils.loadJsonResource(LoadUtils.mflPlayersResourcePath(priorYear)) as Map)

        AuctionValuation.value(curve, requirements, available(year, fuadData), franchiseSalary,
                freeCap(year, league), slotsToFill(year, teams))
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
            franchiseByPlayer.containsKey(player.mflId) ||
                    (!rostered.contains(player.mflId) &&
                            player.redraftRank.positionRank <= ROSTERED_DEPTH[player.player.position])
        }.collectEntries { FuadPlayer player ->
            [(player.mflId): [player.player.name, player.player.position,
                              player.redraftRank.positionRank, franchiseByPlayer[player.mflId]]]
        }
    }

    /**
     * What players actually scored, indexed by the consensus rank they held before that season.
     *
     * Indexing by rank rather than by player is deliberate. The league site rewrites its projections as a
     * season goes, so comparing a finished season's projections against its results measures hindsight, not
     * accuracy. A preseason rank cannot be revised after the fact, so this comparison stays honest.
     */
    private Map<String, Map<Integer, List<BigDecimal>>> realisedByRank() {
        Map<String, Map<Integer, List<BigDecimal>>> realised = [:].withDefault { [:].withDefault { [] } }
        REALISED_SEASONS.each { String season ->
            Map<String, BigDecimal> totals = MflWeeklyScoresLoader.seasonTotals(
                    MflWeeklyScoresLoader.weeklyScores(LoadUtils.mflPlayerScoresResourcePath(season)))
            FuadData seasonData = new FuadLoader().loadData(season)
            seasonData.playerByNameMap.values().each { FuadPlayer player ->
                BigDecimal scored = totals[player.mflId]
                if (scored != null && player.redraftRank && POSITIONS.contains(player.player.position)) {
                    realised[player.player.position][player.redraftRank.positionRank] << scored
                }
            }
        }
        realised
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
