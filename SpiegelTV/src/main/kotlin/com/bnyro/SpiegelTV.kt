package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import java.util.Date

open class SpiegelTV : MainAPI() {
    override var name: String = "SpiegelTv"
    override var mainUrl: String = "https://www.spiegel.de"
    override var lang: String = "de"

    override val hasMainPage: Boolean = true

    override val supportedTypes: Set<TvType> = setOf(TvType.Documentary)

    open val jwPlayerUrl = "https://cdn.jwplayer.com"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("$mainUrl/thema/spiegel-tv/").document
        val results = response.select("[data-block-el=articleTeaser] > article")
            .map { article -> article.toHomePageItemSearchResponse() }

        return newHomePageResponse(
            HomePageList(name, results, isHorizontalImages = true)
        )
    }

    fun Element.toHomePageItemSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = select("h2 > a").attr("title"),
            url = select("h2 > a").attr("href"),
        ) { posterUrl = selectFirst("picture > img")?.attr("data-src") }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val videos = app.get(
            "$mainUrl/services/sitesearch/search",
            params = mapOf(
                "q" to query,
                "segments" to "spon",
                "page_size" to PAGE_SIZE.toString(),
                "page" to page.toString()
            )
        )
            .parsed<SearchResponseRoot>()          // results also contain non-video articles
        return newSearchResponseList(videos.results.filter { it.style == "video" }
            .map { it.toSearchResponse() }, hasNext = videos.numResults > PAGE_SIZE * page)
    }

    private fun SearchResultItem.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(name = this.heading, url = this.url) {
            year = Date(this@toSearchResponse.publishDate).year
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url).document
        val rawData = response.selectFirst("script[type=\"application/ld+json\"]")?.data()
            ?: throw RuntimeException("no script data found")
        val data = AppUtils.parseJson<VideoDataList>(rawData)
        val metadata = data.first { it.type == "NewsArticle" }
        val video = data.first { it.type == "VideoObject" }
        val relatedItems = response.select("section[data-area=related_articles] ul > li")
            .map { li ->
                newMovieSearchResponse(
                    name = li.select("a").attr("title"),
                    url = li.select("a").attr("href")
                ) {
                    posterUrl = li.selectFirst("picture img")
                        ?.let { it.attr("data-src").ifEmpty { it.attr("src") } }
                }
            }

        return newMovieLoadResponse(
            name = metadata.headline!!,
            url = url,
            type = TvType.Documentary,
            dataUrl = video.contentUrl!!
        ) {
            this.plot = response.selectFirst("header[data-area=intro] div.RichText")
                ?.text()
            this.posterUrl = metadata.image.firstOrNull()
            this.tags = metadata.keywords
            this.year = metadata.datePublished?.take(4)?.toIntOrNull()
            this.recommendations = relatedItems
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {         // for some reason, the m3u8 doesn't work, so we manually need to load the playlist
        val id = data.substringAfterLast("/").substringBefore(".")
        val jwPlayerResponse = app.get("$jwPlayerUrl/v2/media/$id").parsed<JwPlayerResponse>()
        val links = jwPlayerResponse.playlist.flatMap { it.sources }

        links.forEach { source ->
            callback.invoke(newExtractorLink(source = name, name = name, url = source.file))
        }

        return links.isNotEmpty()
    }

    private data class SearchResponseRoot(
        @JsonProperty("num_results") val numResults: Long,
        val results: List<SearchResultItem>,
        val aggregations: Map<String, Any>,
    )

    private data class SearchResultItem(
        @JsonProperty("access_level") val accessLevel: String,
        val channel: Channel,
        val collection: String,
        val domain: String,
        @JsonProperty("header_image") val headerImage: String?,
        val heading: String,
        val id: String,
        val intro: String?,
        @JsonProperty("is_breaking") val isBreaking: Boolean,
        @JsonProperty("is_live") val isLive: Boolean,
        @JsonProperty("publish_date") val publishDate: Long,
        val slug: String,
        val style: String,
        val teaser: Teaser,
        val title: String,
        val type: String,
        val url: String,
        val authors: List<Author>?,
        @JsonProperty("author_details_prefix") val authorDetailsPrefix: String?,
    )

    private data class Channel(
        val id: String,
        val slug: String,
        @JsonProperty("sub_channel") val subChannel: SubChannel,
        val title: String,
    )

    private data class SubChannel(val id: String, val slug: String, val title: String)
    private data class Teaser(val length: Long, val type: String, val id: String?)
    data class Author(val name: String, val image: String)
    private class VideoDataList : ArrayList<VideoDataItem>()
    private data class VideoDataItem(
        @JsonProperty("@context") val context: String,
        @JsonProperty("@type") val type: String,          // news article properties
        val articleSection: String?,
        val dateCreated: String?,
        val dateModified: String?,
        val datePublished: String?,
        val headline: String?,
        val isAccessibleForFree: Boolean?,
        val keywords: List<String>?,
        val mainEntityOfPage: String?,
        val name: String?,
        val url: String?,
        val image: List<String> = emptyList(),          // video object properties
        val contentUrl: String?,
        val description: String?,
        val duration: String?,
        val uploadDate: String?,
    )

    private data class JwPlayerResponse(
        val title: String,
        val description: String,
        val kind: String,
        val playlist: List<JwPlayerPlaylist>,
        @JsonProperty("feed_instance_id") val feedInstanceId: String,
    )

    private data class JwPlayerPlaylist(
        val title: String,
        val mediaid: String,
        val link: String,
        val image: String,
        val images: List<Image>,
        val duration: Long,
        @JsonProperty("content_source_type") val contentSourceType: String,
        val pubdate: Long,
        val updated: Long,
        val description: String,
        val tags: String,
        val sources: List<Source>,
        val jwpseg: List<String>,
        @JsonProperty("import_guid") val importGuid: String?,
        @JsonProperty("import_id") val importId: String?,
    )

    private data class Image(val src: String, val width: Long, val type: String)
    private data class Source(val file: String, val type: String)
    companion object {
        private const val PAGE_SIZE = 20
    }
}