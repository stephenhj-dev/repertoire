package app.oslo.repertoire.tmdb

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spaces calls at least [minIntervalMs] apart across all callers (TMDB allows about 40 requests
 * a second; we stay well below). [clock] and [wait] are injectable for tests.
 */
class RateLimiter(
    private val minIntervalMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val lock = Mutex()
    private var next = 0L

    suspend fun acquire() {
        lock.withLock {
            val now = clock()
            val at = maxOf(now, next)
            if (at > now) wait(at - now)
            next = at + minIntervalMs
        }
    }
}
