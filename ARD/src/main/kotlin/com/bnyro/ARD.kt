package com.bnyro

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mvvm.debugException
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


open class ARD : MainAPI() {
    override var name = "ARD"
    override var lang = "de"
    override val hasQuickSearch = true
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Live)
    override var mainUrl = "https://api.ardmediathek.de"
    open val programApiUrl = "https://programm-api.ard.de"

    override val mainPage = mainPageOf(
        "1FdQ5oz2JK6o2qmyqMsqiI:-4573418300315789064" to "Jetzt Live",
        "1FdQ5oz2JK6o2qmyqMsqiI:7024894723483797725" to "Film-Empfehlungen",
        "1f65j6Y49hbQUyQbg3sWRP:-6881003991164746949" to "Unsere Top-Serien",
        "3JvraZLz6r8E9VJOSjxe0m:3945748791191973508" to "Spannende Dokus und Reportagen",
        "1f65j6Y49hbQUyQbg3sWRP:-1242529055250270726" to "Drama-Serien",
        "1f65j6Y49hbQUyQbg3sWRP:8954650660112843577" to "Dramedy-Serien",
        "1f65j6Y49hbQUyQbg3sWRP:-1698940734772669089" to "Crime und spannende Serien",
        "3JvraZLz6r8E9VJOSjxe0m:-4951729414550313310" to "Dokumentarfilme",
        "1FdQ5oz2JK6o2qmyqMsqiI:-4156144666028728696" to "Unsere besten Dokuserien",
        "d08a2b5f-8133-4bdd-8ea9-64b6172ce5ee:5379188208992982100" to "Aktuelle Debatten",
        "d08a2b5f-8133-4bdd-8ea9-64b6172ce5ee:-1083078599273736954" to "Exklusive Recherchen",
        "3JvraZLz6r8E9VJOSjxe0m%3A-7375671202743065214" to "Wissens-Dokus",
        "1FdQ5oz2JK6o2qmyqMsqiI:-6311741896596619341" to "Doku-Soaps",
        "1FdQ5oz2JK6o2qmyqMsqiI%3A-3221987816760504385" to "Soaps und Kultserien",
        "1FdQ5oz2JK6o2qmyqMsqiI:-8035917636575745435" to "Politik-Talks und Politik-Magazine"
    )

    // Sources: https://gist.github.com/Axel-Erfurt/b40584d152e1c2f13259590a135e05f4, https://www.zdf.de/live-tv
    private val extraLiveLinks = listOf(
        LiveStream(
            "ZDF",
            "http://zdf-hls-15.akamaized.net/hls/live/2016498/de/high/master.m3u8",
            "https://www.zdf.de/assets/2400-zdf-100~768x432"
        ),
        LiveStream(
            "ZDF neo",
            "http://zdf-hls-16.akamaized.net/hls/live/2016499/de/high/master.m3u8",
            "https://www.zdf.de/assets/2400-zdfneo-100~768x432"
        ),
        LiveStream(
            "ZDF info",
            "http://zdf-hls-17.akamaized.net/hls/live/2016500/de/high/master.m3u8",
            "https://www.zdf.de/assets/2400-zdfinfo-100~768x432"
        )
    )

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
    private val dateTimeFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY)

    private fun getImageUrl(url: String, highQuality: Boolean): String {
        val quality = if (highQuality) IMAGE_QUALITY else THUMBNAIL_QUALITY
        return url.replace("{width}", quality.toString())
    }

    private fun Teaser.getID() = this.links?.target?.id ?: this.id
    private fun Teaser.getTags() = listOfNotNull(
        maturityContentRating?.takeIf { it != "NONE" },
        publicationService?.name,
        publicationService?.publisherType
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        try {
            val response =
                app.get("${mainUrl}/page-gateway/widgets/ard/editorials/${request.data}?pageSize=${PAGE_SIZE}&pageNumber=${page - 1}")
                    .parsed<Editorial>()

            val searchResults = if (request.name == "Jetzt Live") {
                val programInfoMap = fetchProgramInformation()

                val results = response.teasers.map { channel ->
                    // append program information (if available)
                    val programInfo = programInfoMap[channel.getID()]

                    if (programInfo != null) {
                        channel.copy(
                            title = "${programInfo.title} (${channel.title})",
                            images = programInfo.images,
                            broadcastedOn = programInfo.broadcastedOn,
                        )
                    } else channel
                }.mapNotNull { it.toSearchResponse() }

                // append external live streams
                results + extraLiveLinks.map { it.toSearchResponse() }
            } else {
                response.teasers.mapNotNull { it.toSearchResponse() }
            }

            return newHomePageResponse(
                request.copy(horizontalImages = true),
                searchResults,
                hasNext = false
            )
        } catch (e: Exception) {
            // catch exceptions if a single home page category fails
            // because that would otherwise cancel all others as well
            debugException { e.toString() }

            return newHomePageResponse(request.name, emptyList())
        }
    }

    private suspend fun fetchProgramInformation(): Map<String, Teaser> {
        val now = Calendar.getInstance().time
        val todayDate = dateFormatter.format(now)

        val programToday = app.get("$programApiUrl/program/api/program?day=${todayDate}")
            .parsedLarge<ProgramResponse>()

        val program = mutableMapOf<String, Teaser>()
        for (channel in programToday.channels) {
            val teaser = channel.timeSlots.flatten().find { teaser ->
                if (teaser.broadcastedOn == null || teaser.broadcastEnd == null) return@find false

                val start = dateTimeFormatter.parse(teaser.broadcastedOn.take(19))!!
                val end = dateTimeFormatter.parse(teaser.broadcastEnd.take(19))!!

                return@find start < now && now < end
            } ?: continue

            program[channel.crid] = teaser
        }

        return program
    }

    private fun LiveStream.toSearchResponse(): SearchResponse {
        return newLiveSearchResponse(
            name = name,
            url = this.toJson(),
            type = TvType.Live
        ) {
            this.posterUrl = thumbUrl
        }
    }

    private fun getType(coreAssetType: String?): TvType {
        return when {
            coreAssetType?.endsWith("LIVESTREAM") == true -> TvType.Live
            coreAssetType?.endsWith("SERIES") == true || coreAssetType == "SINGLE" -> TvType.TvSeries
            else -> TvType.Movie
        }
    }

    private fun Teaser.toSearchResponse(): SearchResponse? {
        // results without coreAssetType are no media items, but rather info pages, e.g. editorial pages
        if (coreAssetType == null) return null

        // external target links are not supported, e.g. from ZDF
        if (links?.target?.type?.endsWith("external") == true) return null

        val type = getType(this.coreAssetType)

        return newMovieSearchResponse(
            name = this.title ?: return null,
            url = ItemInfo(getID(), type).toJson(),
            type = type,
        ) {
            this.posterUrl = this@toSearchResponse.images.values.firstOrNull()?.src?.let {
                getImageUrl(it, false)
            }
            this.year = this@toSearchResponse.broadcastedOn?.take(4)?.toIntOrNull()
        }
    }

    private fun Teaser.toEpisode(season: Widget, index: Int, type: TvType): Episode {
        return newEpisode(ItemInfo(getID(), type)) {
            this.name = title
            this.season = season.seasonNumber?.toIntOrNull()
            this.runTime = duration?.div(60)?.toInt()
            this.episode = index
            this.posterUrl = images.values.firstOrNull()?.src?.let {
                getImageUrl(it, false)
            }
            addDate(broadcastedOn?.take(10))
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        return listOf("shows", "vods").amap { type ->
            app.get("$mainUrl/search-system/search/${type}/ard?query=${query}&pageSize=${PAGE_SIZE}&platform=MEDIA_THEK&sortingCriteria=SCORE_DESC")
                .parsed<Search>()
                .teasers
                .mapNotNull { it.toSearchResponse() }
        }.flatten()
    }

    private fun List<Teaser>.toEpisodeList(season: Widget, type: TvType): List<Episode> {
        return this.mapIndexed { index, episode ->
            episode.toEpisode(season, index + 1, type)
        }
    }

    private suspend fun getEpisodesFromSeason(season: Widget, type: TvType): List<Episode> {
        if (season.pagination == null || season.teasers.size == season.pagination.totalElements) {
            return season.teasers.toEpisodeList(season, type)
        }

        // already fetched episodes need to be re-fetched here because the page size used differs
        // by the default one used in embedded widget responses
        var pageNumber = 0
        val teasers = mutableListOf<Teaser>()
        while (teasers.size < minOf(season.pagination.totalElements, MAX_SERIES_EPISODE_COUNT)) {
            try {
                val nextPageResponse = app.get(
                    "https://api.ardmediathek.de/page-gateway/widgets/ard/asset/${season.id}?pageNumber=$pageNumber&pageSize=${MAX_PAGE_SIZE}&embedded=true"
                ).parsed<Widget>()

                teasers.addAll(nextPageResponse.teasers)
            } catch (e: Exception) {
                // stop fetching episode info on errors to avoid infinite loading times
                break
            }

            pageNumber++
        }

        return teasers.ifEmpty { season.teasers }.toEpisodeList(season, type)
    }

    private suspend fun loadSeries(url: String, seriesId: String): LoadResponse {
        val response = app.get(
            "${mainUrl}/page-gateway/pages/ard/grouping/${seriesId}?seasoned=true&embedded=true"
        )
            .parsed<GroupingResponse>()

        val type = getType(response.coreAssetType)
        val episodes = response.widgets
            .filter { it.compilationType?.startsWith("itemsOf") == true }
            .amap { season -> getEpisodesFromSeason(season, type) }
            .flatten()

        return newTvSeriesLoadResponse(
            name = response.title,
            url = url,
            type = type,
            episodes = episodes
        ) {
            this.posterUrl = (response.heroImage ?: response.image)?.src
                ?.let { getImageUrl(it, true) }
            this.plot = response.synopsis
            this.tags = listOfNotNull(
                response.publicationService?.name,
                response.publicationService?.publisherType
            )
            response.trailer?.links?.target?.id?.let {
                addTrailer(ItemInfo(it, type).toJson())
            }
        }
    }

    private suspend fun fetchEpisodeInfo(episodeId: String): DetailsResponse {
        return app.get(
            "${mainUrl}/page-gateway/pages/ard/item/${episodeId}?embedded=true&mcV6=true"
        )
            .parsed<DetailsResponse>()
    }

    private fun findPlayerInfo(widgets: List<Widget>): Widget? {
        return widgets.firstOrNull { it.type.startsWith("player") }
    }

    private suspend fun loadEpisode(url: String, episodeId: String): LoadResponse {
        val response = fetchEpisodeInfo(episodeId)

        val streamInfo = findPlayerInfo(response.widgets)
        val embedded = streamInfo?.mediaCollection?.embedded
        val type = getType(response.coreAssetType)

        // live broadcasted stream - show program information if available
        if (type == TvType.Live) {
            val programInformation = fetchProgramInformation()[episodeId]

            if (programInformation != null) {
                return newMovieLoadResponse(
                    name = programInformation.title?.let { "$it (${response.title})" }
                        ?: response.title,
                    url = url,
                    type = type,
                    data = embedded?.toJson()
                ) {
                    this.posterUrl = programInformation.images.values.firstOrNull()?.src?.let {
                        getImageUrl(it, true)
                    }
                    this.plot = programInformation.synopsis
                    this.tags = programInformation.getTags()
                }
            }
        }

        return newMovieLoadResponse(
            name = response.title,
            url = url,
            type = type,
            data = embedded?.toJson()
        ) {
            this.posterUrl = streamInfo?.image?.src?.let { getImageUrl(it, true) }
            this.plot = streamInfo?.synopsis
            this.duration = embedded?.meta?.durationSeconds?.div(60)?.toInt()
            this.comingSoon = embedded?.streams?.isEmpty() == true
            this.year = streamInfo?.broadcastedOn?.take(4)?.toIntOrNull()
            this.tags = listOfNotNull(
                streamInfo?.maturityContentRating?.takeIf { it != "NONE" },
                streamInfo?.publicationService?.name,
                streamInfo?.publicationService?.publisherType
            )
        }
    }

    private suspend fun liveStreamLoadResponse(liveStream: LiveStream): LoadResponse {
        return newLiveStreamLoadResponse(
            name = liveStream.name,
            url = liveStream.url,
            dataUrl = liveStream.toJson()
        ) {
            this.posterUrl = liveStream.thumbUrl
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val liveStream = tryParseJson<LiveStream>(url)?.takeIf { it.url.isNotBlank() }
        if (liveStream != null) {
            return liveStreamLoadResponse(liveStream)
        }

        val (id, type) = parseJson<ItemInfo>(url)

        return when (type) {
            TvType.TvSeries -> loadSeries(url, id)
            else -> loadEpisode(url, id)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // JSON data can either be of type Embedded, LiveStream or ItemInfo

        val liveStream = tryParseJson<LiveStream>(data)?.takeIf { it.url.isNotBlank() }
        if (liveStream != null) {
            callback.invoke(
                newExtractorLink(
                    source = liveStream.name,
                    name = liveStream.name,
                    url = liveStream.url
                ) {
                    referer = "$mainUrl/"
                    quality = Qualities.P1080.value
                }
            )
            return true
        }

        var embedded = tryParseJson<Embedded>(data)?.takeIf { it.meta != null }
        if (embedded == null) {
            val (id, _) = parseJson<ItemInfo>(data)

            embedded = findPlayerInfo(fetchEpisodeInfo(id).widgets)?.mediaCollection?.embedded
        }

        if (embedded == null) throw IllegalArgumentException("Couldn't find episode info!")

        for (stream in embedded.streams) {
            for (media in stream.media) {
                callback.invoke(
                    newExtractorLink(
                        name = "ARD ${media.forcedLabel} (${stream.kind})",
                        source = "ARD",
                        url = media.url
                    ) {
                        quality = media.maxVresolutionPx?.toInt() ?: Qualities.Unknown.value
                        referer = mainUrl
                    }
                )
            }
        }

        for (subtitle in embedded.subtitles) {
            for (source in subtitle.sources) {
                subtitleCallback.invoke(
                    SubtitleFile(
                        lang = subtitle.languageCode.orEmpty(),
                        url = source.url ?: continue
                    )
                )
            }
        }

        return embedded.streams.isNotEmpty()
    }

    // used internally in this implementation
    data class ItemInfo(
        val id: String,
        val type: TvType
    )

    data class LiveStream(
        val name: String,
        val url: String,
        val thumbUrl: String,
    )

    // ARD JSON response objects start here
    data class Search(
        val id: String,
        val teasers: List<Teaser> = emptyList(),
        val title: String,
        val type: String,
    )

    data class Teaser(
        val availableTo: String?,
        val broadcastedOn: String?,
        val broadcastEnd: String?,
        val duration: Double?,
        val id: String,
        val images: Map<String, Image> = emptyMap(),
        @JsonProperty("title")
        @JsonAlias("shortTitle")
        val title: String?,
        val show: Show?,
        val links: Links?,
        val coreAssetType: String?,
        val synopsis: String?,
        val maturityContentRating: String?,
        @JsonProperty("publicationService")
        @JsonAlias("channel")
        val publicationService: PublicationService?
    )

    data class PublicationService(
        val name: String? = null,
        val publisherType: String? = null
    )

    data class Links(
        val target: Link?
    )

    data class Link(
        val id: String?,
        val title: String?,
        val type: String?
    )

    data class Show(
        val id: String,
        val title: String,
        val shortSynopsis: String?,
        val synopsis: String?,
        val longSynopsis: String?,
        val coreId: String?,
        val groupingType: String?,
        val hasSeasons: Boolean?,
    )

    data class DetailsResponse(
        val fskRating: String?,
        val id: String?,
        val title: String,
        val widgets: List<Widget> = emptyList(),
        val coreAssetType: String?
    )

    data class Widget(
        val availableTo: String?,
        val blockedByFsk: Boolean?,
        val broadcastedOn: String?,
        val embeddable: Boolean?,
        val geoblocked: Boolean?,
        val id: String,
        val image: Image?,
        val maturityContentRating: String?,
        val mediaCollection: MediaCollection?,
        val show: ShowShort?,
        val synopsis: String?,
        val title: String,
        val type: String,
        val compilationType: String?,
        val size: String?,
        val seasonNumber: String?,
        val publicationService: PublicationService?,
        val teasers: List<Teaser> = emptyList(),
        val pagination: Pagination?
    )

    data class Image(
        val src: String?,
        val title: String?,
    )

    data class MediaCollection(
        val embedded: Embedded,
        val href: String,
    )

    data class Embedded(
        val id: String?,
        val isGeoBlocked: Boolean = false,
        val meta: Meta?,
        // only included if mcV6 is set to false in request query parameters
        @JsonProperty("_mediaArray") val mediaArray: List<MediaStreamArray> = emptyList(),
        val streams: List<Stream> = emptyList(),
        val subtitles: List<Subtitle> = emptyList(),
    )

    data class MediaStreamArray(
        @JsonProperty("_mediaStreamArray") val mediaStreamArray: List<MediaStream>
    )

    data class MediaStream(
        @JsonProperty("_quality") val quality: String?,
        @JsonProperty("_stream") val stream: String,
    )

    data class Meta(
        val availableToDateTime: String?,
        val broadcastedOnDateTime: String?,
        val clipSourceName: String?,
        val durationSeconds: Long?,
        val seriesTitle: String?,
        val synopsis: String?,
        val title: String?,
    )

    data class Stream(
        val kind: String?,
        val kindName: String?,
        val media: List<Medum> = emptyList(),
        val videoLanguageCode: String?,
    )

    data class Medum(
        val aspectRatio: String?,
        val audios: List<Audio> = emptyList(),
        val forcedLabel: String?,
        val isAdaptiveQualitySelectable: Boolean = false,
        val isHighDynamicRange: Boolean = false,
        @JsonProperty("maxHResolutionPx")
        val maxHresolutionPx: Long?,
        @JsonProperty("maxVResolutionPx")
        val maxVresolutionPx: Long?,
        val mimeType: String? = null,
        val url: String,
        val videoCodec: String? = null,
    )

    data class Audio(
        val kind: String?,
        val languageCode: String?,
    )

    data class Subtitle(
        val kind: String?,
        val languageCode: String?,
        val sources: List<Source> = emptyList(),
    )

    data class Source(
        val kind: String?,
        val url: String?,
    )

    data class ShowShort(
        val id: String,
        val title: String,
        val image: Image?,
    )

    data class Editorial(
        @JsonProperty("aZContent")
        val aZcontent: Boolean,
        val compilationType: String,
        val id: String,
        val title: String,
        val size: String?,
        val teasers: List<Teaser> = emptyList(),
        val type: String?,
    )

    data class GroupingResponse(
        val id: String?,
        val title: String,
        val synopsis: String?,
        val heroImage: Image?,
        val image: Image?,
        val widgets: List<Widget> = emptyList(),
        val coreAssetType: String?,
        val trailer: Trailer?,
        val publicationService: PublicationService?
    )

    data class Trailer(
        val duration: Long?,
        val links: Links?,
        val title: String?,
        val synopsis: String?,
    )

    data class Pagination(
        val pageNumber: Int,
        val pageSize: Int,
        val totalElements: Int
    )

    data class ProgramResponse(
        val links: Links,
        val channels: List<Channel>
    )

    data class Channel(
        val id: String,
        val crid: String, // equals a normal ARD Mediathek urlid
        val timeSlots: List<List<Teaser>>,
    )

    companion object {
        private const val PAGE_SIZE = 30
        private const val IMAGE_QUALITY = 1080
        private const val THUMBNAIL_QUALITY = 480
        private const val MAX_PAGE_SIZE = 200
        private const val MAX_SERIES_EPISODE_COUNT = 500
    }
}