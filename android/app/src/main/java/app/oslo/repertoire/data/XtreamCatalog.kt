package app.oslo.repertoire.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    /** Stream details for one catalog version. Blocking; null when the portal has none. */
    fun details(portal: String, username: String, password: String, kind: String, remoteId: String): VersionDetail? {
        val action = if (kind == "show") "get_series_info&series_id=" else "get_vod_info&vod_id="
        val id = URLEncoder.encode(remoteId, Charsets.UTF_8.name())
        val connection = PortalUrl.open(PortalUrl.action(portal, username, password, action + id))
        val body = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        return VersionDetail.parse(body)
    }

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

/** Stream details of one catalog version from get_vod_info / get_series_info (first episode for series). */
data class VersionDetail(
    val width: Int? = null,
    val height: Int? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
    val audioLanguage: String? = null,
    val durationSecs: Int? = null,
    val bitrateKbps: Int? = null,
    val container: String? = null,
) {
    /** "1920×1080 · H264 · AAC 5.1 · eng · 1h 51m"; empty when the portal said nothing. */
    val line: String
        get() = buildList {
            if (width != null && height != null && width > 0 && height > 0) add("$width×$height")
            videoCodec?.let { add(it.uppercase()) }
            val audio = listOfNotNull(
                audioCodec?.uppercase(),
                audioChannels?.let { when (it) { 1 -> "mono"; 2 -> "stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "${it}ch" } },
            ).joinToString(" ")
            if (audio.isNotEmpty()) add(audio)
            audioLanguage?.let { add(it) }
            durationSecs?.takeIf { it > 0 }?.let { add(if (it >= 3600) "${it / 3600}h ${(it % 3600) / 60}m" else "${it / 60}m") }
            bitrateKbps?.takeIf { it > 0 }?.let { add(if (it >= 1000) "%.1f Mb/s".format(it / 1000.0) else "$it kb/s") }
            container?.let { add(it.lowercase()) }
        }.joinToString(" · ")

    companion object {
        /** Parses get_vod_info / get_series_info JSON. Pure, tolerant of missing or odd fields. */
        fun parse(body: String): VersionDetail? {
            val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
            var info = root["info"] as? JsonObject
            var container = ((root["movie_data"] as? JsonObject)?.get("container_extension") as? JsonPrimitive)?.contentOrNull
            // Series: the first episode carries the stream details.
            val episodes = root["episodes"]
            val firstEpisode = when (episodes) {
                is JsonObject -> (episodes.values.firstOrNull() as? JsonArray)?.firstOrNull() as? JsonObject
                is JsonArray -> (episodes.firstOrNull() as? JsonArray)?.firstOrNull() as? JsonObject
                else -> null
            }
            if (firstEpisode != null) {
                (firstEpisode["info"] as? JsonObject)?.let { if (it["video"] != null || it["duration_secs"] != null) info = it }
                container = container ?: (firstEpisode["container_extension"] as? JsonPrimitive)?.contentOrNull
            }
            val data = info ?: return null
            fun text(obj: JsonObject?, name: String) = (obj?.get(name) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
            fun number(obj: JsonObject?, name: String) = text(obj, name)?.toDoubleOrNull()?.toInt()
            val video = data["video"] as? JsonObject
            val audio = data["audio"] as? JsonObject
            val tags = audio?.get("tags") as? JsonObject
            val duration = number(data, "duration_secs") ?: text(data, "duration")?.let(::clockSeconds)
            val detail = VersionDetail(
                width = number(video, "width"),
                height = number(video, "height"),
                videoCodec = text(video, "codec_name"),
                audioCodec = text(audio, "codec_name"),
                audioChannels = number(audio, "channels"),
                audioLanguage = text(tags, "language"),
                durationSecs = duration,
                bitrateKbps = number(data, "bitrate"),
                container = container?.takeIf { it.isNotBlank() },
            )
            return detail.takeIf { it.line.isNotEmpty() }
        }

        /** "01:51:00" -> 6660. */
        private fun clockSeconds(value: String): Int? {
            val parts = value.trim().split(':').map { it.toIntOrNull() ?: return null }
            return when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                else -> null
            }
        }
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
