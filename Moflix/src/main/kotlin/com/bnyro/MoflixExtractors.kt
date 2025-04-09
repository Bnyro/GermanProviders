package com.bnyro

import com.lagradost.cloudstream3.extractors.Chillx
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.Vidguardto

class MoflixFans : Chillx() {
    override val name = "MoflixFans"
    override val mainUrl = "https://moflix-stream.fans"
}

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