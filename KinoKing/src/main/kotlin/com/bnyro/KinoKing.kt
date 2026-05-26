package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

open class KinoKing : MainAPI() {
    override var name: String = "KinoKing"
    override var mainUrl: String = "https://kinoking.cc"
    open val tmdbImageUrl = "https://image.tmdb.org/t/p/w500"
    override val supportedTypes: Set<TvType> = setOf(TvType.TvSeries, TvType.Movie)
    override var lang = "de"
    override val hasMainPage: Boolean = true

    private val DURATION_REGEX = Regex("""^(?:(\d)H )?(\d{1,2})M$""")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("$mainUrl/index.php").document

        val seriesResults = response.select("#seriesRef > div")
            .map { it.toSearchResponse() }

        val movieResults = response.select("#moviesRef > div")
            .map { it.toSearchResponse() }

        return newHomePageResponse(
            listOf(
                HomePageList("Filme", movieResults),
                HomePageList("Serien", seriesResults)
            ), hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/index.php?search=$query").document

        return response.select("main div#ajax-grid-container > div")
            .map { it.toSearchResponse() }
    }

    private fun Element.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = attr("data-title"),
            url = "$mainUrl/${attr("data-type")}.php?id=${attr("data-id")}"
        ) {
            posterUrl = attr("data-img")
            type = if (attr("data-type") == "series") TvType.TvSeries else TvType.Movie

            select("span")
                .filter { it.text().trim().toIntOrNull() == null }
                // ratings are always floats, ints are the position or the year, so we don't want ints
                .firstNotNullOfOrNull { it.text().trim().toFloatOrNull() }?.let {
                    score = Score.from(it, 10)
                }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).document
        val tmdbId = url.toHttpUrl().queryParameter("id")

        if (url.contains("movie.php")) {
            val doc = response.select("main")

            val title = doc.selectFirst("h1")?.text() ?: return null
            val plot = doc.select("> div:last-child > p").text()
            val poster = doc.selectFirst("> div:nth-child(2) img")?.attr("src")
            val year = doc.select("p")
                .mapNotNull { it.text().trim().toIntOrNull() }
                .firstOrNull { it > 1900 }
            val duration =
                doc.select("div > p").firstNotNullOfOrNull { DURATION_REGEX.find(it.text()) }?.let {
                    runCatching {
                        val hours = if (it.groupValues.size == 3) it.groupValues[1].toInt() else 0
                        val minutes = it.groupValues.last().toInt()
                        hours * 60 + minutes
                    }.getOrNull()
                }
            val links = doc.select("a")
                .map { it.attr("href") }
                .filter { it.startsWith("?id=") }
                .map { queryArgs -> "${url.substringBefore("?")}$queryArgs" }

            return newMovieLoadResponse(
                title,
                url,
                type = TvType.Movie,
                data = links
            ) {
                this.plot = plot
                this.posterUrl = poster
                this.year = year
                this.duration = duration
                addTMDbId(tmdbId)
            }
        } else {
            val metaInfo = response.select("section.bento-card")
            val title = metaInfo.select("h2").text()
            val plot = metaInfo.select("p").text()
            val poster = metaInfo.selectFirst("img")?.attr("src")

            val year = metaInfo.select("span")
                .mapNotNull { it.text().trim().toIntOrNull() }
                .firstOrNull { it > 1900 }

            val script = response.select("script").last()!!.data()
            val allEpisodesData = script.substringAfter("const allEpisodesData = ")
                .substringBefore("\n")
                .substringBeforeLast(";")
                .let { AppUtils.parseJson<KinoEpisodeList>(it) }

            fun String.fixUrl() = this.replace("\\/", "/")

            val episodes = allEpisodesData.map { episode ->
                val links = episode.videoLinks.split(",").map { it.fixUrl() }
                newEpisode(links) {
                    name = episode.name
                    description = episode.overview
                    this.episode = episode.episodeNumber.toInt()
                    this.season = episode.seasonNumber.toInt()
                    this.posterUrl = "$tmdbImageUrl${episode.stillPath.fixUrl()}"
                    this.runTime = episode.runtime.toInt()
                    this.score = Score.from(episode.voteAverage.toFloatOrNull(), 10)
                }
            }

            return newTvSeriesLoadResponse(
                name = title,
                url = url,
                type = TvType.TvSeries,
                episodes = episodes
            ) {
                this.plot = plot
                this.posterUrl = poster
                this.year = year
                addTMDbId(tmdbId)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = AppUtils.parseJson<Links>(data)

        links.amap { link ->
            if (link.startsWith(mainUrl)) { // movies
                val script = app.get(link).document.select("script").last() ?: return@amap

                val iframeCode =
                    script.data().substringAfter("target.innerHTML = `").substringBefore("`")
                val iframe = Jsoup.parse(iframeCode)

                val streamLink = iframe.select("iframe").attr("src")
                loadExtractor(streamLink, subtitleCallback, callback)
            } else { // series
                loadExtractor(link, subtitleCallback, callback)
            }
        }

        return links.isNotEmpty()
    }

    typealias Links = List<String>


    typealias KinoEpisodeList = List<KinoEpisode>
    data class KinoEpisode(
        val id: Long,
        @JsonProperty("series_id")
        val seriesId: Long,
        @JsonProperty("tmdb_series_id")
        val tmdbSeriesId: Long,
        @JsonProperty("season_number")
        val seasonNumber: Long,
        @JsonProperty("episode_number")
        val episodeNumber: Long,
        val name: String,
        val overview: String,
        @JsonProperty("air_date")
        val airDate: String,
        @JsonProperty("still_path")
        val stillPath: String,
        @JsonProperty("vote_average")
        val voteAverage: String,
        val runtime: Long,
        @JsonProperty("custom_video_url")
        val customVideoUrl: Any?,
        @JsonProperty("video_source")
        val videoSource: String,
        val status: String,
        val watched: Long,
        @JsonProperty("created_at")
        val createdAt: String,
        @JsonProperty("updated_at")
        val updatedAt: String,
        @JsonProperty("video_links")
        val videoLinks: String,
        @JsonProperty("link_count")
        val linkCount: Long,
    )
}