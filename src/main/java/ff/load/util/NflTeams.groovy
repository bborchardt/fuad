package ff.load.util

/**
 * NFL teams, so that a defence named one way in one source can be recognised in another.
 *
 * Team defences are the one thing the sources name by convention rather than by person, and the conventions
 * do not agree. FantasyPros writes {@code Arizona Cardinals} in most seasons and {@code Arizona (ARI)} in
 * 2018 to 2020; the Yahoo draft export writes {@code Cardinals}. None of the name matching that works for
 * players helps here — there is no common prefix between "Chicago (CHI)" and "Bears" at all.
 *
 * <b>Three franchises have changed name inside the collected seasons</b>, and a map keyed on the current
 * name would drop them from the years they went by the old one. Washington is Redskins through 2019,
 * Football Team in 2020 and 2021, and Commanders from 2022. The Raiders moved from Oakland to Las Vegas and
 * the Chargers from San Diego to Los Angeles, which changes the city and the abbreviation while the nickname
 * holds.
 *
 * <b>The city alone is not an identity.</b> Both Los Angeles teams are "Los Angeles", so the 2018-2020 form
 * is resolved on its abbreviation and every other form on its nickname.
 */
class NflTeams {

    /** Nickname to canonical abbreviation, including the nicknames franchises have since dropped. */
    private static final Map<String, String> BY_NICKNAME = [
            'bills': 'BUF', 'dolphins': 'MIA', 'patriots': 'NE', 'jets': 'NYJ',
            'ravens': 'BAL', 'bengals': 'CIN', 'browns': 'CLE', 'steelers': 'PIT',
            'texans': 'HOU', 'colts': 'IND', 'jaguars': 'JAC', 'titans': 'TEN',
            'broncos': 'DEN', 'chiefs': 'KC', 'raiders': 'LV', 'chargers': 'LAC',
            'cowboys': 'DAL', 'giants': 'NYG', 'eagles': 'PHI',
            'commanders': 'WAS', 'redskins': 'WAS', 'football team': 'WAS',
            'bears': 'CHI', 'lions': 'DET', 'packers': 'GB', 'vikings': 'MIN',
            'falcons': 'ATL', 'panthers': 'CAR', 'saints': 'NO', 'buccaneers': 'TB',
            'cardinals': 'ARI', 'rams': 'LAR', '49ers': 'SF', 'seahawks': 'SEA',
    ].asImmutable() as Map<String, String>

    /**
     * Abbreviations the sources have used, mapped onto the canonical one.
     *
     * The 2018-2020 exports carry the abbreviation in parentheses and nothing else usable, so this is the
     * only way into those three seasons.
     */
    private static final Map<String, String> BY_ABBREVIATION = [
            'JAX': 'JAC', 'LA': 'LAR', 'OAK': 'LV', 'LVR': 'LV', 'SD': 'LAC', 'SDG': 'LAC',
            'WSH': 'WAS', 'NOR': 'NO', 'TAM': 'TB', 'SFO': 'SF', 'KAN': 'KC', 'NWE': 'NE',
            'GNB': 'GB', 'ARZ': 'ARI', 'BLT': 'BAL', 'CLV': 'CLE', 'HST': 'HOU',
    ].asImmutable() as Map<String, String>

    private static final String ABBREVIATED = /^(.*?)\s*\(([A-Za-z]{2,3})\)$/

    /**
     * The canonical abbreviation for a team named any of the ways the sources name one, or null.
     *
     * Null for anything that is not a team, which is how a caller tells a defence from a player without
     * having to know in advance which it is holding.
     */
    static String abbreviationOf(String name) {
        String trimmed = name?.trim()
        if (!trimmed) {
            return null
        }
        // "Arizona (ARI)": the abbreviation is the only part that identifies the team, since both Los
        // Angeles teams share a city.
        def matcher = trimmed =~ ABBREVIATED
        if (matcher.matches()) {
            String abbreviation = (matcher[0] as List)[2].toString().toUpperCase()
            return canonical(abbreviation)
        }
        String lower = trimmed.toLowerCase()
        if (BY_NICKNAME.containsKey(lower)) {
            return BY_NICKNAME[lower]
        }
        // "Arizona Cardinals", "Washington Football Team": the nickname is the tail, and is one or two words.
        String matched = BY_NICKNAME.keySet().find { lower.endsWith(" $it") }
        matched ? BY_NICKNAME[matched] : canonical(trimmed.toUpperCase())
    }

    /** Whether a name refers to a team rather than to a player. */
    static boolean isTeam(String name) {
        abbreviationOf(name) != null
    }

    private static String canonical(String abbreviation) {
        BY_ABBREVIATION[abbreviation] ?: (BY_NICKNAME.values().contains(abbreviation) ? abbreviation : null)
    }
}
