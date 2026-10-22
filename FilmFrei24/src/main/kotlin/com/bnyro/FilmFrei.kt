package com.bnyro

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink

// TODO: series support
class FilmFrei : MainAPI() {
    override var name: String = "FilmFrei24"
    override var mainUrl: String = "https://filmfrei24.com"
    override val hasMainPage: Boolean = true
    override val supportedTypes: Set<TvType> = setOf(TvType.Movie)
    override var lang: String = "de"

    private suspend fun loadCatalog(): Catalog {
        return app.get("$mainUrl/beta/api.php?q=catalog&part=core")
            .parsed<Catalog>()
    }

    private fun Film.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse(
            name = title,
            url = "$mainUrl/#/play/f/$title",
            type = TvType.Movie
        ) {
            posterUrl = fixImageUrl(card)
            year = this@toSearchResponse.year.toIntOrNull()
        }
    }

    private fun fixImageUrl(url: String): String {
        // const b64u = x => btoa(x).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
        // const unpack = v => { if (!v || v[0] !== '~') return v; const i = v.lastIndexOf('|'); return MAIN + '/api/image.php?u=' + b64u('https://image.tmdb.org/t/p/' + v.slice(1, i)) + '&w=' + v.slice(i + 1); };
        if (url[0] != '~' || !url.contains("|")) return url

        val (path, resolution) = url.split("|", limit = 2)
        val fullImageUrl = "https://image.tmdb.org/t/p/" + path.substring(1)
//                    .replace("+", "-")
//                    .replace("/", "_")
//                    .trimEnd('=')
        // val pathEncoded = base64Encode(fullImageUrl.toByteArray())
        // tmdb image proxied through filmfrei api
        // return "$mainUrl/api/image.php?u=${pathEncoded}&w=$resolution"

        return fullImageUrl
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val catalog = loadCatalog()
        val genres = catalog.films.flatMap { movie -> movie.genres.map { genre -> genre to movie } }
            .groupBy { (genre, _) -> genre }
            .toList()
            .sortedBy { (genre, _) -> genre }

        val topMovies = catalog.top10.mapNotNull { slug ->
            catalog.films.find { "${it.t}:${it.id}" == slug }
        }.map { it.toSearchResponse() }
        val lists = genres.map { (genre, movies) ->
            HomePageList(genre, movies.map { it.second.toSearchResponse() })
        }

        return newHomePageResponse(
            listOf(HomePageList("Beliebt", topMovies)) + lists,
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val allMovies = loadCatalog().films

        return allMovies
            .filter { it.title.contains(query, ignoreCase = true) }
            .sortedByDescending { it.votes }
            .map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val slug = url.substringAfterLast("/")

        val movie = app.get("$mainUrl/beta/api.php?q=play&type=f&id=$slug")
            .parsed<PlayResponse>()
        return newMovieLoadResponse(
            name = movie.title,
            url = url,
            dataUrl = movie.src,
            type = TvType.Movie
        ) {
            posterUrl = fixImageUrl(movie.poster)
            plot = movie.desc
            year = movie.sub.takeWhile { it.isDigit() }.toIntOrNull()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(newExtractorLink(source = name, name = name, url = data))

        return true
    }

    private data class Catalog(
        val films: List<Film>,
        val series: List<Series>,
        val top10: List<String>,
        val tbase: String,
        val trailers: Map<String, String>,
    )

    private data class Film(
        val soon: Long,
        val t: String,
        val id: String,
        val title: String,
        val year: String,
        val min: Long,
        val genres: List<String>,
        val rating: Double,
        val votes: Long,
        val quality: String,
        val lang: String,
        val added: String,
        val card: String,
        val hero: String,
        val poster: String,
        val logo: String,
    )

    private data class Series(
        val t: String,
        val soon: Long,
        val id: String,
        val title: String,
        val year: String,
        val endYear: String,
        val genres: List<String>,
        val rating: Double,
        val votes: Long,
        val added: String,
        val nf: Long,
        val prog: Any?,
        val ne: List<Long>,
        val seasons: Long,
        val episodes: Long,
        val card: String,
        val hero: String,
        val poster: String,
        val logo: String,
    )

    private data class PlayResponse(
        val ok: Boolean,
        val type: String,
        val id: String,
        val title: String,
        val sub: String,
        val desc: String,
        val src: String,
        val ready: Boolean,
        val vtt: String,
        val poster: String,
        val logo: String,
        val progressSlug: String,
    )
}