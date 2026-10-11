package app.oslo.repertoire.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "titles",
    indices = [
        Index("kind", "country"),
        Index("kind", "groupCode"),
        Index("kind", "categoryId"),
        Index("kind", "name"),
        Index("kind", "tmdbId"),
    ],
)
data class TitleRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    val remoteId: String,
    val categoryId: String?,
    val country: String?,
    val groupCode: String?,
    val banner: Boolean,
    val sortOrder: Int,
    val section: String? = null,
    /** Channel logo (stream_icon) or series/movie cover URL from the portal. */
    val icon: String? = null,
    /** TMDB id when the portal lists one (tmdb / tmdb_id), for movies and series. */
    val tmdbId: Int? = null,
    /** Release year from the portal (year / releaseDate), when given. */
    val year: Int? = null,
)

/** The columns needed to match catalog movies and series against TMDB. */
data class LibraryRow(
    val id: Long,
    val kind: String,
    val name: String,
    val icon: String?,
    val tmdbId: Int?,
    val year: Int?,
)

/**
 * What TMDB says a catalog movie/series name is, kept across catalog refreshes (keyed by kind and
 * the provider's raw name, like favourites). [tmdbId] is null when TMDB had no confident match;
 * such rows are retried after a while. Title, year and poster are TMDB's, for list display.
 */
@Entity(
    tableName = "tmdb_resolved",
    primaryKeys = ["kind", "name"],
    indices = [Index("kind", "tmdbId")],
)
data class ResolvedTitle(
    val kind: String,
    val name: String,
    val tmdbId: Int?,
    val title: String?,
    val year: Int?,
    val posterPath: String?,
    val checkedAt: Long,
)

/** A catalog movie/series that still needs a TMDB lookup. */
data class PendingTitle(val kind: String, val name: String, val tmdbId: Int?, val year: Int?)

/** A catalog name and the TMDB id it belongs to (from the portal or a lookup). */
data class VersionName(val kind: String, val name: String, val tmdbId: Int)

@Entity(tableName = "groups", primaryKeys = ["kind", "categoryId"])
data class GroupRow(
    val kind: String,
    val categoryId: String,
    val name: String,
)

data class CodeCount(val code: String, val count: Int)

data class SectionCount(val section: String?, val count: Int)

data class LiveMark(val id: Long, val name: String, val banner: Boolean)

data class GroupCount(val categoryId: String, val name: String, val count: Int)

@Dao
interface CatalogDao {
    @Query("DELETE FROM titles")
    fun clearTitles()

    @Query("DELETE FROM groups")
    fun clearGroups()

    @Insert
    fun insertTitles(rows: List<TitleRow>)

    @Insert
    fun insertGroups(rows: List<GroupRow>)

    @Query("SELECT * FROM titles WHERE id = :id")
    suspend fun titleById(id: Long): TitleRow?

    @Query(
        """
        SELECT t.id AS id, t.kind AS kind, t.name AS name, t.icon AS icon,
               COALESCE(t.tmdbId, r.tmdbId) AS tmdbId, t.year AS year
        FROM titles t LEFT JOIN tmdb_resolved r ON r.kind = t.kind AND r.name = t.name
        WHERE t.banner = 0 AND t.kind IN ('movie', 'show')
        """,
    )
    suspend fun libraryRows(): List<LibraryRow>

    @Query("SELECT * FROM tmdb_resolved WHERE kind = :kind AND name IN (:names)")
    suspend fun resolved(kind: String, names: List<String>): List<ResolvedTitle>

    @androidx.room.Upsert
    suspend fun saveResolved(rows: List<ResolvedTitle>)

