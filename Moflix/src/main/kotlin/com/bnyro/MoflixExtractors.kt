package com.bnyro

import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.extractors.ByseSX
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.Vidguardto

class MoflixDay : Vidguardto() {
    override val name = "MoflixDay"
    override val mainUrl = "https://moflix-stream.day"
}

class MoflixUpns : VidStack() {
    override var name: String = "Moflix UPNS"
    override var mainUrl: String = "https://moflix.upns.xyz"
}

class MoflixRpmplay : VidStack() {
    override var name: String = "Moflix UPNS"
    override var mainUrl: String = "https://moflix.rpmplay.xyz"
}

open class MoflixClick : VidHidePro() {
    override val name = "MoflixClick"
    override val mainUrl = "https://moflix-stream.click"
}

@Prerelease
open class MoflixLink : ByseSX() {
    override var name = "MoflixLink"
    override var mainUrl = "https://moflix-stream.link"
}
