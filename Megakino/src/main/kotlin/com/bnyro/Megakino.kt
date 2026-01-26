package com.bnyro

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.net.URI

class Megakino : MainAPI() {
    @Deprecated("Shouldn't be used directly. Use getActualMainUrl instead!")
    override var mainUrl = "https://megakino.live"
    override var name = "Megakino"
    override val hasMainPage = true
    override var lang = "de"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Documentary)

    override val mainPage = mainPageOf(
        "" to "Trends",
        "kinofilme" to "Kinofilme",
        "serials" to "Serien",
        "documentary" to "Dokumentationen",
    )

    private var urlCookieCache: Pair<Map<String, String>, String>? = null


    /**
     * Follow the redirect set on [mainUrl] in order to get the current
     * base domain of Megakino.
     *
     * That workaround is needed because they change they TLDs almost daily.
     */
    @Suppress("DEPRECATION")
    private suspend fun getActualMainUrlAndCookies(): Pair<Map<String, String>, String> {
        urlCookieCache?.let { return it }

        // follow redirects to get the real base url
        val actualMainUrl = app.get(mainUrl).url.trimEnd('/')
        // get cookies to send with each request
        val cookies = app.get("$actualMainUrl/index.php?yg=token").cookies

        return cookies to actualMainUrl
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val (cookies, actualMainUrl) = getActualMainUrlAndCookies()

        val document =
            app.get(
                "$actualMainUrl/${request.data}/page/$page",
                cookies = cookies
            ).document
        val home = document.select("#dle-content > a").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("h3").text()
        val href = fixUrl(this.attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = SearchQuality.HD
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val (cookies, actualMainUrl) = getActualMainUrlAndCookies()

        val data =
            mapOf("do" to "search", "subaction" to "search", "story" to query.replace(" ", "+"))
        val response =
            app.post(actualMainUrl, data = data, cookies = cookies.orEmpty()).document

        return response.select("a.poster.grid-item").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        // replace base url with the redirected main url
        val (cookies, actualMainUrl) = getActualMainUrlAndCookies()
        val fixedUrl = actualMainUrl + URI.create(url).rawPath

        val document = app.get(fixedUrl, cookies = cookies.orEmpty()).document
        val title = document.selectFirst("div.page__subcols.d-flex h1")?.text() ?: "Unknown"
        val description = document.selectFirst("div.page__text")?.text()
        val poster =
            fixUrl(document.select("div.pmovie__poster.img-fit-cover img").attr("data-src"))
        val year = document.select("div.pmovie__year > span:nth-child(2)").text().toIntOrNull()
        val genres = document.selectFirst("div.pmovie__genres")?.text()
            ?.split(" / ")?.map { it.trim() } ?: emptyList()

        val trailer = document.select("link[itemprop=embedUrl]").attr("href")

        val typeTag = document.select("div.pmovie__genres").text()
        val type = if (typeTag.contains("Filme")) TvType.Movie else TvType.TvSeries
        val related = document.select("section.pmovie__related a.poster").map {
            it.toSearchResult()
        }

        return if (type == TvType.TvSeries) {
            val episodes = document.select("select[name=pmovie__select-items]")
                .filter { it.id().startsWith("ep") }
                .map {
                    val episodeNumber = it.id()
                        .takeLastWhile { c -> c.isDigit() }
                        .toIntOrNull()
                    val episodeStreamLinks = it.select("option").map { option ->
                        option.attr("value")
                    }

                    newEpisode(episodeStreamLinks.toJson()) {
                        this.episode = episodeNumber
                        this.posterUrl = poster
                    }
                }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genres
                this.year = year
                this.recommendations = related
                addTrailer(trailer)
            }
        } else {
            val streamLinks = document.select("div.pmovie__player iframe")
                .map { it.attr("src").ifEmpty { it.attr("data-src") } }.toJson()


            newMovieLoadResponse(title, url, TvType.Movie, streamLinks) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genres
                this.year = year
                this.recommendations = related
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = parseJson<Links>(data)
        for (link in links) {
            loadExtractor(link, subtitleCallback, callback)
        }

        return links.isNotEmpty()
    }

    class Links : ArrayList<String>()
}
