package app.oslo.repertoire.ui.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.remote.Keys
import app.oslo.repertoire.remote.NavTransport
import app.oslo.repertoire.remote.TvStatus
import app.oslo.repertoire.ui.components.MessageCard
import app.oslo.repertoire.ui.components.MessageTone
import app.oslo.repertoire.ui.components.StatusDot
import app.oslo.repertoire.ui.theme.AppTheme

/** Connection summary for the chip: label and whether the remote can send keys now. */
private data class Link(val text: String, val color: Color, val ready: Boolean)

@Composable
private fun linkState(model: GuideViewModel, tv: TvStatus): Link {
    val good = AppTheme.status.success
    val warn = AppTheme.status.warning
    val bad = MaterialTheme.colorScheme.error
    return when {
        !model.hasTv -> Link("No TV chosen", bad, false)
        model.transport == NavTransport.None -> Link("Remote unavailable", bad, false)
        model.transport == NavTransport.Adb -> when (model.adbOk) {
            true -> Link("Connected · ADB", good, true)
            false -> Link("Debugging off", bad, false)
            null -> Link("Keys over ADB", warn, true)
        }
        tv.connected -> Link("Connected", good, true)
        !model.paired -> Link("Not paired", warn, false)
        model.linked -> Link(tv.detail.takeIf { it.isNotBlank() && it != "TV offline" } ?: "Connecting", warn, false)
        else -> Link("Disconnected", bad, false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    model: GuideViewModel,
    tv: TvStatus,
    onOpenDevices: () -> Unit,
    bottomInset: PaddingValues,
) {
    var numpad by rememberSaveable { mutableStateOf(false) }
    val link = linkState(model, tv)
    val hold = { code: Int -> { down: Boolean -> model.hold(code, down) } }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Remote") },
            actions = {
                IconButton(onClick = { model.tap(Keys.MEDIA_AUDIO_TRACK) }) {
                    Icon(Icons.Rounded.Audiotrack, contentDescription = "Audio track")
                }
                IconButton(onClick = { model.tap(Keys.CAPTIONS) }) {
                    Icon(Icons.Rounded.Subtitles, contentDescription = "Subtitles")
                }
                IconButton(onClick = { numpad = true }) {
                    Icon(Icons.Rounded.Dialpad, contentDescription = "Number pad")
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomInset.calculateBottomPadding() + 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AssistChip(
                onClick = onOpenDevices,
                label = { Text("${model.deviceName} · ${link.text}", maxLines = 1) },
                leadingIcon = { StatusDot(link.color) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            AnimatedVisibility(!link.ready) {
                MessageCard(
                    text = when {
                        !model.hasTv -> "Choose the TV that runs TiviMate."
                        !model.paired && model.transport == NavTransport.RemoteV2 ->
                            "Pair this phone with ${model.deviceName} to use the remote. It takes a few seconds."
                        model.adbOk == false -> "Turn on network debugging on the TV so the remote can send keys."
                        else -> "The TV isn't answering yet. Make sure it's on and on the same Wi-Fi."
                    },
                    tone = MessageTone.Info,
                    action = if (!model.hasTv) "Choose TV" else "Set up",
                    onAction = onOpenDevices,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            DPad(model)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                IconKey(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = { model.tap(Keys.BACK) })
                IconKey(Icons.Rounded.Home, "Home", onClick = { model.tap(Keys.HOME) })
                IconKey(Icons.Rounded.Menu, "TV menu", onClick = model::menu)
                IconKey(
                    Icons.Rounded.LiveTv,
                    "Open TiviMate",
                    onClick = model::launchTiviMate,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val volume = when {
                    tv.muted -> "Muted"
                    tv.volumeLevel != null -> "${tv.volumeLevel}"
                    else -> null
                }
                Rocker(
                    title = "VOL",
                    caption = volume ?: "VOL",
                    upIcon = Icons.Rounded.Add,
                    downIcon = Icons.Rounded.Remove,
                    upLabel = "Volume up",
                    downLabel = "Volume down",
                    onUp = hold(Keys.VOLUME_UP),
                    onDown = hold(Keys.VOLUME_DOWN),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RemoteKey(
                        "Play or pause",
                        Modifier.size(80.dp),
                        onClick = model::playPause,
                        container = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Row {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                            Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                    }
                    IconKey(
                        Icons.AutoMirrored.Rounded.VolumeOff,
                        if (tv.muted) "Unmute" else "Mute",
                        size = 56.dp,
                        onClick = { model.tap(Keys.VOLUME_MUTE) },
                        container = if (tv.muted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
                Rocker(
                    title = "CH",
                    upIcon = Icons.Rounded.KeyboardArrowUp,
                    downIcon = Icons.Rounded.KeyboardArrowDown,
                    upLabel = "Channel up",
                    downLabel = "Channel down",
                    onUp = hold(Keys.CHANNEL_UP),
                    onDown = hold(Keys.CHANNEL_DOWN),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconKey(Icons.Rounded.SkipPrevious, "Previous", size = 56.dp, onClick = { model.tap(Keys.MEDIA_PREVIOUS) })
                IconKey(Icons.Rounded.FastRewind, "Rewind", size = 56.dp, onHold = hold(Keys.MEDIA_REWIND))
                IconKey(Icons.Rounded.FastForward, "Fast forward", size = 56.dp, onHold = hold(Keys.MEDIA_FAST_FORWARD))
                IconKey(Icons.Rounded.SkipNext, "Next", size = 56.dp, onClick = { model.tap(Keys.MEDIA_NEXT) })
            }
        }
    }
    if (numpad) {
        ModalBottomSheet(onDismissRequest = { numpad = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            NumberPad(onDigit = model::digit)
        }
    }
}

/**
 * Circular D-pad: four arrow segments around a large OK key. Arrows are deliberately not
 * auto-mirrored: they send physical left/right keys, so they must not flip in RTL layouts.
 */
@Suppress("DEPRECATION")
@Composable
private fun DPad(model: GuideViewModel) {
    val ring = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        Modifier.size(236.dp).clip(CircleShape).background(ring),
        contentAlignment = Alignment.Center,
    ) {
        val arrow = Modifier.size(80.dp)
        val clear = Color.Transparent
        IconKey(Icons.Rounded.KeyboardArrowUp, "Up", arrow.align(Alignment.TopCenter), 80.dp, onClick = { model.tap(Keys.DPAD_UP) }, container = clear)
        IconKey(Icons.Rounded.KeyboardArrowDown, "Down", arrow.align(Alignment.BottomCenter), 80.dp, onClick = { model.tap(Keys.DPAD_DOWN) }, container = clear)
        IconKey(Icons.Rounded.KeyboardArrowLeft, "Left", arrow.align(Alignment.CenterStart), 80.dp, onClick = { model.tap(Keys.DPAD_LEFT) }, container = clear)
        IconKey(Icons.Rounded.KeyboardArrowRight, "Right", arrow.align(Alignment.CenterEnd), 80.dp, onClick = { model.tap(Keys.DPAD_RIGHT) }, container = clear)
        RemoteKey(
            "OK",
            Modifier.size(96.dp),
            onClick = { model.tap(Keys.DPAD_CENTER) },
            container = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) { Text("OK", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun NumberPad(onDigit: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Channel number", style = MaterialTheme.typography.titleMedium)
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(0)).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                line.forEach { digit ->
                    RemoteKey("$digit", Modifier.size(72.dp), onClick = { onDigit(digit) }) {
                        Text("$digit", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}
