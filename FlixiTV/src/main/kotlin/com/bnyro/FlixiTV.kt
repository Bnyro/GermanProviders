package com.bnyro

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amapIndexed
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.util.UUID

class FlixiTV : MainAPI() {
    override var mainUrl: String = "https://flixitv-stream.eu/"
    override var name: String = "FlixiTV"
    override var lang: String = "de"
    override val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage: Boolean = true

    // rate-limits are per session id, so we just don't re-use session IDs
    private fun sessionIdCookies() = mapOf(
        "PHPSESSID" to UUID.randomUUID().toString()
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val doc = app.get(mainUrl).document

        val lists = doc.select("section").map { section ->
            HomePageList(name = section.select("h2").text(), extractItems(section))
        }

        return newHomePageResponse(lists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.post(
            "$mainUrl/search/",
            cookies = sessionIdCookies(),
            data = mapOf("srh" to query),
            allowRedirects = true
        ).document

        return extractItems(doc)
    }

    private fun extractItems(element: Element): List<SearchResponse> {
        return element.select("a.card-link").map { result ->
            val link = result.attr("href")
            val type = if (link.startsWith("/watch")) TvType.Movie else TvType.TvSeries

            newTvSeriesSearchResponse(
                name = result.select("h5").text(),
                url = fixUrl(link),
                type = type
            ) {
                // images are in AVIF format, not currently supported by Cloudstream
                posterUrl = fixUrl(result.select("img").attr("src"))
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        if (url.contains("/watch")) {
            return newMovieLoadResponse(
                name = doc.select(".title-desc h4").text(),
                url = url,
                dataUrl = url,
                type = TvType.Movie
            )
        } else {
            val seasons = doc.select("a.card-link").map { it.attr("href") }
            val episodes = seasons.amapIndexed { seasonIndex, seasonLink ->
                val seasonDoc = app.get("$mainUrl/serie/${seasonLink}").document

                seasonDoc.select("tbody tr").map {
                    newEpisode(url = it.select("td:first-child a").attr("href"), initializer = {
                        season = seasonIndex + 1
                        episode = it.select("td:nth-child(2)").text().trim().toIntOrNull()
                        name = it.select("td:last-child").text()
                    })
                }
            }.flatten()

            return newTvSeriesLoadResponse(
                name = doc.select("h2").text(),
                url = url,
                type = TvType.TvSeries,
                episodes = episodes
            ) {
                plot = doc.select("h2 + div p").text()
                posterUrl = doc.select("img.img-fluid").attr("src")
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val iframeSrc = doc.select("iframe").attr("src")

        loadExtractor(iframeSrc, subtitleCallback, callback)

        return true
    }
}