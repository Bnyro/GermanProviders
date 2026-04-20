// use an integer for version numbers
version = 2


cloudstream {
    language = "de"
    // All of these properties are optional, you can safely remove them

    description = "Videos von Welt (mit Vorsicht zu genießen: gehört zum Axel Springer Verlag)"
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
        "Documentary"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=https://www.welt.de&sz=%size%"
}
