package app.oslo.repertoire.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.R
import app.oslo.repertoire.data.portalHost
import android.content.Intent
import android.net.Uri
import app.oslo.repertoire.remote.NavTransport
import app.oslo.repertoire.tmdb.Tmdb
import app.oslo.repertoire.ui.details.TmdbMark
import app.oslo.repertoire.remote.TvStatus
import app.oslo.repertoire.ui.components.MessageCard
import app.oslo.repertoire.ui.components.SectionHeader
import app.oslo.repertoire.ui.theme.ThemeMode
import app.oslo.repertoire.ui.theme.dynamicColorAvailable
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    model: GuideViewModel,
    tv: TvStatus,
    onOpenPortal: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onRunSetup: () -> Unit,
    bottomInset: PaddingValues,
) {
    LaunchedEffect(model.hasPortal) { model.loadAccount() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
        LargeTopAppBar(title = { Text("Settings") }, scrollBehavior = scroll)
        LazyColumn(contentPadding = PaddingValues(bottom = bottomInset.calculateBottomPadding() + 24.dp)) {
            item { SectionHeader("IPTV portal") }
            item {
                ListItem(
                    headlineContent = { Text(if (model.hasPortal) portalHost(model.portalUrl) else "Not set up") },
                    supportingContent = { Text(accountLine(model)) },
                    leadingContent = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClickLabel = "Edit portal", onClick = onOpenPortal),
                    colors = itemColors,
                )
            }
            if (model.hasPortal) {
                item {
                    ListItem(
                        headlineContent = { Text("Refresh guide") },
                        supportingContent = {
                            Text(
                                when {
                                    model.syncing -> model.syncStep.ifBlank { "Updating…" }
                                    model.counts.total > 0 -> "%,d channels · %,d movies · %,d series".format(
                                        model.counts.live,
                                        model.counts.movies,
                                        model.counts.shows,
                                    )
                                    else -> "Download the catalog from your portal"
                                },
                            )
                        },
                        leadingContent = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                        trailingContent = {
                            if (model.syncing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        },
                        modifier = Modifier.clickable(enabled = !model.syncing, onClick = model::refresh),
                        colors = itemColors,
                    )
                }
            }
            model.syncError?.let { error ->
                item { MessageCard(error, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), action = "Edit portal", onAction = onOpenPortal) }
            }

            item { SectionHeader("TV") }
            item {
                ListItem(
                    headlineContent = { Text(if (model.hasTv) model.deviceName else "No TV chosen") },
                    supportingContent = {
                        Text(
                            if (!model.hasTv) "Find the TV that runs TiviMate"
                            else listOfNotNull(
                                model.deviceModel.takeIf { it.isNotBlank() && it != model.deviceName },
                                model.selectedHost,
                                when (model.transport) {
                                    NavTransport.RemoteV2 -> if (tv.connected) "remote connected" else if (model.paired) "paired" else "not paired"
                                    NavTransport.Adb -> "keys over ADB"
                                    NavTransport.None -> "remote unavailable"
                                },
                            ).joinToString(" · "),
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.Tv, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClickLabel = "Switch TV", onClick = onOpenDevices),
                    colors = itemColors,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Stay connected") },
                    supportingContent = {
                        Text(if (model.paired) "Keep the remote linked; the pairing is kept either way." else "Pair the TV's remote first.")
                    },
                    leadingContent = { Icon(Icons.Rounded.Link, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = model.linked,
                            onCheckedChange = null,
                            enabled = model.paired,
                        )
                    },
                    modifier = Modifier.clickable(enabled = model.paired, role = Role.Switch) {
                        if (model.linked) model.disconnectTv() else model.connect()
                    },
                    colors = itemColors,
                )
            }

            item { SectionHeader("Appearance") }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = ThemeMode.entries
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = model.themeMode == mode,
                                onClick = { model.setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                            ) { Text(if (mode == ThemeMode.System) "System" else mode.name) }
                        }
                    }
                }
            }
            if (dynamicColorAvailable) {
                item {
                    ListItem(
                        headlineContent = { Text("Wallpaper colors") },
                        supportingContent = { Text("Use Material You colors from your wallpaper instead of Repertoire amber") },
                        leadingContent = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                        trailingContent = { Switch(checked = model.dynamicColor, onCheckedChange = null) },
                        modifier = Modifier.clickable(role = Role.Switch) { model.setDynamic(!model.dynamicColor) },
                        colors = itemColors,
                    )
                }
            }

            item { SectionHeader("Library") }
            item {
                ListItem(
                    headlineContent = { Text("Watchlist") },
                    supportingContent = { Text("Coming soon: save movies and shows for later") },
                    leadingContent = { Icon(Icons.Rounded.BookmarkBorder, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenWatchlist),
                    colors = itemColors,
                )
            }

            item { SectionHeader("About") }
            item {
                ListItem(
                    headlineContent = { Text("Run setup again") },
                    supportingContent = { Text("Portal, TV and pairing, step by step") },
                    leadingContent = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onRunSetup),
                    colors = itemColors,
                )
            }
            item {
                val context = LocalContext.current
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                }
                ListItem(
                    headlineContent = { Text("${context.getString(R.string.app_name)} $version") },
                    supportingContent = {
                        Text("${context.getString(R.string.app_tagline)}. Video plays on the TV; this phone is the guide and the remote.")
                    },
                    leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    colors = itemColors,
                )
            }
            item {
                val context = LocalContext.current
                ListItem(
                    headlineContent = { Text("Movie and TV data from TMDB") },
                    supportingContent = {
                        Text(
                            "This product uses the TMDB API but is not endorsed or certified by TMDB." +
                                if (Tmdb.enabled) "" else "\nTMDB features are off in this build (no API token).",
                        )
                    },
                    leadingContent = { TmdbMark(height = 20.dp) },
                    modifier = Modifier.clickable(onClickLabel = "Open themoviedb.org") {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org/")))
                        }
                    },
                    colors = itemColors,
                )
            }
        }
    }
}

private fun accountLine(model: GuideViewModel): String {
    if (!model.hasPortal) return "Sign in with your Xtream login"
    val account = model.account
    val parts = mutableListOf(model.username)
    if (account != null) {
        if (account.status.isNotBlank()) parts += account.status
        account.expires?.let { parts += "until " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it * 1000)) }
    }
    return parts.joinToString(" · ")
}
