package app.oslo.repertoire.tmdb

import android.content.Context
import android.util.Log
import app.oslo.repertoire.data.CatalogDatabase
import app.oslo.repertoire.data.PendingTitle
import app.oslo.repertoire.data.ResolvedTitle
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Looks up catalog movies and series on TMDB and stores the answer in tmdb_resolved, so lists
 * can show clean titles and posters without asking TMDB again. Titles on screen are looked up
 * first ([request]); the rest of the catalog is backfilled slowly in the background. Live
 * channels are never looked up.
 */
class TitleResolver(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val dao = CatalogDatabase.get(app).catalog()
    private val wanted = Channel<List<PendingTitle>>(Channel.UNLIMITED)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val backfillPace = RateLimiter(BACKFILL_SPACING_MS)
    private var job: Job? = null
    @Volatile private var backfillDone = false
    private var lastInvalidate = 0L

    /** Emits after new lookups were saved. */
    val updates: SharedFlow<Unit> = _updates

    fun start() {
        if (!Tmdb.enabled || job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { run() }
    }

    /** Looks these up soon (they are on screen). Movies and series only. */
    fun request(items: List<PendingTitle>) {
        if (!Tmdb.enabled) return
        val fresh = items.filter { it.kind != "live" && inFlight.add(key(it)) }
        if (fresh.isNotEmpty()) wanted.trySend(fresh)
    }

    /** The catalog changed: look for titles without a lookup again. */
    fun catalogChanged() {
        backfillDone = false
        wanted.trySend(emptyList())
    }

    private fun key(item: PendingTitle) = item.kind + "\t" + item.name

    private suspend fun run() {
        var failures = 0
        while (scope.isActive) {
            val urgent = wanted.tryReceive().getOrNull()
            val batch = urgent ?: if (!backfillDone) {
                val pending = runCatching { dao.pendingTitles(System.currentTimeMillis() - RETRY_NO_MATCH_MS, 40) }.getOrDefault(emptyList())
                    .filter { inFlight.add(key(it)) }
                if (pending.isEmpty()) {
                    backfillDone = true
                    invalidate(force = true)
                    null
                } else {
                    pending
                }
            } else {
                null
            } ?: wanted.receive()
            val background = urgent == null && !backfillDone
            val results = try {
                process(batch, background)
            } finally {
                batch.forEach { inFlight.remove(key(it)) }
            }
            if (results.saved.isNotEmpty()) {
                runCatching { dao.saveResolved(results.saved) }
                _updates.tryEmit(Unit)
                invalidate(force = false)
            }
            if (results.offline) {
                // Offline or TMDB unhappy: back off and don't hammer.
                failures++
                delay((15_000L * failures).coerceAtMost(5 * 60_000L))
                backfillDone = false
            } else {
                failures = 0
            }
        }
    }

    private class Results(val saved: List<ResolvedTitle>, val offline: Boolean)

    private suspend fun process(batch: List<PendingTitle>, background: Boolean): Results {
        if (batch.isEmpty()) return Results(emptyList(), offline = false)
        val gate = Semaphore(CONCURRENCY)
        val networkErrors = AtomicInteger()
        val now = System.currentTimeMillis()
        // Saved as "no match" but checked long ago, so the backfill asks again in about an hour.
        fun retrySoon(item: PendingTitle) =
            ResolvedTitle(item.kind, item.name, null, null, null, null, now - RETRY_NO_MATCH_MS + RETRY_ERROR_MS)
        val answers = kotlinx.coroutines.coroutineScope {
            batch.map { item ->
                async {
                    gate.withPermit {
                        if (background) backfillPace.acquire()
                        try {
                            withTimeoutOrNull(30_000) { lookup(item) } ?: run {
                                networkErrors.incrementAndGet()
                                item to null
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: TmdbHttpException) {
                            if (error.code == 429 || error.code >= 500) {
                                networkErrors.incrementAndGet()
                                item to null
                            } else {
                                // 404 for a wrong portal id and the like: remember "no match".
                                item to ResolvedTitle(item.kind, item.name, null, null, null, null, now)
                            }
                        } catch (error: IOException) {
                            networkErrors.incrementAndGet()
                            item to null
                        } catch (error: Exception) {
                            Log.w("TitleResolver", "Lookup failed for ${item.name}", error)
                            item to retrySoon(item)
                        }
                    }
                }
            }.awaitAll()
        }
        val offline = networkErrors.get() > 0 && networkErrors.get() * 2 >= batch.size
        val saved = answers.mapNotNull { (item, answer) -> answer ?: if (offline) null else retrySoon(item) }
        return Results(saved, offline)
    }

    private suspend fun lookup(item: PendingTitle): Pair<PendingTitle, ResolvedTitle> = item to lookupNow(item)

    private suspend fun lookupNow(item: PendingTitle): ResolvedTitle {
        val mediaType = tmdbMediaType(item.kind)
        val now = System.currentTimeMillis()
        val portalId = item.tmdbId
        if (portalId != null) {
            val detail = Tmdb.basic(app, mediaType, portalId)
            return ResolvedTitle(item.kind, item.name, portalId, detail.displayTitle.ifBlank { null }, detail.year, detail.posterPath, now)
        }
        val parsed = TitleNormalizer.parse(item.name)
        val hit = Tmdb.searchCandidate(app, mediaType, parsed.copy(year = item.year ?: parsed.year))
        return ResolvedTitle(item.kind, item.name, hit?.id, hit?.title, hit?.year, hit?.posterPath, now)
    }

    /** The "in your library" index includes looked-up ids; rebuild it now and then, not per batch. */
    private fun invalidate(force: Boolean) {
        val now = System.currentTimeMillis()
        if (force || now - lastInvalidate > 60_000) {
            lastInvalidate = now
            Tmdb.invalidateLibrary()
        }
    }

    private companion object {
        const val CONCURRENCY = 3
        /** Background lookups at most ~6 a second (cache hits included), on top of the client's limit. */
        const val BACKFILL_SPACING_MS = 160L
        /** "No match" answers are asked again after 30 days. */
        const val RETRY_NO_MATCH_MS = 30L * 24 * 60 * 60 * 1000
        const val RETRY_ERROR_MS = 60L * 60 * 1000
    }
}
