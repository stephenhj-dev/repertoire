package app.oslo.repertoire

import app.oslo.repertoire.data.RepertoireSchema
import app.oslo.repertoire.data.WatchKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

/** The v7 → v8 migration on a real SQLite: tables created, watchlist ticks carried over. */
class RepertoireMigrationTest {
    // The v7 watchlist_items table, as MIGRATION_5_6 creates it.
    private val v7Items =
        "CREATE TABLE `watchlist_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `listId` INTEGER NOT NULL, " +
            "`position` INTEGER NOT NULL, `kind` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `title` TEXT NOT NULL, `year` INTEGER, " +
            "`posterPath` TEXT, `watched` INTEGER NOT NULL, `catalogName` TEXT, `addedAt` INTEGER NOT NULL)"

    @Test
    fun migratesWatchlistTicksIntoWatched() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { st ->
                st.execute(v7Items)
                st.execute("INSERT INTO watchlist_items (listId, position, kind, tmdbId, title, watched, catalogName, addedAt) VALUES (1, 0, 'movie', 603, 'The Matrix', 1, NULL, 10)")
                // Same film ticked in a second list: one watched row.
                st.execute("INSERT INTO watchlist_items (listId, position, kind, tmdbId, title, watched, catalogName, addedAt) VALUES (2, 0, 'movie', 603, 'The Matrix', 1, NULL, 20)")
                st.execute("INSERT INTO watchlist_items (listId, position, kind, tmdbId, title, watched, catalogName, addedAt) VALUES (1, 1, 'movie', 0, 'Odd', 1, ' EN - Odd Film ', 30)")
                st.execute("INSERT INTO watchlist_items (listId, position, kind, tmdbId, title, watched, catalogName, addedAt) VALUES (1, 2, 'show', 1396, 'Breaking Bad', 0, NULL, 40)")
                RepertoireSchema.statements.forEach(st::execute)
                // Running it twice (CREATE IF NOT EXISTS / INSERT OR IGNORE) is harmless.
                RepertoireSchema.statements.forEach(st::execute)
                val keys = mutableListOf<String>()
                st.executeQuery("SELECT `key`, auto FROM watched ORDER BY `key`").use { rs ->
                    while (rs.next()) {
                        keys += rs.getString(1)
                        assertEquals(0, rs.getInt(2))
                    }
                }
                assertEquals(listOf(WatchKey.of("movie", 0, " EN - Odd Film "), WatchKey.of("movie", 603)).sorted(), keys)
                st.executeQuery("SELECT COUNT(*) FROM followed_people").use { rs -> rs.next(); assertEquals(0, rs.getInt(1)) }
            }
        }
    }

    @Test
    fun watchKeysMatchTheSqlKeys() {
        assertEquals("movie:603", WatchKey.of("movie", 603))
        assertEquals("show:name:Some Show", WatchKey.of("show", 0, " Some Show "))
    }

    /** The migration must create exactly the tables Room expects, or Room refuses to open the database. */
    @Test
    fun migrationSqlMatchesRoomsSchema() {
        val impl = File("build/generated/ksp/debug/java/app/oslo/repertoire/data/CatalogDatabase_Impl.java")
        if (!impl.exists()) return // only checkable after KSP ran (always the case in a full build)
        val text = impl.readText()
        RepertoireSchema.create.forEach { sql -> assertTrue(sql, sql in text) }
    }
}
