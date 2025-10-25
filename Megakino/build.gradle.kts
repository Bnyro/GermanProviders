version = 5


cloudstream {
    language = "de"

    // All of these properties are optional, you can safely remove them
    description = "Kostenlos Filme und Serien auf Deutsch anschauen"
    authors = listOf("Phisher98", "Bnyro")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of available types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf("Movie", "TvSeries", "Documentary")
    iconUrl = "https://www.google.com/s2/favicons?domain=https://megakino.tube&sz=%size%"
}
