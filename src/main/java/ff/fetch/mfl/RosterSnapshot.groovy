package ff.fetch.mfl

/**
 * The points in a season at which rosters are worth keeping.
 *
 * rosters.json, pulled by {@link MflDataRefresh}, is a third one: the rosters before that season's auction,
 * with every expiring contract already wiped. It is the only snapshot that cannot be refetched after the
 * fact, since the league site holds one live copy of each season that moves on without it.
 */
enum RosterSnapshot {

    /** Week 1, so the auction is in but in season waiver pickups are not. What players were signed for. */
    POST_DRAFT('&W=1', 'rosters_post_draft.json'),

    /**
     * The trading deadline, which is kickoff of the first game of week 12 by bylaw 10.1.
     *
     * The one snapshot taken for a rule rather than for a state. Both the franchise tag and every rookie
     * salary are set off "salaries at the prior year trading deadline", and a salary is not fixed for the
     * season: a player signed in week 14 has a salary at the end of the year and none at the deadline, and
     * one released before it has the reverse. Reading either rule off {@link #END_OF_YEAR} is right most
     * years and wrong without warning in the rest — it puts 2025's RB baseline at 9 where the deadline says
     * 7, which moves every running back taken in the 2026 draft.
     */
    DEADLINE('&W=12', 'rosters_deadline.json'),

    /** The season's final state, which is what the next season's pre draft rosters are derived from. */
    END_OF_YEAR('', 'rosters_end_of_year.json')

    final String urlSuffix
    final String fileName

    private RosterSnapshot(String urlSuffix, String fileName) {
        this.urlSuffix = urlSuffix
        this.fileName = fileName
    }
}
