
package com.bnyro

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Element

open class Serienstream : MainAPI() {
    override var mainUrl = "https://s.to"
    override var name = "Serienstream"
    override val supportedTypes = setOf(TvType.TvSeries)

    override val hasMainPage = true
    override var lang = "de"

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get("$mainUrl/beliebte-serien").document

        val homePageLists = document.select(".popular-page > div").map { elem ->
            val header = elem.selectFirst("div > h2")?.text() ?: return@map null

            val items = elem.select("a.show-card").mapNotNull {
                it.toSearchResult()
            }
            HomePageList(header, items).takeIf { items.isNotEmpty() }
        }.filterNotNull()

        return newHomePageResponse(homePageLists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val resp = app.get(
            "$mainUrl/suche",
            params = mapOf("term" to query, "tab" to "shows"),
            referer = "$mainUrl/suche",
        ).document

        return resp.select(".results-group .card").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val metaContainer = document.selectFirst(".show-header-wrapper .container-fluid > div")
            ?: throw RuntimeException("Metadata container HTML element not found")

        val title = metaContainer.selectFirst("h1")?.text()
            ?: throw RuntimeException("Failed to find series title")
        val poster = fixUrlNull(metaContainer.selectFirst("img")?.attr("data-src"))
        val year = metaContainer.selectFirst("h1 + p > a")?.text()?.toIntOrNull()
        val description = metaContainer.select(".description-text").text()
        val actors =
            metaContainer.select("li.series-group:contains(Besetzung:) a").map { it.text() }
        val genres =
            metaContainer.select("li.series-group:contains(Genre:) a").map { it.text() }
        val trailerUrl = metaContainer.selectFirst("button[data-trailer-url]")?.attr("data-trailer-url")

        val episodes = document.select("#season-nav ul > li a").amap {
            val seasonNumber = it.text().trim().toIntOrNull()
            val seasonDocument = app.get(fixUrl(it.attr("href"))).document

            seasonDocument.select(".episode-section .episode-row").map { eps ->
                val episodeLink = eps.attr("onclick")
                    .substringAfter("=")
                    .trim('\'')

                newEpisode(episodeLink) {
                    this.episode = eps.selectFirst(".episode-number-cell")?.text()?.toIntOrNull()
                    this.name = eps.select(".episode-title-cell > *")
                        .joinToString(" - ") { el -> el.text() }
                    this.season = seasonNumber
                }
            }
        }.flatten()

        return newTvSeriesLoadResponse(
            title,
            url,
            TvType.TvSeries,
            episodes
        ) {
            this.name = title
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = genres
            addTrailer(trailerUrl)

            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select(".link-wrapper > button").amap {
            val streamUrl = fixUrl(it.attr("data-play-url"))
            val source = it.attr("data-provider-name")
            val language = it.attr("data-language-label")

            val redirectedStreamUrl = app.get(fixUrl(streamUrl)).url
            loadExtractor(redirectedStreamUrl, data, subtitleCallback) { link ->
                val linkWithFixedName = runBlocking {
                    newExtractorLink(
                        source = source,
                        name =  "$source [$language]",
                        url = link.url
                    ) {
                        referer = link.referer
                        quality = link.quality
                        type = link.type
                        headers = link.headers
                        extractorData = link.extractorData
                    }
                }
                callback.invoke(linkWithFixedName)
            }
        }

        return true
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val title = this.selectFirst("img")?.attr("alt") ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        })

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }
}