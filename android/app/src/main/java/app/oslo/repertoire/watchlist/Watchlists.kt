package app.oslo.repertoire.watchlist

import android.content.Context
import app.oslo.repertoire.data.CatalogDatabase
import app.oslo.repertoire.data.WatchlistItemRow
import app.oslo.repertoire.data.WatchlistRow
import app.oslo.repertoire.tmdb.CatalogLookup

/** A title that can go on a watchlist: TMDB id (0 when TMDB doesn't know it) plus a snapshot. */
data class TitleRef(
    val kind: String,
    val tmdbId: Int,
    val title: String,
    val year: Int? = null,
    val posterPath: String? = null,
    /** Raw catalog name, only for titles without a TMDB id. */
    val catalogName: String? = null,
)

/** Watchlist storage plus matching items to catalog versions. */
class Watchlists(context: Context) {
    private val app = context.applicationContext
    private val db = CatalogDatabase.get(app)
    val dao = db.watchlists()

    suspend fun create(name: String, titles: List<TitleRef> = emptyList(), personId: Int? = null): Long {
        val now = System.currentTimeMillis()
        val row = WatchlistRow(name = name.trim().ifBlank { "New list" }, position = dao.nextListPosition(), createdAt = now, personId = personId)
        val items = titles.distinctBy { key(it) }.map { it.toRow(listId = 0, position = 0, now = now) }
        return dao.createWithItems(row, items).also { db.repertoire().syncList(it) }
    }

    /** Adds [title] at the end of the list unless it's already there. */
    suspend fun add(listId: Long, title: TitleRef): Boolean {
        val existing = dao.itemsNow(listId)
        if (existing.any { key(it) == key(title) }) return false
        dao.insertItems(listOf(title.toRow(listId, dao.nextItemPosition(listId), System.currentTimeMillis())))
        db.repertoire().syncList(listId)
        return true
    }

    /** Watched is one state for the whole app: a list item ticks off the title everywhere. */
    suspend fun setWatched(row: WatchlistItemRow, watched: Boolean) =
        db.repertoire().setWatched(row.kind, row.tmdbId, row.catalogName.takeIf { row.tmdbId <= 0 }, watched, auto = false, now = System.currentTimeMillis())

    suspend fun remove(listId: Long, title: TitleRef) = dao.removeTitle(listId, title.kind, title.tmdbId, title.catalogName)

    suspend fun share(listId: Long): String? {
        val list = dao.listNow(listId) ?: return null
        val items = dao.itemsNow(listId).map {
            WatchlistShare.SharedItem(it.kind, it.tmdbId, it.title, it.year, it.posterPath, it.catalogName)
        }
        return WatchlistShare.message(WatchlistShare.SharedList(name = list.name, items = items))
    }

    suspend fun import(shared: WatchlistShare.SharedList): Long =
        create(shared.name, shared.items.map { TitleRef(it.kind, it.tmdbId, it.title, it.year, it.posterPath, it.catalogName) })

    /** Catalog versions (raw names) for each item id; empty when the title isn't in the library. */
    suspend fun versionsFor(items: List<WatchlistItemRow>): Map<Long, List<String>> {
        if (items.isEmpty()) return emptyMap()
        val catalog = db.catalog()
        val out = HashMap<Long, List<String>>()
        items.groupBy { it.kind }.forEach { (kind, rows) ->
            val byId = CatalogLookup.byIds(app, kind, rows.map { it.tmdbId })
            val named = rows.mapNotNull { it.catalogName }.distinct().chunked(500)
                .flatMap { catalog.titlesNamed(kind, it) }.map { it.name }.toSet()
            rows.forEach { row ->
                out[row.id] = if (row.tmdbId > 0) {
                    byId[row.tmdbId].orEmpty().map { it.name }.distinct()
                } else {
                    listOfNotNull(row.catalogName?.takeIf { it in named })
                }
            }
        }
        return out
    }

    companion object {
        fun key(ref: TitleRef) = Triple(ref.kind, ref.tmdbId, if (ref.tmdbId > 0) null else ref.catalogName)
        fun key(row: WatchlistItemRow) = Triple(row.kind, row.tmdbId, if (row.tmdbId > 0) null else row.catalogName)

        fun ref(row: WatchlistItemRow) = TitleRef(row.kind, row.tmdbId, row.title, row.year, row.posterPath, row.catalogName)

        private fun TitleRef.toRow(listId: Long, position: Int, now: Long) = WatchlistItemRow(
            listId = listId,
            position = position,
            kind = kind,
            tmdbId = tmdbId,
            title = title,
            year = year,
            posterPath = posterPath,
            catalogName = catalogName.takeIf { tmdbId <= 0 },
            addedAt = now,
        )
    }
}
