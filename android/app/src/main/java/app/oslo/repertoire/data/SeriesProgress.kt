package app.oslo.repertoire.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The last episode played per series. Keyed by TMDB id when known ("tmdb:1396"), else by the
 * catalog name ("name:Breaking Bad"), so unmatched series remember too.
 */
@Entity(tableName = "series_progress")
data class SeriesProgressRow(
    @PrimaryKey val seriesKey: String,
    val season: Int,
    val episode: Int,
    val episodeTitle: String?,
    val updatedAt: Long,
) {
    companion object {
        fun key(tmdbId: Int?, catalogName: String?): String? = when {
            tmdbId != null && tmdbId > 0 -> "tmdb:$tmdbId"
            !catalogName.isNullOrBlank() -> "name:${catalogName.trim()}"
            else -> null
        }
    }
}

@Dao
interface SeriesProgressDao {
    @Query("SELECT * FROM series_progress WHERE seriesKey = :key")
    fun progress(key: String): Flow<SeriesProgressRow?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(row: SeriesProgressRow)
}
