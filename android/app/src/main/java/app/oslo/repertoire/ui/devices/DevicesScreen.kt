package app.oslo.repertoire.ui.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.remote.NavTransport
import app.oslo.repertoire.remote.Support
import app.oslo.repertoire.remote.TvDevice
import app.oslo.repertoire.remote.TvStatus
import app.oslo.repertoire.ui.components.SectionHeader
import app.oslo.repertoire.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(model: GuideViewModel, tv: TvStatus, onBack: () -> Unit) {
    LaunchedEffect(Unit) {
        model.onDevicesShown()
        if (model.devices.size <= 1) model.findTv()
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (model.devices.isEmpty()) "Find your TV" else "Devices") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
            actions = {
                IconButton(onClick = model::findTv, enabled = !model.scanning) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Scan again")
                }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            deviceList(model, tv)
        }
    }
    PairDialog(model, tv)
}

/** Device picker, selected-TV status, manual address and help. Shared with setup. */
fun LazyListScope.deviceList(model: GuideViewModel, tv: TvStatus, showConnection: Boolean = true) {
    item(key = "scan") {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            if (model.scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    "Looking for TVs on this Wi-Fi…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (model.devicesNote.isNotBlank()) {
                Text(model.devicesNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    items(model.devices, key = { "tv:${it.host}" }) { device ->
        DeviceCard(model, tv, device, showConnection)
    }
    if (model.devices.isEmpty() && !model.scanning) {
        item(key = "empty") {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.WifiFind, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(
                    "No TVs found yet. Make sure the TV is on and connected to the same Wi-Fi as this phone.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = model::findTv) { Text("Scan again") }
            }
        }
    }
    item(key = "manual") { ManualAddress(model) }
    item(key = "help") { DebuggingHelp() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(model: GuideViewModel, tv: TvStatus, device: TvDevice, showConnection: Boolean) {
    val selected = device.host == model.selectedHost
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = { if (!selected) model.selectDevice(device) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = if (selected) colors.secondaryContainer else colors.surfaceContainer),
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tv, contentDescription = null, modifier = Modifier.size(32.dp), tint = colors.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    val sub = listOfNotNull(device.model.takeIf { it.isNotBlank() && it != device.name }, device.host).joinToString(" · ")
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (selected) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = colors.primary)
                }
            }
            if (!selected) return@Column
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Capability("Remote service", model.remoteV2)
                Capability("Network debugging", model.adbSupport)
                if (model.fireTv) Capability("Fire TV", Support.Yes)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (model.checkingAdb) "Checking what this TV supports…" else model.adbDetail,
                style = MaterialTheme.typography.bodySmall,
                color = if (model.adbOk == true) AppTheme.status.success else colors.onSurfaceVariant,
            )
            if (showConnection) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Remote buttons use " + when (model.transport) {
                        NavTransport.RemoteV2 -> if (model.paired) "the Android TV remote service${if (tv.connected) " (connected)" else ""}." else "the Android TV remote service once paired."
                        NavTransport.Adb -> "key events over network debugging."
                        NavTransport.None -> "nothing yet: this TV has neither service turned on."
                    } + " Channels are tuned over network debugging.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (model.remoteV2 != Support.No) {
                    if (model.paired) {
                        OutlinedButton(onClick = model::pair) { Text("Pair again") }
                    } else {
                        Button(onClick = model::pair, enabled = !model.pairing) {
                            if (model.pairing) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Pair remote")
                        }
                    }
                }
                TextButton(onClick = model::checkDevice, enabled = !model.checkingAdb) { Text("Check again") }
            }
        }
    }
}

@Composable
private fun Capability(label: String, value: Support) {
    val (mark, color) = when (value) {
        Support.Yes -> "✓" to AppTheme.status.success
        Support.No -> "✕" to MaterialTheme.colorScheme.error
        Support.Unknown -> "?" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val state = when (value) {
        Support.Yes -> "available"
        Support.No -> "not available"
        Support.Unknown -> "not checked"
    }
    SuggestionChip(
        onClick = {},
        label = { Text("$mark $label") },
        colors = SuggestionChipDefaults.suggestionChipColors(labelColor = color),
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$label: $state" },
    )
}

@Composable
private fun ManualAddress(model: GuideViewModel) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionHeader("Enter an address", Modifier.padding(start = 0.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = model.host,
                onValueChange = { model.host = it },
                modifier = Modifier.weight(1f),
                label = { Text("TV IP address") },
                placeholder = { Text("192.168.1.20") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { model.useAddress(model.host) }),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = { model.useAddress(model.host) }, enabled = model.host.isNotBlank()) { Text("Use") }
        }
    }
}

@Composable
private fun DebuggingHelp() {
    var open by rememberSaveable { mutableStateOf(false) }
    Card(
        onClick = { open = !open },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("How to turn on network debugging", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (open) "Collapse" else "Expand")
            }
            AnimatedVisibility(open) {
                Text(
                    "Repertoire tunes channels by talking to the TV over network debugging (ADB).\n\n" +
                        "• NVIDIA Shield: Settings → Device preferences → Developer options → Network debugging.\n" +
                        "• Google TV / Android TV: Settings → System → About, tap Build 7 times, then System → Developer options → USB debugging.\n" +
                        "• Fire TV: Settings → My Fire TV → Developer options → ADB debugging.\n\n" +
                        "The first time, the TV asks to allow this phone. Tick Always allow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/** Shown while the TV displays a pairing code. */
@Composable
fun PairDialog(model: GuideViewModel, tv: TvStatus) {
    if (!tv.waitingForCode) return
    AlertDialog(
        onDismissRequest = model::cancelPairing,
        icon = { Icon(Icons.Rounded.Link, contentDescription = null) },
        title = { Text("Enter the code on your TV") },
        text = {
            Column {
                Text(
                    "${model.deviceName} is showing a 6-character code.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = model.pin,
                    onValueChange = { value ->
                        model.pin = value.uppercase().filter { it.isLetterOrDigit() }.take(6)
                        model.pinError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 6.sp, textAlign = TextAlign.Center),
                    isError = model.pinError != null,
                    supportingText = model.pinError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { model.submitPin() }),
                )
            }
        },
        confirmButton = { Button(onClick = model::submitPin, enabled = model.pin.length == 6) { Text("Pair") } },
        dismissButton = { TextButton(onClick = model::cancelPairing) { Text("Cancel") } },
        modifier = Modifier.navigationBarsPadding(),
    )
}
