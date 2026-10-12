package app.oslo.repertoire.ui.watchlists

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.data.WatchlistSummary
import app.oslo.repertoire.tmdb.TmdbImages
import app.oslo.repertoire.ui.components.EmptyState
import app.oslo.repertoire.ui.details.PosterImage
import kotlinx.coroutines.launch

/** The Lists tab: every watchlist, reorderable by dragging the handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistsScreen(
    model: GuideViewModel,
    onOpen: (Long) -> Unit,
    bottomInset: PaddingValues,
    onOpenSettings: () -> Unit = {},
    page: WatchlistsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<WatchlistSummary?>(null) }
    var deleting by remember { mutableStateOf<WatchlistSummary?>(null) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, onMove = page::move, onDrop = page::drop)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
            LargeTopAppBar(
                title = { Text("Lists") },
                actions = {
                    IconButton(onClick = { importing = true }) { Icon(Icons.Rounded.ContentPaste, contentDescription = "Import a shared list") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                },
                scrollBehavior = scroll,
            )
            if (page.loaded && page.lists.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = "No watchlists yet",
                    body = "Make lists of movies and shows to watch in order. Add titles from their page, or save an actor's or director's films from their page.",
                    action = "New list",
                    onAction = { creating = true },
                    secondary = "Import a shared list",
                    onSecondary = { importing = true },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomInset.calculateBottomPadding() + 88.dp),
                ) {
                    items(page.lists, key = { it.id }, contentType = { REORDERABLE }) { list ->
                        val dragging = reorder.draggingKey == list.id
                        var menu by remember { mutableStateOf(false) }
                        ListItem(
                            modifier = Modifier
                                .then(if (dragging) Modifier else Modifier.animateItem())
                                .draggedRow(reorder, list.id)
                                .clickable(onClickLabel = "Open ${list.name}") { onOpen(list.id) }
                                .semantics {
                                    customActions = listOf(
                                        CustomAccessibilityAction("Move up") { page.moveBy(list.id, -1); true },
                                        CustomAccessibilityAction("Move down") { page.moveBy(list.id, 1); true },
                                    )
                                },
                            leadingContent = {
                                PosterStack(page.posters[list.id].orEmpty().ifEmpty { listOfNotNull(list.posterPath) }, list.name)
                            },
                            headlineContent = { Text(list.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(countLine(list)) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More for ${list.name}") }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(text = { Text("Play") }, onClick = {
                                                menu = false
                                                scope.launch { playList(model, list.id, list.name) }
                                            })
                                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = list })
                                            DropdownMenuItem(text = { Text("Share") }, onClick = {
                                                menu = false
                                                scope.launch { page.share(list.id)?.let { shareText(context, list.name, it) } }
                                            })
                                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = list })
                                        }
                                    }
                                    Icon(
                                        Icons.Rounded.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.dragHandle(reorder, list.id).padding(12.dp),
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                }
            }
        }
        if (!page.loaded || page.lists.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New list") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottomInset.calculateBottomPadding() + 16.dp),
            )
        }
    }

    if (creating) {
        NameDialog("New list", "", "Create", onConfirm = { name ->
            creating = false
            page.create(name) { onOpen(it) }
        }, onDismiss = { creating = false })
    }
    renaming?.let { list ->
        NameDialog("Rename list", list.name, "Rename", onConfirm = { page.rename(list.id, it); renaming = null }, onDismiss = { renaming = null })
    }
    deleting?.let { list ->
        ConfirmDeleteDialog(list.name, onConfirm = { page.delete(list.id); deleting = null }, onDismiss = { deleting = null })
    }
    if (importing) {
        ImportDialog(context, onImport = { text ->
            importing = false
            model.offerImport(text)
        }, onDismiss = { importing = false })
    }
}

/** A list's first posters fanned out like a hand of cards, Letterboxd-style; one blank card when empty. */
@Composable
private fun PosterStack(paths: List<String>, name: String) {
    val shown = paths.take(4)
    val cardW = 46.dp
    val step = 18.dp
    Box(Modifier.size(width = cardW + step * 3, height = 70.dp)) {
        if (shown.isEmpty()) {
            PosterImage(null, name, width = cardW, height = 69.dp, corner = 6.dp)
        }
        // Back to front: the first poster sits on top.
        shown.withIndex().reversed().forEach { (i, path) ->
            PosterImage(
                TmdbImages.poster(path),
                name,
                width = cardW,
                height = 69.dp,
                corner = 6.dp,
                modifier = Modifier
                    .offset(x = step * i)
                    .shadow(6.dp, androidx.compose.foundation.shape.RoundedCornerShape(6.dp), ambientColor = Color.Black, spotColor = Color.Black),
            )
        }
    }
}

private fun countLine(list: WatchlistSummary): String {
    val titles = if (list.count == 1) "1 title" else "${list.count} titles"
    return when {
        list.count == 0 -> "Empty"
        // Lists saved from a person read like a filmography in progress.
        list.personId != null -> "${list.count - list.unwatched} of ${list.count} watched"
        list.unwatched == 0 -> "$titles · all watched"
        list.unwatched == list.count -> titles
        else -> "$titles · ${list.unwatched} to watch"
    }
}

/** Plays a list from the overview (matches its items to the catalog first). */
suspend fun playList(model: GuideViewModel, listId: Long, name: String) {
    val rows = model.watchlists.dao.itemsNow(listId)
    val versions = model.watchlists.versionsFor(rows)
    model.startQueue(
        name,
        rows.map { app.oslo.repertoire.watchlist.QueueItem(it.title, versions[it.id].orEmpty(), it.watched, it.id) },
        listId,
    )
}
