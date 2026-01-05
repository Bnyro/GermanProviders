package com.bnyro

import android.content.Context
import com.lagradost.cloudstream3.extractors.DoodPmExtractor
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MegakinoProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Megakino())
        registerExtractorAPI(Voe())
        registerExtractorAPI(DoodPmExtractor())
    }
}