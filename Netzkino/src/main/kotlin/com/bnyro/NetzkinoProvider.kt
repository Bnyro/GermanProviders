package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
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
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlin.math.roundToInt


open class NetzkinoProvider : MainAPI() {
    override var name = "Netzkino"
    override var lang = "de"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)
    override var mainUrl = "https://api.netzkino.de.simplecache.net"

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val response = app.get("${mainUrl}/capi-2.0a/index.json?d=www")
            .parsed<IndexResponse>()
        val homepages = response.homepageCategories.map {
            HomePageList(it.title, it.posts.map { post -> post.toSearchResponse() })
        }

        return newHomePageResponse(homepages, hasNext = false)
    }

    private fun Post.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = this@toSearchResponse.title,
            url = "$mainUrl/capi-2.0a/movies/${this@toSearchResponse.id}.json?d=www",
            type = TvType.Movie
        ) {
            this.posterUrl = this@toSearchResponse.thumbnail
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("${mainUrl}/capi-2.0a/search?q=$query&d=www")
            .parsed<SearchResp>()

        return response.posts.map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).parsed<Post>()

        return newMovieLoadResponse(
            name = response.title,
            url = url,
            type = TvType.Movie,
            data = response.customFields.toJson()
        ) {
            this.posterUrl = response.customFields.featuredImgAll.firstOrNull() ?: response.thumbnail
            this.plot = response.content
            this.duration = response.customFields.duration.firstOrNull()?.toIntOrNull()?.div(60)
            this.year = response.customFields.jahr.firstOrNull()?.toIntOrNull()
            this.score = response.customFields.imdbBewertung.firstOrNull()?.toFloatOrNull()?.let {
                Score.from10(it.roundToInt())
            }
            addActors(response.customFields.stars.map { it.split(",") }.flatten())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val customFields = AppUtils.parseJson<CustomFields>(data)

        customFields.streaming.forEach { streamSlug ->
            callback(
                newExtractorLink(
                    source = "Netzkino",
                    name = "Netzkino MP4",
                    url = "https://pmd.netzkino-seite.netzkino.de/${streamSlug}.mp4"
                ) {
                    referer = "https://www.netzkino.de/"
                }
            )
        }

        return customFields.streaming.isNotEmpty()
    }

    data class IndexResponse(
        val categories: List<Category>,
        @JsonProperty("categories_count")
        val categoriesCount: Long,
        @JsonProperty("homepage_categories")
        val homepageCategories: List<HomepageCategory>,
    )

    data class Category(
        val id: Long,
        val slug: String,
        val title: String,
        val description: String,
        @JsonProperty("post_count")
        val postCount: Long,
        val parent: Long,
        @JsonProperty("sort_id")
        val sortId: Long,
        @JsonProperty("_id")
        val id2: Long,
        @JsonProperty("_fullyLoaded")
        val fullyLoaded: Boolean,
        val page: Long,
        val count: Long,
    )

    data class HomepageCategory(
        val id: Long,
        val slug: String,
        val title: String,
        val description: String,
        @JsonProperty("post_count")
        val postCount: Long,
        val parent: Long,
        @JsonProperty("sort_id")
        val sortId: Long,
        val posts: List<Post>,
        @JsonProperty("_id")
        val id2: Long,
        @JsonProperty("_fullyLoaded")
        val fullyLoaded: Boolean,
        val page: Long,
        val count: Long,
        val pages: Long,
    )

    data class Post(
        val id: Long,
        val slug: String,
        val title: String,
        val content: String,
        val date: String,
        val modified: String,
        val author: Author,
        val thumbnail: String,
        @JsonProperty("custom_fields")
        val customFields: CustomFields,
        val properties: List<String>,
    )

    data class Author(
        val name: String,
    )

    data class CustomFields(
        @JsonProperty("Adaptives_Streaming")
        val adaptivesStreaming: List<String>,
        @JsonProperty("Artikelbild")
        val artikelbild: List<String>,
        @JsonProperty("Duration")
        val duration: List<String>,
        val productionCountry: String,
        @JsonProperty("featured_img_all")
        val featuredImgAll: List<String>,
        @JsonProperty("featured_img_all_small")
        val featuredImgAllSmall: List<String>,
        @JsonProperty("featured_img_seven")
        val featuredImgSeven: List<String>,
        @JsonProperty("featured_img_slider")
        val featuredImgSlider: List<String>,
        @JsonProperty("featured_img_logo")
        val featuredImgLogo: List<String>,
        @JsonProperty("art_logo_img")
        val artLogoImg: List<String>,
        @JsonProperty("hero_landscape_img")
        val heroLandscapeImg: List<String>,
        @JsonProperty("hero_portrait_img")
        val heroPortraitImg: List<String>,
        @JsonProperty("primary_img")
        val primaryImg: List<String>,
        @JsonProperty("video_still_img")
        val videoStillImg: List<String>,
        val licenseStart: String,
        val licenseEnd: String,
        val activeCountries: List<String>,
        val drm: Boolean,
        @JsonProperty("FSK")
        val fsk: List<String>,
        @JsonProperty("GEO_Availability_Exclusion")
        val geoAvailabilityExclusion: List<String>,
        @JsonProperty("IMDb-Bewertung")
        val imdbBewertung: List<String>,
        @JsonProperty("IMDb-Link")
        val imdbLink: List<String>,
        @JsonProperty("Jahr")
        val jahr: List<String>,
        val offlineAvailable: List<String>,
        @JsonProperty("Regisseur")
        val regisseur: List<String>,
        @JsonProperty("Stars")
        val stars: List<String>,
        @JsonProperty("Streaming")
        val streaming: List<String>,
        @JsonProperty("TV_Movie_Cover")
        val tvMovieCover: List<String>,
        @JsonProperty("TV_Movie_Genre")
        val tvMovieGenre: List<String>,
        @JsonProperty("Youtube_Deliverry_Active")
        val youtubeDeliverryActive: List<String>,
        @JsonProperty("Youtube_Delivery_Id")
        val youtubeDeliveryId: List<String>,
        @JsonProperty("Youtube_Delivery_Preview_Only")
        val youtubeDeliveryPreviewOnly: List<String>,
        @JsonProperty("Youtube_Delivery_Preview_Start")
        val youtubeDeliveryPreviewStart: List<String>,
        @JsonProperty("Youtube_Delivery_Preview_End")
        val youtubeDeliveryPreviewEnd: List<String>,
        @JsonProperty("Featured_Video_Slider")
        val featuredVideoSlider: List<String>,
        @JsonProperty("featured_img_seven_small")
        val featuredImgSevenSmall: List<String>,
        val offlineAvaiable: List<String>,
        val skuAvod: String?,
        val skuSvod: String?,
    )

    data class SearchResp(
        @JsonProperty("_qryArr")
        val qryArr: List<String>,
        val searchTerm: String,
        val status: String,
        @JsonProperty("count_total")
        val countTotal: Long,
        val count: Long,
        val page: Long,
        val pages: Any?,
        val posts: List<Post>,
        val slug: String,
        val id: Long,
        @JsonProperty("post_count")
        val postCount: Long,
    )
}