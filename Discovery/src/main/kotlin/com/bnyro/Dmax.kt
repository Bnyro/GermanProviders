package com.bnyro

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Dmax : MainAPI() {
    override var lang: String = "de"
    override val hasMainPage: Boolean = true

    override var name: String = "Dmax"
    override var mainUrl: String = "https://dmax.de"
    open var apiUrl: String = "https://eu1-prod.disco-api.com"
    open var metadataApiUrl: String = "https://public.aurora.enhanced.live"

    // service specific configuration
    open var serviceIdentifier: String = "dmaxde" // used for metadataApiUrl
    open var mediathekSlug: String = "sendungen" // used for metadataApiUrl
    open var apiTokenRealm = "dmaxde" // used for obtaining tokens from apiUrl

    private suspend fun obtainApiToken(): String {
        return app.get(
            "$apiUrl/token?realm=$apiTokenRealm", headers = mapOf(
                "X-Device-Info" to "STONEJS/1 (Unknown/Unknown; Linux/undefined; Unknown)",
                "X-disco-client" to "WEB:UNKNOWN:wbdatv:2.1.9",
                "X-disco-params" to "realm=$apiTokenRealm",
            )
        ).parsed<TokenResponse>().data.attributes.token
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val response =
            app.get("$metadataApiUrl/site/page/homepage/?filter[environment]=$serviceIdentifier&v=2&include=default")
                .parsed<MediaResult>()

        val pages =
            response.blocks.filter { it.items.isNotEmpty() }
                .map { block ->
                    HomePageList(
                        name = block.title.orEmpty(),
                        list = block.items.filter { it.pageType == "showpage" }
                            .map { it.toSearchResponse() }
                    )
                }.filter { it.list.isNotEmpty() }

        return newHomePageResponse(pages, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val response =
            app.get("$metadataApiUrl/site/search/page/?q=$query&filter[environment]=$serviceIdentifier&filter[type]=showpage&page[size]=20&v=2&include=default")
                .parsed<SearchRoot>()

        return response.data.map { it.toSearchResponse() }
    }

    private fun MediaResult.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = title,
            url = "$mainUrl/$mediathekSlug/$slug",
            type = TvType.Movie
        ) {
            this.posterUrl = image?.url
            this.year = datePublished.take(4).toIntOrNull()
        }
    }

    private fun EpisodeInfo.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = title.orEmpty(),
            url = fixUrl(link?.url ?: "/$mediathekSlug/${alternateId ?: url}"),
            type = TvType.Movie
        ) {
            this.posterUrl = poster?.src ?: image?.url
            this.year = publishStart?.take(4)?.toIntOrNull()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val slug = url.removeSuffix("/").substringAfterLast("/")
        val response =
            app.get("$metadataApiUrl/site/page/$slug/?filter[environment]=$serviceIdentifier&parent_slug=$mediathekSlug&v=2")
                .parsed<MediaResult>()

        val seriesBlock = response.blocks.firstOrNull { it.showId != null }
        if (seriesBlock != null) {
            val episodes = seriesBlock.items.mapNotNull { episode ->
                if (episode.id == null) null
                else newEpisode(StreamInfo(episode.id)) {
                    this.season = episode.seasonNumber?.toInt()
                    this.episode = episode.episodeNumber?.toInt()
                    this.name = episode.title
                    this.description = episode.description
                    this.runTime = episode.videoDuration?.div(60000)?.toInt()
                    this.posterUrl = episode.poster?.src
                }
            }

            return newTvSeriesLoadResponse(
                name = response.title,
                url = url,
                type = TvType.TvSeries,
                episodes = episodes
            ) {
                this.posterUrl = response.metaMedia.firstOrNull()?.media?.url
                this.year = response.datePublished.take(4).toIntOrNull()
                this.plot = response.description ?: response.metaDescription
                this.tags = response.taxonomies.map { it.title }
            }
        }

        val videoId =
            response.blocks.firstOrNull { it.videoId != null && it.title == response.title }
                ?.videoId

        return newMovieLoadResponse(
            name = response.title,
            url = url,
            type = TvType.Movie,
            dataUrl = StreamInfo(videoId.orEmpty()).toJson(),
        ) {
            this.posterUrl = response.metaMedia.firstOrNull()?.media?.url
            this.year = response.datePublished.take(4).toIntOrNull()
            this.plot = response.description ?: response.metaDescription
            this.tags = response.taxonomies.map { it.title }
            this.comingSoon = videoId == null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val videoId = AppUtils.parseJson<StreamInfo>(data).id

        val authToken = obtainApiToken()

        val response = app.post(
            "$apiUrl/playback/v3/videoPlaybackInfo",
            headers = mapOf(
                "Authorization" to "Bearer $authToken"
            ),
            json = VideoPlaybackRequest(videoId = videoId)
        )
            .parsed<VideoPlaybackResponse>()

        for (source in response.data.attributes.streaming) {
            callback(
                newExtractorLink(
                    source = serviceIdentifier,
                    name = serviceIdentifier,
                    url = source.url,
                )
            )
        }

        return response.data.attributes.streaming.isNotEmpty()
    }

    private data class TokenResponse(
        val data: TokenData
    )

    private data class TokenData(
        val attributes: TokenAttributes,
        val id: String,
        val type: String,
    )

    private class TokenAttributes(
        val token: String,
    )

    private data class SearchRoot(
        val data: List<MediaResult>,
    )

    private data class MediaResult(
        val uid: String? = null,
        val title: String,
        val dateCreated: String,
        val dateLastModified: String,
        val datePublished: String,
        val type: String,
        val slug: String,
        val subtitle: String?,
        val description: String?,
        val metaTitle: String?,
        val metaDescription: String?,
        val taxonomies: List<Taxonomy>,
        val image: Image?,
        // not available in search
        val metaMedia: List<MediaItem> = emptyList(),
        val blocks: List<Block> = emptyList()
    )

    private data class Image(
        val title: String?,
        val type: String?,
        val url: String?,
        val imageServiceKey: String?,
    )

    private data class Taxonomy(
        val id: String,
        val title: String,
        val slug: String,
        val category: String,
    )

    private data class MediaItem(
        val media: Image,
        val role: String,
    )

    private data class Block(
        val title: String?,
        val type: String?,
        val videoId: String?,
        val showId: String?,
        val items: List<EpisodeInfo> = emptyList()
    )

    private data class EpisodeInfo(
        val id: String?,
        val alternateId: String?,
        val description: String?,
        val videoType: String?,
        val videoDuration: Long?,
        val publishStart: String?,
        val publishEnd: String?,
        val episodeNumber: Long?,
        val seasonNumber: Long?,
        val type: String?,
        val title: String?,
        val show: Show?,
        val channel: Channel?,
        val poster: Poster?,
        val genres: List<String> = emptyList(),
        // only available at homepage
        val image: Image?,
        val link: Link?,
        val url: String?,
        val pageType: String?
    )

    private data class Link(
        val url: String
    )

    private data class Show(
        val id: String,
        val title: String,
    )

    private data class Channel(
        val id: String,
        val title: String,
    )

    private data class Poster(
        val src: String
    )

    private data class VideoPlaybackRequest(
        val videoId: String,
        val deviceInfo: DeviceInfo = DeviceInfo(),
        val visteriaProperties: Map<String, String> = mapOf()
    )

    private data class DeviceInfo(
        val adBlocker: Boolean = false,
        val drmSupported: Boolean = false,
        val hdrCapabilities: List<String> = listOf("SDR"),
        val hwDecodingCapabilities: List<String> = listOf(),
        val soundCapabilities: List<String> = listOf("STEREO")
    )

    private data class VideoPlaybackResponse(
        val data: VideoInfoData,
    )

    private data class VideoInfoData(
        val attributes: VideoAttributes,
        val id: String,
        val type: String,
    )

    private data class VideoAttributes(
        val markers: Markers,
        val reportProgressInterval: Long,
        val ssaiInfo: SsaiInfo,
        val streaming: List<Streaming>,
        val userInfo: UserInfo,
        val viewingHistory: ViewingHistory,
    )

    private data class Markers(
        val videoAboutToEnd: Long,
    )

    private data class SsaiInfo(
        val adMetadataSchemaVersion: String,
        val forecastTimeline: List<ForecastTimeline>,
        val vendorAttributes: VendorAttributes,
    )

    private data class ForecastTimeline(
        val event: Event,
        val time: Time,
        val triggerTime: Double,
    )

    private data class Event(
        val action: String,
        val schema: String,
        val duration: Double?,
        val position: Long?,
    )

    private data class Time(
        val contentPosition: Double,
        val streamPosition: Double,
    )

    private data class VendorAttributes(
        val interstitialUrl: String,
        val pingUrl: String,
        val sessionId: String,
    )

    private data class Streaming(
        val cdn: String,
        val fallback: Boolean,
        val playbackProfile: String,
        val protection: Protection,
        val provider: String,
        val streamMode: String,
        val type: String,
        val url: String,
    )

    private data class Protection(
        val clearkeyEnabled: Boolean,
        val drmEnabled: Boolean,
    )

    private data class UserInfo(
        val packages: List<String>,
    )

    private data class ViewingHistory(
        val viewed: Boolean,
    )

    private data class StreamInfo(
        val id: String
    )
}
