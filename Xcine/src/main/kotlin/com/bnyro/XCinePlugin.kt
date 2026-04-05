
package com.bnyro

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.DoodLiExtractor
import com.lagradost.cloudstream3.extractors.DoodToExtractor
import com.lagradost.cloudstream3.extractors.DoodWsExtractor
import com.lagradost.cloudstream3.extractors.FileMoonSx
import com.lagradost.cloudstream3.extractors.Lulustream2
import com.lagradost.cloudstream3.extractors.MixDropAg
import com.lagradost.cloudstream3.extractors.MixDropTo
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.Supervideo
import com.lagradost.cloudstream3.extractors.Voe

@CloudstreamPlugin
class XCinePlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(XcineRU())
        registerMainAPI(Movie4k())
        registerMainAPI(Streamcloud())
        registerMainAPI(KinoKisteClub())

        registerExtractorAPI(Voe())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(MixDropPs())
        registerExtractorAPI(MixDropAg())
        registerExtractorAPI(MixDropTo())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(Supervideo())
        registerExtractorAPI(DoodToExtractor())
        registerExtractorAPI(Dooodster())
        registerExtractorAPI(DoodToExtractor())
        registerExtractorAPI(DoodsPro())
        registerExtractorAPI(DoodRe())
        registerExtractorAPI(DoodWsExtractor())
        registerExtractorAPI(DoodLiExtractor())
        registerExtractorAPI(SupervideoTv())
        registerExtractorAPI(DroploadIo())
        registerExtractorAPI(GoofyBanana())
        registerExtractorAPI(Lulustream2())
        registerExtractorAPI(Mixdrp())
        registerExtractorAPI(Luluvdo())
        registerExtractorAPI(Ryderjet())
        registerExtractorAPI(StreamRuby())
        registerExtractorAPI(SaveFiles())
    }
}