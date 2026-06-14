package com.bnyro

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.max

class HuhuTo : MainAPI() {
    override var name: String = "Huhu"
    override var mainUrl: String = "https://huhu.to"
    override var lang: String = "de"
    override val mainPage: List<MainPageData> = mainPageOf(
        "movie" to "Filme",
        "series" to "Serien",
    )
    override val hasMainPage: Boolean = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response =
            app.get("$mainUrl/web-vod/api/list?id=${request.data}.trending", headers = huhuHeaders)
                .parsed<TrendsResponse>()

        return newHomePageResponse(
            request,
            response.data.map { it.toSearchResponse() },
            hasNext = false
        )
    }

    fun MediaItem.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = name,
            url = "$mainUrl/web-vod/item?id=$id",
            type = if (this.id.contains("movie")) TvType.Movie else TvType.TvSeries
        ) {
            posterUrl = poster
            year = releaseDate.take(4).toIntOrNull()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val movies =
            app.get(
                "$mainUrl/web-vod/api/list?id=movie.trending.search=$query",
                headers = huhuHeaders
            ).parsed<TrendsResponse>().data.map { it.toSearchResponse() }

        val series =
            app.get(
                "$mainUrl/web-vod/api/list?id=series.trending.search=$query",
                headers = huhuHeaders
            ).parsed<TrendsResponse>().data.map { it.toSearchResponse() }

        return mergeListsAlternating(movies, series)
    }

    /**
     * Merge two lists by alternating elements.
     *
     * E.g. for the input [1, 5], [8, 2], this returns [1, 8, 5, 2].
     */
    private fun <T> mergeListsAlternating(a: List<T>, b: List<T>): List<T> {
        val maxSize = max(a.size, b.size)
        val result = mutableListOf<T>()
        for (i in 0..maxSize) {
            a.getOrNull(i)?.let { result.add(it) }
            b.getOrNull(i)?.let { result.add(it) }
        }
        return result
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.toHttpUrl().queryParameter("id")!!
        val url = "$mainUrl/web-vod/api/info?id=$id"
        val info = app.get(
            "$mainUrl/web-vod/api/info?id=$id",
            headers = huhuHeaders
        ).parsed<MediaItem>()

        return if (info.seasons == null) {
            newMovieLoadResponse(name = info.name, url = url, type = TvType.Movie, data = id) {
                this.posterUrl = info.poster
                this.year = info.releaseDate.take(4).toIntOrNull()
                this.plot = info.description
                // id has "movie." as prefix
                addTMDbId(id.split(".").last())

                for (video in info.videos) {
                    addTrailer(video.url)
                }
            }
        } else {
            val episodes = info.seasons.map { (seasonNum, episodes) ->
                episodes.map {
                    newEpisode(it.id, initializer = {
                        this.season = seasonNum.toIntOrNull()
                        this.episode = it.episode
                        this.name = it.name
                    }, fix = false)
                }
            }.flatten()

            newTvSeriesLoadResponse(
                name = info.name,
                url = url,
                type = TvType.TvSeries,
                episodes = episodes
            ) {
                this.posterUrl = info.poster
                this.year = info.releaseDate.take(4).toIntOrNull()
                this.plot = info.description
                // id has "series." as prefix
                addTMDbId(id.split(".").last())

                for (video in info.videos) {
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
        val url = "$mainUrl/web-vod/api/links?id=$data"
        val streamSources = app.get(url, headers = huhuHeaders).parsed<StreamSources>()

        for (source in streamSources) {
            loadExtractor("$mainUrl/web-vod/api/get?link=${source.url}", subtitleCallback, callback)
        }

        return streamSources.isNotEmpty()
    }

    class StreamSources : ArrayList<StreamSource>()

    data class StreamSource(
        val url: String,
        val name: String,
        val language: String,
        val icon: String,
    )


    data class TrendsResponse(
        val data: List<MediaItem>,
        val next: String,
    )

    data class MediaItem(
        val id: String,
        val poster: String,
        val backdrop: String?,
        val name: String,
        val originalName: String,
        val description: String?,
        val releaseDate: String,
        val genres: List<String>,
        val videos: List<Trailer> = emptyList(),
        val seasons: Map<String, List<EpisodeInfo>>? = null,
    )

    data class EpisodeInfo(
        val id: String,
        val episode: Int,
        val name: String? = null,
    )

    data class Trailer(
        val kind: String,
        val id: String,
        val type: String,
        val url: String,
        val name: String,
        val languages: List<String>,
        val subtitlesExclusive: Boolean,
        val forbidDownload: Boolean,
        val addonId: String,
        val icon: String,
        val key: String,
    )

    companion object {
        private val huhuHeaders = mapOf(
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin"
        )
    }
}