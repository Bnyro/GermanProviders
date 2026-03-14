// use an integer for version numbers
version = 5


cloudstream {
    language = "de"
    // All of these properties are optional, you can safely remove them

    description = "Öffentlich-rechtliche Rundfunkanstalten"
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
        "Live"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=https://ardmediathek.de&sz=%size%"
}
