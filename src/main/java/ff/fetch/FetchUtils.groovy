package ff.fetch

class FetchUtils {

    static String getBaseResourceFilePath() {
        String knownResource = 'ff/mfl/data/2017/draft.json'
        String knownFile = FetchUtils.class.getResource("/$knownResource").file
        String modulePath = knownFile - "/target/classes/$knownResource".toString()
        "$modulePath/src/main/resources"
    }
}
