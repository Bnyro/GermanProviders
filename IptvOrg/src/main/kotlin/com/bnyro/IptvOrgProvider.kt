package com.bnyro

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newDrmExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.UUID

open class IptvOrgLanguageProvider(iptvLanguageCode: String) : IptvOrgProvider() {
    override var lang = iptvLanguageCode.take(2)
    override var name = "IPTV-Org ($iptvLanguageCode)"
    override var mainUrl = "https://iptv-org.github.io/iptv/languages/$iptvLanguageCode.m3u"
}

open class IptvOrgProvider : MainAPI() {
    override var mainUrl = "https://iptv-org.github.io/iptv/index.m3u"
    override var name = "IPTV-Org (all)"
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Live,
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val data = IptvPlaylistParser().parseM3U(app.get(mainUrl).textLarge)

        return newHomePageResponse(data.items.groupBy { it.groupTitle }
            .map { group ->
                val show = group.value.map { channel ->
                    newLiveSearchResponse(
                        channel.title,
                        channel.toJson(),
                        TvType.Live
                    ) {
                        this.posterUrl = channel.logo
                    }
                }
                HomePageList(
                    group.key.orEmpty(),
                    show,
                    isHorizontalImages = true
                )
            })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = IptvPlaylistParser().parseM3U(app.get(mainUrl).textLarge)

        return data.items.filter { it.title.contains(query, ignoreCase = true) }
            .map { channel ->
                newLiveSearchResponse(
                    channel.title,
                    channel.toJson(),
                    TvType.Live
                ) {
                    this.posterUrl = channel.logo
                }
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<PlaylistItem>(url)

        return newLiveStreamLoadResponse(data.title, data.url, url)
        {
            this.posterUrl = data.logo
            this.plot = data.groupTitle
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<PlaylistItem>(data)

        if (loadData.url.contains("mpd")) {
            callback.invoke(
                newDrmExtractorLink(
                    this.name,
                    this.name,
                    loadData.url,
                    INFER_TYPE,
                    UUID.randomUUID()
                ) {
                    this.referer = loadData.referer.orEmpty()
                    this.quality = Qualities.Unknown.value
                    this.key = loadData.key.orEmpty().trim()
                    this.kid = loadData.keyid.orEmpty().trim()
                }
            )
        } else if (loadData.url.contains("&e=.m3u")) {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = loadData.url,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = loadData.referer.orEmpty()
                    this.quality = Qualities.Unknown.value
                }
            )
        } else {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    loadData.title,
                    url = loadData.url,
                    INFER_TYPE
                ) {
                    this.referer = loadData.referer.orEmpty()
                    this.quality = Qualities.Unknown.value
                }
            )
        }
        return true
    }
}
