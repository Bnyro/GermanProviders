package com.bnyro

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class HuhuToExtractor() : ExtractorApi() {
    override val name: String = "Huhu"
    override val mainUrl: String = "https://huhu.to"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val redirectedUrl = app.get(url, allowRedirects = true).url

        loadExtractor(redirectedUrl, subtitleCallback, callback)
    }
}
