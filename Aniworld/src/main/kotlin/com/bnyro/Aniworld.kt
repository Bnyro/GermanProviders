package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesSearchResponse
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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

open class Aniworld : MainAPI() {
    override var mainUrl = "https://aniworld.to"
    override var name = "Aniworld"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override val hasMainPage = true
    override var lang = "de"

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageLists = document.select("div.carousel").map { ele ->
            val header = ele.selectFirst("h2")?.text() ?: return@map null

            val items = ele.select("div.coverListItem").mapNotNull {
                it.toSearchResult()
            }
            HomePageList(header, items).takeIf { items.isNotEmpty() }
        }.filterNotNull()

        return newHomePageResponse(homePageLists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val resp = app.post(
            "$mainUrl/ajax/search",
            data = mapOf("keyword" to query),
            referer = "$mainUrl/search",
            headers = mapOf(
                "x-requested-with" to "XMLHttpRequest"
            )
        )
        return resp.parsed<SearchResp>().filter {
            !it.link.contains("episode-") && it.link.contains("/stream")
        }.map {
            newTvSeriesSearchResponse(
                it.title?.replace(Regex("</?em>"), "") ?: "",
                fixUrl(it.link),
                TvType.TvSeries
            )
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.series-title span")?.text() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.seriesCoverBox img")?.attr("data-src"))
        val tags = document.select("div.genres li a").map { it.text() }
        val year = document.selectFirst("span[itemprop=startDate] a")?.text()?.toIntOrNull()
        val description = document.select("p.seri_des").text()
        val actors =
            document.select("li:contains(Schauspieler:) ul li a").map { it.select("span").text() }

        val episodes = document.select("div#stream > ul:first-child li").mapNotNull { ele ->
            val seasonLink = ele.selectFirst("a") ?: return@mapNotNull null
            val seasonNumber = seasonLink.text().toIntOrNull()
            val seasonDocument = app.get(fixUrl(seasonLink.attr("href"))).document

            seasonDocument.select("table.seasonEpisodesList tbody tr").map { eps ->
                newEpisode(
                    fixUrl(eps.selectFirst("a")?.attr("href") ?: return@map null),
                ) {
                    this.episode = eps.selectFirst("meta[itemprop=episodeNumber]")
                        ?.attr("content")?.toIntOrNull()
                    this.name = eps.selectFirst(".seasonEpisodeTitle")?.text()
                    this.season = seasonNumber
                }
            }.filterNotNull()
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
            this.tags = tags

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
        document.select("div.hosterSiteVideo ul li").map {
            Triple(
                it.attr("data-lang-key"),
                it.attr("data-link-target"),
                it.select("h4").text()
            )
        }.amap {
            val redirectUrl = app.get(fixUrl(it.second)).url
            val lang = it.first.getLanguage(document)
            val name = "${it.third} [${lang}]"

            loadExtractor(redirectUrl, data, subtitleCallback) { link ->
                val linkWithFixedName = runBlocking {
                    newExtractorLink(
                        source = it.third,
                        name = name,
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

    private fun Element.toSearchResult(): TvSeriesSearchResponse? {
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val title = this.selectFirst("h3")?.text() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun String.getLanguage(document: Document): String? {
        return document.selectFirst("div.changeLanguageBox img[data-lang-key=$this]")
            ?.attr("title")?.removePrefix("mit")?.trim()
    }

    private class SearchResp: ArrayList<SearchItem>()

    private data class SearchItem(
        @JsonProperty("link") val link: String,
        @JsonProperty("title") val title: String? = null,
    )
}