
package com.bnyro

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.DoodToExtractor
import com.lagradost.cloudstream3.extractors.DoodstreamCom
import com.lagradost.cloudstream3.extractors.MixDropAg
import com.lagradost.cloudstream3.extractors.Supervideo

@CloudstreamPlugin
class HDFilmePlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(HDFilme())

        registerExtractorAPI(DoodstreamCom())
        registerExtractorAPI(SupervideoCom())
        registerExtractorAPI(SupervideoTv())
        registerExtractorAPI(Supervideo())
        registerExtractorAPI(DroploadIo())
        registerExtractorAPI(MixDropAg())
        registerExtractorAPI(DoodToExtractor())
        registerExtractorAPI(DropZeroStream())
    }
}