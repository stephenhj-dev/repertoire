package app.oslo.repertoire.ui.watchlists

import app.oslo.repertoire.watchlist.firstPostersPerList

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.oslo.repertoire.data.WatchlistItemRow
import app.oslo.repertoire.data.WatchlistRow
import app.oslo.repertoire.data.WatchlistSummary
import app.oslo.repertoire.navigation.WatchlistRoute
import app.oslo.repertoire.watchlist.QueueItem
import app.oslo.repertoire.watchlist.WatchlistOrder
import app.oslo.repertoire.watchlist.Watchlists
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** All lists, in the user's order. */
class WatchlistsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Watchlists(app)

    var lists by mutableStateOf<List<WatchlistSummary>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set

    /** Up to five posters per list. */
    var posters by mutableStateOf<Map<Long, List<String>>>(emptyMap())
        private set
    private var dragging = false

    init {
        viewModelScope.launch {
            repo.dao.summaries().collectLatest {
                if (!dragging) lists = it
                loaded = true
            }
        }
        viewModelScope.launch {
            repo.dao.posters().collectLatest { rows ->
                posters = firstPostersPerList(rows.map { it.listId to it.posterPath })
            }
        }
    }

    fun create(name: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch { onCreated(repo.create(name)) }
    }

    fun rename(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repo.dao.rename(id, clean) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.dao.deleteList(id) }
    }

    fun move(from: Any, to: Any) {
        dragging = true
        lists = WatchlistOrder.moveByKey(lists, { it.id }, from, to)
    }

    /** Accessibility: move one step up (-1) or down (+1). */
    fun moveBy(id: Long, delta: Int) {
        val at = lists.indexOfFirst { it.id == id }
        if (at < 0) return
        lists = WatchlistOrder.move(lists, at, at + delta)
        drop()
    }

    fun drop() {
        val ids = lists.map { it.id }
        viewModelScope.launch {
            repo.dao.orderLists(ids)
            dragging = false
        }
    }

    suspend fun share(id: Long): String? = repo.share(id)
}

/** A list item with its catalog versions (empty = not in the library). */
@Immutable
data class ShownItem(val row: WatchlistItemRow, val versions: List<String>) {
    val inLibrary: Boolean get() = versions.isNotEmpty()
}

/** One list. */
class WatchlistViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    val route: WatchlistRoute = handle.toRoute()
    private val repo = Watchlists(app)

    var list by mutableStateOf<WatchlistRow?>(null)
        private set
    var items by mutableStateOf<List<ShownItem>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    /** True once the list was deleted (the screen closes). */
    var deleted by mutableStateOf(false)
        private set
    private var dragging = false

    init {
        viewModelScope.launch {
            combine(repo.dao.list(route.listId), repo.dao.items(route.listId)) { list, rows -> list to rows }
                .collectLatest { (row, rows) ->
                    if (row == null && loaded) deleted = true
                    list = row
                    val versions = runCatching { repo.versionsFor(rows) }.getOrDefault(emptyMap())
                    if (!dragging) items = rows.map { ShownItem(it, versions[it.id].orEmpty()) }
                    loaded = true
                }
        }
    }

    val unwatched: Int get() = items.count { !it.row.watched }
    val playable: Int get() = items.count { !it.row.watched && it.inLibrary }

    fun queueItems(): List<QueueItem> = items.map { QueueItem(it.row.title, it.versions, it.row.watched, it.row.id) }

    fun setWatched(item: ShownItem, watched: Boolean) {
        viewModelScope.launch { repo.dao.setWatched(item.row.id, watched) }
    }

    fun remove(item: ShownItem) {
        viewModelScope.launch { repo.dao.deleteItem(item.row.id) }
    }

    /** Puts a removed item back where it was (snackbar Undo). */
    fun restore(item: ShownItem) {
        viewModelScope.launch {
            repo.dao.insertItems(listOf(item.row))
        }
    }

    fun markAllUnwatched() {
        viewModelScope.launch { items.filter { it.row.watched }.forEach { repo.dao.setWatched(it.row.id, false) } }
    }

    fun rename(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repo.dao.rename(route.listId, clean) }
    }

    fun delete() {
        viewModelScope.launch {
            repo.dao.deleteList(route.listId)
            deleted = true
        }
    }

    fun move(from: Any, to: Any) {
        dragging = true
        items = WatchlistOrder.moveByKey(items, { it.row.id }, from, to)
    }

    fun moveBy(id: Long, delta: Int) {
        val at = items.indexOfFirst { it.row.id == id }
        if (at < 0) return
        items = WatchlistOrder.move(items, at, at + delta)
        drop()
    }

    fun drop() {
        val ids = items.map { it.row.id }
        viewModelScope.launch {
            repo.dao.orderItems(ids)
            dragging = false
        }
    }

    suspend fun share(): String? = repo.share(route.listId)
}
