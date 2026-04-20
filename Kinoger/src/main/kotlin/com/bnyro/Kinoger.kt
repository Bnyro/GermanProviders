package com.bnyro

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element


class Kinoger : MainAPI() {
    override var name = "Kinoger"
    override var mainUrl = "https://kinoger.com"
    override var lang = "de"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "" to "Alle Filme",
        "stream/action" to "Action",
        "stream/fantasy" to "Fantasy",
        "stream/drama" to "Drama",
        "stream/mystery" to "Mystery",
        "stream/romance" to "Romance",
        "stream/animation" to "Animation",
        "stream/horror" to "Horror",
        "stream/familie" to "Familie",
        "stream/komdie" to "Komdie",
    )

    private val cloudflareInterceptor by lazy { CloudflareKiller() }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page", interceptor = cloudflareInterceptor).document
        val home = document.select("div#dle-content div.short").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun getProperLink(uri: String): String {
        return if (uri.contains("-episode-")) {
            "$mainUrl/series/" + Regex("$mainUrl/(.+)-ep.+").find(uri)?.groupValues?.get(1)
        } else {
            uri
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = getProperLink(this.selectFirst("a")?.attr("href") ?: return null)
        val title = this.selectFirst("a")?.text()?.removeSuffix(" Film")
            ?: this.selectFirst("img")?.attr("alt")
            ?: this.selectFirst("a")?.attr("title")
            ?: return null

        // They mix in titles like "KinoGer.com und kinoGer.to DNS-Sperren umgehen",
        // which we don't want to show in our results
        if (title.contains("KinoGer", ignoreCase = true)) return null

        val posterPath = this.selectFirst("div.content_text img")?.getImageAttr()
            ?: this.nextElementSibling()?.selectFirst("div.content_text img")?.getImageAttr()
            ?: this.selectFirst("img")?.getImageAttr()

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = fixUrlNull(posterPath)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/?do=search&subaction=search&titleonly=3&story=$query&x=0&y=0&submit=submit", interceptor = cloudflareInterceptor).document.select(
            "div#dle-content div.titlecontrol"
        ).mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, interceptor = cloudflareInterceptor).document
        val title = document.selectFirst("h1#news-title")?.text() ?: ""
        val poster = fixUrlNull(document.selectFirst("div.images-border img")?.getImageAttr())
        val description = document.select("div.images-border").text()
        val year = """\((\d{4})\)""".toRegex().find(title)?.groupValues?.get(1)?.toIntOrNull()
        val tags = document.select("li.category a").map { it.text() }

        val recommendations = document.select("ul.ul_related li").mapNotNull {
            it.toSearchResult()
        }

        val scripts = document.select("div[id^=container-video] script")
            .map { it.data() }
        val links = scripts
            .map { script ->
                val data = script.substringAfter("[").substringBeforeLast("]")
                    .replace("\'", "\"")
                AppUtils.tryParseJson<List<List<String>>>("[$data]").orEmpty()
            }.let { transpose(it) }.map { transpose(it) }

        val type = if (scripts.isNotEmpty() && scripts.first().substringBeforeLast(")")
                .substringAfterLast(",") == "0.2"
        ) TvType.Movie else TvType.TvSeries

        val episodes = links.flatMapIndexed { season: Int, episodeList: List<List<String>> ->
            episodeList.mapIndexed { episode, iframes ->
                newEpisode(
                    LinkData(iframes).toJson(),
                ).apply {
                    this.season = season + 1
                    this.episode = episode + 1
                }
            }
        }

        return newTvSeriesLoadResponse(title, url, type, episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = parseJson<LinkData>(data).links

        links.amap { link ->
            loadExtractor(link, "$mainUrl/", subtitleCallback, callback)
        }

        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("data-src")
            this.hasAttr("data-lazy-src") -> this.attr("data-lazy-src")
            this.hasAttr("srcset") -> this.attr("srcset").substringBefore(" ")
            else -> this.attr("src")
        }
    }

    data class LinkData(
        val links: List<String>
    )

    private fun <T> transpose(table: List<List<T>>): List<List<T>> {
        val ret: MutableList<List<T>> = ArrayList()
        val N = table[0].size
        for (i in 0 until N) {
            val col: MutableList<T> = ArrayList()
            for (row in table) {
                col.add(row[i])
            }
            ret.add(col)
        }
        return ret
    }
}