package app.oslo.repertoire.tmdb

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class TmdbHttpException(val code: Int) : IOException("TMDB answered $code")

/**
 * Response cache: a small in-memory LRU in front of one JSON file per request in [directory].
 * Entries older than [ttlMs] are ignored and refetched.
 */
class TmdbCache(private val directory: File?, private val ttlMs: Long = 7L * 24 * 60 * 60 * 1000, private val clock: () -> Long = System::currentTimeMillis) {
    private data class Entry(val body: String, val time: Long)

    private val memory = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > 120
    }

    @Synchronized
    fun get(key: String): String? {
        val now = clock()
        memory[key]?.let { if (now - it.time < ttlMs) return it.body else memory.remove(key) }
        val file = fileFor(key) ?: return null
        if (!file.exists() || now - file.lastModified() >= ttlMs) return null
        val body = runCatching { file.readText() }.getOrNull() ?: return null
        memory[key] = Entry(body, file.lastModified())
        return body
    }

    @Synchronized
    fun put(key: String, body: String, persist: Boolean) {
        memory[key] = Entry(body, clock())
        if (!persist) return
        val file = fileFor(key) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(body)
            temp.renameTo(file)
        }
    }

    /** Drops expired files so the cache directory doesn't grow forever. */
    fun prune() {
        val now = clock()
        directory?.listFiles()?.forEach { if (now - it.lastModified() >= ttlMs) it.delete() }
    }

    private fun fileFor(key: String): File? {
        val dir = directory ?: return null
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".json")
    }
}

/** Minimal TMDB v3 client over HttpURLConnection (like the Xtream code), authenticated with a v4 read token. */
class TmdbClient(private val token: String, private val cache: TmdbCache) {
    val enabled: Boolean get() = token.isNotBlank()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    suspend fun <T> get(
        path: String,
        params: Map<String, String?> = emptyMap(),
        serializer: KSerializer<T>,
        persist: Boolean = true,
    ): T = withContext(Dispatchers.IO) {
        check(enabled) { "TMDB is not configured" }
        val query = params.filterValues { !it.isNullOrBlank() }.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val url = "$BASE$path" + if (query.isEmpty()) "" else "?$query"
        val body = cache.get(url) ?: fetch(url).also { cache.put(url, it, persist) }
        json.decodeFromString(serializer, body)
    }

    private fun fetch(url: String): String {
        var attempt = 0
        while (true) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Repertoire/1.0")
            }
            try {
                val code = connection.responseCode
                // Rate limited: wait as told (briefly) and try once more.
                if (code == 429 && attempt == 0) {
                    attempt++
                    val wait = connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(1, 5) ?: 1
                    Thread.sleep(wait * 1000)
                    continue
                }
                if (code !in 200..299) throw TmdbHttpException(code)
                return connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val BASE = "https://api.themoviedb.org/3"
    }
}
