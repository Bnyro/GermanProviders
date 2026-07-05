package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.UUID

open class PlutoTV : MainAPI() {
    override var mainUrl: String = "https://pluto.tv"
    override var name: String = "PlutoTV"
    override val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries, TvType.Live)
    override var lang: String = "en"

    open val bootUrl = "https://boot.pluto.tv"

    override val hasMainPage: Boolean = true

    private lateinit var sessionData: Pair<String, Servers>

    private suspend fun obtainSessionData(): Pair<String, Servers> {
        if (this::sessionData.isInitialized) return sessionData

        val bootResponse = app.get(
            "$bootUrl/v4/start", params = mapOf(
                "appName" to "web",
                "appVersion" to "9.22.0",
                "deviceVersion" to "142.0.0",
                "deviceModel" to "web",
                "deviceMake" to "firefox",
                "clientID" to UUID.randomUUID().toString(),
                "clientModelNumber" to "1.0.0"
            )
        ).parsed<BootResponse>()

        sessionData = bootResponse.sessionToken to bootResponse.servers
        return sessionData
    }

    private suspend fun authHeaders(): Map<String, String> {
        val (jwt, _) = obtainSessionData()

        return mapOf("Authorization" to "Bearer $jwt")
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val (_, servers) = obtainSessionData()

        // live
        val channels = app.get(
            "${servers.channels}/v2/guide/channels?sort=number:asc",
            headers = authHeaders()
        )
            .parsed<ChannelsResponse>().data.map { channel ->
                newLiveSearchResponse(
                    name = channel.name,
                    url = channel.toJson(),
                    fix = false
                ) {
                    this.posterUrl = channel.images.firstOrNull()?.url
                }
            }

        // vod
        val categoriesResponse = app.get(
            "${servers.vod}/v4/vod/categories",
            params = mapOf("includeItems" to "true"),
            headers = authHeaders()
        ).parsedLarge<CategoriesResponse>()

        val categories = categoriesResponse.categories.map { category ->
            HomePageList(category.name, category.items.map { it.toMovieSearchResponse() })
        }

        return newHomePageResponse(
            listOf(
                HomePageList("Live", channels)
            ) + categories
        )
    }

    fun MediaItem.toMovieSearchResponse(): MovieSearchResponse {
        return newMovieSearchResponse(
            name = name,
            url = toJson(),
            fix = false
        ) {
            this.posterUrl = featuredImage.path
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val (_, servers) = obtainSessionData()

        val response = app.get(
            "${servers.search}/v1/search", headers = authHeaders(),
            params = mapOf("q" to query, "limit" to "100")
        ).parsed<SearchResults>()

        val resultsDatas = app.get(
            "${servers.vod}/v4/vod/items",
            headers = authHeaders(),
            params = mapOf(
                "ids" to response.data.joinToString(",") { it.id }
            )
        ).parsed<SearchDetailsResponse>()

        return resultsDatas.map { it.toMovieSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val (_, servers) = obtainSessionData()

        val channel = AppUtils.tryParseJson<Channel>(url)
        channel?.let { channel ->
            return newLiveStreamLoadResponse(
                name = channel.name,
                url = "$mainUrl/gsa/live-tv/${channel.id}",
                dataUrl = channel.stitched.toJson()
            ) {
                this.posterUrl = channel.images.firstOrNull()?.url
                this.plot = channel.summary
            }
        }

        val details = AppUtils.tryParseJson<MediaItem>(url)
        details?.let { details ->
            if (details.type == "series") {
                val seasonInfo = app.get(
                    "${servers.vod}/v4/vod/series/${details.id}/seasons",
                    headers = authHeaders(),
                ).parsed<SeasonInfo>()

                val episodes = seasonInfo.seasons.flatMap { season ->
                    season.episodes.map { episode ->
                        newEpisode(episode.stitched) {
                            this.name = episode.name
                            this.posterUrl = episode.covers.firstOrNull()?.url
                            this.runTime = (episode.originalContentDuration / 1000 / 60).toInt()
                            this.description = episode.description
                            this.season = season.number.toInt()
                        }
                    }
                }

                return newTvSeriesLoadResponse(
                    name = seasonInfo.name,
                    url = "$mainUrl/gsa/search/details/series/${seasonInfo.id}",
                    type = TvType.TvSeries,
                    episodes = episodes
                ) {
                    this.posterUrl = seasonInfo.featuredImage.path
                    this.plot = seasonInfo.summary
                }
            } else {
                return newMovieLoadResponse(
                    name = details.name,
                    url = "$mainUrl/gsa/search/details/movies/${details.id}",
                    dataUrl = details.stitched.toJson(),
                    type = TvType.Movie,
                ) {
                    this.posterUrl = details.featuredImage.path
                    this.plot = details.summary
                    this.duration = (details.originalContentDuration / 1000 / 60).toInt()
                }
            }
        }

        return null
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val stream = AppUtils.parseJson<Stitched>(data)
        val (_, servers) = obtainSessionData()

        val (url, type) = when (stream.type) {
            null, "hls" -> "${servers.stitcher}/v2${stream.path}" to ExtractorLinkType.M3U8
            "dash", "mpd" -> "${servers.stitcherDash}/v2${stream.path}" to ExtractorLinkType.DASH
            else -> return true
        }

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = url,
                type = type
            ) {
                this.headers = authHeaders()
            }
        )

        return true
    }


    data class BootResponse(
        val servers: Servers,
        val session: Session,
        val startingChannel: StartingChannel,
        val stitcherParams: String,
        val serverTime: String,
        val refreshInSec: Long,
        val isSuspicious: Boolean,
        val sessionToken: String,
    )

    data class Servers(
        val api: String,
        val channels: String,
        val vod: String,
        val stitcher: String,
        val analytics: String,
        val watchlist: String,
        val search: String,
        val concierge: String,
        val preferences: String,
        val campaigns: String,
        val users: String,
        val recommender: String,
        val catalog: String,
        val stitcherDash: String,
        val pause: String,
        val carousel: String,
        val features: String,
        val hub: String,
    )

    data class Session(
        @JsonProperty("sessionID")
        val sessionId: String,
        @JsonProperty("clientIP")
        val clientIp: String,
        val countryCode: String,
        val activeRegion: String,
        val city: String,
        val postalCode: String,
        val preferredLanguage: String,
        val deviceType: String,
        val deviceMake: String,
        val deviceModel: String,
        val logLevel: String,
        @JsonProperty("clientID")
        val clientId: String,
        val clientDeviceType: Long,
        val marketingRegion: String,
        @JsonProperty("restartThresholdMS")
        val restartThresholdMs: Long,
    )

    data class Stitched(
        val path: String?,
        val type: String?
    )

    data class Image(
        val type: String,
        val style: String,
        val ratio: Double,
        val defaultWidth: Long,
        val defaultHeight: Long,
        val url: String,
    )

    data class StartingChannel(
        val id: String,
        val slug: String,
    )

    data class ChannelsResponse(
        val data: List<Channel>,
    )

    data class Channel(
        val id: String,
        val slug: String,
        val name: String,
        val hash: String,
        val number: Long,
        val summary: String,
        val plutoOfficeOnly: Boolean,
        val featured: Boolean,
        val featuredOrder: Long?,
        val stitched: Stitched,
        val images: List<Image>,
        @JsonProperty("categoryIDs")
        val categoryIds: List<String>,
        val tmsid: String?,
        val kidsMode: Boolean?,
    )

    data class SearchResults(
        val data: List<SearchResult>,
    )

    data class SearchResult(
        val id: String,
        val slug: String,
        val name: String,
        val type: String,
        val language: String,
        val rating: String?,
        val start: String?,
        val stop: String?,
        val channel: SearchChannel?,
        val number: Long?,
    )

    data class SearchChannel(
        val slug: String,
        val name: String,
        val number: Long,
        val logo: Logo,
    )

    data class Logo(
        val path: String,
    )

    class SearchDetailsResponse : ArrayList<MediaItem>()

    data class MediaItem(
        @JsonProperty("_id")
        val id: String,
        @JsonProperty("seriesID")
        val seriesId: String?,
        val slug: String,
        val name: String,
        val summary: String,
        val description: String,
        val duration: Long?,
        val originalContentDuration: Long,
        val allotment: Long?,
        val rating: String,
        val featuredImage: FeaturedImage,
        val genre: String,
        val type: String,
        val seasonsNumbers: List<Long>,
        val stitched: Stitched,
        val covers: List<Cover>,
        val clip: Clip?,
        val avail: Map<String, Any>,
    )

    data class FeaturedImage(
        val path: String,
    )

    data class Clip(
        val actors: List<String>?,
        val directors: List<String>?,
        val producers: List<String>?,
        val originalReleaseDate: String,
        val writers: List<String>?,
    )

    data class SeasonInfo(
        @JsonProperty("_id")
        val id: String,
        val name: String,
        val summary: String,
        val description: String,
        val slug: String,
        val type: String,
        val rating: String,
        val featuredImage: FeaturedImage,
        val genre: String,
        val offset: Long,
        val page: Long,
        val seasons: List<Season>,
        val covers: List<Cover2>,
        val avail: Map<String, Any>,
    )

    data class Season(
        val episodes: List<Episode>,
        val number: Long,
    )

    data class Episode(
        @JsonProperty("_id")
        val id: String,
        val name: String,
        val description: String,
        val allotment: Long,
        val rating: String,
        val slug: String,
        val duration: Long,
        val originalContentDuration: Long,
        val genre: String,
        val type: String,
        val number: Long,
        val season: Long,
        val stitched: Stitched,
        val covers: List<Cover>,
        val clip: Clip,
    )

    data class Cover(
        val aspectRatio: String,
        val url: String,
    )

    data class Cover2(
        val aspectRatio: String,
        val url: String,
    )

    data class CategoriesResponse(
        val offset: Long,
        val page: Long,
        val totalCategories: Long,
        val totalPages: Long,
        val categories: List<HomePageCategory>,
    )

    data class HomePageCategory(
        @JsonProperty("_id")
        val id: String,
        val name: String,
        val plutoOfficeOnly: Boolean,
        val page: Long,
        val offset: Long,
        val totalItemsCount: Long,
        val items: List<MediaItem>,
        @JsonProperty("hero_carousel")
        val heroCarousel: Boolean?,
        val kidsMode: Boolean?,
    )
}