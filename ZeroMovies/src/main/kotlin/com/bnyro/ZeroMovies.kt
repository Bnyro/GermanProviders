package com.bnyro

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

open class ZeroMovies : MainAPI() {
    override var name: String = "ZeroMovies"
    override var mainUrl: String = "https://zeromovies.space"
    private val apiUrl get() = "$mainUrl/hcgi/platform/api"
    override val supportedTypes: Set<TvType> = setOf(TvType.TvSeries, TvType.Movie)
    override var lang = "de"
    override val hasMainPage: Boolean = true

    override val mainPage: List<MainPageData> = mainPageOf(
        "movies" to "Filme",
        "series" to "Serien"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val results =
            app.get("$apiUrl/collections/${request.data}/records?page=1&perPage=24&sort=-created")
                .parsed<MediaList>().items.map { it.toSearchResponse() }

        return newHomePageResponse(
            HomePageList(request.name, results), hasNext = false
        )
    }

    private fun MediaItem.toSearchResponse(): SearchResponse {
        val slug = if (collectionName == "movies") "movie" else "series"
        return newMovieSearchResponse(
            name = title,
            url = "$mainUrl/$slug/$id"
        ) {
            year = this@toSearchResponse.year
            posterUrl = getImageUrl(collectionId, this@toSearchResponse.id, poster)
            type = if (collectionName == "movies") TvType.Movie else TvType.TvSeries
        }
    }

    private fun getImageUrl(collectionId: String, itemId: String, fileName: String): String {
        return "$apiUrl/files/$collectionId/$itemId/$fileName"
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$apiUrl/collections/movies/records?page=1&perPage=24&sort=-created&filter=(title ~ \"$query\" || description ~ \"$query\")")
            .parsed<MediaList>().items.map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val isMovie = url.substringBeforeLast("/").endsWith("movie")

        if (isMovie) {
            val metadata =
                app.get("$apiUrl/collections/movies/records/$id").parsed<MediaItem>()

            return newMovieLoadResponse(
                name = metadata.title,
                url = url,
                data = url,
                type = TvType.Movie,
            ) {
                year = metadata.year
                posterUrl = getImageUrl(metadata.collectionId, metadata.id, metadata.poster)
                plot = metadata.description
            }
        } else {
            val metadata =
                app.get("$apiUrl/collections/series/records/$id").parsed<MediaItem>()

            val seasons = app.get("$apiUrl/collections/seasons/records?page=1&perPage=1000&skipTotal=1&filter=seriesId=\"$id\"&sort=seasonNumber")
                .parsed<SeasonsList>().items

            val episodes = app.get("$apiUrl/collections/episodes/records?page=1&perPage=1000&skipTotal=1&filter=seriesId=\"$id\"&sort=seasonId,episodeNumber")
                .parsed<EpisodeList>().items

            return newTvSeriesLoadResponse(
                name = metadata.title,
                url = url,
                type = TvType.TvSeries,
                episodes = episodes.map {
                    newEpisode(it.id, {
                        episode = it.episodeNumber.toInt()
                        season = seasons.first { s -> s.id == it.seasonId }.seasonNumber.toInt()
                        name = it.title
                    }, fix = false)
                }
            ) {
                posterUrl = getImageUrl(metadata.collectionId, metadata.id, metadata.poster)
                plot = metadata.description
                year = metadata.year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val isMovie = data.substringBeforeLast("/").endsWith("movie")
        val id = data.substringAfterLast("/")
        val filterType = if (isMovie) "movieId" else "episodeId"
        val slug = if (isMovie) "streaming_links" else "series_streaming_links"

        val streamingLinks =
            app.get("$apiUrl/collections/$slug/records?page=1&perPage=50&filter=$filterType=\"$id\"")
                .parsed<StreamLinkResponse>()

        streamingLinks.items.amap { link ->
            loadExtractor(link.url, subtitleCallback, callback)
        }

        return streamingLinks.items.isNotEmpty()
    }

    private data class MediaList(
        val items: List<MediaItem>,
        val page: Long,
        val perPage: Long,
        val totalItems: Long,
        val totalPages: Long,
    )

    private data class MediaItem(
        val collectionId: String,
        val collectionName: String,
        val created: String,
        val description: String,
        val id: String,
        val poster: String,
        val title: String,
        val updated: String,
        val videoUrl: String?,
        val viewCount: Long,
        val year: Int?
    )

    private data class StreamLinkResponse(
        val items: List<StreamLink>,
        val page: Long,
        val perPage: Long,
        val totalItems: Long,
        val totalPages: Long,
    )

    private data class StreamLink(
        val collectionId: String,
        val collectionName: String,
        val id: String,
        val provider: String,
        val url: String,
    )

    private data class SeasonsList(
        val items: List<Season>,
        val page: Long,
        val perPage: Long,
        val totalItems: Long,
        val totalPages: Long,
    )

    private data class Season(
        val collectionId: String,
        val collectionName: String,
        val created: String,
        val id: String,
        val numberOfEpisodes: Long,
        val seasonNumber: Long,
        val seriesId: String,
        val updated: String,
    )

    private data class EpisodeList(
        val items: List<Episode>,
        val page: Long,
        val perPage: Long,
        val totalItems: Long,
        val totalPages: Long,
    )

    data class Episode(
        val collectionId: String,
        val collectionName: String,
        val created: String,
        val episodeNumber: Long,
        val id: String,
        val seasonId: String,
        val seriesId: String,
        val title: String,
        val updated: String,
        val videoUrl: String,
    )


}
