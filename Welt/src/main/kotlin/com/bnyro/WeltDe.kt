package com.bnyro

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class WeltDe : MainAPI() {
    override var name: String = "Welt"
    override var mainUrl: String = "https://www.welt.de"
    override val hasMainPage: Boolean = true
    override val mainPage: List<MainPageData> = mainPageOf(
        "reportage" to "Reportagen",
        "dokumentation" to "Dokumentationen",
        "magazin" to "Magazine",
        "talk" to "Politik-Talkshows"
    )
    override val supportedTypes: Set<TvType> = setOf(TvType.Documentary)
    override var lang: String = "de"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val resp = app.get("$mainUrl/mediathek/${request.data}/").document

        val lists = resp.select("section.c-stage").map { section ->
            val title = section.select("h2").text().ifEmpty { request.name }
            val items = section.select("article").map {
                it.toSearchResponse()
            }
            HomePageList(title, items)
        }

        return newHomePageResponse(lists)
    }

    // Don't modify, this is set to a fixed value by Welt
    private val SEARCH_PAGE_SIZE = 50
    override suspend fun search(query: String, page: Int): SearchResponseList {
        val response = app.get(
            "$mainUrl/api/search/$query?offset=${(page - 1) * SEARCH_PAGE_SIZE}&section=mediathek",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:149.0) Gecko/20100101 Firefox/149.0",
                "Accept-Language" to "en-US,en;q=0.9",
                "Sec-GPC" to "1",
                "Cache-Control" to "no-cache"
            )
        )
            .parsed<SearchResult>()

        val results = response.items.map {
            it.toSearchResponse()
        }
        val hasNext = response.totalResults > page * SEARCH_PAGE_SIZE
        return newSearchResponseList(results, hasNext = hasNext)
    }

    private fun MediaItem.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = headline,
            url = url,
            type = TvType.Documentary
        ) {
            posterUrl = teaserImage
            year = publicationDate.take(4).toIntOrNull()
        }
    }

    private fun Element.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = select("h4").text(),
            url = select("h4 a").attr("href"),
            type = TvType.Documentary
        ) {
            posterUrl = select("picture img").attr("src")
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringBeforeLast("/").takeLast(24)
        val response = app.get(url).document

        val contentRoot = response.select(".c-article-page")
        val title = contentRoot.select(".c-article-header h2").text()
        val year = contentRoot.select(".c-article-header__date").text()
            .reversed()
            .dropWhile { !it.isDigit() }
            .takeWhile { it.isDigit() }
            .reversed()
            .toIntOrNull()
        val plot = contentRoot.select(".c-article-page__intro, .c-rich-text-renderer").text()
        val duration = contentRoot.select(".c-article-header__duration").text()
            .filter { it.isDigit() }.toInt()

        val video = contentRoot.select("noscript video")
        val poster = video.attr("poster")
        val videoSources = video.select("source").map {
            VideoSource(
                it.attr("src"),
                it.attr("type")
            )
        }

        val relatedVideos = runCatching {
            app.get("$mainUrl/api/articles/mlt/video/$id")
                .parsed<Recommendations>()
                .map {
                    newMovieSearchResponse(
                        name = it.data.headline,
                        url = fixUrl(it.data.webUrl)
                    ) {
                        this.posterUrl = it.data.teaserMedia.imageUrl
                        this.year = it.data.publicationDate.take(4).toIntOrNull()
                    }
                }
        }.getOrElse { emptyList() }

        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.Documentary,
            data = VideoSources(videoSources)
        ) {
            this.year = year
            this.posterUrl = poster
            this.plot = plot
            this.duration = duration
            this.recommendations = relatedVideos
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val videoSources = AppUtils.parseJson<VideoSources>(data).sources

        for (source in videoSources) {
            val isM3u8 = source.type == "application/x-mpegURL"
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name ${if (isM3u8) "(HLS)" else ""}",
                    url = source.source,
                    type = if (isM3u8) ExtractorLinkType.M3U8 else INFER_TYPE
                )
            )
        }

        return videoSources.isNotEmpty()
    }

    private data class VideoSources(
        val sources: List<VideoSource>
    )

    private data class VideoSource(
        val source: String,
        val type: String
    )

    private data class SearchResult(
        val query: String,
        val offset: Long,
        val totalResults: Long,
        val items: List<MediaItem>,
    )

    private data class MediaItem(
        val id: String,
        val url: String,
        val subType: String,
        val teaserImage: String,
        val headline: String,
        val intro: String,
        val publicationDate: String,
        val lastModifiedDate: String,
        val sectionPath: String,
        val sectionLabel: String,
        val topic: String?,
        val type: String,
        val premium: Boolean,
        val sponsored: Boolean,
        val hasVideoOpener: Boolean,
        val productStory: Boolean,
    )


    private class Recommendations: ArrayList<Recommendation>()

    private data class Recommendation(
        val data: RecommendationData,
    )

    private data class RecommendationData(
        val id: String,
        val webUrl: String,
        val contentType: String,
        val contentSubType: String,
        val headline: String,
        val topic: String,
        val publicationDate: String,
        val lastModifiedDate: String,
        val fskRating: Long,
        val teaserMedia: TeaserMedia,
        val intro: String,
        val duration: Long,
    )

    private data class TeaserMedia(
        val type: String,
        val id: String,
        val format: String,
        val imageUrl: String,
        val altText: String?,
    )
}