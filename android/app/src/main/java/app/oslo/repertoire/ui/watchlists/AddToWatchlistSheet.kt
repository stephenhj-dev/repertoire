package app.oslo.repertoire.ui.watchlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.watchlist.TitleRef
import kotlinx.coroutines.launch

/** Picks the lists [title] is on: tap to add or remove, or make a new list with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToWatchlistSheet(model: GuideViewModel, title: TitleRef, onDismiss: () -> Unit) {
    val dao = model.watchlists.dao
    val lists by remember { dao.summaries() }.collectAsState(initial = emptyList())
    val containing by remember(title) { dao.listsWith(title.kind, title.tmdbId, title.catalogName) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text("Save “${title.title}” to", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "new") {
                    ListItem(
                        headlineContent = { Text("New list") },
                        leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        modifier = Modifier.clickable { naming = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                items(lists, key = { it.id }) { list ->
                    val on = list.id in containing
                    ListItem(
                        headlineContent = { Text(list.name) },
                        supportingContent = { Text(if (list.count == 1) "1 title" else "${list.count} titles") },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                        trailingContent = { Checkbox(checked = on, onCheckedChange = null) },
                        modifier = Modifier.clickable(role = Role.Checkbox) {
                            scope.launch {
                                if (on) {
                                    model.watchlists.remove(list.id, title)
                                    model.notify("Removed from ${list.name}")
                                } else {
                                    model.watchlists.add(list.id, title)
                                    model.notify("Saved to ${list.name}")
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
    if (naming) {
        NameDialog("New list", "", "Create", onConfirm = { name ->
            naming = false
            scope.launch {
                model.watchlists.create(name, listOf(title))
                model.notify("Saved to $name")
                onDismiss()
            }
        }, onDismiss = { naming = false })
    }
}
