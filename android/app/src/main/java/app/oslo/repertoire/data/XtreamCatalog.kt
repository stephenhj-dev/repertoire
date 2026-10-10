package app.oslo.repertoire.data

import android.os.Handler
import android.os.Looper
import android.util.JsonReader
import android.util.JsonToken
import androidx.room.withTransaction
import app.oslo.repertoire.tmdb.TitleNormalizer
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PortalUrl {
    fun normalize(input: String): String {
        var value = input.trim().trimEnd('/')
        value = value.removeSuffix("/player_api.php").removeSuffix("/xmltv.php").trimEnd('/')
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://$value"
        return value
    }

    fun action(portal: String, username: String, password: String, action: String): String =
        "${account(portal, username, password)}&action=$action"

    /** player_api.php without an action answers with the account (user_info) and server info. */
    fun account(portal: String, username: String, password: String): String {
        val user = URLEncoder.encode(username, Charsets.UTF_8.name())
        val pass = URLEncoder.encode(password, Charsets.UTF_8.name())
        return "${normalize(portal)}/player_api.php?username=$user&password=$pass"
    }

    internal fun open(url: String): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 180_000
            setRequestProperty("User-Agent", "TivimateGuide/1.0")
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw PortalHttpException(code)
        }
        return connection
    }
}

/** What the portal says about the account. Dates are epoch seconds. */
data class PortalAccount(
    val status: String = "",
    val expires: Long? = null,
    val maxConnections: Int? = null,
    val activeConnections: Int? = null,
)

object PortalCheck {
    /** Signs in once and reads user_info. Throws a Portal*Exception or an IO error. Blocking. */
    fun verify(portal: String, username: String, password: String): PortalAccount {
        val connection = PortalUrl.open(PortalUrl.account(portal, username, password))
        try {
            JsonReader(connection.inputStream.bufferedReader()).use { reader ->
                if (reader.peek() != JsonToken.BEGIN_OBJECT) throw PortalFormatException("Not an Xtream account answer")
                var account: PortalAccount? = null
                var auth = 0
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "user_info" && reader.peek() == JsonToken.BEGIN_OBJECT) {
                        val info = HashMap<String, String>()
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            when (reader.peek()) {
                                JsonToken.STRING, JsonToken.NUMBER -> info[key] = reader.nextString()
                                JsonToken.BOOLEAN -> info[key] = if (reader.nextBoolean()) "1" else "0"
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        auth = info["auth"]?.toIntOrNull() ?: 0
                        account = PortalAccount(
                            status = info["status"].orEmpty(),
                            expires = info["exp_date"]?.toLongOrNull(),
                            maxConnections = info["max_connections"]?.toIntOrNull(),
                            activeConnections = info["active_cons"]?.toIntOrNull(),
                        )
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
                if (account == null) throw PortalFormatException("No user_info in the answer")
                if (auth != 1) throw PortalAuthException()
                return account
            }
        } catch (error: IllegalStateException) {
            throw PortalFormatException(error.message ?: "Unreadable answer")
        } catch (error: java.io.EOFException) {
            throw PortalFormatException("Empty answer")
        } finally {
            connection.disconnect()
        }
    }
}

/** TMDB id and year for one movie or series, from get_vod_info / get_series_info. */
data class XtreamTitleInfo(val tmdbId: Int?, val year: Int?)

object XtreamInfo {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    /** Asks the portal about one title. Blocking; null when the portal has nothing useful. */
    fun lookup(portal: String, username: String, password: String, kind: String, remoteId: String): XtreamTitleInfo? {
        val action = if (kind == "show") "get_series_info&series_id=" else "get_vod_info&vod_id="
        val id = URLEncoder.encode(remoteId, Charsets.UTF_8.name())
        val connection = PortalUrl.open(PortalUrl.action(portal, username, password, action + id))
        val body = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? kotlinx.serialization.json.JsonObject ?: return null
        val info = root["info"] as? kotlinx.serialization.json.JsonObject ?: return null
        fun field(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
            (info[name] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
        }
        val tmdbId = field("tmdb_id", "tmdb")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        val year = TitleNormalizer.yearOf(field("releasedate", "releaseDate", "release_date", "year"))
        return if (tmdbId == null && year == null) null else XtreamTitleInfo(tmdbId, year)
    }
}

class CatalogSync(private val database: CatalogDatabase) {
    suspend fun refresh(
        portal: String,
        username: String,
        password: String,
        onProgress: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val main = Handler(Looper.getMainLooper())
        val report: (String) -> Unit = { text -> main.post { onProgress(text) } }
        report("Downloading groups")
        val liveGroups = fetchGroups(portal, username, password, "get_live_categories")
        val movieGroups = fetchGroups(portal, username, password, "get_vod_categories")
        val seriesGroups = fetchGroups(portal, username, password, "get_series_categories")
        report("Downloading channels")
        val live = fetchTitles(portal, username, password, "get_live_streams", "live", "stream_id", report)
        report("Downloading movies")
        val movies = fetchTitles(portal, username, password, "get_vod_streams", "movie", "stream_id", report)
        report("Downloading series")
        val series = fetchTitles(portal, username, password, "get_series", "show", "series_id", report)
        database.withTransaction {
            val dao = database.catalog()
            dao.clearTitles()
            dao.clearGroups()
            dao.insertGroups(liveGroups + movieGroups + seriesGroups)
            live.chunked(800).forEach { dao.insertTitles(it) }
            movies.chunked(800).forEach { dao.insertTitles(it) }
            series.chunked(800).forEach { dao.insertTitles(it) }
        }
        report("Guide ready")
    }

