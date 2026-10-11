package app.oslo.repertoire.tmdb

import android.content.Context
import app.oslo.repertoire.data.CatalogDatabase
import app.oslo.repertoire.data.likeNeedle

/**
 * Finds catalog versions of TMDB titles with indexed queries instead of an in-memory index of
 * the whole catalog (which took about 35 s to build on a phone for ~200k movies and series).
 */
object CatalogLookup {
    /** Entries with these TMDB ids (portal ids and stored lookups), per id. One indexed query per 500 ids. */
    suspend fun byIds(context: Context, kind: String, ids: Collection<Int>): Map<Int, List<LibraryEntry>> {
        val dao = CatalogDatabase.get(context).catalog()
        val out = HashMap<Int, MutableList<LibraryEntry>>()
        ids.filter { it > 0 }.distinct().chunked(500).forEach { chunk ->
            dao.versionNames(kind, chunk).forEach {
                out.getOrPut(it.tmdbId) { mutableListOf() } += LibraryEntry(it.id, it.kind, it.name, it.icon, it.tmdbId, it.year)
            }
        }
        return out
    }

    /**
     * Entries without any TMDB id whose name matches [ref] (folded title and year, as
     * [LibraryIndex] matches). One LIKE scan for the title's most distinctive word; on request only.
     */
    suspend fun byName(context: Context, ref: TmdbRef): List<LibraryEntry> {
        val words = listOfNotNull(ref.title, ref.originalTitle).mapNotNull(::likeWord).distinct()
        if (words.isEmpty()) return emptyList()
        val dao = CatalogDatabase.get(context).catalog()
        val kind = catalogKind(ref.mediaType)
        val candidates = words.flatMap { dao.nameCandidates(kind, likeNeedle(it), 400) }
            .distinctBy { it.id }
            .map { LibraryEntry(it.id, it.kind, it.name, it.icon, it.tmdbId, it.year) }
        return LibraryIndex(candidates).find(ref)
    }
}
