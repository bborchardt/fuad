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

    /** The season's final state, which is what the next season's pre draft rosters are derived from. */
    END_OF_YEAR('', 'rosters_end_of_year.json')

    final String urlSuffix
    final String fileName

    private RosterSnapshot(String urlSuffix, String fileName) {
        this.urlSuffix = urlSuffix
        this.fileName = fileName
    }
}
