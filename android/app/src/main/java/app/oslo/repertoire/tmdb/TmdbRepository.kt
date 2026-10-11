package app.oslo.repertoire.tmdb

import android.content.Context
import app.oslo.repertoire.BuildConfig
import app.oslo.repertoire.data.CatalogDatabase
import app.oslo.repertoire.data.PortalStore
import app.oslo.repertoire.data.TitleRow
import app.oslo.repertoire.data.XtreamInfo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** App-wide access to TMDB and to the catalog index used for "in your library" matching. */
object Tmdb {
    /** False when the build has no tmdb.properties: every TMDB section is hidden. */
    val enabled: Boolean get() = BuildConfig.TMDB_READ_TOKEN.isNotBlank()

    @Volatile private var client: TmdbClient? = null
    private val indexLock = Mutex()
    private val portalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var index: LibraryIndex? = null

    private fun client(context: Context): TmdbClient = client ?: synchronized(this) {
        client ?: TmdbClient(
            BuildConfig.TMDB_READ_TOKEN,
            TmdbCache(File(context.applicationContext.cacheDir, "tmdb")).also { cache ->
                Thread { runCatching { cache.prune() } }.start()
            },
        ).also { client = it }
    }

    /** English data: catalog names are matched against English titles, and overviews always exist in English. */
    private const val language = "en-US"

    suspend fun title(context: Context, mediaType: String, id: Int): TmdbTitleDetail =
        client(context).get(
            "/$mediaType/$id",
            mapOf("append_to_response" to "credits,videos", "language" to language, "include_video_language" to "en,null"),
            TmdbTitleDetail.serializer(),
        )

    suspend fun person(context: Context, id: Int): TmdbPerson =
        client(context).get(
            "/person/$id",
            mapOf("append_to_response" to "combined_credits", "language" to language),
            TmdbPerson.serializer(),
        )

    suspend fun searchPeople(context: Context, query: String): List<TmdbPersonHit> =
        client(context).get(
            "/search/person",
            mapOf("query" to query.trim(), "include_adult" to "false", "language" to language),
            TmdbPage.serializer(TmdbPersonHit.serializer()),
            persist = false,
        ).results.filter { it.knownForDepartment != null || it.profilePath != null }

    /** Finds the TMDB id for a catalog title by name and year; null when nothing fits well. */
    suspend fun searchTitle(context: Context, mediaType: String, parsed: ParsedTitle): Int? =
        searchCandidate(context, mediaType, parsed)?.id

    /** Title, year and poster only (no credits), for list display. */
    suspend fun basic(context: Context, mediaType: String, id: Int): TmdbTitleDetail =
        client(context).get("/$mediaType/$id", mapOf("language" to language), TmdbTitleDetail.serializer())

    /** The confident TMDB search hit for a catalog title, with its title, year and poster. */
    suspend fun searchCandidate(context: Context, mediaType: String, parsed: ParsedTitle): SearchCandidate? {
        suspend fun search(year: Int?): List<SearchCandidate> = client(context).get(
            "/search/$mediaType",
            mapOf(
                "query" to parsed.title,
                "include_adult" to "false",
                "language" to language,
                (if (mediaType == "tv") "first_air_date_year" else "year") to year?.toString(),
            ),
            TmdbPage.serializer(TmdbSearchItem.serializer()),
        ).results.map { it.candidate() }
        pickSearchResult(parsed, search(parsed.year))?.let { return it }
        // The portal's year is sometimes off by one or simply wrong; retry without it.
        if (parsed.year != null) pickSearchResult(parsed, search(null))?.let { return it }
        return null
    }

    /**
     * Resolves a catalog movie/series to its TMDB id: the id the portal synced, then the portal's
     * get_vod_info / get_series_info (remembered in the catalog), then a TMDB search by name + year.
     */
    suspend fun resolve(context: Context, row: TitleRow): Int? {
        row.tmdbId?.let { return it }
        val dao = CatalogDatabase.get(context).catalog()
        // A lookup made for the lists already answered this.
        dao.resolved(row.kind, listOf(row.name)).firstOrNull()?.tmdbId?.let { return it }
        val mediaType = tmdbMediaType(row.kind)
        var year = row.year
        val login = PortalStore(context).load()
        if (login != null) {
            // Portals can be slow; don't hold the page for more than a few seconds.
            val lookup = portalScope.async {
                runCatching { XtreamInfo.lookup(login.url, login.username, login.password, row.kind, row.remoteId) }.getOrNull()
            }
            val info = withTimeoutOrNull(6_000) { lookup.await() }
            info?.tmdbId?.let { id ->
                CatalogDatabase.get(context).catalog().setTmdbId(row.id, id)
                return id
            }
            year = year ?: info?.year
        }
        val parsed = TitleNormalizer.parse(row.name)
        return searchTitle(context, mediaType, parsed.copy(year = year ?: parsed.year))
    }

    /** The catalog's movies and series, indexed for matching. Built once per catalog refresh. */
    suspend fun library(context: Context): LibraryIndex = index ?: indexLock.withLock {
        index ?: withContext(Dispatchers.IO) {
            val rows = CatalogDatabase.get(context).catalog().libraryRows()
            LibraryIndex(rows.map { LibraryEntry(it.id, it.kind, it.name, it.icon, it.tmdbId, it.year) })
        }.also { index = it }
    }

    fun invalidateLibrary() {
        index = null
    }
}
