package com.bnyro

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

open class HDFilme : MainAPI() {
    override var name = "HDFilme"
    override var lang = "de"

    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie)
    override var mainUrl = "https://hdfilme.my"

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl/page/${page}/").document
        val recommendations = doc.select("#dle-content div.item")
            .map { it.toSearchResponse() }

        return newHomePageResponse(
            HomePageList(name = "Recommended", recommendations),
            hasNext = true
        )
    }

    private fun Element.toSearchResponse(): SearchResponse {
        val title = select("a.movie-title").text()
        val url = select("a.movie-title").attr("href")
        val posterUrl = selectFirst("figure img")?.getImageUrl()
        val metaList = select("div.meta > span")

        return newMovieSearchResponse(title, type = TvType.Movie, url = url).apply {
            this.posterUrl = posterUrl
            this.year = metaList.firstOrNull()?.text()?.toIntOrNull()
        }
    }

    override suspend fun quickSearch(query: String) = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?story=$query&do=search&subaction=search").document

        return doc.select("#dle-content div.item").map { it.toSearchResponse() }
    }

    private fun Element.getImageUrl(): String {
        return fixUrl(attr("data-src").ifEmpty { attr("src") })
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        val details = doc.selectFirst("section.detail") ?: return null

        val title = details.select(".info h1").text().replace("hdfilme", "").trim()
        val posterUrl = details.selectFirst("figure img")?.getImageUrl()
        val meta = details.select("h1 + div span:not(.divider)")
        val description = doc.selectFirst("section:has(> h2) > div > p")?.text()
        val actors = details.select("li > a[href^='$mainUrl/xfsearch/actors/']").map { it.text() }

        val tags = meta.firstOrNull()?.select("a")?.map { it.text() }
        val year = meta.getOrNull(2)?.text()?.toIntOrNull()
        val duration = meta.getOrNull(3)?.text()
            ?.replace("min", "", ignoreCase = true)?.trim()?.toIntOrNull()

        val seasons = doc.select(".su-spoiler")
        if (seasons.isNotEmpty()) {
            val episodes = seasons.mapIndexed { seasonIndex, season ->
                // collect all episode stream links
                val episodes = mutableListOf<List<String>>()
                var currentEpisodeLinks = mutableListOf<String>()
                for (item in season.select(".su-spoiler-content > *")) {
                    if (item.nameIs("br")) {
                        episodes.add(currentEpisodeLinks)
                        currentEpisodeLinks = mutableListOf()
                    } else if (item.nameIs("a")) {
                        val streamLink = fixUrl(item.attr("href"))
                        currentEpisodeLinks.add(streamLink)
                    }
                }

                episodes.mapIndexed { index, streams ->
                    newEpisode(
                        data = LoadData(streams).toJson()
                    ) {
                        this.name = title
                        this.episode = index + 1
                        this.season = seasonIndex + 1
                    }
                }
            }.flatten()

            return newTvSeriesLoadResponse(
                name = title,
                url = url,
                type = TvType.TvSeries,
                episodes = episodes
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = description
                this.duration = duration
                this.tags = tags
                addActors(actors)
            }
        }

        val streamsJsUrl =
            doc.selectFirst("script[src^='https://meinecloud.click/ddl']")?.attr("src")
                ?: throw NoSuchFieldException("Couldn't find link to streams!")

        val streamsJs = app.get(streamsJsUrl).text
        val streamLinkRegex = Regex("(?<=')http.*(?=')")
        val streams = streamLinkRegex.findAll(streamsJs).map { it.value }.toList()

        val related = doc.select("section.top-filme .listing a").map {
            newMovieSearchResponse(
                name = it.attr("title"),
                url = it.attr("href"),
                type = TvType.Movie
            ) {
                this.posterUrl = it.selectFirst("figure img")?.getImageUrl()
            }
        }

        return newMovieLoadResponse(
            title.ifEmpty { return null },
            url,
            TvType.Movie,
            LoadData(streams).toJson()
        ) {
            this.posterUrl = posterUrl
            this.year = year
            this.duration = duration
            this.plot = description
            this.tags = tags
            this.recommendations = related
            addActors(actors)
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

            loadExtractor(
                link,
                "$mainUrl/",
                subtitleCallback,
                callback
            )
        }

        return links.isNotEmpty()
    }

    data class LoadData(
        val links: List<String>
    )
}