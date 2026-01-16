package com.bnyro

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink


open class MediaCCC : MainAPI() {
    override var name = "C3TV"
    override var lang = "de"
    override val hasQuickSearch = true
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Others)
    override var mainUrl = "https://api.media.ccc.de"

    override val mainPage = mainPageOf(
        "public/events/recent" to "Recent events",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val response = app.get("${mainUrl}/${request.data}")
            .parsed<EventsResponse>()

        return newHomePageResponse(
            request.name,
            response.events.map { it.toSearchResponse() },
            hasNext = false
        )
    }

    private fun Event.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = this.title,
            url = this.frontendLink,
            type = TvType.Others,
        ) {
            this.posterUrl = this@toSearchResponse.thumbUrl
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("${mainUrl}/public/events/search?q=${query}")
            .parsed<EventsResponse>()

        return response.events.map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val slug = url.substringAfterLast("/")

        val response = app.get("${mainUrl}/public/events/${slug}")
            .parsed<Event>()

        return newMovieLoadResponse(
            name = response.title,
            url = url,
            type = TvType.Others,
            data = response.recordings.toJson()
        ) {
            this.posterUrl = response.posterUrl
            this.tags = response.tags
            this.plot = response.description
            this.duration = response.duration?.toInt()?.div(60)
            this.comingSoon = response.recordings.isEmpty()
            this.year = response.date?.take(4)?.toIntOrNull()
            addActors(response.persons)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val recordings = parseJson<List<Recording>>(data)
        if (recordings.isEmpty()) return false

        for (recording in recordings) {
            if (recording.mimeType.startsWith("video")) {
                val streamInfo = "MediaCCC (${recording.language})"
                callback.invoke(
                    newExtractorLink(
                        source = streamInfo,
                        name = streamInfo,
                        url = recording.recordingUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        referer = ""
                        quality = recording.height?.toInt() ?: Qualities.Unknown.value
                    }
                )
            } else if (recording.mimeType.startsWith("text")) {
                subtitleCallback.invoke(
                    SubtitleFile(recording.language, recording.url)
                )
            }
        }

        return true
    }

    data class EventsResponse(
        val events: List<Event>,
    )

    data class Event(
        val guid: String,
        val title: String,
        val subtitle: String?,
        val slug: String?,
        val link: String?,
        val description: String?,
        @JsonProperty("original_language")
        val originalLanguage: String?,
        val persons: List<String>,
        val tags: List<String>,
        @JsonProperty("view_count")
        val viewCount: Long?,
        val promoted: Boolean?,
        val date: String?,
        @JsonProperty("release_date")
        val releaseDate: String?,
        @JsonProperty("updated_at")
        val updatedAt: String?,
        val length: Long?,
        val duration: Long?,
        @JsonProperty("thumb_url")
        val thumbUrl: String?,
        @JsonProperty("poster_url")
        val posterUrl: String?,
        @JsonProperty("timeline_url")
        val timelineUrl: String?,
        @JsonProperty("thumbnails_url")
        val thumbnailsUrl: String?,
        @JsonProperty("frontend_link")
        val frontendLink: String,
        val url: String,
        @JsonProperty("conference_title")
        val conferenceTitle: String,
        @JsonProperty("conference_url")
        val conferenceUrl: String,
        val recordings: List<Recording> = emptyList(),
    )

    data class Recording(
        val length: Long?,
        @JsonProperty("mime_type")
        val mimeType: String,
        val language: String,
        val filename: String,
        val state: String,
        val folder: String,
        @JsonProperty("high_quality")
        val highQuality: Boolean,
        val width: Long?,
        val height: Long?,
        @JsonProperty("updated_at")
        val updatedAt: String,
        @JsonProperty("recording_url")
        val recordingUrl: String,
        val url: String,
        @JsonProperty("event_url")
        val eventUrl: String,
        @JsonProperty("conference_url")
        val conferenceUrl: String,
    )
}