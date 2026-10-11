package app.oslo.repertoire.watchlist

import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Pure list operations, unit tested. */
object WatchlistOrder {
    /** [items] with the element at [from] moved to [to] (both clamped). */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices) return items
        val target = to.coerceIn(0, items.lastIndex)
        if (from == target) return items
        val out = items.toMutableList()
        val moved = out.removeAt(from)
        out.add(target, moved)
        return out
    }

    /** Moves the element with key [fromKey] to where [toKey] is. */
    fun <T, K> moveByKey(items: List<T>, key: (T) -> K, fromKey: K, toKey: K): List<T> {
        val from = items.indexOfFirst { key(it) == fromKey }
        val to = items.indexOfFirst { key(it) == toKey }
        if (from < 0 || to < 0) return items
        return move(items, from, to)
    }
}

/** A title in a play queue: catalog versions (empty = not in the library) and whether it's watched. */
data class QueueItem(
    val title: String,
    val versions: List<String>,
    val watched: Boolean = false,
    /** Watchlist item row, so advancing can mark it watched. */
    val itemId: Long? = null,
) {
    val inLibrary: Boolean get() = versions.isNotEmpty()
}

object PlayOrder {
    /** Index of the first item after [after] that is unwatched and in the library, or null. */
    fun next(items: List<QueueItem>, after: Int): Int? {
        for (i in (after + 1).coerceAtLeast(0) until items.size) {
            val item = items[i]
            if (item.inLibrary && !item.watched) return i
        }
        return null
    }

    fun first(items: List<QueueItem>): Int? = next(items, -1)

    /** How many playable items remain after [index]. */
    fun remaining(items: List<QueueItem>, index: Int): Int =
        items.withIndex().count { (i, item) -> i > index && item.inLibrary && !item.watched }
}

/** A shared list: compact JSON, base64url, behind [PREFIX]. Offline; no server involved. */
object WatchlistShare {
    const val PREFIX = "repertoire://list/"

    @Serializable
    data class SharedItem(
        @SerialName("k") val kind: String,
        @SerialName("i") val tmdbId: Int,
        @SerialName("t") val title: String,
        @SerialName("y") val year: Int? = null,
        @SerialName("p") val posterPath: String? = null,
        @SerialName("c") val catalogName: String? = null,
    )

    @Serializable
    data class SharedList(
        @SerialName("v") val version: Int = 1,
        @SerialName("n") val name: String,
        @SerialName("items") val items: List<SharedItem>,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(list: SharedList): String {
        val bytes = json.encodeToString(SharedList.serializer(), list).toByteArray(Charsets.UTF_8)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Finds a shared list anywhere in [text] (e.g. inside a chat message). Null when absent or broken. */
    fun decode(text: String): SharedList? {
        val start = text.indexOf(PREFIX)
        if (start < 0) return null
        val rest = text.substring(start + PREFIX.length)
        val payload = rest.takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' || it == '=' }
        if (payload.isEmpty()) return null
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(payload.trimEnd('='))
            json.decodeFromString(SharedList.serializer(), String(bytes, Charsets.UTF_8))
        }.getOrNull()?.takeIf { it.name.isNotBlank() || it.items.isNotEmpty() }?.let { list ->
            // Keep only sane entries.
            list.copy(
                name = list.name.trim().take(80).ifBlank { "Shared list" },
                items = list.items.filter { (it.kind == "movie" || it.kind == "show") && (it.tmdbId > 0 || !it.catalogName.isNullOrBlank()) }
                    .take(1000),
            )
        }
    }

    /** The message put on the share sheet. */
    fun message(list: SharedList): String =
        "${list.name} · ${list.items.size} ${if (list.items.size == 1) "title" else "titles"} on Repertoire\n" + encode(list)
}

/** The first [count] distinct posters of each list, in list order, for the overview's stacks. */
fun firstPostersPerList(rows: List<Pair<Long, String>>, count: Int = 5): Map<Long, List<String>> {
    val out = LinkedHashMap<Long, MutableList<String>>()
    for ((list, poster) in rows) {
        if (poster.isBlank()) continue
        val posters = out.getOrPut(list) { mutableListOf() }
        if (posters.size < count && poster !in posters) posters += poster
    }
    return out
}
