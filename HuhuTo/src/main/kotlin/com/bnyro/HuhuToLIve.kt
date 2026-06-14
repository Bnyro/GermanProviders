package com.bnyro

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.newExtractorLink

class HuhuToLive : MainAPI() {
    override var name: String = "Huhu Live"
    override var mainUrl: String = "https://huhu.to"
    override var lang: String = "de"
    override val hasMainPage: Boolean = true

    private var cachedChannels: Channels? = null

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val channels = cachedChannels ?: run {
            app.get("$mainUrl/channels").parsed<Channels>().also { cachedChannels = it }
        }

        val homePageLists = channels.groupBy { it.country }.map { (country, channels) ->
            HomePageList(
                name = country,
                list = channels.sortedBy { it.name.lowercase() }.map {
                    newLiveSearchResponse(
                        name = it.name,
                        url = it.toJson(),
                        fix = false
                    )
                }
            )
        }

        return newHomePageResponse(homePageLists, hasNext = false)
    }

    override suspend fun load(url: String): LoadResponse? {
        val channel = AppUtils.parseJson<Channel>(url)
        val url = "$mainUrl/play/${channel.id}/index.m3u8"

        return newLiveStreamLoadResponse(
            name = channel.name,
            url = url,
            dataUrl = url,
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // gets bot-blocked otherwise
        val headers =
            mapOf("User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:151.0) Gecko/20100101 Firefox/151.0")

        M3u8Helper.generateM3u8(
            source = "Huhu",
            streamUrl = data,
            referer = "https://huhu.to/",
            headers = headers
        ).forEach {
            callback.invoke(it)
        }

        callback.invoke(
            newExtractorLink(
                source = "Huhu",
                name = "Huhu",
                url = data,
                type = ExtractorLinkType.M3U8
            ) {
                this.headers = headers
            }
        )

        return true
    }

    class Channels : ArrayList<Channel>();
    data class Channel(
        val country: String,
        val id: Long,
        val name: String,
        val p: Long,
    )
}
