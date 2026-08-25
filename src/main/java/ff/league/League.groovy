package ff.league

import ff.load.nflverse.ScoringRules
import ff.projection.StarterRequirements

/**
 * What distinguishes one league from another, gathered in one place so nothing has to assume there is only
 * one.
 *
 * Two leagues are modelled here and they agree on almost nothing a valuation depends on. One is a dynasty
 * salary cap auction on MyFantasyLeague, superflex, ten teams, scored four different ways since 2017. The
 * other is a Yahoo snake draft with keepers, fourteen teams, single quarterback, full PPR, and scoring that
 * has not moved in ten seasons. What they share is the machinery underneath: a rank's level comes from the
 * same statistics restated under whichever rules are being priced, and its worth over replacement from the
 * same arithmetic once the lineup is known.
 *
 * <b>The point of this class is that the differences are data.</b> They used to be constants reachable from
 * anywhere — {@code ScoringRules.CURRENT} above all, which named no league and so silently meant the only
 * one there was. A second league turns every such global into a wrong answer that nothing detects, because
 * scoring a Yahoo season under MFL's rules produces numbers rather than errors.
 *
 * <b>Starting requirements are here only where they are constant.</b> Greenfield has started the same nine
 * every year, so its lineup is a property of the league. The dynasty league's has changed twice and is read
 * per season from that season's {@code league.json}, so it has none here and {@link #requirements} refuses
 * rather than inventing one. See docs/LEAGUE_RULES.md.
 */
class League {

    /** The dynasty salary cap league. Its lineup is per season; see {@link #requirements}. */
    static final League FUAD = new League(
            name: 'fuad',
            scoring: ScoringRules.FUAD_2026,
            scoredPositions: ['QB', 'RB', 'WR', 'TE', 'PK'],
            seasons: (2017..2025).collect { it as String })

    /**
     * The Greenfield keeper league.
     *
     * QB, WR, WR, RB, RB, TE, W/R/T, K, DEF — nine starters, of which one is a flex filled from RB, WR or
     * TE. Read as ranges that is QB 1, RB 2-3, WR 2-3, TE 1-2, PK 1, with the single flex the only slack.
     *
     * <b>Team defences are not among the scored positions, and so are not among the starters either.</b> The
     * league starts one and scores it, but the statistics this project keeps are per player, so no curve can
     * be built for it. Leaving it out is the honest state: a position with no curve is reported as having
     * none rather than priced at a guess, which is what the kickers used to be.
     *
     * That is why {@link #startersPerTeam} is eight against a nine slot lineup. The count is what the flex
     * is allocated against, so a slot no modelled position can fill has to come out of it: leaving it in
     * hands the defence's slot to an extra running back or receiver, pushing replacement a rank deeper at
     * whichever position wins it and overstating what the ones above it are worth. One starting slot of nine
     * is unpriced until team defence statistics are collected, and it is unpriced by being absent rather
     * than by being quietly filled with somebody else.
     */
    static final League GREENFIELD = new League(
            name: 'greenfield',
            scoring: ScoringRules.GREENFIELD,
            scoredPositions: ['QB', 'RB', 'WR', 'TE', 'PK'],
            seasons: (2017..2025).collect { it as String },
            teams: 14,
            startersPerTeam: 8,
            starterMinimums: [QB: 1, RB: 2, WR: 2, TE: 1, PK: 1],
            starterMaximums: [QB: 1, RB: 3, WR: 3, TE: 2, PK: 1])

    String name

    /** How this league scores, which is what every season is restated under before anything is levelled. */
    ScoringRules scoring

    /** Positions a curve can be built for: those the statistics carry and the league scores. */
    List<String> scoredPositions

    /**
     * Finished seasons the curve is built from, every one nflverse statistics are held for.
     *
     * Pooled flat rather than weighted towards the recent ones. Restating 2017-19 and 2022-24 under a single
     * rule set leaves them within a few per cent at every position, so there is no era left to correct for,
     * and nine seasons is what gives a rank about 45 observations instead of 15.
     */
    List<String> seasons

    Integer teams
    Integer startersPerTeam
    Map<String, Integer> starterMinimums
    Map<String, Integer> starterMaximums

    /** True where the lineup is a property of the league rather than of one of its seasons. */
    boolean hasFixedLineup() {
        teams != null && startersPerTeam != null && starterMinimums != null && starterMaximums != null
    }

    /**
     * The lineup, for a league that has always started the same one.
     *
     * Refuses rather than guessing where it does not, since a league whose starting requirements have moved
     * has no single answer and the one it would fall back on is whichever season happened to be loaded.
     */
    StarterRequirements requirements() {
        if (!hasFixedLineup()) {
            throw new IllegalStateException(
                    "$name has no fixed lineup; read it from the season being priced")
        }
        new StarterRequirements(starterMinimums, starterMaximums, startersPerTeam, teams)
    }
}
