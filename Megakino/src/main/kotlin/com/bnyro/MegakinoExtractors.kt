package com.bnyro

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

open class Gxplayer : ExtractorApi() {
    override var name = "Gxplayer"
    override var mainUrl = "https://watch.gxplayer.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val jsonString = app.get(url, referer = mainUrl).text
            .substringAfter("var video = ").substringBefore(";")
        val video = parseJson<Details>(jsonString)

        M3u8Helper.generateM3u8(
            this.name,
            "$mainUrl/m3u8/${video.uid}/${video.md5}/master.txt?s=1&id=${video.id}&cache=${video.status}",
            referer = "$mainUrl/",
        ).forEach(callback)
    }

    data class Details(
        val id: String,
        val uid: String,
        val slug: String,
        val title: String,
        val folderid: Any?,
        val quality: String,
        val sources: Any?,
        val type: String,
        val userlinkhost: String,
        val poster: String,
        val subtitles: Any?,
        val added: String,
        val updatedtime: String,
        val status: String,
        val errorcount: String,
        val progressbar: String,
        val progress: String,
        val views: String,
        val md5: String,
    )
}
