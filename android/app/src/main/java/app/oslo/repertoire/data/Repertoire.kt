package app.oslo.repertoire.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * A title the user has seen. [key] is [WatchKey.of]: "movie:603" by TMDB id, or
 * "movie:name:<catalog name>" for catalog titles TMDB doesn't know. [kind] is "movie" or "show".
 */
@Entity(tableName = "watched")
data class WatchedRow(
    @PrimaryKey val key: String,
    val kind: String,
    val tmdbId: Int,
    val watchedAt: Long,
    /** True when it was marked by playing it through the app, false when marked by hand. */
    val auto: Boolean,
)

/** A person in the user's Repertoire, with a snapshot so the tab draws before TMDB answers. */
@Entity(tableName = "followed_people")
data class FollowedPersonRow(
    @PrimaryKey val personId: Int,
    val name: String,
    val profilePath: String?,
    /** TMDB known_for_department ("Directing", "Acting", ...). */
    val department: String?,
    val backdropPath: String?,
    val position: Int,
    val addedAt: Long,
)

object WatchKey {
    fun of(kind: String, tmdbId: Int, catalogName: String? = null): String =
        if (tmdbId > 0) "$kind:$tmdbId" else "$kind:name:${catalogName.orEmpty().trim()}"
}

@Dao
interface RepertoireDao {
    @Query("SELECT `key` FROM watched")
    fun watchedKeys(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatched(row: WatchedRow)

    @Query("DELETE FROM watched WHERE `key` = :key")
    suspend fun deleteWatched(key: String)

    @Query("SELECT COUNT(*) FROM watched WHERE `key` = :key")
    suspend fun isWatched(key: String): Int

    /** Keeps watchlist items in step with the shared watched state. */
    @Query("UPDATE watchlist_items SET watched = :watched WHERE kind = :kind AND tmdbId = :tmdbId AND (:tmdbId != 0 OR catalogName = :catalogName)")
    suspend fun setItemsWatched(kind: String, tmdbId: Int, catalogName: String?, watched: Boolean)

    /** Ticks off items of a list that are watched elsewhere (after adding or importing titles). */
    @Query(
        """
        UPDATE watchlist_items SET watched = 1 WHERE listId = :listId AND watched = 0 AND
            (CASE WHEN tmdbId > 0 THEN kind || ':' || tmdbId ELSE kind || ':name:' || TRIM(COALESCE(catalogName, '')) END)
            IN (SELECT `key` FROM watched)
        """,
    )
    suspend fun syncList(listId: Long)

    @Transaction
    suspend fun setWatched(kind: String, tmdbId: Int, catalogName: String?, watched: Boolean, auto: Boolean, now: Long) {
        val key = WatchKey.of(kind, tmdbId, catalogName)
        if (watched) insertWatched(WatchedRow(key, kind, tmdbId, now, auto)) else deleteWatched(key)
        setItemsWatched(kind, tmdbId, catalogName, watched)
    }

    @Query("SELECT * FROM followed_people ORDER BY position, addedAt")
    fun people(): Flow<List<FollowedPersonRow>>

    @Query("SELECT personId FROM followed_people")
    fun followedIds(): Flow<List<Int>>

    @Query("SELECT COUNT(*) FROM followed_people")
    suspend fun followedCount(): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM followed_people")
    suspend fun nextPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPerson(row: FollowedPersonRow): Long

    @Query("UPDATE followed_people SET backdropPath = :backdrop, profilePath = COALESCE(:profile, profilePath) WHERE personId = :id")
    suspend fun refreshPerson(id: Int, backdrop: String?, profile: String?)

    @Query("DELETE FROM followed_people WHERE personId = :id")
    suspend fun unfollow(id: Int)
}

/** The v7 → v8 schema change, kept as plain SQL so a JVM test can run it on SQLite. */
object RepertoireSchema {
    val create = listOf(
        "CREATE TABLE IF NOT EXISTS `watched` (`key` TEXT NOT NULL, `kind` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, " +
            "`watchedAt` INTEGER NOT NULL, `auto` INTEGER NOT NULL, PRIMARY KEY(`key`))",
        "CREATE TABLE IF NOT EXISTS `followed_people` (`personId` INTEGER NOT NULL, `name` TEXT NOT NULL, `profilePath` TEXT, " +
            "`department` TEXT, `backdropPath` TEXT, `position` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`personId`))",
    )

    /** Titles ticked off in watchlists become watched everywhere (keys as [WatchKey.of]). */
    const val BACKFILL =
        "INSERT OR IGNORE INTO `watched` (`key`, `kind`, `tmdbId`, `watchedAt`, `auto`) " +
            "SELECT CASE WHEN tmdbId > 0 THEN kind || ':' || tmdbId ELSE kind || ':name:' || TRIM(COALESCE(catalogName, '')) END, " +
            "kind, tmdbId, MAX(addedAt), 0 FROM watchlist_items WHERE watched = 1 " +
            "GROUP BY CASE WHEN tmdbId > 0 THEN kind || ':' || tmdbId ELSE kind || ':name:' || TRIM(COALESCE(catalogName, '')) END"

    val statements: List<String> get() = create + BACKFILL
}
