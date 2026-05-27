package com.bnyro

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.loadExtractor

class MeineCloudClickExtractor : ExtractorApi() {
    override val name: String = "MeineCloud"
    override val mainUrl: String = "https://meinecloud.click"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val dom = app.get(url, referer = referer).document
        val links = dom.select("._player-mirrors li")
            .map { fixUrl(it.attr("data-link")) }

        links.amap {
            loadExtractor(it, subtitleCallback, callback)
        }
    }
}