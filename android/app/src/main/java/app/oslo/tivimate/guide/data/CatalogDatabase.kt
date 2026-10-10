package app.oslo.tivimate.guide.data

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
)

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

    @Query("SELECT COUNT(*) FROM titles WHERE banner = 0")
    suspend fun titleCount(): Int

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

@Database(entities = [TitleRow::class, GroupRow::class], version = 2, exportSchema = false)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE titles ADD COLUMN section TEXT")
            }
        }

        @Volatile private var instance: CatalogDatabase? = null

        fun get(context: Context): CatalogDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CatalogDatabase::class.java,
                    "guide.db",
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
