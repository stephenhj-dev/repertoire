package app.oslo.repertoire.tmdb

import kotlin.math.abs

/** Catalog kinds ("movie", "show") against TMDB media types ("movie", "tv"). */
fun tmdbMediaType(kind: String): String = if (kind == "show") "tv" else "movie"

fun catalogKind(mediaType: String): String = if (mediaType == "tv") "show" else "movie"

/** A movie or series from the Xtream catalog, as needed for matching. */
data class LibraryEntry(
    val id: Long,
    val kind: String,
    val name: String,
    val icon: String? = null,
    val tmdbId: Int? = null,
    val year: Int? = null,
) {
    val parsed: ParsedTitle by lazy { TitleNormalizer.parse(name) }
    val key: String by lazy { TitleNormalizer.key(parsed.title) }
    val effectiveYear: Int? get() = year ?: parsed.year
}

/** What we know about a TMDB title when looking for it in the catalog. */
data class TmdbRef(
    val mediaType: String,
    val id: Int,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
)

/**
 * In-memory index of the catalog's movies and series. Matching prefers the TMDB id the portal
 * gives; otherwise it falls back to the folded title plus the year (within a year either way,
 * since portals and TMDB disagree on release vs. premiere dates).
 */
class LibraryIndex(entries: List<LibraryEntry>) {
    private val byTmdb: Map<Pair<String, Int>, List<LibraryEntry>> =
        entries.filter { it.tmdbId != null }.groupBy { it.kind to it.tmdbId!! }
    private val byKey: Map<Pair<String, String>, List<LibraryEntry>> =
        entries.filter { it.key.isNotEmpty() }.groupBy { it.kind to it.key }

    val size: Int = entries.size

    /** Every catalog entry that is this TMDB title, best version first. */
    fun find(ref: TmdbRef): List<LibraryEntry> {
        val kind = catalogKind(ref.mediaType)
        val exact = byTmdb[kind to ref.id].orEmpty()
        val keys = listOfNotNull(ref.title, ref.originalTitle).map(TitleNormalizer::key).filter { it.isNotEmpty() }.distinct()
        val byName = keys.flatMap { byKey[kind to it].orEmpty() }.filter { entry ->
            // An entry with a different TMDB id is another film with the same name (a remake).
            (entry.tmdbId == null || entry.tmdbId == ref.id) && yearsAgree(entry.effectiveYear, ref.year)
        }
        return (exact + byName).distinctBy { it.id }.sortedWith(versionOrder(ref))
    }

    fun contains(ref: TmdbRef): Boolean = find(ref).isNotEmpty()

    companion object {
        fun yearsAgree(catalogYear: Int?, tmdbYear: Int?): Boolean =
            catalogYear == null || tmdbYear == null || abs(catalogYear - tmdbYear) <= 1

        /** Exact TMDB id first, then exact year, then the plainest name (fewest tags). */
        private fun versionOrder(ref: TmdbRef) = compareBy<LibraryEntry>(
            { if (it.tmdbId == ref.id) 0 else 1 },
            { if (it.effectiveYear == ref.year) 0 else 1 },
            { it.name.length - it.parsed.title.length },
            { it.name.length },
        )
    }
}

/** A TMDB search hit, reduced to what picking the right one needs. */
data class SearchCandidate(
    val id: Int,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val popularity: Double = 0.0,
)

/**
 * Picks the TMDB result for a catalog title. A hit must have the same folded title (or original
 * title) and an agreeing year; failing that, a hit with the same year whose title contains ours
 * or the other way round ("Dune" vs "Dune: Part One"). Returns null rather than guess.
 */
fun pickSearchResult(query: ParsedTitle, results: List<SearchCandidate>): SearchCandidate? {
    val wanted = TitleNormalizer.key(query.title)
    if (wanted.isEmpty()) return null
    fun keys(c: SearchCandidate) = listOfNotNull(c.title, c.originalTitle).map(TitleNormalizer::key)
    val exact = results.filter { c -> wanted in keys(c) && LibraryIndex.yearsAgree(c.year, query.year) }
    if (exact.isNotEmpty()) {
        return exact.minWith(compareBy<SearchCandidate>({ if (query.year != null && it.year == query.year) 0 else 1 }, { -it.popularity }))
    }
    if (query.year == null) return null
    return results.firstOrNull { c ->
        c.year != null && abs(c.year - query.year) <= 1 &&
            keys(c).any { key -> key.isNotEmpty() && (key.startsWith(wanted) || wanted.startsWith(key)) }
    }
}
