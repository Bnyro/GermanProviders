package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

// Discovered Routes
// https://https://api.arte.tv/api/emac/v4/de/web/collections/{collectionId} for series
// https://api.arte.tv/api/emac/v4/de/web/programs/{episodeId} for continuation of episodes
// https://api.arte.tv/api/emac/v4/de/web/pages/ for a list of collections (e.g. CIN, SER)
// https://api.arte.tv/api/emac/v4/de/web/pages/{page} for viewing a specific page (e.g. CIN)
// https://api.arte.tv/api/emac/v4/de/web/pages/SEARCH/?page={pageNo}&query={query} for searching
// https://api.arte.tv/api/emac/v4/de/web/zones/{zoneId}/content?collectionId={collectionId}&page={pageNo}&subCollectionId={collectionId} for collection pagination
// https://api.arte.tv/api/player/v2/config/de/{programId} for stream sources
// https://api.arte.tv/api/player/v2/playlist/de/{collectionId} for stream playlist info

open class Arte : MainAPI() {
    override var name = "Arte"
    override var lang = "de"
    override val hasQuickSearch = true
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var mainUrl = "https://www.arte.tv"
    open var apiBaseUrl = "https://api.arte.tv"

    open var client = "web"

    override val mainPage = mainPageOf(
        "AVN" to "Demnächst",
        "CIN" to "Filme",
        "SER" to "Serien",
        "HIS" to "Geschichte",
        "SCI" to "Wissenschaft",
        "CPO" to "Kultur und Pop",
        "DEC" to "Entdeckung der Welt",
        "ACT" to "Aktuelles und Gesellschaft"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val zones = app.get("$apiBaseUrl/api/emac/v4/$lang/$client/pages/${request.data}")
            .parsed<ZoneInfoResponse>()
            .zones
            .filter { it.content != null && it.content.data.filterNotTopic().size >= 5 }

        val pages = zones.map { zone ->
            val items = zone.content!!.data.filterNotTopic()
            HomePageList(name = zone.title, list = items.map { it.toSearchResponse() })
        }

        return newHomePageResponse(pages, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val resp = app.get(
            "$apiBaseUrl/api/emac/v4/$lang/$client/pages/SEARCH/?page=1&query=$query"
        )
            .parsed<MediaListResponse>()

        return resp.zones.flatMap { it.content?.data.orEmpty() }.filterNotTopic()
            .map { it.toSearchResponse() }
    }

    private fun List<ResultItem>.filterNotTopic(): List<ResultItem> {
        return filter { it.kind.code != "TOPIC" }
    }

    private fun ResultItem.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            type = if (kind.code == "SHOW") TvType.TvSeries else TvType.Movie,
            url = fixUrl(this.url),
            name = this.title,
        ) {
            posterUrl = this@toSearchResponse.mainImage?.url?.replace("__SIZE__", "380x214")
            year = this@toSearchResponse.availability?.start?.take(4)?.toIntOrNull()
        }
    }

    private fun extractProgramId(url: String): String? {
        return Regex("/videos/(.+?)/").find(url)?.groupValues?.get(1)
    }

    private fun isSeries(programId: String): Boolean {
        return programId.startsWith("RC")
    }

    private fun ResultItem.toEpisodeItem(season: Int?): Episode {
        return newEpisode(url = fixUrl(url), initializer = {
            this.name = listOfNotNull(title, subtitle).joinToString(" - ")
            this.description = shortDescription
            this.posterUrl = mainImage?.url?.replace("__SIZE__", "380x214")

            val numberMatch = Regex(""""\((\d+)/\d+\)$""").find(title)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            this.season = season
            this.episode = numberMatch

            this.runTime = duration.div(60).toInt()
        })
    }

    override suspend fun load(url: String): LoadResponse? {
        val programId = extractProgramId(url) ?: return null

        if (isSeries(programId)) {
            val seriesInfo = app.get("$apiBaseUrl/api/emac/v4/$lang/$client/collections/$programId")
                .parsed<ProgramInfo>()

            val episodes = seriesInfo.zones.filter {
                it.displayOptions?.template != "single-collectionContent"
            }.amap { zone ->
                val seasonNumber = zone.slug?.substringAfterLast("-")?.toIntOrNull()
                val episodes = zone.content?.data.orEmpty().map { it.toEpisodeItem(seasonNumber) }
                    .toMutableList()

                // format: {zoneId}_{collectionId}_{subCollectionId}
                val zoneIdParts = zone.id.split("_")
                val paginationZoneId = zoneIdParts.first()

                val maxPage = zone.content?.pagination?.pages ?: 1
                var currentPage = zone.content?.pagination?.currentPage ?: 1
                while (currentPage != maxPage) {
                    currentPage++

                    var paginationUrl =
                        "$apiBaseUrl/api/emac/v4/$lang/$client/zones/$paginationZoneId/content?page=${currentPage}&collectionId=$programId"
                    if (zoneIdParts.size == 3) paginationUrl += "&subCollectionId=${zoneIdParts.last()}"
                    val episodesInfo = app.get(paginationUrl)
                        .parsed<ZoneContent>()

                    val episodesPage = episodesInfo.data.map { episode ->
                        episode.toEpisodeItem(seasonNumber)
                    }
                    episodes.addAll(episodesPage)
                }

                episodes
            }.flatten()

            return newTvSeriesLoadResponse(
                episodes = episodes,
                type = TvType.TvSeries,
                url = url,
                name = seriesInfo.metadata.title
            ) {
                plot = seriesInfo.metadata.description
                posterUrl = seriesInfo.metadata.og.image.url
                year = seriesInfo.metadata.publish?.online?.take(4)?.toIntOrNull()
            }
        } else {
            val response = app.get("$apiBaseUrl/api/player/v2/config/$lang/$programId")
                .parsed<PlayerConfigResponse>().data.attributes

            return newMovieLoadResponse(
                name = response.metadata.title,
                url = url,
                type = TvType.Movie,
                dataUrl = url
            ) {
                plot = response.metadata.description
                posterUrl = response.metadata.images.firstOrNull()?.url
                year = response.rights?.begin?.take(4)?.toIntOrNull()
                duration = response.metadata.duration.seconds.div(60).toInt()
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val programId = extractProgramId(data)
        val response = app.get("$apiBaseUrl/api/player/v2/config/$lang/$programId")
            .parsed<PlayerConfigResponse>()

        for (stream in response.data.attributes.streams) {
            callback.invoke(
                newExtractorLink(
                    source = "Arte",
                    name = "Arte",
                    url = stream.url,
                ) {
                    quality = stream.mainQuality.label.takeWhile { it.isDigit() }.toIntOrNull()
                        ?: Qualities.Unknown.value
                }
            )
        }

        return response.data.attributes.streams.isNotEmpty()
    }

    data class PlayerConfigResponse(
        val data: Data,
    )

    data class Data(
        val id: String,
        val type: String,
        val attributes: Attributes,
    )

    data class Attributes(
        val provider: String,
        val metadata: Metadata,
        val live: Boolean,
        val rights: Rights?,
        val streams: StreamList = StreamList(),
    )

    data class Metadata(
        val providerId: String,
        val language: String,
        val title: String,
        val subtitle: String?,
        val description: String,
        val images: List<Image>,
        val link: Link,
        val config: Config,
        val duration: Duration,
        val episodic: Boolean,
    )

    data class Image(
        val url: String,
        val caption: String?,
    )

    data class Link(
        val url: String,
        val deeplink: String,
        val videoOnDemand: Any?,
        val replayUrl: Any?,
    )

    data class Config(
        val url: String,
        val replay: String?,
        val playlist: String,
    )

    data class Duration(
        val seconds: Long,
    )

    data class Rights(
        val begin: String,
        val end: String,
    )

    class StreamList : ArrayList<Stream>()

    data class Stream(
        val url: String,
        val versions: List<Version>,
        val mainQuality: Quality,
        val slot: Long,
        val protocol: String,
        val segments: List<Any?>,
        val externalId: Any?,
    )

    data class Version(
        val code: String,
        val label: String,
        val shortLabel: String,
        val audioLanguage: String,
        val subtitleLanguage: String,
        val closedCaptioning: Boolean,
        val audioDescription: Boolean,
    )

    data class Quality(
        val code: String,
        val label: String,
    )

    data class ZoneInfoResponse(
        val zones: List<Zone> = emptyList(),
    )

    data class Zone(
        val id: String,
        val title: String,
        val slug: String?,
        val content: ZoneContent?,
        val displayOptions: DisplayOptions?,
    )

    data class DisplayOptions(
        val template: String
    )

    data class ZoneContent(
        val data: List<ResultItem>,
        val pagination: Pagination?
    )

    data class Pagination(
        @JsonProperty("page") val currentPage: Int,
        val pages: Int
    )

    data class MediaListResponse(
        val zones: List<Zone>
    )

    data class ResultItem(
        val deeplink: String?,
        val id: String,
        val kind: Kind,
        val mainImage: Image?,
        val shortDescription: String?,
        val subtitle: String?,
        val title: String,
        val trackingPixel: String,
        val type: String,
        val availability: Availability? = null,
        val duration: Long,
        val genre: Genre? = null,
        val programId: String?,
        val teaserText: String?,
        val url: String,
    )

    data class Kind(
        val code: String,
        val isCollection: Boolean,
        val label: String? = null,
    )

    data class Availability(
        val end: String,
        val start: String,
        val type: String,
        val upcomingDate: String,
        val hasVideoStreams: Boolean,
        val remainingDays: Long?,
    )

    data class Genre(
        val id: Long,
        val deeplink: String,
        val label: String,
        val genreName: String,
        val itemLabel: String,
        val url: String,
    )

    data class ProgramInfo(
        val code: String,
        val language: String,
        val support: String,
        val type: String,
        val level: Long,
        val url: String,
        val deeplink: String,
        val slug: String,
        val metadata: ProgramMetadata,
        val zones: List<Zone>,
    )

    data class ProgramMetadata(
        val title: String,
        val description: String,
        val publish: Publish?,
        val og: Og,
    )

    data class Publish(
        val online: String?
    )

    data class Og(
        val image: Image,
    )
}