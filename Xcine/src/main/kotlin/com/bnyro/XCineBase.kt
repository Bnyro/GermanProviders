package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
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
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URL

abstract class XCineBase : MainAPI() {
    override var name = "XCine"
    override var lang = "de"
    override val hasQuickSearch = true
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        // By type (series/movie)
        "movies,trending," to "Film-Trends",
        "tvseries,trending," to "Serien-Trends",
        "movies,updates," to "Neue Filme",
        "tvseries,updates," to "Neue Serien",
        "movies,views," to "Meistgesehene Filme",
        "tvseries,views," to "Meistgesehene Serien",

        ) +
            // By genre
            arrayOf(
                "Action",
                "Animation",
                "Komödie",
                "Dokumentation",
                "Drama",
                "Familie",
                "Horror",
                "Romantik",
                "Sci-Fi",
                "Thriller"
            ).flatMap { genre ->
                mainPageOf(
                    ",trending,$genre" to "$genre-Trends",
                    ",updates,$genre" to "Neu in $genre",
                    ",views,$genre" to "Meistgesehen in $genre",
                )
            }

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null

        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val (type, order, genre) = request.data.split(",", limit = 3)

        val url = "$mainUrl/data/browse/?lang=2&type=$type&order_by=$order&genre=$genre&page=$page"
        val home =
            app.get(url, referer = "$mainUrl/")
                // prevent crashing the whole main page by using parsedSafe
                .parsedSafe<MediaResponse>()?.movies?.mapNotNull { res ->
                    res.toSearchResponse()
                }.orEmpty()
        return newHomePageResponse(request.name, home)
    }

    private fun Media.toSearchResponse(): SearchResponse? {
        return newTvSeriesSearchResponse(
            title ?: originalTitle ?: return null,
            ItemData(id).toJson(),
            TvType.TvSeries,
            false
        ) {
            this.posterUrl = getImageUrl(posterSeasonPath ?: posterPath ?: backdropPath)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("$mainUrl/data/browse/?lang=2&keyword=$query", referer = "$mainUrl/")
            .parsed<MediaResponse>()

        return res.movies?.mapNotNull {
            it.toSearchResponse()
        } ?: throw ErrorLoadingException("Failed to parse search response.")
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = parseJson<ItemData>(url).id

        val requestUrl = "$mainUrl/data/watch/?_id=$id"
        val res = app.get(requestUrl, referer = "$mainUrl/")
            .parsed<MediaDetail>()

        val category = if (res.tv == 1) "tv" else "movie"
        val recommendations = runCatching {
            app.get("$mainUrl/data/related_movies/?lang=2&cat=$category&_id=$id&server=0")
                .parsed<RecommendationsResponse>()
                .mapNotNull { it.toSearchResponse() }
        }.getOrElse { emptyList() }

        val actors = when (res.cast) {
            is String -> res.cast.split(", ")
            is List<*> -> res.cast.filterIsInstance<String>()
            else -> emptyList()
        }

        val posterUrl = getImageUrl(res.backdropPath ?: res.posterPath)
        return if (res.tv == 1) {
            val episodes = res.streams?.groupBy { it.e }?.mapNotNull { eps ->
                val loadData = LoadData(eps.value.mapNotNull { it.stream }).toJson()

                newEpisode(loadData) {
                    this.episode = eps.key
                    this.name = eps.value.firstOrNull()?.eTitle
                }
            }.orEmpty()

            newTvSeriesLoadResponse(
                res.title ?: return null,
                requestUrl,
                TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = posterUrl
                this.year = res.year
                this.plot = res.storyline ?: res.overview
                this.tags = listOf(res.genres ?: "")
                this.contentRating = res.rating
                this.recommendations = recommendations
                addActors(actors)
            }
        } else {
            newMovieLoadResponse(
                res.title ?: return null,
                requestUrl,
                TvType.Movie,
                LoadData(res.streams?.mapNotNull { it.stream }.orEmpty()).toJson()
            ) {
                this.posterUrl = posterUrl
                this.year = res.year
                this.plot = res.storyline ?: res.overview
                this.tags = listOf(res.genres ?: "")
                this.contentRating = res.rating
                this.recommendations = recommendations
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)

        // only take the first 3 links, and 2 random links, per provider
        // otherwise loading times increase a lot because XCine frequently
        // returns more than 400 stream links per video
        val links = loadData.links.groupBy {
            URL(it).host
        }.flatMap { (_, group) ->
            group.take(3) + group.drop(3).shuffled().take(2)
        }

        links.amap { link ->
            loadExtractor(
                link,
                "$mainUrl/",
                subtitleCallback,
                callback
            )
        }

        return true
    }

    data class ItemData(
        val id: String? = null,
    )

    data class LoadData(
        val links: List<String>
    )

    data class Streams(
        @JsonProperty("_id") val id: String? = null,
        @JsonProperty("stream") val stream: String? = null,
        @JsonProperty("e") val e: Int? = null,
        @JsonProperty("e_title") val eTitle: String? = null,
    )

    data class MediaDetail(
        @JsonProperty("_id") val id: String? = null,
        @JsonProperty("tv") val tv: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("rating") val rating: String? = null,
        @JsonProperty("genres") val genres: String? = null,
        @JsonProperty("storyline") val storyline: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
        @JsonProperty("cast") val cast: Any? = null,
    )

    data class Media(
        @JsonProperty("_id") val id: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("poster_season_path") val posterSeasonPath: String? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
    )

    data class MediaResponse(
        @JsonProperty("movies") val movies: ArrayList<Media>? = arrayListOf(),
    )

    class RecommendationsResponse : ArrayList<Media>()
}