    suspend fun backfillSections() {
        val dao = database.catalog()
        val marks = dao.liveMarks()
        var current: String? = null
        val updates = mutableListOf<Pair<Long, String>>()
        for (mark in marks) {
            if (mark.banner) current = cleanBanner(mark.name)
            else if (current != null) updates += mark.id to current
        }
        if (updates.isEmpty()) return
        database.withTransaction {
            updates.forEach { (id, section) -> dao.setSection(id, section) }
        }
    }

    private fun fetchGroups(portal: String, username: String, password: String, action: String): List<GroupRow> {
        val kind = when (action) {
            "get_live_categories" -> "live"
            "get_vod_categories" -> "movie"
            else -> "show"
        }
        val rows = mutableListOf<GroupRow>()
        readArray(PortalUrl.action(portal, username, password, action)) { item ->
            val id = item["category_id"] ?: return@readArray
            val name = item["category_name"]?.trim().orEmpty()
            if (name.isNotEmpty()) rows += GroupRow(kind, id, name)
        }
        return rows
    }

    private fun fetchTitles(
        portal: String,
        username: String,
        password: String,
        action: String,
        kind: String,
        idKey: String,
        onProgress: (String) -> Unit,
    ): List<TitleRow> {
        val incoming = mutableListOf<Incoming>()
        readArray(PortalUrl.action(portal, username, password, action)) { item ->
            val name = item["name"]?.trim().orEmpty()
            val id = item[idKey] ?: return@readArray
            if (name.isEmpty()) return@readArray
            val icon = (item["stream_icon"] ?: item["cover"])?.trim()?.takeIf { it.startsWith("http") }
            val tmdbId = (item["tmdb"] ?: item["tmdb_id"])?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            val year = TitleNormalizer.yearOf(item["year"] ?: item["releaseDate"] ?: item["release_date"] ?: item["releasedate"])
            incoming += Incoming(name, id, item["category_id"], icon, tmdbId, year)
            if (incoming.size % 5000 == 0) onProgress("$kind ${incoming.size}")
        }
        val classes = if (kind == "live") {
            assignSections(incoming.map { classifyName(it.name) })
        } else {
            incoming.map { NameClass(banner = false, country = null, groupCode = null) }
        }
        return incoming.mapIndexed { index, row ->
            val mark = classes[index]
            TitleRow(
                kind = kind,
                name = row.name,
                remoteId = row.remoteId,
                categoryId = row.categoryId,
                country = mark.country,
                groupCode = mark.groupCode,
                banner = mark.banner,
                sortOrder = index,
                section = if (mark.banner) null else mark.section,
                icon = row.icon,
                tmdbId = row.tmdbId,
                year = row.year,
            )
        }
    }

    private fun readArray(url: String, onItem: (Map<String, String>) -> Unit) {
        val connection = PortalUrl.open(url)
        try {
            JsonReader(connection.inputStream.bufferedReader()).use { reader ->
                if (reader.peek() == JsonToken.BEGIN_OBJECT) throw PortalAuthException()
                if (reader.peek() != JsonToken.BEGIN_ARRAY) throw PortalFormatException("The portal did not return a catalog")
                reader.beginArray()
                while (reader.hasNext()) onItem(readObject(reader))
                reader.endArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readObject(reader: JsonReader): Map<String, String> {
        val values = HashMap<String, String>(4)
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            if (key !in FIELDS) {
                reader.skipValue()
                continue
            }
            when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> values[key] = reader.nextString()
                JsonToken.NULL -> reader.nextNull()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return values
    }

    private data class Incoming(
        val name: String,
        val remoteId: String,
        val categoryId: String?,
        val icon: String?,
        val tmdbId: Int? = null,
        val year: Int? = null,
    )

    private companion object {
        val FIELDS = setOf(
            "name", "stream_id", "series_id", "category_id", "category_name", "stream_icon", "cover",
            "tmdb", "tmdb_id", "year", "releaseDate", "release_date", "releasedate",
        )
    }
}