    /**
     * Movies and series with no lookup yet, or whose "no match" is older than [retryBefore],
     * one row per distinct name; portal TMDB ids first since they are one cheap request each.
     */
    @Query(
        """
        SELECT t.kind AS kind, t.name AS name, MAX(t.tmdbId) AS tmdbId, MAX(t.year) AS year
        FROM titles t LEFT JOIN tmdb_resolved r ON r.kind = t.kind AND r.name = t.name
        WHERE t.banner = 0 AND t.kind IN ('movie', 'show')
          AND (r.name IS NULL OR (r.tmdbId IS NULL AND r.checkedAt < :retryBefore))
        GROUP BY t.kind, t.name
        ORDER BY MAX(t.tmdbId) IS NULL, t.kind, t.name
        LIMIT :limit
        """,
    )
    suspend fun pendingTitles(retryBefore: Long, limit: Int): List<PendingTitle>

    /** Every catalog name for these TMDB ids, by the portal's id or a lookup. */
    @Query(
        """
        SELECT kind, name, tmdbId FROM titles
        WHERE banner = 0 AND kind = :kind AND tmdbId IN (:ids)
        UNION
        SELECT t.kind AS kind, t.name AS name, r.tmdbId AS tmdbId
        FROM tmdb_resolved r JOIN titles t ON t.kind = r.kind AND t.name = r.name
        WHERE r.kind = :kind AND r.tmdbId IN (:ids) AND t.banner = 0 AND t.tmdbId IS NULL
        """,
    )
    suspend fun versionNames(kind: String, ids: List<Int>): List<VersionName>

    @Query("SELECT * FROM titles WHERE banner = 0 AND kind = :kind AND name IN (:names)")
    suspend fun titlesNamed(kind: String, names: List<String>): List<TitleRow>

    @Query("UPDATE titles SET tmdbId = :tmdbId WHERE id = :id")
    suspend fun setTmdbId(id: Long, tmdbId: Int)

    @Query("SELECT COUNT(*) FROM titles WHERE banner = 0")
    suspend fun titleCount(): Int

    @Query("SELECT kind AS code, COUNT(*) AS count FROM titles WHERE banner = 0 GROUP BY kind")
    suspend fun kindCounts(): List<CodeCount>

    @Query(
        """
        SELECT * FROM titles
        WHERE banner = 0 AND (kind || char(9) || name) IN (:keys)
        GROUP BY kind, name
        ORDER BY CASE kind WHEN 'live' THEN 0 WHEN 'movie' THEN 1 ELSE 2 END, name COLLATE NOCASE
        """,
    )
    suspend fun favorites(keys: List<String>): List<TitleRow>

    @Query(
        """
        SELECT country AS code, COUNT(*) AS count FROM titles
        WHERE kind = 'live' AND banner = 0 AND country IS NOT NULL
        GROUP BY country
        """,
    )
    suspend fun countries(): List<CodeCount>

    @Query(
        """
        SELECT groupCode AS code, COUNT(*) AS count FROM titles
        WHERE kind = 'live' AND banner = 0 AND country IS NULL AND groupCode IS NOT NULL
        GROUP BY groupCode
        ORDER BY groupCode COLLATE NOCASE
        """,
    )
    suspend fun otherGroups(): List<CodeCount>

    @Query(
        """
        SELECT COUNT(*) FROM titles
        WHERE kind = 'live' AND banner = 0 AND country IS NULL AND groupCode IS NULL
        """,
    )
    suspend fun ungroupedCount(): Int

