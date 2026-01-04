package com.bnyro

import java.io.InputStream


class IptvPlaylistParser {
    /**
     * Parse M3U8 string into [Playlist]
     *
     * @param content M3U8 content string.
     * @throws PlaylistParserException if an error occurs.
     */
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    /**
     * Parse M3U8 content [InputStream] into [Playlist]
     *
     * @param input Stream of input data.
     * @throws PlaylistParserException if an error occurs.
     */
    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()

        if (!reader.readLine().isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems = mutableListOf<PlaylistItem>()

        var currentTitle: String? = null
        var currentAttributes: Map<String, String> = emptyMap()
        var currentTagParams: MutableMap<String, String> = mutableMapOf()

        reader.readLines().map { it.trim() }.forEach { trimmedLine ->
            if (trimmedLine.isEmpty()) return@forEach

            when {
                trimmedLine.startsWith(EXT_INF) -> {
                    currentTitle = trimmedLine.getTitle()
                    currentAttributes = trimmedLine.getAttributes()
                    // Reset fields in case the last item was incomplete
                    currentTagParams = mutableMapOf()
                }

                trimmedLine.startsWith(EXT_VLC_OPT) -> {
                    val (tag, value) = trimmedLine.getTag() ?: return@forEach
                    currentTagParams[tag] = value
                }

                !trimmedLine.startsWith("#") -> {
                    val url = trimmedLine.getUrl()
                    val urlParams = trimmedLine.getUrlParameters()

                    if (currentTitle != null && url != null) {
                        playlistItems.add(
                            PlaylistItem(
                                url = url,
                                title = currentTitle,

                                userAgent = currentTagParams["http-user-agent"] ?: urlParams["user-agent"],
                                referer = currentTagParams["http-referrer"] ?: urlParams["referer"],

                                key = urlParams["key"] ?: currentAttributes["key"],
                                keyid = urlParams["keyid"] ?: currentAttributes["keyid"],

                                channelId = currentAttributes["tvg-id"],
                                logo = currentAttributes["tvg-logo"],
                                groupTitle = currentAttributes["group-title"]
                            )
                        )
                    }

                    currentTitle = null
                    currentTagParams = mutableMapOf()
                    currentAttributes = mutableMapOf()
                }
            }
        }

        return Playlist(playlistItems)
    }

    /**
     * Replace "" (quotes) from given string.
     */
    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    /**
     * Check if given content is valid M3U8 playlist.
     */
    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U)

    /**
     * Get title of media.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTINF:-1 tvg-id="1234" group-title="Kids" tvg-logo="url/to/logo", Title
     * ```
     * Result: Title
     */
    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    /**
     * Get media url.
     *
     * Example:-
     *
     * Input:
     * ```
     * https://example.com/sample.m3u8|user-agent="Custom"
     * ```
     * Result: https://example.com/sample.m3u8
     */
    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }

    /**
     * Get url parameters.
     *
     * Example:-
     *
     * Input:
     * ```
     * http://192.54.104.122:8080/d/abcdef/video.mp4|User-Agent=Mozilla&Referer=CustomReferrer
     * ```
     * Result will be equivalent to kotlin map:
     * ```Kotlin
     * mapOf(
     *   "User-Agent" to "Mozilla",
     *   "Referer" to "CustomReferrer"
     * )
     * ```
     */
    private fun String.getUrlParameters(): Map<String, String> {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val headersString = replace(urlRegex, "").replaceQuotesAndTrim()
        return headersString.split("&").mapNotNull {
            val pair = it.split("=")
            if (pair.size == 2) pair.first().lowercase() to pair.last() else null
        }.toMap()
    }

    /**
     * Get attributes from `#EXTINF` tag as Map<String, String>.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTINF:-1 tvg-id="1234" group-title="Kids" tvg-logo="url/to/logo", Title
     * ```
     * Result will be equivalent to kotlin map:
     * ```Kotlin
     * mapOf(
     *   "tvg-id" to "1234",
     *   "group-title" to "Kids",
     *   "tvg-logo" to "url/to/logo"
     *)
     * ```
     */
    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("($EXT_INF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()
        return attributesString.split(Regex("\\s")).mapNotNull {
            val pair = it.split("=")
            if (pair.size == 2) pair.first() to pair.last()
                .replaceQuotesAndTrim() else null
        }.toMap()
    }

    /**
     * Get key and value from a tag.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTVLCOPT:http-referrer=http://example.com/
     * ```
     * Result: ("http-referrer", "http://example.com/")
     */
    private fun String.getTag(): Pair<String, String>? {
        val keyRegex = Regex("$EXT_VLC_OPT:(.*)=(.*)", RegexOption.IGNORE_CASE)
        val result = keyRegex.find(this)?.groups ?: return null

        return result[1]?.value.orEmpty() to result[2]?.value?.replaceQuotesAndTrim().orEmpty()
    }

    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }

}

/**
 * Exception thrown when an error occurs while parsing playlist.
 */
sealed class PlaylistParserException(message: String) : Exception(message) {

    /**
     * Exception thrown if given file content is not valid.
     */
    class InvalidHeader :
        PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")


}

data class PlaylistItem(
    val url: String,
    val title: String,

    // HTTP header
    val userAgent: String? = null,
    val referer: String? = null,

    // DRM
    val key: String? = null,
    val keyid: String? = null,

    // Channel information
    val channelId: String? = null,
    val logo: String? = null,
    val groupTitle: String? = null,
)

data class Playlist(
    val items: List<PlaylistItem> = emptyList(),
)