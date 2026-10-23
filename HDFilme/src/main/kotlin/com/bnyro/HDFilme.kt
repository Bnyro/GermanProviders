package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.node.ObjectNode
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
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

// Topstreamfilm is the same layout
open class HDFilme : MainAPI() {
    override var name = "HDFilme"
    override var lang = "de"

    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie)
    override var mainUrl = "https://hdfilme.ceo"
    open val meineCloudUrl = "https://devideosrc.co"

    private val imdbIdRegex = Regex("""imdb\s*=\s*'(.*)';""")

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(mainUrl).document
        val recommendationSections = doc.select("section.hd-row")

        val homePageLists = recommendationSections.map { section ->
            HomePageList(
                section.select(".hd-row-title").text(),
                section.select(".hd-scroller .hd-c").map { it.toHomeSearchResponse() }
            )
        }

        return newHomePageResponse(homePageLists, hasNext = false)
    }

    private fun Element.toHomeSearchResponse(): SearchResponse {
        val title = select(".hd-cw-title").text()
            .ifEmpty { select(".hd-c-title").text() }
        val url = attr("href")
        val posterUrl = selectFirst("img")?.attr("src")?.let(::fixUrl)
        val metaList = select(".hd-cw-meta").text().split(" · ")

        return newMovieSearchResponse(title, type = TvType.Movie, url = url).apply {
            this.posterUrl = posterUrl
            this.year = metaList.firstOrNull()?.toIntOrNull()
            if (metaList.size >= 2) {
                this.score = Score.from(
                    metaList[1].takeLastWhile { it.isDigit() || it == '.' }.toIntOrNull(),
                    maxScore = 10
                )
            }
        }
    }

    private fun Element.toSearchResponse(): SearchResponse {
        val title = select("a.movie-title").text()
        val url = select("a.movie-title").attr("href")
        val posterUrl = selectFirst("figure img")?.attr("data-src")?.let(::fixUrl)
        val metaList = select("div.meta > span")

        return newMovieSearchResponse(title, type = TvType.Movie, url = url).apply {
            this.posterUrl = posterUrl
            this.year = metaList.firstOrNull()?.text()?.toIntOrNull()
        }
    }


    private fun Element.toRelatedSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = attr("title"),
            url = attr("href"),
            type = TvType.Movie
        ) {
            this.posterUrl = selectFirst("figure img")?.attr("src")?.let(::fixUrl)
        }
    }


    override suspend fun quickSearch(query: String) = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?story=$query&do=search&subaction=search").document

        return doc.select("#dle-content div.item").map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val resp = app.get(url)
        val doc = resp.document

        val details = doc.selectFirst("section.detail") ?: return null

        val title = details.select(".info h1").text().replace("hdfilme", "").trim()
        val posterUrl = details.selectFirst("figure img")?.attr("data-src")
            ?.let(::fixUrl)
        val meta = details.select("h1 + div span:not(.divider)")
        val description = doc.selectFirst("section:has(> h2) > div > p")?.text()
        val actors = details.select("li > a[href^='$mainUrl/xfsearch/actors/']").map { it.text() }

        val tags = meta.firstOrNull()?.select("a")?.map { it.text() }
        val year = meta.getOrNull(2)?.text()?.toIntOrNull()
        val duration = meta.getOrNull(3)?.text()
            ?.replace("min", "", ignoreCase = true)?.trim()?.toIntOrNull()

        val related = doc.select(".sidebar-section .scroll-content a").map {
            it.toRelatedSearchResponse()
        }

        if (resp.text.contains("Über diese Serie")) {
            val imdbId = imdbIdRegex.find(resp.text)?.groupValues?.getOrNull(1)
                ?: throw IllegalArgumentException("missing imdb id")

            // fails if devideosrc doesn't have any episodes for this series
            val episodes = runCatching { loadEpisodes(imdbId) }
                .getOrElse { emptyList() }

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
                this.recommendations = related
                addActors(actors)
            }
        } else {
            val iframeSrc = doc.select("iframe").attr("data-src")
            val iframe = app.get(iframeSrc).document

            val details = app.post(
                "$meineCloudUrl/api/resolve",
                json = mapOf(
                    "id" to iframe.select("main").attr("data-id"),
                    "token" to iframe.select("main").attr("data-resolve-token")
                )
            ).parsed<EmbedLinksResponse>()

            return newMovieLoadResponse(
                title.ifEmpty { return null },
                url,
                TvType.Movie,
                LoadData(details.sources.map { it.url })
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
    }

    private suspend fun loadEpisodes(imdbId: String): List<Episode> {
        val iframe = app.get("$meineCloudUrl/serial/$imdbId").text
        val requestBodyRaw = iframe.substringAfter("JSON.stringify(").substringBefore(")")
            .replace("type", "\"type\"")
            .replace("id", "\"id\"")
            .replace("token", "\"token\"")
            .replace("'", "\"")
        val requestBody = parseJson<ObjectNode>(requestBodyRaw)

        val details = app.post(
            "$meineCloudUrl/api/embed-links", json = mapOf(
                "id" to requestBody.get("id").textValue(),
                "token" to requestBody.get("token").textValue(),
                "type" to requestBody.get("type").textValue(),
            )
        ).parsed<TvEmbedLinks>()

        return details.tv.seasons.flatMap { season ->
            season.episodes.map { episode ->
                newEpisode(
                    url = LoadData(episode.sources.map { s -> s.url }).toJson(),
                    fix = false,
                    initializer = {
                        this.season = season.seasonNumber.toInt()
                        this.name = episode.title
                        this.description = episode.description
                    }
                )
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = parseJson<LoadData>(data).links

        links.amap {
            val link = fixUrlNull(it) ?: return@amap null

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

    data class EmbedLinksResponse(
        val ok: Boolean,
        val type: String,
        val id: String,
        @JsonProperty("imdb_id")
        val imdbId: String,
        val season: Any?,
        val episode: Any?,
        val title: Any?,
        val backdrop: Any?,
        val sources: List<Source>,
        val captions: List<Any?>,
    )

    data class Source(
        val id: String,
        val name: String,
        val url: String,
        val rank: Long,
    )

    data class TvEmbedLinks(
        val ok: Boolean,
        val type: String,
        val tv: Tv,
    )

    data class Tv(
        val title: String,
        val backdrop: String,
        val seasons: List<Season>,
    )

    data class Season(
        @JsonProperty("season_number")
        val seasonNumber: Long,
        val episodes: List<SeasonEpisode>,
    )

    data class SeasonEpisode(
        @JsonProperty("episode_number")
        val episodeNumber: Long,
        val title: String,
        val description: String,
        val sources: List<Source>,
    )
}
