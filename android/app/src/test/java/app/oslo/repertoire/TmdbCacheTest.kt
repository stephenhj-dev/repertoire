package app.oslo.repertoire

import app.oslo.repertoire.tmdb.TmdbCache
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbCacheTest {
    @Test
    fun memoryEntriesExpireAfterTheTtl() {
        var now = 0L
        val cache = TmdbCache(directory = null, ttlMs = 1_000, clock = { now })
        cache.put("a", "{}", persist = true)
        now = 999
        assertEquals("{}", cache.get("a"))
        now = 1_000
        assertNull(cache.get("a"))
    }

    @Test
    fun persistedEntriesSurviveANewCache() {
        val dir = Files.createTempDirectory("tmdb").toFile()
        TmdbCache(dir).put("https://api/x", "{\"id\":1}", persist = true)
        TmdbCache(dir).put("https://api/search", "{}", persist = false)
        assertEquals("{\"id\":1}", TmdbCache(dir).get("https://api/x"))
        assertNull(TmdbCache(dir).get("https://api/search"))
        dir.deleteRecursively()
    }
}
