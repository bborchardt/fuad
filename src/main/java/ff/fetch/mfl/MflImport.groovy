package ff.fetch.mfl

/**
 * Write to myfantasyleague.com.
 *
 * {@link ff.fetch.FetchUtils} reads; this writes, and the two are not symmetric. Every import is a POST
 * carrying a commissioner's session cookie, and MFL answers a request it could not apply exactly as it
 * answers one it did: <code>&lt;status&gt;OK&lt;/status&gt;</code>. A load that reported four OKs and left
 * the league empty is what taught this, so nothing here treats OK as evidence. The runner verifies against
 * the export afterwards, and this class only reports what MFL said.
 *
 * Two rules the API documents and this enforces:
 *
 * <ul>
 *   <li><b>The DATA format is the export format.</b> "With XML data is imperative that you get the
 *       formatting right as well as the various element and attribute names. The data being imported would
 *       always be in the same format as the corresponding export data." A draftResults payload without the
 *       draftUnit wrapper, or with round="1" where the export writes round="01", imports nothing and
 *       returns OK.</li>
 *   <li><b>Writes go to the league's own host.</b> MFL answers other hosts with a redirect, and a POST is
 *       not re-sent across one, so a write aimed at api.myfantasyleague.com can be dropped in silence.
 *       {@link #resolveHost} reads the league's host out of its own export.</li>
 * </ul>
 */
class MflImport {

    private static final int TOO_MANY_REQUESTS = 429
    private static final int MAX_ATTEMPTS = 5
    private static final long FIRST_BACKOFF_MILLIS = 5000
    /** MFL rate limits a burst; a load is dozens of calls in a row. */
    private static final long PACE_MILLIS = 1000

    final String host
    final int year
    final int leagueId
    private String cookie

    MflImport(String host, int year, int leagueId) {
        this.host = host
        this.year = year
        this.leagueId = leagueId
    }

    /**
     * The host MFL serves this league from, read from the league's own draftResults export.
     *
     * Falls back to the API host, which is right for reads and wrong only for a league whose export cannot
     * be parsed -- in which case the caller has a larger problem than the host.
     */
    static String resolveHost(int year, int leagueId, String apiHost = 'api.myfantasyleague.com') {
        try {
            String json = ff.fetch.FetchUtils.fetchText(
                    "https://$apiHost/$year/export?JSON=1&TYPE=draftResults&L=$leagueId")
            def unit = new groovy.json.JsonSlurper().parseText(json)?.draftResults?.draftUnit
            String url = (unit instanceof List ? unit.first() : unit)?.static_url
            def matcher = url =~ '^https?://([^/]+)/'
            return matcher ? matcher[0][1] : apiHost
        } catch (Exception ignored) {
            return apiHost
        }
    }

    /**
     * Exchange credentials for a session cookie.
     *
     * The password is never logged and never placed in a URL: MFL's own documentation asks for POST over
     * HTTPS for exactly this call.
     */
    void login(String username, String password) {
        String body = form([USERNAME: username, PASSWORD: password, XML: '1'])
        HttpURLConnection connection = open("https://$host/$year/login")
        String response = send(connection, body)
        String header = connection.getHeaderFields()?.get('Set-Cookie')?.find { it?.contains('MFL_USER_ID') }
        if (!header) {
            throw new IllegalStateException("Login as $username returned no MFL_USER_ID cookie: $response")
        }
        cookie = header.split(';')[0]
    }

    /**
     * Post one import and return MFL's response body.
     *
     * The caller decides what the body means. An <code>&lt;error&gt;</code> is returned rather than thrown
     * because several are expected and survivable -- a player already rostered, a franchise with nothing to
     * drop -- and a load that aborted on each would never finish.
     */
    String importType(String type, Map<String, String> params) {
        if (!cookie) {
            throw new IllegalStateException('Not logged in: call login() before importType().')
        }
        Map<String, String> all = [TYPE: type, L: leagueId as String] + params
        long backoff = FIRST_BACKOFF_MILLIS
        for (int attempt = 1; ; attempt++) {
            HttpURLConnection connection = open("https://$host/$year/import")
            connection.setRequestProperty('Cookie', cookie)
            String response = send(connection, form(all))
            if (attempt >= MAX_ATTEMPTS || !rateLimited(connection, response)) {
                Thread.sleep(PACE_MILLIS)
                return response
            }
            println "  rate limited, retrying in ${backoff}ms"
            Thread.sleep(backoff)
            backoff *= 2
        }
    }

    /** True when MFL turned the request away for rate rather than for content. */
    private static boolean rateLimited(HttpURLConnection connection, String response) {
        connection.responseCode == TOO_MANY_REQUESTS || response?.contains(TOO_MANY_REQUESTS as String)
    }

    /** MFL reports a refusal as an error element; everything else is taken at face value by the caller. */
    static boolean failed(String response) {
        response == null || response.contains('<error>')
    }

    /** The text of an error response, for a caller that wants to print why rather than that. */
    static String errorText(String response) {
        def matcher = response =~ '(?s)<error>(.*?)</error>'
        matcher ? matcher[0][1].trim() : response?.trim()
    }

    private static HttpURLConnection open(String url) {
        HttpURLConnection connection = new URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = 'POST'
        connection.doOutput = true
        connection.instanceFollowRedirects = false
        connection.setRequestProperty('Content-Type', 'application/x-www-form-urlencoded')
        connection
    }

    private static String send(HttpURLConnection connection, String body) {
        connection.outputStream.withWriter('UTF-8') { it.write(body) }
        InputStream stream = connection.responseCode >= 400 ? connection.errorStream : connection.inputStream
        stream ? stream.getText('UTF-8') : ''
    }

    /** Form encoding, so a password or a payload carrying &, = or a newline survives the trip intact. */
    private static String form(Map<String, String> params) {
        params.collect { key, value ->
            "${URLEncoder.encode(key, 'UTF-8')}=${URLEncoder.encode(value ?: '', 'UTF-8')}"
        }.join('&')
    }
}
