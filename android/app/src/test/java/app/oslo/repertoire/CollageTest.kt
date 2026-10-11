package app.oslo.repertoire

import app.oslo.repertoire.splash.CollageCanon
import app.oslo.repertoire.splash.CollageList
import app.oslo.repertoire.splash.CollageMovie
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageTest {
    private fun movie(id: Int, votes: Int = 20_000, rating: Double = 8.5, poster: String = "/p$id.jpg") =
        CollageMovie(id, "Film $id", poster, 1970, votes, rating)

    @Test
    fun canonNeedsManyVotesAndAPoster() {
        val top = listOf(
            movie(1, votes = 21_000, rating = 8.7), // The Godfather
            movie(2, votes = 400, rating = 9.1), // high score, few votes: not canon
            movie(3, votes = 5_000, rating = 8.2),
            movie(4, votes = 30_000, rating = 8.4, poster = ""),
            movie(5, votes = 9_000, rating = 8.9, poster = "../x.jpg"),
            movie(1, votes = 21_000, rating = 8.7), // duplicate page entry
        )
        val picked = CollageCanon.select(top, emptyList())
        assertEquals(listOf(1, 3), picked.map { it.id })
    }

    @Test
    fun canonIsOrderedByRatingThenVotesAndBlendsRecent() {
        val top = (1..10).map { movie(it, votes = 10_000 + it, rating = 8.0 + it / 100.0) }
        val recent = listOf(movie(9, rating = 9.9), movie(100, votes = 2_000, rating = 8.1), movie(101, votes = 1_600, rating = 8.6))
        val picked = CollageCanon.select(top, recent, limit = 6, recentCount = 2)
        assertEquals(listOf(10, 9, 8, 7, 101, 100), picked.map { it.id })
    }

    @Test
    fun recentNeverExceedsLimit() {
        val picked = CollageCanon.select(emptyList(), (1..50).map { movie(it) }, limit = 5, recentCount = 16)
        assertEquals(5, picked.size)
    }

    @Test
    fun staleAfterAWeekOrWhenTheClockWentBack() {
        val day = 24L * 60 * 60 * 1000
        assertFalse(CollageCanon.isStale(fetchedAt = 10 * day, now = 16 * day))
        assertTrue(CollageCanon.isStale(fetchedAt = 10 * day, now = 17 * day))
        assertTrue(CollageCanon.isStale(fetchedAt = 10 * day, now = 9 * day))
        assertTrue(CollageCanon.isStale(fetchedAt = 0, now = day))
    }

    @Test
    fun cacheRoundTripDropsBadEntries() {
        val list = CollageList(1234L, listOf(movie(1), movie(2, poster = "nope")))
        val decoded = CollageCanon.decode(CollageCanon.encode(list))!!
        assertEquals(1234L, decoded.fetchedAt)
        assertEquals(listOf(1), decoded.movies.map { it.id })
        assertNull(CollageCanon.decode("{broken"))
        assertNull(CollageCanon.decode(null))
    }

    @Test
    fun launchPicksOnlyCachedThumbnails() {
        val movies = (1..20).map { movie(it) }
        val cached = setOf("p2.jpg", "p4.jpg", "p6.jpg", "p8.jpg")
        val picked = CollageCanon.pickForLaunch(movies, cached, count = 3, random = Random(7))
        assertEquals(3, picked.size)
        assertTrue(picked.all { CollageCanon.fileName(it.posterPath) in cached })
        assertEquals("abc.jpg", CollageCanon.fileName("/abc.jpg"))
    }

    @Test
    fun columnsUseEverythingBeforeRepeating() {
        val items = (0 until 12).toList()
        val grid = CollageCanon.columns(items, columns = 3, perColumn = 4)
        assertEquals(12, grid.flatten().toSet().size)
    }

    @Test
    fun columnsAvoidVerticalAndHorizontalRepeats() {
        for (n in 2..8) {
            val grid = CollageCanon.columns((0 until n).toList(), columns = 4, perColumn = 6)
            grid.forEach { column -> column.zipWithNext().forEach { (a, b) -> assertNotEquals("n=$n", a, b) } }
            for (r in 0 until 6) for (c in 0 until 3) assertNotEquals("n=$n", grid[c][r], grid[c + 1][r])
        }
        assertEquals(listOf(emptyList<Int>(), emptyList()), CollageCanon.columns(emptyList<Int>(), 2, 3))
    }
}
