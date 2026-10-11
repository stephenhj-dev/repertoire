package app.oslo.repertoire.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.library.Version

/** Small tonal tag ("EN", "4K"). */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (strong) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/**
 * Tonal round play button. Tap plays the preferred version; long-press (when [onLongPress] is
 * set) opens the version chooser.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayButton(
    tuning: Boolean,
    enabled: Boolean,
    label: String,
    onPlay: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (tuning) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
            return@Box
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = label,
                    onLongClickLabel = if (onLongPress != null) "Choose version" else null,
                    onLongClick = onLongPress,
                    onClick = onPlay,
                )
                .semantics {
                    contentDescription = label
                    role = Role.Button
                    if (onLongPress != null) customActions = listOf(CustomAccessibilityAction("Choose version") { onLongPress(); true })
                },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, contentDescription = null) }
        }
    }
}

/** Extra line under a version: its portal details, or loading. */
sealed interface VersionInfoLine {
    data object Loading : VersionInfoLine
    data class Text(val text: String) : VersionInfoLine
}

/**
 * One catalog version: language/quality tags and the provider's name, playable. With [onInfo],
 * an info button loads its portal details into [info].
 */
@Composable
fun VersionRow(
    version: Version,
    best: Boolean,
    tuning: Boolean,
    enabled: Boolean,
    onPlay: () -> Unit,
    info: VersionInfoLine? = null,
    onInfo: (() -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClickLabel = "Play ${version.name}", role = Role.Button, onClick = onPlay),
        headlineContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val label = version.tags.label
                if (label.isEmpty()) TagChip("Version") else label.split(' ').forEach { TagChip(it, strong = best) }
                if (best) Text("Preferred", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        supportingContent = {
            Column {
                Text(version.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when (info) {
                    null -> Unit
                    VersionInfoLine.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Loading details…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is VersionInfoLine.Text -> Text(info.text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onInfo != null && info == null) {
                    androidx.compose.material3.IconButton(onClick = onInfo) {
                        Icon(Icons.Outlined.Info, contentDescription = "Stream details for ${version.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (onInfo != null && info is VersionInfoLine.Text) {
                    androidx.compose.material3.IconButton(onClick = onInfo) {
                        Icon(Icons.Outlined.Info, contentDescription = "Stream details for ${version.name}", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (tuning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

/** Bottom sheet listing every version of a title, preferred first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionChooserSheet(
    title: String,
    versions: List<Version>,
    tuningName: String?,
    enabled: Boolean,
    onPlay: (Version) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                if (versions.size == 1) "1 version" else "${versions.size} versions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(versions, key = { it.name }) { version ->
                    VersionRow(
                        version = version,
                        best = version == versions.first() && versions.size > 1,
                        tuning = tuningName == version.name,
                        enabled = enabled,
                        onPlay = {
                            onPlay(version)
                            onDismiss()
                        },
                    )
                }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}
