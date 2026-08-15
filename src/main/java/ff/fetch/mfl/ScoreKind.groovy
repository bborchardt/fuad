package ff.fetch.mfl

/**
 * The two kinds of weekly score the league site will serve, both under the league's own scoring rules.
 *
 * Only one of them is safe to refetch. See {@link MflWeeklyScoresRefresh}.
 */
enum ScoreKind {

    /** What players are expected to score. Only a forecast if collected before the season starts. */
    PROJECTED('projectedScores', 'projectedScores', 'projected_scores.json'),

    /** What players did score. A finished season's record, refetchable at any time. */
    ACTUAL('playerScores', 'playerScores', 'player_scores.json')

    final String exportType
    final String jsonKey
    final String fileName

    private ScoreKind(String exportType, String jsonKey, String fileName) {
        this.exportType = exportType
        this.jsonKey = jsonKey
        this.fileName = fileName
    }
}
