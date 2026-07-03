// use an integer for version numbers
version = 2


cloudstream {
    language = "de"
    // All of these properties are optional, you can safely remove them

    description = "Viel mehr als TV"
    authors = listOf("Bnyro")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Movie",
        "TvSeries",
    )

    iconUrl = "https://static-cdn.arte.tv/replay/favicons/favicon-%size%x%size%.png"
}
