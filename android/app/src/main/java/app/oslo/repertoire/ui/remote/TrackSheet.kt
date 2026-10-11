package app.oslo.repertoire.ui.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.TrackSheet
import app.oslo.repertoire.tune.TiviMateScreens
import app.oslo.repertoire.ui.components.rememberHaptics
import app.oslo.repertoire.ui.theme.Space

/** Audio or subtitle choices read from TiviMate on the TV; a tap switches the track there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackChooser(sheet: TrackSheet, onChoose: (String) -> Unit, onDismiss: (closeOnTv: Boolean) -> Unit) {
    val haptics = rememberHaptics()
    val audio = sheet.track == TiviMateScreens.Track.Audio
    ModalBottomSheet(onDismissRequest = { onDismiss(true) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(bottom = Space.xxl)) {
            Row(Modifier.padding(horizontal = Space.page, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (audio) Icons.Rounded.Audiotrack else Icons.Rounded.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Space.m))
                Text(if (audio) "Audio track" else "Subtitles", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                if (sheet.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (sheet.message.isNotBlank()) {
                Text(
                    sheet.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.page, vertical = Space.xs),
                )
            }
            Spacer(Modifier.height(Space.s))
            sheet.options.forEach { option ->
                ListItem(
                    headlineContent = { Text(option.label) },
                    trailingContent = {
                        if (option.selected) Icon(Icons.Rounded.Check, contentDescription = "Current", tint = MaterialTheme.colorScheme.primary)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(enabled = !sheet.busy) {
                        haptics.confirm()
                        onChoose(option.label)
                    },
                )
            }
        }
    }
}
