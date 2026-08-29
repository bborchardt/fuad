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
    private List<MflPlayer> priced
    private Map<String, Integer> ranks
    private List<MflPlayer> unpriced
    private LineupValue.Bracket base

    /**
     * The roster as it will reach the auction: contracts still running.
     *
     * Everyone expiring is in the pool rather than on the roster, this team's own included, since keeping
     * one is a decision it makes at a price like anybody else's.
     *
     * A contracted player the curve cannot level brings no points and leaves the lineup: someone ranked
     * past the depth the curve reaches, or someone the consensus does not rank at all. Dropping him changes
     * no number here, since a player worth nothing is
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
        // Kept beside the levelled roster so the depth report can name who is already there. A Rostered
        // carries a rate and a bye and no identity, which is all the lineup needs and none of what a reader
        // needs to see why a marginal is large.
        //
        // <b>In the order it arrived, and sorted only where it is printed.</b> LineupValue draws a player's
        // season from his slot on the roster, deliberately, so that two evaluations of nearly the same
        // roster share their draws and the marginal between them carries the noise of neither. Reordering
        // the list therefore reorders the draws: sorting this by rank moved the lineup nineteen points,
        // which is Monte Carlo noise wearing the shape of a finding.
        priced = split[true] ?: []
        ranks = priced.collectEntries { MflPlayer held ->
            [(held.id): byMflId[held.id]?.redraftRank?.positionRank ?: Integer.MAX_VALUE]
        }
        roster = priced.collect { MflPlayer held ->
            lineups.rostered(held.player.position, byMflId[held.id]?.redraftRank?.positionRank)
        }
    }

    /**
     * Who is already signed at a position, best first, as the surnames a draft room uses.
     *
     * <b>The column that stops every other number here reading as a mistake.</b> A marginal is large exactly
     * when the incumbent is weak, and a count cannot say that: this team holds two quarterbacks, which
     * explains nothing, and holds Shedeur Sanders and Anthony Richardson, which explains everything. The
     * header already names the unpriced for the same reason.
     */
    private String signedAt(String position) {
        roster()
        priced.findAll { MflPlayer held -> held.player.position == position }
                .sort { MflPlayer held -> ranks[held.id] }
                .collect { MflPlayer held -> surname(held.player.name) }
                .join(', ')
    }

    /** The name a draft room uses, from whichever way round the source happens to write it. */
    private static String surname(String name) {
        name.contains(',') ? name.split(',').first().trim() : name.split(' ').last()
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
     * Every marginal in the main report is measured against the roster as it stands, so they are all the
     * value of being the <b>first</b> signing at that position and they do not add up. A team with no
     * quarterback gains hugely from its first and much less from its second, and reading two large
     * marginals as a plan to sign both overstates it by most of the smaller one.
     *
     * That is exactly the question a superflex team with two starting slots and three candidates has to
     * answer, so it is answered directly: the best available at each position are added in turn and each
     * one's contribution reported. Taken in order of expected points rather than of price, since this is
     * what the lineup gains and not what it costs.
     *
     * <b>Every position the lineup fields, taken from the lineup.</b> It used to be a list of four written
     * down here, which stopped being every position the day kickers were levelled — leaving the report that
     * exists to say whether a second one is worth buying unable to mention the position at all. The second
     * kicker is exactly the case it is needed for: only one starts, so the answer is nothing, and a reader
     * who has just seen a kicker priced far below his value needs telling that once is enough.
     */
    void printDepth(PrintWriter out) {
        List<LineupValue.Rostered> roster = roster()
        out.println(header())
        out.println(['POS', 'ADD1', 'ADD2', 'ADD3', 'ADD4', 'SIGNED'].join('\t'))
        lineups.positions().each { String position ->
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
            out.println(([position] + added + [signedAt(position)]).join('\t'))
        }
    }

    /**
     * What the lineup below is, which is not the roster a reader pictures.
     *
     * <b>Everyone expiring is in the pool rather than in the lineup, this team's own included</b>, so the
     * count of expiring contracts belongs here beside the count of signed ones. Without it "16 in the
     * lineup" reads as a full roster, and a first quarterback worth 194 points reads as a defect rather
     * than as the consequence of Jalen Hurts, Lamar Jackson and Tua Tagovailoa all being up for auction.
     *
     * <b>How many of them the board prices is on the line too, because {@code teams} counts the other
     * one.</b> That report's EXPIRING is the expiring players it can put a price on, which is the number a
     * budget cares about; this one counts contracts that ended, which is the number a lineup cares about.
     * Franchise 0001 has thirteen and ten, the three between them being ranked past the depth anything is
     * priced to. Two reports disagreeing about a word is worse than either being wrong, so both are said.
     */
    private String header() {
        List<LineupValue.Rostered> inLineup = roster()
        String missing = unpriced ? ", ${unpriced.size()} unpriced (${unpriced
                .collect { "$it.player.position $it.player.name" }.join('; ')})" : ''
        List<MflPlayer> expiring = franchise().players.findAll { MflPlayer held -> !held.contract }
        int priced = expiring.count { MflPlayer held ->
            valuations.any { it.playerId == held.id }
        }
        "# franchise $franchiseId (${franchise().ownerName}), ${inLineup.size()} under contract and in the " +
                "lineup$missing, ${expiring.size()} expiring and in the pool ($priced of them priced), " +
                "lineup ${round(base().onExpectation)} to ${round(base().withHindsight)} points"
    }

    private static BigDecimal round(BigDecimal points) { points.setScale(0, RoundingMode.HALF_UP) }

    private String holder(PlayerValuation v) {
        MflFranchise franchise = fuadData.mflData.franchiseByIdMap[v.franchiseId]
        String name = franchise?.ownerName ?: franchise?.name
        name ? name.split(' ')[0] : 'UFA'
    }
}
