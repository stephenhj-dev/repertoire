package app.oslo.repertoire.ui.settings

import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.TextFields
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import app.oslo.repertoire.library.VersionTagParser
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
    onOpenGuides: () -> Unit,
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
                    headlineContent = { Text("TV setup guides") },
                    supportingContent = { Text("Step by step for Shield, Google TV, Fire TV, Sony, TCL and more") },
                    leadingContent = { Icon(Icons.Rounded.Tv, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenGuides),
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

            item { SectionHeader("Playback") }
            item {
                var choosing by remember { mutableStateOf(false) }
                val code = model.playbackPrefs.language
                ListItem(
                    headlineContent = { Text("Preferred audio language") },
                    supportingContent = {
                        Text(
                            languageName(code) + (if (model.audioLanguageIsDevice) " (device language)" else "") +
                                ". Play picks this version when a title has several; English otherwise.",
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.Translate, contentDescription = null) },
                    modifier = Modifier.clickable(onClickLabel = "Change language") { choosing = true },
                    colors = itemColors,
                )
                if (choosing) {
                    LanguageDialog(
                        current = if (model.audioLanguageIsDevice) null else code,
                        onPick = {
                            model.setAudioLanguage(it)
                            choosing = false
                        },
                        onDismiss = { choosing = false },
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("Prefer 4K") },
                    supportingContent = { Text("Pick 4K/UHD versions first. Off: Full HD first, which streams more reliably.") },
                    leadingContent = { Icon(Icons.Rounded.HighQuality, contentDescription = null) },
                    trailingContent = { Switch(checked = model.playbackPrefs.prefer4k, onCheckedChange = null) },
                    modifier = Modifier.clickable(role = Role.Switch) { model.setPrefer4k(!model.playbackPrefs.prefer4k) },
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

            item {
                ListItem(
                    headlineContent = { Text("Posters for movies and series") },
                    supportingContent = { Text("Show them as a poster grid in the Guide and Search, or as rows") },
                    leadingContent = { Icon(Icons.Rounded.GridView, contentDescription = null) },
                    trailingContent = { Switch(checked = model.posterGrid, onCheckedChange = null) },
                    modifier = Modifier.clickable(role = Role.Switch) { model.showPosterGrid(!model.posterGrid) },
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
                ListItem(
                    headlineContent = { Text("Typefaces") },
                    supportingContent = { Text("Fraunces and Inter, both under the SIL Open Font License 1.1") },
                    leadingContent = { Icon(Icons.Rounded.TextFields, contentDescription = null) },
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

private fun languageName(code: String): String =
    java.util.Locale(code.lowercase()).getDisplayLanguage(java.util.Locale.ENGLISH).ifBlank { code } + " ($code)"

@Composable
private fun LanguageDialog(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val device = VersionTagParser.languageForLocale(java.util.Locale.getDefault().language)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preferred audio language") },
        text = {
            LazyColumn {
                item {
                    LanguageOption("Device language · ${languageName(device)}", current == null) { onPick(null) }
                }
                items(VersionTagParser.SETTING_LANGUAGES.size) { index ->
                    val code = VersionTagParser.SETTING_LANGUAGES[index]
                    LanguageOption(languageName(code), current == code) { onPick(code) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
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
