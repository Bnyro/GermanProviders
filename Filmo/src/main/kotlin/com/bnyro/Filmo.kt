package com.bnyro

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class Filmo : MainAPI() {
    override var name: String = "Filmo"
    override var mainUrl: String = "https://filmo.to"
    override val hasMainPage: Boolean = true
    override val supportedTypes: Set<TvType> = setOf(TvType.Movie)
    override var lang: String = "de"

    private val headers =
        mapOf("User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:152.0) Gecko/20100101 Firefox/152.0")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl/popular", headers = headers).document

        val lists = doc.select("section.popular-spotlight, div.video-row").map { section ->
            val movies = section.select(".popular-spotlight-card__link, a.video-card").map { movie ->
                newMovieSearchResponse(
                    name = movie.select("[class*=title]").text(),
                    url = movie.attr("href"),
                    type = TvType.Movie
                ) {
                    posterUrl = movie.select(".ft-packshot img").attr("src")
                }
            }

            HomePageList(name = section.selectFirst("h3")?.ownText().orEmpty(), list = movies)
        }

        return newHomePageResponse(lists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/search?q=$query", headers = headers).document

        return doc.select("section.search-top-results article > a, a.movie-poster-grid-card").map {
            newMovieSearchResponse(
                name = it.select("[class*=__title]").text(),
                url = it.attr("href")
            ) {
                this.posterUrl = it.select("img").attr("src")
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = headers).document
        val details = doc.select("div.details-group dl").associate {
            it.select("dt").text().trim() to it.select("dd").text().trim()
        }

        return newMovieLoadResponse(
            name = doc.select(".primary-container h1").text(),
            url = url,
            dataUrl = url,
            type = TvType.Movie
        ) {
            plot = doc.select("p.movie-detail-synopsis").text()
            posterUrl = doc.select("img.ft-packshot-meta").attr("src")
            year = details["Erscheinungsdatum"]?.take(4)?.toIntOrNull()
            duration = details["Laufzeit"]?.takeWhile { it.isDigit() }?.toIntOrNull()
            addScore(details["Bewertung"]?.substringBefore(" "))
            addActors(details["Darsteller"]?.split(","))
            tags = details["Genres"]?.split(",")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val moviePage = app.get(data, headers = headers)
        val cookies = moviePage.cookies
        val linkPayloads = moviePage.document.select(".provider-chip").map { it.attr("data-p") }

        linkPayloads.amap { payload ->
            val slug = app.post(
                "$mainUrl/n",
                json = mapOf("p" to payload),
                cookies = cookies,
                headers = headers + mapOf(
                    "Content-Type" to "application/json",
                    "X-XSRF-TOKEN" to cookies["XSRF-TOKEN"]!!.replace("%3D", "=")
                )
            )
                .parsed<LinkResponse>().x

            val embedUrl = app.get(
                "$mainUrl/n/$slug",
                cookies = cookies,
                headers = headers,
                allowRedirects = true
            ).url
            loadExtractor(embedUrl, subtitleCallback, callback)
        }

        return linkPayloads.isNotEmpty()
    }

    private data class LinkResponse(val x: String)
}