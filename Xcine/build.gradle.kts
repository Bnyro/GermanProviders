// use an integer for version numbers
version = 12


cloudstream {
    language = "de"
    // All of these properties are optional, you can safely remove them

    description = "Enthält: XcineIO, Movie4k, Streamcloud, Kinokiste"
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
        "TvSeries",
        "Movie",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=https://xcine.io&sz=%size%"
}
