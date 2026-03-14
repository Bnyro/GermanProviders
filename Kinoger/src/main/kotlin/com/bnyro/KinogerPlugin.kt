
package com.bnyro

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KinogerPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Kinoger())
        registerExtractorAPI(KinogerRu())
        registerExtractorAPI(KinogerBe())
        registerExtractorAPI(KinogerRe())
        registerExtractorAPI(KinogerP2PPlay())
    }
}