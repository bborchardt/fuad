package ff.projection

import ff.data.FranchiseTag

/**
 * Work out which of a season's signings the franchise tag priced.
 *
 * The league records tags nowhere, so they have to be recovered from what they leave behind. Two things do.
 *
 * An **uncontested** tag is a team re-signing its own expiring player, for one year, at exactly the
 * franchise salary. That price is set by rule rather than bid, and the data shows it plainly: across
 * 2018-2025, 42 same-team one-year re-signings land exactly on the rate while the average neighbouring
 * dollar holds fewer than one, so an exact hit is roughly fifty times likelier to be a tag than a
 * coincidence.
 *
 * A **contested** tag is a player signed away by another team, which the league only allows against
 * compensation in rookie draft picks. That shows up as a first round pick moving from the signing team to
 * the team that held the player, with nothing coming back. Six of these appear in the data and each one
 * matches exactly one player who changed hands in that auction. Contested tags are not restricted to a
 * single year, since the winning team writes its own contract: the six run from one year to five.
 *
 * What cannot be recovered is a tag that was bid up and kept, or bid away without the compensation being
 * recorded as a trade. Those look like any other expensive auction win. The one lever against them is that
 * a team gets **one** tag, so an above-rate signing by a team that already tagged somebody was not a tag.
 * That rules out most of them and leaves a residue this reports as {@link FranchiseTag.Status#CANDIDATE}.
 *
 * See docs/LEAGUE_RULES.md.
 */
class FranchiseTagIdentifier {

    private static final String WIPED_SALARY = '0.01'

    /**
     * Kickers are excluded. Their franchise salary sits at or within a dollar or two of the league minimum
     * every season, so a tag would buy nothing that the auction would not, and ordinary cheap kicker
     * signings hit the rate by coincidence often enough to swamp the signal.
     */
    private static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE'].asImmutable()

    static List<FranchiseTag> tags(Map preDraftRosters, Map postDraftRosters, Map transactions,
                                   Map players, Map<String, Integer> franchiseSalaries) {
        Map<String, Map> playerById = (players.players.player as List<Map>)
                .collectEntries { [(it.id as String): it] }
        Map<String, List> preDraft = rostered(preDraftRosters)
        Map<String, List> postDraft = rostered(postDraftRosters)
        Set<List<String>> compensated = compensations(transactions)

        List<FranchiseTag> found = preDraft.findAll { String id, List held ->
            held[1] == WIPED_SALARY && postDraft.containsKey(id) &&
                    POSITIONS.contains(playerById[id]?.position)
        }.collect { String id, List held ->
            String taggingFranchise = held[0]
            List signed = postDraft[id]
            String signingFranchise = signed[0]
            int salary = new BigDecimal(signed[1] as String).intValue()
            int years = signed[2] as int
            String position = playerById[id].position as String
            int rate = franchiseSalaries[position]

            FranchiseTag.Basis basis = basisFor(taggingFranchise, signingFranchise, salary, years, rate,
                    compensated)
            basis ? new FranchiseTag(
                    playerId: id,
                    playerName: playerById[id].name as String,
                    position: position,
                    taggingFranchiseId: taggingFranchise,
                    signingFranchiseId: signingFranchise,
                    salary: salary,
                    contractYears: years,
                    franchiseSalary: rate,
                    status: FranchiseTag.Status.CONFIRMED,
                    basis: basis) : null
        }.findAll() as List<FranchiseTag>

        withStatuses(found)
    }

    private static FranchiseTag.Basis basisFor(String taggingFranchise, String signingFranchise, int salary,
                                               int years, Integer rate, Set<List<String>> compensated) {
        if (rate == null) {
            return null
        }
        if (taggingFranchise == signingFranchise && years == 1 && salary == rate) {
            return FranchiseTag.Basis.EXACT_RATE
        }
        if (taggingFranchise != signingFranchise && salary > rate &&
                compensated.contains([signingFranchise, taggingFranchise])) {
            return FranchiseTag.Basis.PICK_COMPENSATED
        }
        salary > rate ? FranchiseTag.Basis.ABOVE_RATE : null
    }

    /**
     * Resolve what each team's one tag can have been.
     *
     * A team showing more than one signing at exactly the rate cannot have tagged them all, so none of them
     * can be told apart and all become uncertain. A team with a tag already accounted for cannot have used
     * it on an above-rate signing as well, so those drop out entirely; the rest stay as candidates.
     */
    private static List<FranchiseTag> withStatuses(List<FranchiseTag> found) {
        Map<String, List<FranchiseTag>> byTagger = found.groupBy { it.taggingFranchiseId }

        List<FranchiseTag> resolved = found.collect { FranchiseTag tag ->
            if (tag.basis == FranchiseTag.Basis.PICK_COMPENSATED) {
                return tag
            }
            if (tag.basis == FranchiseTag.Basis.EXACT_RATE) {
                boolean contested = byTagger[tag.taggingFranchiseId]
                        .count { it.basis == FranchiseTag.Basis.EXACT_RATE } > 1
                return contested ? tag.copyWith(status: FranchiseTag.Status.UNCERTAIN) : tag
            }
            tag.copyWith(status: FranchiseTag.Status.CANDIDATE)
        }

        Set<String> spent = resolved
                .findAll { it.basis != FranchiseTag.Basis.ABOVE_RATE }
                .collect { it.taggingFranchiseId } as Set

        resolved.findAll { FranchiseTag tag ->
            tag.basis != FranchiseTag.Basis.ABOVE_RATE || !spent.contains(tag.taggingFranchiseId)
        }
    }

    /** Franchise id to [franchise, salary, contractYear], for everyone on a roster. */
    private static Map<String, List> rostered(Map rosters) {
        (rosters.rosters.franchise as List<Map>).collectMany { Map franchise ->
            def held = franchise.player ?: []
            ((held instanceof List ? held : [held]) as List<Map>).collect { Map player ->
                [player.id as String, [franchise.id as String, player.salary as String,
                                       (player.contractYear ?: '0') as String as int]]
            }
        }.collectEntries { it as List } as Map<String, List>
    }

    /**
     * Pairs of [signing team, tagging team] where a first round rookie pick went one way and nothing came
     * back, which is what compensation for a tagged player looks like. A pick moving as part of a two sided
     * trade is an ordinary trade and is not counted.
     */
    private static Set<List<String>> compensations(Map transactions) {
        (transactions.transactions.transaction as List<Map>)
                .findAll { it.type == 'TRADE' }
                .collectMany { Map trade ->
                    String gave = (trade.franchise1_gave_up ?: '') as String
                    String got = (trade.franchise2_gave_up ?: '') as String
                    if (firstRoundPicks(gave) && !got.trim()) {
                        return [[trade.franchise as String, trade.franchise2 as String]]
                    }
                    if (firstRoundPicks(got) && !gave.trim()) {
                        return [[trade.franchise2 as String, trade.franchise as String]]
                    }
                    []
                } as Set
    }

    /** Rookie draft picks are DP_<round>_<pick> for this year and FP_<franchise>_<year>_<round> for later
     *  ones, with the round zero indexed in the first and one indexed in the second. */
    private static boolean firstRoundPicks(String assets) {
        assets.split(',').any { it.startsWith('DP_0_') || it ==~ /FP_\d+_\d+_1/ }
    }
}
