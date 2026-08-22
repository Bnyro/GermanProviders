package com.bnyro

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class KellerKino : MainAPI() {
    override var name: String = "Kellerkino"
    override var mainUrl: String = "https://www.kellerkino.com"
    override val hasMainPage: Boolean = true
    override val supportedTypes: Set<TvType> = setOf(TvType.Movie)
    override var lang: String = "de"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document

        val lists = doc.select("section.top-strip, section.movie-list-section").map { section ->
            val movies = section.select("a.top-cover, a.movie-thumb").map { movie ->
                newMovieSearchResponse(
                    name = movie.select("img").attr("alt"),
                    url = movie.attr("href"),
                    type = TvType.Movie
                ) {
                    posterUrl = movie.select("img")
                        .let { it.attr("src").ifEmpty { it.attr("data-dmt-theme-src") } }
                }
            }

            HomePageList(name = section.selectFirst("h1, h2")?.ownText().orEmpty(), list = movies)
        }

        return newHomePageResponse(lists, hasNext = false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val doc = app.get("$mainUrl/seite/$page/?s=$query").document

        val movies = doc.select("article.movie-card").map {
            newMovieSearchResponse(
                name = it.select("h2").text(),
                url = it.select("h2 a").attr("href")
            ) {
                this.posterUrl = it.select(".movie-thumb img").attr("src")
            }
        }

        return newSearchResponseList(movies)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val detailsContainer = doc.select("article.movie-detail")
        val details = doc.select(".info-list > div").associate {
            it.select("dt").text().trimEnd(':') to it.select("dd").text()
        }

        return newMovieLoadResponse(
            name = detailsContainer.select("h1").text(),
            url = url,
            data = doc.select("iframe").map { it.attr("src") },
            type = TvType.Movie
        ) {
            plot = doc.select("section.movie-description").text()
            posterUrl = doc.select(".poster-box img").attr("src")
            year = details["Jahr"]?.toIntOrNull()
            duration = details["Laufzeit"]?.takeWhile { it.isDigit() }?.toIntOrNull()
            tags = details["Genre"]?.split(",")
            addScore(details["IMDb-Bewertung"])
            addActors(details["Schauspieler"]?.split(", "))
            addImdbId(detailsContainer.select(".nfo-movie-imdb-id").text().removePrefix(": "))
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
            loadExtractor(link, subtitleCallback, callback)
        }

        return links.isNotEmpty()
    }

    private class Links : ArrayList<String>()
}