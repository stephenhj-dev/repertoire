package app.oslo.repertoire

import app.oslo.repertoire.tmdb.RateLimiter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RateLimiterTest {
    @Test
    fun spacesCallsApart() = runBlocking {
        var now = 1_000L
        val waits = mutableListOf<Long>()
        val limiter = RateLimiter(100, clock = { now }, wait = { waits += it; now += it })
        repeat(3) { limiter.acquire() }
        assertEquals(listOf(100L, 100L), waits)
        now += 500
        limiter.acquire()
        assertEquals(2, waits.size)
    }
}
