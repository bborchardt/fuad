package ff.print.fuad

import ff.data.PlayerValuation
import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.mfl.MflFranchise
import ff.data.mfl.MflPlayer
import ff.projection.LineupValue

import java.math.RoundingMode

/**
 * What each available player would add to <b>one team's</b> starting lineup, rather than to anyone's.
 *
 * The auction board prices a player against a league-wide replacement, which is the right way to price a
 * market and is nobody's actual alternative. A team holding one quarterback and a team holding four are not
 * choosing between the same things, and the board cannot say so: it prices each player alone.
 *
 * This report asks the other question. Given what a team already has under contract, what does one more
 * player add to the lineup it will actually field? That picks up the two things a per-player price cannot:
 * the bye he covers, and the weeks he is the better of two when only one can start.
 *
 * <b>Points, deliberately, and never dollars.</b> The marginal value of a player is the most this team
 * would rationally pay, which is not what he will cost — an auction clears where it clears, and the board's
 * PRICE remains the estimate of that. Putting the two side by side is the reader's job and is the whole
 * point of running this. Converting one into the other would make the board team-specific through the back
 * door, and quietly turn an evaluation into a prediction.
 *
 * See docs/STRATEGY.md.
 */
class FuadRosterFitPrinter {

    private static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE'].asImmutable()

    /** How deep the diminishing returns are worth showing: past a fourth at a position, nothing is. */
    private static final int DEPTH_SHOWN = 4

    private final FuadData fuadData
    private final List<PlayerValuation> valuations
    private final LineupValue lineups
    private final String franchiseId

    FuadRosterFitPrinter(FuadData fuadData, List<PlayerValuation> valuations, LineupValue lineups,
                         String franchiseId) {
        this.fuadData = fuadData
        this.valuations = valuations
        this.lineups = lineups
        this.franchiseId = franchiseId
    }

    private List<LineupValue.Rostered> roster
    private List<MflPlayer> unpriced
    private LineupValue.Bracket base

    /**
     * The roster as it will reach the auction: contracts still running.
     *
     * Everyone expiring is in the pool rather than on the roster, this team's own included, since keeping
     * one is a decision it makes at a price like anybody else's.
     *
     * A contracted player the curve cannot level brings no points and leaves the lineup: a kicker, whom the
     * statistics carry nothing for, someone ranked past the depth the curve reaches, or someone the
     * consensus does not rank at all. Dropping him changes no number here, since a player worth nothing is
     * never selected and could only ever fill a slot that would otherwise stand empty. It is named in the
     * header rather than silently absorbed, because his salary and his roster spot are still counted
     * everywhere else and the two counts should be seen to differ.
     */
    private List<LineupValue.Rostered> roster() {
        if (roster != null) {
            return roster
        }
        MflFranchise franchise = franchise()
        Map<String, FuadPlayer> byMflId = fuadData.playerByNameMap.values()
                .findAll { it.mflId }.collectEntries { [(it.mflId): it] }
        List<MflPlayer> signed = franchise.players.findAll { MflPlayer held -> held.contract }
        Map<Boolean, List<MflPlayer>> split = signed.groupBy { MflPlayer held ->
            lineups.rostered(held.player.position, byMflId[held.id]?.redraftRank?.positionRank) != null
        }
        unpriced = split[false] ?: []
        roster = (split[true] ?: []).collect { MflPlayer held ->
            lineups.rostered(held.player.position, byMflId[held.id]?.redraftRank?.positionRank)
        }
    }

    private LineupValue.Bracket base() {
        base ?: (base = lineups.evaluate(roster()))
    }

    private MflFranchise franchise() {
        MflFranchise franchise = fuadData.mflData.franchiseByIdMap[franchiseId]
        if (!franchise) {
            throw new IllegalArgumentException("No such franchise: $franchiseId")
        }
        franchise
    }

