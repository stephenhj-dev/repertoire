package app.oslo.repertoire.splash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.oslo.repertoire.Perf
import app.oslo.repertoire.tmdb.TitleNormalizer
import app.oslo.repertoire.tmdb.Tmdb
import app.oslo.repertoire.tmdb.TmdbMovieListItem
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * The launch wall's films and their poster thumbnails, kept in files/collage so the wall shows
 * instantly and offline. The list is refreshed weekly in the background.
 */
object CollageStore {
    private const val TAG = "Collage"
    private const val PAGES = 25
    private const val SIZE = "w185"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun dir(context: Context) = File(context.filesDir, "collage")
    private fun listFile(context: Context) = File(dir(context), "list.json")
    private fun posters(context: Context) = File(dir(context), "posters")

    fun load(context: Context): CollageList? =
        CollageCanon.decode(runCatching { listFile(context).takeIf { it.exists() }?.readText() }.getOrNull())

    /** Starts a refresh after [delayMs] when the list is missing, old, or thumbnails are missing. */
    fun refreshInBackground(context: Context, delayMs: Long = 6_000) {
        val app = context.applicationContext
        scope.launch {
            delay(delayMs)
            runCatching { refreshIfNeeded(app) }.onFailure {
                if (it is CancellationException) throw it
                Log.d(TAG, "refresh failed: ${it.message}")
            }
        }
    }

    private suspend fun refreshIfNeeded(context: Context) {
        if (!Tmdb.enabled) return
        val cached = load(context)
        val list = if (cached == null || CollageCanon.isStale(cached.fetchedAt, System.currentTimeMillis())) {
            fetch(context).also { save(context, it) }
        } else {
            cached
        }
        download(context, list)
    }

    private suspend fun fetch(context: Context): CollageList {
        val top = (1..PAGES).flatMap { page -> Tmdb.topRatedMovies(context, page).map { it.collage() } }
        val since = Calendar.getInstance().get(Calendar.YEAR) - 4
        val recent = runCatching { Tmdb.acclaimedRecentMovies(context, since).map { it.collage() } }.getOrDefault(emptyList())
        val movies = CollageCanon.select(top, recent)
        Log.d(TAG, "fetched ${top.size} top rated, ${recent.size} recent -> ${movies.size}")
        return CollageList(System.currentTimeMillis(), movies)
    }

    private fun TmdbMovieListItem.collage() = CollageMovie(
        id = id,
        title = title.orEmpty(),
        posterPath = posterPath.orEmpty(),
        year = TitleNormalizer.yearOf(releaseDate),
        votes = voteCount,
        rating = voteAverage,
    )

    private fun save(context: Context, list: CollageList) {
        val file = listFile(context)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "list.json.tmp")
        temp.writeText(CollageCanon.encode(list))
        temp.renameTo(file)
    }

    /** Downloads missing thumbnails (4 at a time) and deletes ones no longer on the list. */
    private suspend fun download(context: Context, list: CollageList) = withContext(Dispatchers.IO) {
        val folder = posters(context).apply { mkdirs() }
        val wanted = list.movies.associateBy { CollageCanon.fileName(it.posterPath) }
        folder.listFiles()?.forEach { if (it.name !in wanted && !it.name.endsWith(".tmp")) it.delete() }
        val gate = Semaphore(4)
        val fetched = java.util.concurrent.atomic.AtomicInteger()
        wanted.filterKeys { !File(folder, it).exists() }.map { (name, movie) ->
            async {
                gate.withPermit {
                    runCatching { fetchPoster(movie.posterPath, File(folder, name)); fetched.incrementAndGet() }
                }
            }
        }.awaitAll()
        if (fetched.get() > 0) Log.d(TAG, "downloaded $fetched posters")
    }

    private fun fetchPoster(path: String, target: File) {
        val connection = (URL("https://image.tmdb.org/t/p/$SIZE$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (connection.responseCode !in 200..299) return
            val temp = File(target.parentFile, target.name + ".tmp")
            connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (temp.length() > 1024) temp.renameTo(target) else temp.delete()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Decodes [count] random cached thumbnails for this launch, with rounded corners baked in so
     * drawing them each frame is a plain bitmap blit. Empty when nothing is cached yet.
     */
    suspend fun launchPosters(context: Context, count: Int = 64): List<ImageBitmap> = withContext(Dispatchers.Default) {
        Perf.time("collage decode") {
            val list = load(context) ?: return@time emptyList()
            val folder = posters(context)
            val cached = folder.list()?.toHashSet() ?: return@time emptyList()
            val picked = CollageCanon.pickForLaunch(list.movies, cached, count)
            if (picked.size < 12) return@time emptyList()
            picked.chunked((picked.size + 3) / 4).map { chunk ->
                async { chunk.mapNotNull { decodeRounded(File(folder, CollageCanon.fileName(it.posterPath))) } }
            }.awaitAll().flatten()
        }
    }

    private fun decodeRounded(file: File): ImageBitmap? {
        val source = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() ?: return null
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        val radius = source.width * 0.06f
        Canvas(out).drawRoundRect(RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()), radius, radius, paint)
        source.recycle()
        return out.asImageBitmap()
    }
}
