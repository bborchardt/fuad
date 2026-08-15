package ff.fetch

class FetchUtils {

    private static final List<Integer> REDIRECT_CODES = [301, 302, 303, 307, 308]
    private static final int MAX_REDIRECTS = 5

    private static final int TOO_MANY_REQUESTS = 429
    private static final int MAX_ATTEMPTS = 7
    private static final long FIRST_BACKOFF_MILLIS = 5000

    static String getBaseResourceFilePath() {
        String knownResource = 'ff/mfl/data/2017/draft.json'
        String knownFile = FetchUtils.class.getResource("/$knownResource").file
        String modulePath = knownFile - "/target/classes/$knownResource".toString()
        "$modulePath/src/main/resources"
    }

    /**
     * Fetch a url, following redirects by hand.
     *
     * MFL redirects every export to the server holding that season, and for seasons before 2020 it points at
     * http, which HttpURLConnection refuses to follow from an https request and so yields an empty body.
     * Those servers do serve https, so follow to the same location over https rather than dropping to
     * plaintext.
     */
    /**
     * Fetch a url, retrying when the league site rate limits.
     *
     * A season of weekly scores is fourteen calls in a row, which is enough to be turned away with a 429.
     * Backing off and retrying costs a few seconds; not doing so leaves a season half collected.
     */
    static String fetchText(String url) {
        long backoff = FIRST_BACKOFF_MILLIS
        for (int attempt = 1; ; attempt++) {
            try {
                return fetchTextOnce(url)
            } catch (IOException e) {
                if (attempt >= MAX_ATTEMPTS || !e.message?.contains(TOO_MANY_REQUESTS as String)) {
                    throw e
                }
                println "  rate limited, retrying in ${backoff}ms"
                Thread.sleep(backoff)
                backoff *= 2
            }
        }
    }

    private static String fetchTextOnce(String url) {
        String location = url
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection connection = new URL(location).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            if (!REDIRECT_CODES.contains(connection.responseCode)) {
                return connection.inputStream.getText('UTF-8')
            }
            String redirect = connection.getHeaderField('Location')
            if (!redirect) {
                throw new IllegalStateException("$location returned $connection.responseCode with no location.")
            }
            location = redirect.startsWith('http://') ? redirect.replaceFirst('http://', 'https://') : redirect
        }
        throw new IllegalStateException("$url redirected more than $MAX_REDIRECTS times.")
    }
}
