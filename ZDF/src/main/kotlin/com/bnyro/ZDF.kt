package com.bnyro

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class ZDF : MainAPI() {
    override var name: String = "ZDF"
    override var mainUrl: String = "https://api.zdf.de"
    private val webUrl: String = "https://www.zdf.de"

    override var lang: String = "de"
    override val supportedTypes: Set<TvType> = setOf(
        TvType.TvSeries, TvType.Movie, TvType.Live,
        TvType.Documentary
    )
    override val hasMainPage: Boolean = true
    override val mainPage: List<MainPageData> = mainPageOf(
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#8c3f3656-ceff-48ed-a199-9e23c5a3d135_3f5404d7-380a-4a2b-adbc-21a3925cfa20" to "Livestreams",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#c3bd5ea7-9a49-49af-9d8c-0e800d0b10ab_b5f43a19-4785-425d-9732-f1866999bda2" to "Top-Serien",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#c332da65-84f3-4a20-b862-0416ca6f48e3_718e77ea-5493-4668-a0aa-39a216ff33aa" to "Packende Krimis",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#50a88aff-2eda-4a44-95b6-eba03ebe74da_7b8cb6c9-657b-4bfe-9b18-3388fed98fed" to "Romance und Leidenschaft",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#e2cd45fe-7d46-4725-9e7e-2350cad76e42_a5cc8c7c-2105-4148-9fdb-6f1d1b1f806f" to "Geschichte und Wissenschaft",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#07e33858-ddbf-4699-bfa7-9914c6a6ca7c_f11a163b-8bf1-4265-8dfa-6e33ff31d6dc" to "Politik & True-Crime",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#25e43d92-d2ec-42a9-b20a-57af31b38a29_dc77af8e-5ebe-4017-a3db-e07eec1273e0" to "Stars, Reisen und Schicksale",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#1ea2bf43-cb15-47cd-94e2-9947f44f1ba6_2259dc98-0a87-4438-980a-8c780750488f" to "Spielfilm-Highlights",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#dbd1feb3-1dac-48a6-90ac-bc570c628079_607c94f3-0e35-4f9a-a947-7dabf4424597" to "Ausgezeichnete Filme und Arthouse-Kino",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#8c3f3656-ceff-48ed-a199-9e23c5a3d135_fc5b931e-b4bd-4756-a94f-646dcc09c5a2" to "Nachrichten und Politik",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#f853f497-6707-4246-acd2-e8fc090001d7_c9a8a21b-2d6e-4249-8d1c-c9b3ed81428b" to "Themen, die bewegen",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#dc7cd85a-a9ba-4724-8eba-0a3f5541d21d_1652a5ea-0c7a-410e-b197-e3eef43eefe9" to "Unterhaltung und Shows",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#61c022ff-3dd3-4770-8b82-e24cd06009f3_983f6e20-cd53-4e38-8a44-2f603369bcfe" to "Comedy und Satire",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#ebe2c320-828c-4288-bea5-4413ee474cd6_302dbf4d-c799-4a15-86cb-61317bbce150" to "Deutscher Film",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#3a72780d-5a3d-4d8a-945e-a0dabb2bbc5f_5e5b3ba5-5cd6-4a17-8228-173be9ade1c7" to "Kultserien und Filmklassiker aus dem Retro-Archiv",
        "curated_collection#8c3f3656-ceff-48ed-a199-9e23c5a3d135#7bc735ac-2c59-4785-a889-7b3ceb2332f6_63542e40-e4ae-4d63-bd5e-fce86cde4b79" to "Die besten Filme, Serien und Shows für Kids"
    )

    // hasn't changed since https://github.com/mediathekview/MServer/issues/1039
    private val apiToken = "aa3noh4ohz9eeboo8shiesheec9ciequ9Quah7el"

    val headers = mapOf(
        "api-auth" to "Bearer $apiToken",
        "Origin" to webUrl,
        "Content-Type" to "application/json"
    )

    sealed class Operation {
        class PersistedQuery(val sha256Hash: String) : Operation()
        class FullQuery(val query: String) : Operation()
    }

    private suspend inline fun <reified T : Any> getGraphQL(
        operationName: String,
        operation: Operation,
        variables: Any
    ): T {
        val params = mutableMapOf(
            "operationName" to operationName,
            "variables" to variables,
        )
        when (operation) {
            is Operation.PersistedQuery -> params["extensions"] =
                GraphQLExtensions(persistedQuery = PersistedQuery(sha256Hash = operation.sha256Hash))

            is Operation.FullQuery -> params["query"] = operation.query
        }

        return app.post("$mainUrl/graphql", headers = headers, json = params).parsed<T>()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // TODO: dynamically load list of collections from homepage

        val response = getGraphQL<MetaCollectionsResp>(
            "staticHorizontalClusterItems",
            // Web
            Operation.PersistedQuery("62cc1e4ebdbe1d80a5b9b867d961dea3e9637412608c42c242aa4fcb4e9d4012"),
            HomePageVariables(moduleFilter = ModuleFilter(listOf(request.data)))
        )

        val items = response.data.curatedCollectionByCanonical.modules.nodes.flatMap {
            it.items.nodes.filter { node -> node.canonical != null }
                .map { node -> node.toSearchResponse() }
        }
        return newHomePageResponse(request, items, hasNext = false)
    }

    fun SmartCollection.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = title,
            url = "$webUrl/$canonical"
        ) {
            this.posterUrl = teaser?.imageWithoutLogo?.layouts?.values?.firstOrNull()
            this.year = editorialDate?.take(4)?.toIntOrNull()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = getGraphQL<SearchResp>(
            "getSearchResults",
            // Web
            Operation.PersistedQuery("5f8ae3abb4189c371a3297f8e669c755d08e35b491ebe37bf9e6e34a742518d9"),
            SearchVariables(query = query, mode = "ALL_RESULTS_INCLUDING_TOP_RESULTS", first = 24)
        )

        return response.data.searchDocuments.results.map { it.item.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val slug = url.substringAfterLast("/")

        val info = try {
            getGraphQL<SmartCollectionResponse>(
                "GetSmartCollectionByCanonical",
                // Android app
                Operation.PersistedQuery("f9cd12864be90dd97d015b59fb515ea9d464b14db543ae349fc474a96e49de0b"),
                mapOf("canonical" to slug)
            ).data.smartCollectionByCanonical
        } catch (e: Exception) {
            // plain video, no collection
            val videoInfo = getVideoInfo(slug)
            return newMovieLoadResponse(
                name = videoInfo.title.orEmpty(),
                url = url,
                type = TvType.Movie,
                data = url
            ) {
                this.plot = videoInfo.teaser.description
                this.posterUrl =
                    videoInfo.teaser.imageWithoutLogo?.layouts?.values?.lastOrNull { it != null }
                this.year = videoInfo.productionYear
            }
        }
        val image = info.imageWithoutLogo?.layouts?.values?.firstOrNull()

        if (info.video != null) {
            return newMovieLoadResponse(
                name = info.title,
                url = url,
                type = TvType.Movie,
                dataUrl = "$webUrl/${info.video.canonical}"
            ) {
                posterUrl = image
                plot = info.infoText
                year = info.productionYear
            }
        } else {
            val episodes = info.seasons?.seasons.orEmpty().flatMap { season ->
                season.episodes.videos.map { episode ->
                    newEpisode("$webUrl/${episode.canonical}") {
                        this.name = episode.title ?: episode.teaser.title
                        this.season = season.number.toInt()
                        this.posterUrl =
                            episode.teaser.imageWithoutLogo?.layouts?.values?.lastOrNull {
                                it != null
                            }
                        this.description = episode.leadingParagraph ?: episode.teaser.description
                    }
                }
            }

            return newTvSeriesLoadResponse(
                name = info.title,
                url = url,
                type = TvType.TvSeries,
                episodes = episodes
            ) {
                this.plot = info.infoText
                this.posterUrl = image
            }
        }
    }

    private suspend fun getVideoInfo(slug: String): VideoByCanonical {
        return getGraphQL<VideoMeta>(
            "GetPlayableVideoByCanonical",
            // Android app
            Operation.PersistedQuery("f523709f46392d91135ab82fa5fe03390c4d052e83270ed2c3a8d8e1e0995489"),
            VideoMetaVariables(slug)
        ).data.videoByCanonical
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val slug = data.substringAfterLast("/")
        val videoInfo = getVideoInfo(slug)

        val template = videoInfo.currentMedia.nodes.firstOrNull()?.ptmdTemplate
        // player version is at https://ngp.zdf.de/configs/zdf/zdfmt25/configuration.json
        val playerUrl = mainUrl + template?.replace("{playerId}", "ngplayer_2_5")

        val response = app.get(playerUrl, headers = headers)
            .parsed<StreamPlayer>()

        for (caption in response.captions) {
            subtitleCallback.invoke(newSubtitleFile(lang = caption.language, url = caption.uri))
        }

        for (stream in response.priorityList.flatMap { it.formitaeten }) {
            for (quality in stream.qualities) {
                for (track in quality.audio.tracks) {
                    callback.invoke(
                        newExtractorLink(source = name, name = name, url = track.uri) {
                            this.quality = quality.highestVerticalResolution.toInt()
                        }
                    )
                }
            }
        }

        return true
    }

    data class GraphQLExtensions(
        val clientLibrary: ClientLibrary = ClientLibrary(),
        val persistedQuery: PersistedQuery,
    )

    data class ClientLibrary(
        val name: String = "@apollo/client",
        val version: String = "4.1.9",
    )

    data class PersistedQuery(
        val version: Long = 1,
        val sha256Hash: String,
    )

    data class HomePageVariables(
        val canonical: String = "mt2025-startseite-100",
        val moduleFilter: ModuleFilter,
        val itemsFilter: ItemsFilter = ItemsFilter(),
    )

    data class ModuleFilter(
        val moduleIdIn: List<String>,
    )

    data class ItemsFilter(
        val teaserUsageNotIn: List<String> = listOf("TIVI_HBBTV_ONLY"),
    )

    data class SearchVariables(
        val query: String,
        val mode: String,
        val group: String = "gruppe-b",
        val first: Int,
        val after: String? = null,
    )

    data class MetaCollectionsResp(
        val data: MetaCollectionsData,
    )

    data class MetaCollectionsData(
        val curatedCollectionByCanonical: CuratedCollectionByCanonical,
    )

    data class CuratedCollectionByCanonical(
        val modules: Modules,
    )

    data class Modules(
        val nodes: List<Module>,
    )

    data class Module(
        val id: String,
        val items: Items,
    )

    data class Items(
        val totalCount: Long,
        val nodes: List<SmartCollection>,
    )

    data class SearchResp(val data: SearchData)
    data class SearchData(val searchDocuments: SearchDocuments)
    data class SearchDocuments(val results: List<SearchResult>)
    data class SearchResult(val item: SmartCollection)

    data class SmartCollection(
        val id: String,
        val title: String,
        val canonical: String?,
        val infoText: String?,
        val collectionType: String?,
        val editorialDate: String?,
        val productionYear: Int?,
        val endDate: String?,
        val isCampaign: Boolean?,
        val logo: Image?,
        val imageWithoutLogo: Image?,
        val teaser: Teaser?,
        val streamingOptions: StreamingOptions?,
        val newContentStatus: String?,
        val structuralMetadata: StructuralMetadata?,
        val heroTrailers: HeroTrailers?,
        val countSeasons: Long?,
        val countEpisodes: Long?,
        val recoModel: String?,
        val video: Video?,
        val metaType: String?,
        val seasons: Seasons?,
    )

    data class Image(
        val layouts: Map<String, String>?,
    )

    data class Teaser(
        val title: String?,
        val description: String?,
        val imageWithoutLogo: Image?,
        val sourceVariant: String?,
        val enableAudioDescription: Boolean?,
        val enableSubtitle: Boolean?,
    )

    data class StreamingOptions(
        val ad: Boolean,
        val dgs: Boolean,
        val ut: Boolean,
        val ks: Boolean,
        val ov: Boolean,
        val fskMetaCollection: MetaCollection,
    )

    data class StructuralMetadata(
        val isChildrenContent: Boolean,
        val publicationFormMetaCollection: MetaCollection?,
        val genreMetaCollection: MetaCollection?,
    )

    data class MetaCollection(
        val title: String,
    )

    data class HeroTrailers(
        val nodes: List<Video>,
    )

    data class Availability(
        val fskBlocked: Boolean,
        val vod: Vod,
    )

    data class Vod(
        val fsk: String,
    )

    data class CurrentMedia(
        val nodes: List<StreamTemplate>,
    )

    data class StreamTemplate(
        val ptmdTemplate: String?,
        val vodMediaType: String?,
        val duration: Long?,
        val downloadAllowed: Boolean?,
        val geoLocation: String?,
    )

    data class Video(
        val id: String,
        val canonical: String,
        val structuralMetadata: StructuralMetadata,
        val availability: Availability,
        val currentMedia: CurrentMedia,
    )

    data class VideoMetaVariables(
        val canonical: String
    )

    data class VideoMeta(
        val data: VideoMetaData,
    )

    data class VideoMetaData(
        val videoByCanonical: VideoByCanonical,
    )

    data class VideoByCanonical(
        val id: String,
        val canonical: String,
        val episodeInfo: EpisodeInfo,
        val title: String?,
        val leadingParagraph: String?,
        val teaser: Teaser,
        val embeddingPossible: Boolean,
        val structuralMetadata: StructuralMetadata,
        val availability: Availability,
        val currentMedia: CurrentMedia,
        val productionYear: Int?,
    )

    data class EpisodeInfo(
        val episodeNumber: Long,
        val seasonNumber: Long,
    )

    data class StreamPlayer(
        val basename: String?,
        val captions: List<Caption>,
        val documentVersion: Long,
        val mandant: String,
        val playerId: String,
        val priorityList: List<PriorityList>,
    )

    data class Duration(
        val profile: String,
        val value: Long,
    )

    data class Caption(
        @JsonProperty("class")
        val class_field: String,
        val format: String,
        val language: String,
        val offset: Long,
        val profile: String,
        val uri: String,
    )

    data class PriorityList(
        val formitaeten: List<Formitaeten>,
        val profile: String,
    )

    data class Formitaeten(
        val facets: List<String>,
        val isAdaptive: Boolean,
        val mimeType: String,
        val profile: String,
        val qualities: List<Quality>,
        val type: String,
    )

    data class Quality(
        val audio: Audio,
        val hd: Boolean,
        val highestVerticalResolution: Long,
        val mimeCodec: String?,
        val profile: String,
        val quality: String,
    )

    data class Audio(
        val profile: String,
        val tracks: List<Track>,
    )

    data class Track(
        @JsonProperty("class")
        val class_field: String,
        val language: String,
        val profile: String,
        val uri: String,
    )

    data class Seasons(
        val seasons: List<Season>,
    )

    data class Season(
        val title: String,
        val number: Long,
        val countEpisodes: Long?,
        val episodes: Episodes,
    )

    data class Episodes(
        val videos: List<VideoByCanonical>,
    )

    data class SmartCollectionResponse(
        val data: SmartCollectionData,
    )

    data class SmartCollectionData(
        val smartCollectionByCanonical: SmartCollection,
    )
}