    @Query(
        """
        SELECT * FROM titles
        WHERE kind = 'live' AND banner = 0 AND country = :country
        ORDER BY sortOrder
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun channels(country: String, limit: Int, offset: Int): List<TitleRow>

    @Query(
        """
        SELECT * FROM titles
        WHERE kind = 'live' AND banner = 0 AND country IS NULL AND groupCode = :groupCode
        ORDER BY sortOrder
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun groupedChannels(groupCode: String, limit: Int, offset: Int): List<TitleRow>

    @Query(
        """
        SELECT section AS section, COUNT(*) AS count FROM titles
        WHERE kind = 'live' AND banner = 0 AND country = :country
        GROUP BY section
        ORDER BY MIN(sortOrder)
        """,
    )
    suspend fun sectionsForCountry(country: String): List<SectionCount>

    @Query(
        """
        SELECT section AS section, COUNT(*) AS count FROM titles
        WHERE kind = 'live' AND banner = 0 AND country IS NULL AND groupCode = :groupCode
        GROUP BY section
        ORDER BY MIN(sortOrder)
        """,
    )
    suspend fun sectionsForGroup(groupCode: String): List<SectionCount>

    @Query(
        """
        SELECT * FROM titles
        WHERE kind = 'live' AND banner = 0 AND country = :country
          AND (
            (:missing = 1 AND section IS NULL)
            OR (:missing = 0 AND section = :section)
          )
        ORDER BY sortOrder
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun channelsInSection(
        country: String,
        section: String,
        missing: Int,
        limit: Int,
        offset: Int,
    ): List<TitleRow>

    @Query(
        """
        SELECT * FROM titles
        WHERE kind = 'live' AND banner = 0 AND country IS NULL AND groupCode = :groupCode
          AND (
            (:missing = 1 AND section IS NULL)
            OR (:missing = 0 AND section = :section)
          )
        ORDER BY sortOrder
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun groupChannelsInSection(
        groupCode: String,
        section: String,
        missing: Int,
        limit: Int,
        offset: Int,
    ): List<TitleRow>

    @Query(
        """
        SELECT id, name, banner FROM titles
        WHERE kind = 'live'
        ORDER BY sortOrder
        """,
    )
    suspend fun liveMarks(): List<LiveMark>

    @Query("UPDATE titles SET section = :section WHERE id = :id")
    fun setSection(id: Long, section: String)

    @Query(
        """
        SELECT g.categoryId AS categoryId, g.name AS name,
               (SELECT COUNT(*) FROM titles t
                 WHERE t.kind = g.kind AND t.categoryId = g.categoryId AND t.banner = 0) AS count
        FROM groups g
        WHERE g.kind = :kind
        ORDER BY g.name COLLATE NOCASE
        """,
    )
    suspend fun groups(kind: String): List<GroupCount>

    @Query(
        """
        SELECT * FROM titles
        WHERE kind = :kind AND categoryId = :categoryId AND banner = 0
        ORDER BY name COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun titlesInGroup(kind: String, categoryId: String, limit: Int, offset: Int): List<TitleRow>

    @Query(
        """
        SELECT * FROM titles
        WHERE banner = 0
          AND (:kind IS NULL OR kind = :kind)
          AND (:country IS NULL OR country = :country)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND name LIKE '%' || :needle || '%' ESCAPE '\'
        ORDER BY
          CASE
            WHEN name = :plain COLLATE NOCASE THEN 0
            WHEN name LIKE :needle || '%' ESCAPE '\' THEN 1
            ELSE 2
          END,
          name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun search(
        kind: String?,
        country: String?,
        categoryId: String?,
        needle: String,
        plain: String,
        limit: Int,
    ): List<TitleRow>
}

@Database(entities = [TitleRow::class, GroupRow::class, ResolvedTitle::class], version = 5, exportSchema = false)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE titles ADD COLUMN section TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE titles ADD COLUMN icon TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE titles ADD COLUMN tmdbId INTEGER")
                db.execSQL("ALTER TABLE titles ADD COLUMN year INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_titles_kind_tmdbId` ON `titles` (`kind`, `tmdbId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tmdb_resolved` (`kind` TEXT NOT NULL, `name` TEXT NOT NULL, `tmdbId` INTEGER, " +
                        "`title` TEXT, `year` INTEGER, `posterPath` TEXT, `checkedAt` INTEGER NOT NULL, PRIMARY KEY(`kind`, `name`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tmdb_resolved_kind_tmdbId` ON `tmdb_resolved` (`kind`, `tmdbId`)")
            }
        }

        @Volatile private var instance: CatalogDatabase? = null

        fun get(context: Context): CatalogDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CatalogDatabase::class.java,
                    "guide.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
