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

    /** How many candidates a position's frontier may draw from. See {@link #shortlists}. */
    private static final int SHORTLIST = 8

    /** The most players one frontier point may hold. See {@link #printLadder}. */
    private static final int BUNDLE = 3

    /**
     * Points a row must beat the last one by to be worth a line, and the cost step it must clear.
     *
     * Both are about what four hundred replayed seasons can resolve and what a reader can use. See
     * {@link #frontier}.
     */
    private static final BigDecimal MATERIAL = 5.0

    private static final BigDecimal STEP = 1.3

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
     * What a budget buys at each position: the frontier of cost against points added.
     *
     * {@link #printDepth} answers whether a second at a position is worth having and cannot answer what to
     * spend on it. It walks the best available by points, which is the top of the board — the part of it a
     * team with a hole and a budget is least likely to buy. A plan allocating cap is asking the other
     * question: if thirty dollars go to receiver rather than to running back, what does each return?
     *
     * So every combination of up to {@link #BUNDLE} players from a position's shortlist is evaluated, and
     * the ones no cheaper combination beats are printed. A position's rows are a frontier: read down to the
     * budget being considered and across positions at the same spend. {@code PER$} is the whole bundle's
     * average and not the last step's, since the question is what an allocation returns rather than what one
     * more player does.
     *
     * <b>Why not simply walk the best ratio.</b> That was the first shape and it is worse than it sounds:
     * points per dollar is maximised by minimum bids almost everywhere, so the walk fills with a dollar
     * player at every position and reaches the middle of the board at no point at all. It is right at kicker,
     * where a dollar genuinely is the answer, and it buries exactly the question this report exists for
     * everywhere else. The frontier keeps the cheap end without letting it crowd the rest out.
     *
     * <b>Bundles are capped at {@link #BUNDLE} and the shortlist at {@link #SHORTLIST}</b>, which bounds
     * this at a few hundred replayed seasons rather than a few thousand. A fourth player at one position is
     * worth close to nothing on every ladder generated so far, and the combinations grow faster than the
     * information does.
     *
     * <b>Cost is what this team would pay, which is not one column of the board.</b> A player another team
     * holds costs {@code ACQUIRE}, since that team may match; a player nobody holds costs {@code PRICE};
     * and this team's own expiring player costs {@code PRICE} too, because the right of first refusal that
     * makes him dear to everybody else is this team's to exercise. Charging a team the outside bidder's
     * price for its own player would price it out of its own roster.
     */
    void printLadder(PrintWriter out) {
        List<LineupValue.Rostered> roster = roster()
        out.println(header())
        out.println(['POS', 'COST', 'ADD', 'PER$', 'PLAYERS'].join('\t'))
        Map<String, List<PlayerValuation>> shortlists = shortlists(roster)

        lineups.positions().each { String position ->
            List<PlayerValuation> candidates = shortlists[position] ?: []
            if (!candidates) {
                return
            }
            int most = Math.min(BUNDLE, lineups.maximumStarted(position) + 1)
            List<List> points = bundles(candidates, most).collect { List<PlayerValuation> bundle ->
                BigDecimal added = lineups.evaluate(roster + bundle.collect {
                    lineups.rostered(it.position, it.positionRank)
                }).onExpectation - base().onExpectation
                [(bundle.sum { cost(it) }) as int, added, bundle]
            }
            frontier(points).each { List row ->
                int spent = row[0] as int
                BigDecimal added = row[1] as BigDecimal
                List<PlayerValuation> bundle = row[2] as List<PlayerValuation>
                out.println([
                        position,
                        spent,
                        round(added),
                        (added / Math.max(1, spent)).setScale(2, RoundingMode.HALF_UP),
                        bundle.sort { -it.points }.collect { surname(it.playerName) }.join(', '),
                ].join('\t'))
            }
        }
    }

    /**
     * Every combination of up to {@code most} of them, which is what a budget is actually choosing between.
     *
     * {@code most} is held to what the lineup can start plus one, so a position is never offered a bundle
     * deeper than it can field. Kicker is why: one starts, and three of them was a frontier point before
     * this bound existed.
     */
    private static List<List<PlayerValuation>> bundles(List<PlayerValuation> candidates, int most) {
        List<List<PlayerValuation>> all = candidates.collect { [it] }
        for (int i = 0; most >= 2 && i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                all << [candidates[i], candidates[j]]
                for (int k = j + 1; most >= 3 && k < candidates.size(); k++) {
                    all << [candidates[i], candidates[j], candidates[k]]
                }
            }
        }
        all
    }

    /**
     * The combinations worth printing: those no cheaper one matches, thinned to the ones that differ.
     *
     * Without the first filter a position prints every subset it was handed, most of them dominated, and the
     * reader does the work the report exists to do. A bundle that costs more than another and adds no more
     * is not a choice anybody has to weigh.
     *
     * <b>The thinning is the more important half.</b> An unthinned frontier is a row per dollar — near two
     * hundred of them for one team — differing by a point or two, which is inside what four hundred replayed
     * seasons can resolve and reads as precision the report does not have. So a row has to beat the last one
     * kept by {@link #MATERIAL} points <i>and</i> cost meaningfully more, which leaves a ladder a reader can
     * hold in their head and spans the same range. This is the same argument as {@code TIER} on the auction
     * board: where the model cannot separate two options, it should not print them as a choice.
     */
    private static List<List> frontier(List<List> points) {
        BigDecimal keptAdd = null
        int keptCost = 0
        points.sort { a, b -> (a[0] as int) <=> (b[0] as int) ?: (b[1] as BigDecimal) <=> (a[1] as BigDecimal) }
                .findAll { List row ->
                    int spent = row[0] as int
                    BigDecimal added = row[1] as BigDecimal
                    if (added <= 0) {
                        return false
                    }
                    if (keptAdd != null &&
                            (added < keptAdd + MATERIAL || spent < Math.max(keptCost + 2, keptCost * STEP))) {
                        return false
                    }
                    keptAdd = added
                    keptCost = spent
                    true
                }
    }

    /**
     * The candidates a position's frontier is drawn from: the players no cheaper player beats.
     *
     * <b>Taken as a frontier rather than as a ranking, because every ranking cuts the middle out.</b>
     * Ranking by points keeps the top of the board, which a budget cannot reach; ranking by points per
     * dollar keeps minimum bids, which a budget does not need help finding. Taking some of each — the shape
     * this had first — keeps both ends and drops everything between them, and at receiver everything between
     * them was the answer: the mid-priced receivers carried the best points per dollar at the position and
     * not one of them survived the cut, leaving a frontier with nothing to say between eight dollars and
     * seventy-one.
     *
     * A player belongs on the shortlist when no cheaper player adds more. That spans the cost range by
     * construction, keeps the cheap and the dear where each is genuinely the best of its price, and drops
     * only players something cheaper already beats. Where the frontier is longer than {@link #SHORTLIST} it
     * is sampled evenly rather than truncated, since truncating it would restore the very bias this is here
     * to remove.
     *
     * The marginals are the ones the main report already prints, so the pass costs nothing that was not
     * already being spent.
     */
    private Map<String, List<PlayerValuation>> shortlists(List<LineupValue.Rostered> roster) {
        valuations.collect { PlayerValuation v ->
            LineupValue.Rostered player = lineups.rostered(v.position, v.positionRank)
            [v, player == null ? 0.0 : lineups.marginal(roster, player).onExpectation]
        }.findAll { (it[1] as BigDecimal) > 0 }
                .groupBy { (it[0] as PlayerValuation).position }
                .collectEntries { String position, List<List> rows ->
                    [(position): sampled(bestOfEachPrice(rows))]
                }
    }

    /** Cheapest first, keeping a player only where nothing cheaper already adds as much. */
    private List<PlayerValuation> bestOfEachPrice(List<List> rows) {
        BigDecimal best = null
        rows.sort { a, b ->
            cost(a[0] as PlayerValuation) <=> cost(b[0] as PlayerValuation) ?:
                    (b[1] as BigDecimal) <=> (a[1] as BigDecimal)
        }.findAll { List row ->
            BigDecimal added = row[1] as BigDecimal
            if (best != null && added <= best) {
                return false
            }
            best = added
            true
        }.collect { it[0] as PlayerValuation }
    }

    /** At most {@link #SHORTLIST} of them, spread across the frontier rather than taken off one end. */
    private static List<PlayerValuation> sampled(List<PlayerValuation> frontier) {
        if (frontier.size() <= SHORTLIST) {
            return frontier
        }
        // The dearest is always kept: it is the only row that can say what the top of the position costs,
        // and even spacing alone drops it whenever the frontier does not divide evenly.
        List<PlayerValuation> kept = (0..<(SHORTLIST - 1))
                .collect { frontier[(int) (it * (frontier.size() - 1) / (SHORTLIST - 1))] }
        kept << frontier.last()
        kept.unique()
    }

    /**
     * What this team pays to add him, which depends on whether it is already his.
     *
     * See {@link #printLadder}: {@code ACQUIRE} is the outside bidder's price and is the wrong number to
     * charge a team for keeping its own player.
     */
    private int cost(PlayerValuation v) {
        v.franchiseId == franchiseId ? v.marketSalary : v.acquisitionSalary
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
