package app.oslo.repertoire.splash

import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One film on the launch wall. */
@Serializable
data class CollageMovie(
    val id: Int,
    val title: String,
    val posterPath: String,
    val year: Int? = null,
    val votes: Int = 0,
    val rating: Double = 0.0,
)

/** The cached wall: which films, and when the list was fetched. */
@Serializable
data class CollageList(val fetchedAt: Long, val movies: List<CollageMovie>)

/**
 * Picks the films for the launch wall and lays them out. Pure, so it is unit-tested.
 *
 * The canon is TMDB's top rated movies with a high vote count, so it is the films everyone
 * agrees on (The Godfather, Casablanca, 2001, Seven Samurai...) rather than obscure titles with
 * a handful of perfect scores; a few acclaimed recent films are blended in.
 */
object CollageCanon {
    const val MIN_VOTES = 5_000
    const val LIMIT = 320
    const val RECENT = 16
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** A TMDB image path we can turn into a file name and a URL ("/abc123.jpg"). */
    fun validPoster(path: String?): Boolean {
        if (path == null || path.length < 6 || !path.startsWith("/")) return false
        val lower = path.lowercase()
        return (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".webp")) &&
            path.drop(1).all { it.isLetterOrDigit() && it.code < 128 || it == '.' || it == '_' || it == '-' }
    }

    /** File name for a cached poster thumbnail. */
    fun fileName(posterPath: String): String = posterPath.trimStart('/')

    fun select(
        topRated: List<CollageMovie>,
        recent: List<CollageMovie>,
        minVotes: Int = MIN_VOTES,
        limit: Int = LIMIT,
        recentCount: Int = RECENT,
    ): List<CollageMovie> {
        val canon = topRated.asSequence()
            .filter { it.votes >= minVotes && validPoster(it.posterPath) }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<CollageMovie> { it.rating }.thenByDescending { it.votes })
            .take((limit - recentCount).coerceAtLeast(0))
            .toList()
        val ids = canon.mapTo(HashSet()) { it.id }
        val fresh = recent.asSequence()
            .filter { it.id !in ids && validPoster(it.posterPath) }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<CollageMovie> { it.rating }.thenByDescending { it.votes })
            .take(recentCount.coerceAtMost(limit))
            .toList()
        return canon + fresh
    }

    /** True when the list should be fetched again (older than a week, or the clock went back). */
    fun isStale(fetchedAt: Long, now: Long, maxAge: Long = MAX_AGE_MS): Boolean =
        fetchedAt <= 0 || now < fetchedAt || now - fetchedAt >= maxAge

    fun encode(list: CollageList): String = json.encodeToString(CollageList.serializer(), list)

    fun decode(text: String?): CollageList? =
        text?.let { runCatching { json.decodeFromString(CollageList.serializer(), it) }.getOrNull() }
            ?.let { list -> list.copy(movies = list.movies.filter { validPoster(it.posterPath) }) }

    /** A different handful each launch, from the films whose thumbnails are on disk. */
    fun pickForLaunch(movies: List<CollageMovie>, cached: Set<String>, count: Int, random: Random = Random.Default): List<CollageMovie> =
        movies.filter { fileName(it.posterPath) in cached }.shuffled(random).take(count)

    /**
     * Deals [items] into [columns] columns of [perColumn] cells. Every item is used before any
     * repeats; with repeats, a column never shows the same item twice in a row and neighbouring
     * columns are offset so the wall doesn't look tiled.
     */
    fun <T> columns(items: List<T>, columns: Int, perColumn: Int): List<List<T>> {
        if (items.isEmpty() || columns <= 0 || perColumn <= 0) return List(columns.coerceAtLeast(0)) { emptyList() }
        val n = items.size
        // Row-major dealing: all unique while there are enough items. With fewer, a stride that
        // isn't a multiple of n keeps vertical neighbours different (and horizontal ones differ by 1).
        val stride = if (columns % n == 0) columns + 1 else columns
        return List(columns) { c -> List(perColumn) { r -> items[(r * stride + c) % n] } }
    }
}
