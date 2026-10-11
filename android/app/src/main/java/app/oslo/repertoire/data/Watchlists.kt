package app.oslo.repertoire.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A user's list of titles. [personId] is set for lists saved from a person's filmography. */
@Entity(tableName = "watchlists")
data class WatchlistRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    val createdAt: Long,
    val personId: Int? = null,
)

/**
 * One title in a list, in [position] order. Titles are kept by TMDB id and media type with a
 * snapshot of title/year/poster, and are matched to catalog versions when played. [catalogName]
 * is only set for catalog titles TMDB doesn't know ([tmdbId] 0); [kind] is "movie" or "show".
 */
@Entity(tableName = "watchlist_items", indices = [Index("listId", "position")])
data class WatchlistItemRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val position: Int,
    val kind: String,
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    val posterPath: String?,
    val watched: Boolean = false,
    val catalogName: String? = null,
    val addedAt: Long,
)

data class WatchlistSummary(
    val id: Long,
    val name: String,
    val position: Int,
    val personId: Int?,
    val count: Int,
    val unwatched: Int,
    val posterPath: String?,
)

@Dao
interface WatchlistDao {
    @Query(
        """
        SELECT w.id AS id, w.name AS name, w.position AS position, w.personId AS personId,
               (SELECT COUNT(*) FROM watchlist_items i WHERE i.listId = w.id) AS count,
               (SELECT COUNT(*) FROM watchlist_items i WHERE i.listId = w.id AND i.watched = 0) AS unwatched,
               (SELECT i.posterPath FROM watchlist_items i WHERE i.listId = w.id AND i.posterPath IS NOT NULL
                 ORDER BY i.position LIMIT 1) AS posterPath
        FROM watchlists w
        ORDER BY w.position, w.id
        """,
    )
    fun summaries(): Flow<List<WatchlistSummary>>

    @Query("SELECT * FROM watchlists WHERE id = :id")
    fun list(id: Long): Flow<WatchlistRow?>

    @Query("SELECT * FROM watchlists WHERE id = :id")
    suspend fun listNow(id: Long): WatchlistRow?

    @Query("SELECT * FROM watchlist_items WHERE listId = :listId ORDER BY position, id")
    fun items(listId: Long): Flow<List<WatchlistItemRow>>

    @Query("SELECT * FROM watchlist_items WHERE listId = :listId ORDER BY position, id")
    suspend fun itemsNow(listId: Long): List<WatchlistItemRow>

    /** Lists that already hold this title. */
    @Query("SELECT DISTINCT listId FROM watchlist_items WHERE kind = :kind AND tmdbId = :tmdbId AND (:tmdbId != 0 OR catalogName = :catalogName)")
    fun listsWith(kind: String, tmdbId: Int, catalogName: String?): Flow<List<Long>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM watchlists")
    suspend fun nextListPosition(): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM watchlist_items WHERE listId = :listId")
    suspend fun nextItemPosition(listId: Long): Int

    @Insert
    suspend fun insertList(row: WatchlistRow): Long

    @Insert
    suspend fun insertItems(rows: List<WatchlistItemRow>)

    @Query("UPDATE watchlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM watchlist_items WHERE listId = :id")
    suspend fun deleteItemsOf(id: Long)

    @Query("DELETE FROM watchlists WHERE id = :id")
    suspend fun deleteListRow(id: Long)

    @Transaction
    suspend fun deleteList(id: Long) {
        deleteItemsOf(id)
        deleteListRow(id)
    }

    @Query("DELETE FROM watchlist_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM watchlist_items WHERE listId = :listId AND kind = :kind AND tmdbId = :tmdbId AND (:tmdbId != 0 OR catalogName = :catalogName)")
    suspend fun removeTitle(listId: Long, kind: String, tmdbId: Int, catalogName: String?)

    @Query("UPDATE watchlist_items SET watched = :watched WHERE id = :id")
    suspend fun setWatched(id: Long, watched: Boolean)

    @Query("UPDATE watchlists SET position = :position WHERE id = :id")
    suspend fun setListPosition(id: Long, position: Int)

    @Query("UPDATE watchlist_items SET position = :position WHERE id = :id")
    suspend fun setItemPosition(id: Long, position: Int)

    @Transaction
    suspend fun orderLists(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setListPosition(id, index) }
    }

    @Transaction
    suspend fun orderItems(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setItemPosition(id, index) }
    }

    @Transaction
    suspend fun createWithItems(row: WatchlistRow, items: List<WatchlistItemRow>): Long {
        val id = insertList(row)
        insertItems(items.mapIndexed { index, item -> item.copy(id = 0, listId = id, position = index) })
        return id
    }
}
