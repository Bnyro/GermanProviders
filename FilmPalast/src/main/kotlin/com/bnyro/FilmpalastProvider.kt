package com.bnyro

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class FilmpalastProvider : MainAPI() {
    override var mainUrl = "https://filmpalast.to"
    override var name = "Filmpalast"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override var lang = "de"
    override val hasMainPage = true

    override val mainPage: List<MainPageData> = mainPageOf(
        "movies/top" to "Filme",
        "serien/view" to "Serien"
    )

    private fun Element.toSearchResponse(): SearchResponse {
        val title = select("cite a.rb").text()
        val url = select("a.rb").attr("href")
        val posterPath = select("img.cover-opacity").attr("src")
        return newMovieSearchResponse(title, type = TvType.Movie, url = url).apply {
            this.posterUrl = "$mainUrl$posterPath"
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("$mainUrl/${request.data}/page/${page}/").document
        val results = response.select("#content .liste.rb").mapNotNull {
            it.toSearchResponse()
        }

        return newHomePageResponse(request, results, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/title/$query").document
        return document.select("#content .glowliste").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document.select("#content")

        val title = document.select("h2.rb.bgDark").text()
        val imagePath = document.select(".detail.rb img.cover2").attr("src")
        val description = document.select("span[itemprop=description]").text()
        val details = document.select("detail-content-list li")
        val year = details.first()?.html()?.split("<br>")?.getOrNull(1)?.filter { it.isDigit() }
            ?.toIntOrNull()
        val duration =
            details.select("em").first()?.ownText()?.filter { it.isDigit() }?.toIntOrNull()

        val links = document.select(".currentStreamLinks a.iconPlay").mapNotNull {
            it.attr("href").ifEmpty { it.attr("data-player-url") }
        }
        return newMovieLoadResponse(title, url, TvType.Movie, LoadData(links).toJson()).apply {
            this.posterUrl = "$mainUrl$imagePath"
            this.plot = description
            this.duration = duration
            this.year = year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = parseJson<LoadData>(data).links

        links.apmap {
            val link = fixUrlNull(it) ?: return@apmap null
            loadExtractor(link, "$mainUrl/", subtitleCallback, callback)
        }
        return links.isNotEmpty()
    }

    data class LoadData(
        val links: List<String>
    )
}
