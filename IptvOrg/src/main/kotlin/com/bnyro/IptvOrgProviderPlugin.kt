package com.bnyro

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class IptvOrgProviderPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(IptvOrgProvider())

        // list of all supported languages: https://github.com/iptv-org/iptv/blob/master/PLAYLISTS.md
        registerMainAPI(IptvOrgLanguageProvider("deu"))
    }
}