    void print(PrintWriter out) {
        out.println(header())
        out.println(['POS', 'TIER', 'RANK', 'DYNRANK', 'PLAYER', 'HOLDER', 'BYE', 'PTS', 'ADDEXP', 'ADDHIND',
                     'PRICE', 'ACQUIRE', 'AVAIL'].join('\t'))
        List<LineupValue.Rostered> roster = roster()

        valuations.collect { PlayerValuation v ->
            LineupValue.Bracket added = lineups.marginal(roster,
                    lineups.rostered(v.position, v.positionRank))
            [v, added]
        }.sort { a, b ->
            (b[1] as LineupValue.Bracket).onExpectation <=> (a[1] as LineupValue.Bracket).onExpectation
        }.each { List row ->
            PlayerValuation v = row[0] as PlayerValuation
            LineupValue.Bracket added = row[1] as LineupValue.Bracket
            out.println([
                    v.position,
                    v.tier,
                    v.positionRank,
                    v.dynastyRank ?: '',
                    v.playerName,
                    holder(v),
                    v.bye ?: '',
                    v.points.setScale(0, RoundingMode.HALF_UP),
                    round(added.onExpectation),
                    round(added.withHindsight),
                    v.marketSalary,
                    v.acquisitionSalary,
                    v.availability.setScale(2, RoundingMode.HALF_UP),
            ].join('\t'))
        }
    }

    /**
     * How much a second and a third player at a position are still worth, which the per-player rows cannot
     * say.
     *
     * Every marginal below is measured against the roster as it stands today, so they are all the value of
     * being the <b>first</b> signing at that position and they do not add up: a team with no quarterback
     * gains hugely from its first and much less from its second. That is exactly the question a superflex
     * team with two starting slots and three candidates has to answer, so it is answered here directly, by
     * adding the best available at each position in turn and reporting what each one brings.
     *
     * Taken in order of expected points rather than of price, since this is what the lineup gains and not
     * what it costs. Who is actually available at that depth is on the board below.
     */
    /**
     * How much a second and a third player at a position are still worth, which the per-player rows cannot
     * say.
     *
     * Every marginal in the main report is measured against the roster as it stands, so they are all the
     * value of being the <b>first</b> signing at that position and they do not add up. A team with no
     * quarterback gains hugely from its first and much less from its second, and reading two large
     * marginals as a plan to sign both overstates it by most of the smaller one.
     *
     * That is exactly the question a superflex team with two starting slots and three candidates has to
     * answer, so it is answered directly: the best available at each position are added in turn and each
     * one's contribution reported. Taken in order of expected points rather than of price, since this is
     * what the lineup gains and not what it costs.
     */
    void printDepth(PrintWriter out) {
        List<LineupValue.Rostered> roster = roster()
        out.println(header())
        out.println(['POS', 'ADD1', 'ADD2', 'ADD3', 'ADD4'].join('\t'))
        POSITIONS.each { String position ->
            List<LineupValue.Rostered> best = valuations
                    .findAll { it.position == position }
                    .sort { -it.points }
                    .take(DEPTH_SHOWN)
                    .collect { lineups.rostered(it.position, it.positionRank) }
                    .findAll { it != null }
            if (!best) {
                return
            }
            List<BigDecimal> added = []
            LineupValue.Bracket previous = base()
            best.eachWithIndex { LineupValue.Rostered player, int depth ->
                LineupValue.Bracket next = lineups.evaluate(roster + best.take(depth + 1))
                added << round(next.onExpectation - previous.onExpectation)
                previous = next
            }
            out.println(([position] + added).join('\t'))
        }
    }

    private String header() {
        List<LineupValue.Rostered> priced = roster()
        String missing = unpriced ? ", ${unpriced.size()} unpriced (${unpriced
                .collect { "$it.player.position $it.player.name" }.join('; ')})" : ''
        "# franchise $franchiseId (${franchise().ownerName}), ${priced.size()} of " +
                "${priced.size() + unpriced.size()} signed in the lineup$missing, " +
                "lineup ${round(base().onExpectation)} to ${round(base().withHindsight)} points"
    }

    private static BigDecimal round(BigDecimal points) { points.setScale(0, RoundingMode.HALF_UP) }

    private String holder(PlayerValuation v) {
        MflFranchise franchise = fuadData.mflData.franchiseByIdMap[v.franchiseId]
        String name = franchise?.ownerName ?: franchise?.name
        name ? name.split(' ')[0] : 'UFA'
    }
}
