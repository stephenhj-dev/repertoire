package app.oslo.repertoire.ui.watchlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.library.Version
import app.oslo.repertoire.library.VersionPicker
import app.oslo.repertoire.navigation.TitleDetailRoute
import app.oslo.repertoire.tmdb.TmdbImages
import app.oslo.repertoire.ui.components.EmptyState
import app.oslo.repertoire.ui.components.SkeletonList
import app.oslo.repertoire.ui.details.PosterImage
import app.oslo.repertoire.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch

/** One watchlist: play it in order, reorder by dragging, mark watched, remove, share. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    model: GuideViewModel,
    onBack: () -> Unit,
    onTitle: (TitleDetailRoute) -> Unit,
    bottomInset: PaddingValues,
    page: WatchlistViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = page.list
    val scroll = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, onMove = page::move, onDrop = page::drop)

    LaunchedEffect(page.deleted) { if (page.deleted) onBack() }

    Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
        TopAppBar(
            title = { Text(list?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            actions = {
                IconButton(onClick = {
                    scope.launch { page.share()?.let { shareText(context, list?.name ?: "Watchlist", it) } }
                }, enabled = list != null) { Icon(Icons.Rounded.Share, contentDescription = "Share list") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(
                            text = { Text("Mark all unwatched") },
                            enabled = page.items.any { it.row.watched },
                            onClick = { menu = false; page.markAllUnwatched() },
                        )
                        DropdownMenuItem(text = { Text("Delete list") }, onClick = { menu = false; deleting = true })
                    }
                }
            },
            scrollBehavior = scroll,
        )
        when {
            !page.loaded -> SkeletonList(rows = 5, poster = true)
            page.items.isEmpty() -> EmptyState(
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                title = "Nothing here yet",
                body = "Open a movie or show and tap Save to add it to this list.",
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset.calculateBottomPadding() + 24.dp),
            ) {
                item(key = "header", contentType = "header") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        val count = page.items.size
                        val missing = page.items.count { !it.inLibrary }
                        Text(
                            buildString {
                                if (page.list?.personId != null) {
                                    append("${count - page.unwatched} of $count watched")
                                } else {
                                    append(if (count == 1) "1 title" else "$count titles")
                                    append(" · ${page.unwatched} to watch")
                                }
                                if (missing > 0) append(" · $missing not in your library")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { model.startQueue(list?.name ?: "Watchlist", page.queueItems(), page.route.listId) },
                            enabled = page.playable > 0 && model.tune == null,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (page.playable > 0) "Play on ${model.deviceName}" else "Nothing to play")
                        }
                    }
                }
                items(page.items, key = { it.row.id }, contentType = { REORDERABLE }) { item ->
                    WatchlistItemRow(model, page, item, reorder, onOpen = {
                        onTitle(
                            TitleDetailRoute(
                                kind = item.row.kind,
                                name = item.versions.firstOrNull() ?: item.row.catalogName ?: item.row.title,
                                icon = TmdbImages.poster(item.row.posterPath),
                                tmdbId = item.row.tmdbId.takeIf { it > 0 },
                            ),
                        )
                    })
                }
            }
        }
    }

    if (renaming && list != null) {
        NameDialog("Rename list", list.name, "Rename", onConfirm = { page.rename(it); renaming = false }, onDismiss = { renaming = false })
    }
    if (deleting && list != null) {
        ConfirmDeleteDialog(list.name, onConfirm = { deleting = false; page.delete() }, onDismiss = { deleting = false })
    }
}

@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.WatchlistItemRow(
    model: GuideViewModel,
    page: WatchlistViewModel,
    item: ShownItem,
    reorder: ReorderState,
    onOpen: () -> Unit,
) {
    val row = item.row
    val dragging = reorder.draggingKey == row.id
    var menu by remember { mutableStateOf(false) }
    val status = LocalStatusColors.current
    ListItem(
        modifier = Modifier
            .then(if (dragging) Modifier else Modifier.animateItem())
            .draggedRow(reorder, row.id)
            .clickable(onClickLabel = "Details", onClick = onOpen)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(if (row.watched) "Mark unwatched" else "Mark watched") { page.setWatched(item, !row.watched); true },
                    CustomAccessibilityAction("Move up") { page.moveBy(row.id, -1); true },
                    CustomAccessibilityAction("Move down") { page.moveBy(row.id, 1); true },
                    CustomAccessibilityAction("Remove") { page.remove(item); true },
                )
            },
        leadingContent = {
            Box {
                PosterImage(
                    TmdbImages.poster(row.posterPath),
                    row.title,
                    width = 44.dp,
                    height = 64.dp,
                    modifier = Modifier.alpha(if (row.watched || !item.inLibrary) 0.5f else 1f),
                )
                if (row.watched) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Watched",
                        tint = status.success,
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }
        },
        overlineContent = {
            Text(listOfNotNull(if (row.kind == "show") "Series" else "Movie", row.year?.toString()).joinToString(" · "), maxLines = 1)
        },
        headlineContent = {
            Text(
                row.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (row.watched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            if (!item.inLibrary) {
                Text("Not in your library", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            } else {
                val summary = VersionPicker.summary(item.versions.map(::Version), model.playbackPrefs.language)
                Text(
                    listOfNotNull(summary.line.ifEmpty { null }, summary.moreVersions).joinToString("  "),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More for ${row.title}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (item.inLibrary) {
                            DropdownMenuItem(
                                text = { Text("Play now") },
                                enabled = model.tune == null,
                                onClick = { menu = false; model.playBest(item.versions) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (row.watched) "Mark unwatched" else "Mark watched") },
                            onClick = { menu = false; page.setWatched(item, !row.watched) },
                        )
                        DropdownMenuItem(text = { Text("Remove from list") }, onClick = {
                            menu = false
                            page.remove(item)
                            model.notify("Removed ${row.title}")
                        })
                    }
                }
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.dragHandle(reorder, row.id).padding(12.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        ),
    )
}
