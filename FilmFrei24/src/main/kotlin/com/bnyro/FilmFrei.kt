package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.HttpUrl.Companion.toHttpUrl

class FilmFrei : MainAPI() {
    override var name: String = "FilmFrei24"
    override var mainUrl: String = "https://filmfrei24.com"
    override val hasMainPage: Boolean = true
    override val supportedTypes: Set<TvType> = setOf(TvType.Movie)
    override var lang: String = "de"

    private suspend fun loadMovies(type: String = "ALL_FILMS"): List<Movie> {
        val doc = app.get(mainUrl).text

        val rawJson =
            doc.substringAfter("const $type").substringAfter("=").substringBefore(";").trim()
        return AppUtils.parseJson<MovieList>(rawJson)
    }

    fun Movie.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = title,
            url = "$mainUrl/player/?s=$title",
            type = TvType.Movie
        ) {
            posterUrl = fixUrl(thumbnail)
            year = this@toSearchResponse.year?.toString()?.toIntOrNull()
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val movies = loadMovies("HERO_FILMS")

        return newHomePageResponse(
            HomePageList("Beliebt", movies.map { it.toSearchResponse() }),
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val allMovies = loadMovies()

        return allMovies
            .filter { it.title.contains(query, ignoreCase = true) }
            .sortedByDescending { it.tmdbRating }
            .map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val title = url.toHttpUrl().queryParameter("s")

        val movie = loadMovies().first { it.title == title }
        return newMovieLoadResponse(name = movie.title, url = url, dataUrl = movie.video, type = TvType.Movie) {
            posterUrl = fixUrl(movie.banner)
            year = movie.year?.toString()?.toIntOrNull()
            duration = movie.duration.takeWhile { it.isDigit() }.toIntOrNull()
            tags = movie.genres
            addScore(movie.tmdbRating?.toString())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(newExtractorLink(source = name, name = name, url = data))

        return true
    }

    private class MovieList : ArrayList<Movie>()

    data class Movie(
        val id: Long,
        val title: String,
        @JsonProperty("upload_date")
        val uploadDate: String,
        val thumbnail: String,
        val banner: String,
        val logo: String,
        val video: String,
        val language: String,
        val duration: String,
        val year: Any?,
        val genres: List<String>,
        @JsonProperty("tmdb_rating")
        val tmdbRating: Double?,
        @JsonProperty("tmdb_votes")
        val tmdbVotes: Long,
    )
}