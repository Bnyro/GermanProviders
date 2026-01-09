// Based on https://codeberg.org/cloudstream/cloudstream-extensions-multilingual/


// use an integer for version numbers
version = 2


cloudstream {
    // All of these properties are optional, you can safely remove them

    authors = listOf("Adippe", "Phisher98", "Bnyro")
    description = "Collection of publicly available IPTV channels from all over the world"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Live",
    )

    iconUrl = "https://avatars.githubusercontent.com/iptv-org?s=%size%"
}
