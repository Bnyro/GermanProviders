package com.bnyro

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

open class HuhuTo : MainAPI() {
    override var name: String = "Huhu"

    override var mainUrl: String = "https://huhu.to"

    // actual URL is huhu.to, but all API requests go through mhub
    open val apiUrl: String = "https://mhub.mx"

    override var lang: String = "de"
    override val mainPage: List<MainPageData> = mainPageOf(
        "$mainUrl/live/catalog/live/channels" to "Live",
        "$apiUrl/tmdb/catalog/video/movie" to "Filme",
        "$apiUrl/tmdb/catalog/series/series" to "Serien",
    )
    override val hasMainPage: Boolean = true
    override val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries, TvType.Live)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response =
            app.get("${request.data}.json?sort=trending&language=de")
                .parsed<ItemsResponse>()

        return newHomePageResponse(
            request,
            response.items.map { it.toSearchResponse() },
            hasNext = false
        )
    }

    fun MediaItem.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = name,
            url = if (type == "live") "$mainUrl/live/play/$id" else "$mainUrl/#/item/tmdb/${type}/$id",
            type = when (this.type) {
                "series" -> TvType.TvSeries
                "live" -> TvType.Live
                else -> TvType.Movie
            }
        ) {
            posterUrl = images?.poster
            year = releaseDate?.take(4)?.toIntOrNull()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val movies =
            app.get(
                "$apiUrl/tmdb/catalog/series/series.json?search=$query&language=de",
            ).parsed<ItemsResponse>().items.map { it.toSearchResponse() }

        val series =
            app.get(
                "$apiUrl/tmdb/catalog/video/movie.json?search=$query&language=de",
            ).parsed<ItemsResponse>().items.map { it.toSearchResponse() }

        val channels =
            app.get(
                "$mainUrl/live/catalog/live/channels.json?search=$query&language=de",
            ).parsed<ItemsResponse>().items.map { it.toSearchResponse() }

        return mergeListsAlternating(movies, series, channels)
    }

    /**
     * Merge two lists by alternating elements.
     *
     * E.g. for the input [1, 5], [8, 2], this returns [1, 8, 5, 2].
     */
    private fun <T> mergeListsAlternating(a: List<T>, b: List<T>, c: List<T>): List<T> {
        val maxSize = listOf(a.size, b.size, c.size).max()
        val result = mutableListOf<T>()
        for (i in 0..maxSize) {
            a.getOrNull(i)?.let { result.add(it) }
            b.getOrNull(i)?.let { result.add(it) }
            c.getOrNull(i)?.let { result.add(it) }
        }
        return result
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")

        return if (url.contains("series")) {
            val url = "$apiUrl/tmdb/item/series/tmdb:$id.json?ids=imdb:$id&language=de"
            val info = app.get(url).parsed<MediaDetailsResponse>()

            val seasons = info.catalogs.orEmpty().filter { it.id.startsWith("season_") }
            val episodes = seasons.flatMap { season ->
                val details = app.get("$apiUrl/tmdb/catalog/video/${season.id}.json")
                    .parsed<ItemsResponse>()

                details.items.map {
                    newEpisode(it.id, initializer = {
                        this.season = it.season
                        this.episode = it.episode
                        this.name = it.name
                        this.runTime = it.runtime?.div(60)
                        this.posterUrl = it.images?.thumbnail
                    }, fix = false)
                }
            }

            newTvSeriesLoadResponse(
                name = info.name,
                url = url,
                type = TvType.TvSeries,
                episodes = episodes
            ) {
                this.posterUrl = info.images?.poster
                this.year = info.releaseDate?.take(4)?.toIntOrNull()
                this.plot = info.description
                this.tags = info.genres
                addActors(info.cast.map { Actor(it.name, it.image) })
                addTMDbId(id)

                for (video in info.videos.orEmpty()) {
                    addTrailer(video.url)
                }
            }
        } else if (url.contains("live")) {
            val info = app.get("$mainUrl/live/resolve?url=$url")
                .parsed<ResolveResponse>()

            newLiveStreamLoadResponse(url = url, name = info.name, dataUrl = info.url)
        } else {
            val url = "$apiUrl/tmdb/item/video/tmdb:$id.json?ids=imdb:$id&language=de"
            val info = app.get(url).parsed<MediaDetailsResponse>()

            newMovieLoadResponse(
                name = info.name,
                url = url,
                type = TvType.Movie,
                data = "tmdb:$id"
            ) {
                this.posterUrl = info.images?.poster
                this.year = info.releaseDate?.take(4)?.toIntOrNull()
                this.plot = info.description
                this.tags = info.genres
                addActors(info.cast.map { Actor(it.name, it.image) })
                addTMDbId(id)

                for (video in info.videos.orEmpty()) {
                    addTrailer(video.url)
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // live
        if (data.startsWith("http")) {
            callback.invoke(newExtractorLink(source = name, name = name, url = data))
            return true
        }

        val url = "$mainUrl/vod/source/video/$data.json"
        val streamSources = app.get(url).parsed<StreamSourcesResponse>()

        for (source in streamSources.sources) {
            loadExtractor(source.url, subtitleCallback, callback)
        }

        return streamSources.sources.isNotEmpty()
    }

    data class ItemsResponse(
        val items: List<MediaItem>,
        val nextCursor: String?,
    )

    data class MediaItem(
        val id: String,
        val type: String,
        val name: String,
        val description: String?,
        val releaseDate: String?,
        val ratings: Ratings?,
        val images: Images?,
        val genres: List<String>?,
        // series details only
        val season: Int?,
        val episode: Int?,
        val runtime: Int?
    )

    data class Ratings(
        val tmdb: Double,
    )

    data class Images(
        val poster: String?,
        val thumbnail: String?,
        val backdrops: List<String>?,
    )

    data class MediaDetailsResponse(
        val id: String,
        val type: String,
        val name: String,
        val description: String?,
        val releaseDate: String?,
        val ratings: Ratings?,
        val images: Images?,
        val genres: List<String>?,
        val originalName: String,
        val countries: List<String>?,
        val homepage: String?,
        val cast: List<Cast>,
        val videos: List<Video>?,
        val catalogs: List<Catalog>?,
    )

    data class Cast(
        val name: String,
        val role: String,
        val image: String?,
        val directory: Directory,
    )

    data class Directory(
        val type: String,
        val catalogId: String,
    )

    data class Video(
        val url: String,
        val name: String,
        val languages: List<String>,
    )

    data class Catalog(
        val id: String,
        val name: String,
        val type: String,
        val season: Long?,
        val count: Long?,
        val releaseDate: String?,
        val images: Images?,
        val ratings: Ratings?,
        val items: List<MediaItem>?,
        val nextCursor: String?,
    )

    data class StreamSourcesResponse(
        val sources: List<StreamSource>,
    )

    data class StreamSource(
        val url: String,
        val name: String,
        val quality: String?,
        val languages: List<String>,
    )

    data class ResolveResponse(
        val url: String,
        val live: Boolean,
        val format: String,
        val name: String,
    )
}