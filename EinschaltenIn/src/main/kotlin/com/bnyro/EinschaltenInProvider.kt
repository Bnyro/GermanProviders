package com.bnyro

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

open class EinschaltenInProvider : MainAPI() {
    override var name = "EinschaltenIn"
    override var lang = "de"
    override val hasQuickSearch = true
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)
    override var mainUrl = "https://einschalten.in"
    override val mainPage: List<MainPageData> = mainPageOf(
        "new" to "Neue Filme",
        "added" to "Zuletzt hinzugefügte Filme"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val response = app.get(
            "$mainUrl/api/movies?order=${request.data}",
        ).parsed<Response>()

        val homePage = response.data.map { it.toSearchResponse() }
        return newHomePageResponse(request.name, homePage, hasNext = response.pagination.hasMore)
    }

    private fun getImageUrl(fileWithLeadingSlash: String): String {
        val file = fileWithLeadingSlash.trimStart('/')
        return "$mainUrl/api/image/poster/$file"
    }

    private fun MovieItem.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = title,
            url = "$mainUrl/movies/${id}",
            type = TvType.Movie
        ) {
            this.posterUrl = getImageUrl(posterPath)
            this.year = releaseDate.take(4).toIntOrNull()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "$mainUrl/api/search",
            json = mapOf(
                "pageNumber" to "0",
                "pageSize" to "32",
                "query" to query
            ),
            headers = mapOf(
                "Content-Type" to "application/json"
            )
        ).parsed<Response>()

        return response.data.map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val movieId = url.substringAfterLast("/")
        val movie = app.get("$mainUrl/api/movies/$movieId")
            .parsed<MovieItem>()

        return newMovieLoadResponse(
            name = movie.title,
            url = url,
            type = TvType.Movie,
            data = url
        ) {
            this.posterUrl = getImageUrl(movie.posterPath)
            this.plot = movie.overview
            this.duration = movie.runtime
            this.year = movie.releaseDate.take(4).toIntOrNull()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = data.split("/").last()
        val source = app.get("https://einschalten.in/api/movies/$id/watch")
            .parsed<StreamSource>()

        loadExtractor(source.streamUrl, referer = "$mainUrl/", subtitleCallback, callback)

        return true
    }

    data class MovieItem(
        val id: Int,
        val title: String,
        val releaseDate: String,
        val posterPath: String,
        val voteAverage: Float,

        // only included in load response
        val overview: String? = null,
        val runtime: Int? = null,
    )

    data class Response(
        val data: List<MovieItem>,
        val pagination: Pagination,
    )

    data class Pagination(
        val hasMore: Boolean,
        val currentPage: Int
    )

    data class StreamSource(
        val releaseName: String,
        val streamUrl: String,
    )
}