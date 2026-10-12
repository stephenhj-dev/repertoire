package app.oslo.repertoire.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.remote.Support
import app.oslo.repertoire.setup.GuideStep
import app.oslo.repertoire.setup.StepKind
import app.oslo.repertoire.setup.Trouble
import app.oslo.repertoire.setup.TvGuide
import app.oslo.repertoire.setup.TvGuides
import app.oslo.repertoire.ui.components.MessageCard
import app.oslo.repertoire.ui.components.MessageTone
import app.oslo.repertoire.ui.components.SectionHeader
import app.oslo.repertoire.ui.components.rememberHaptics
import app.oslo.repertoire.ui.details.HeroTopBar
import app.oslo.repertoire.ui.theme.AppTheme
import app.oslo.repertoire.ui.theme.DisplayFamily
import app.oslo.repertoire.ui.theme.Motion
import app.oslo.repertoire.ui.theme.OverlineStyle
import app.oslo.repertoire.ui.theme.Space
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Step-by-step connection guide for one TV brand: pick the brand (suggested from what the app
 * knows about the TV), follow the cards, test with the real connection check, troubleshoot.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvGuideScreen(model: GuideViewModel, initialGuide: String?, onBack: () -> Unit, onOpenDevices: () -> Unit) {
    val suggested = model.suggestedGuide
    var guideId by rememberSaveable { mutableStateOf(initialGuide?.takeIf { it.isNotBlank() } ?: suggested?.id ?: TvGuides.generic.id) }
    val guide = TvGuides.byId(guideId) ?: TvGuides.generic
    // Steps the user ticked off, per guide; the first open one is expanded.
    var doneByGuide by rememberSaveable { mutableStateOf(mapOf<String, List<Int>>()) }
    val done = doneByGuide[guide.id].orEmpty().toSet()
    var expanded by rememberSaveable(guide.id) { mutableStateOf(-1) }
    val current = when {
        expanded >= 0 -> expanded
        expanded == -2 -> -1
        else -> guide.steps.indices.firstOrNull { it !in done } ?: -1
    }
    fun mark(index: Int, on: Boolean) {
        val list = done.toMutableSet().apply { if (on) add(index) else remove(index) }
        doneByGuide = doneByGuide + (guide.id to list.sorted())
        expanded = -1
    }
    // A passing connection test completes the guide.
    val testIndex = guide.steps.indexOfFirst { it.kind == StepKind.Test }
    LaunchedEffect(model.adbOk, guide.id) {
        if (model.adbOk == true && testIndex >= 0 && testIndex !in done && done.isNotEmpty()) mark(testIndex, true)
    }

    val listState = rememberLazyListState()
    val glass = remember { HazeState() }
    val solid by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().hazeSource(glass),
            state = listState,
            contentPadding = PaddingValues(bottom = Space.huge),
        ) {
            item(key = "header") {
                Column(Modifier.statusBarsPadding().padding(start = Space.page, end = Space.page, top = 64.dp)) {
                    Text("SET UP YOUR TV", style = OverlineStyle, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(Space.s))
                    Text(
                        "Connect ${guide.name}",
                        style = MaterialTheme.typography.headlineLarge.copy(fontFamily = DisplayFamily),
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(guide.covers, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "brands") {
                BrandPicker(guide.id, suggested?.id) { guideId = it }
            }
            item(key = "progress") {
                val fraction by animateFloatAsState(done.size / guide.steps.size.toFloat(), Motion.gentle(), label = "progress")
                Column(Modifier.padding(horizontal = Space.page, vertical = Space.s)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (done.size == guide.steps.size) "All set" else "${done.size} of ${guide.steps.size} done",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Space.s))
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
                }
            }
            if (guide.pairingCaveat) {
                item(key = "caveat") {
                    MessageCard(
                        "On recent software (Android 14 Google TV and some newer sets), network debugging only works through “Wireless debugging” with a pairing code. " +
                            "Repertoire can't pair that way yet. If the test below says the connection was refused, see Troubleshooting.",
                        Modifier.padding(horizontal = Space.l, vertical = Space.s),
                        tone = MessageTone.Info,
                        title = "Newer software",
                    )
                }
            }
            itemsIndexed(guide.steps, key = { i, _ -> "${guide.id}-$i" }) { index, step ->
                StepCard(
                    number = index + 1,
                    step = step,
                    done = index in done,
                    open = index == current,
                    onToggle = { expanded = if (current == index) -2 else index },
                    onDone = { mark(index, index !in done) },
                ) {
                    if (step.kind == StepKind.Test) TestPanel(model, onOpenDevices)
                }
            }
            if (guide.quirks.isNotEmpty()) {
                item(key = "quirks-header") { SectionHeader("Good to know") }
                guide.quirks.forEachIndexed { i, quirk ->
                    item(key = "quirk-$i") {
                        Row(Modifier.padding(horizontal = Space.page, vertical = Space.xs)) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(Space.m))
                            Text(quirk, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item(key = "trouble-header") { SectionHeader("Troubleshooting") }
            (guide.troubleshooting + TvGuides.common).forEachIndexed { i, trouble ->
                item(key = "trouble-$i") { TroubleItem(trouble, highlight = troubleMatches(trouble, model)) }
            }
        }
        HeroTopBar("Connect ${guide.name}", solid, onBack, glass)
    }
}

/** Highlights the troubleshooting entry that fits the last failed check. */
private fun troubleMatches(trouble: Trouble, model: GuideViewModel): Boolean {
    if (model.adbOk != false) return false
    val detail = model.adbDetail.lowercase()
    val problem = trouble.problem.lowercase()
    return when {
        "refused" in problem -> model.adbSupport == Support.No || "not accepting" in detail || "refused" in detail
        "timed out" in problem -> "did not answer" in detail || "timed out" in detail
        "prompt" in problem -> "allow" in detail
        else -> false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrandPicker(selected: String, suggested: String?, onSelect: (String) -> Unit) {
    val haptics = rememberHaptics()
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.l),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        TvGuides.all.forEach { guide ->
            val on = guide.id == selected
            val container by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                Motion.gentle(),
                label = "brand",
            )
            Surface(
                onClick = { haptics.tap(); onSelect(guide.id) },
                shape = CircleShape,
                color = container,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = if (guide.id == suggested && !on) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(guide.name, style = MaterialTheme.typography.labelLarge)
                    if (guide.id == suggested) {
                        Spacer(Modifier.width(6.dp))
                        Text("· yours", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    step: GuideStep,
    done: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    onDone: () -> Unit,
    extra: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.l, vertical = Space.xs)
            .clip(MaterialTheme.shapes.large)
            .background(if (open) colors.surfaceContainerHigh else colors.surfaceContainerLow)
            .then(if (open) Modifier.border(1.dp, colors.primary.copy(alpha = 0.35f), MaterialTheme.shapes.large) else Modifier)
            .animateContentSize(Motion.gentle()),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (done) AppTheme.status.success else colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Rounded.Check, contentDescription = "Done", tint = AppTheme.status.onSuccess, modifier = Modifier.size(18.dp))
                else Text("$number", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
            }
            Spacer(Modifier.width(Space.m))
            Text(step.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (open) "Collapse" else "Expand", tint = colors.onSurfaceVariant)
        }
        if (open) {
            Column(Modifier.padding(start = Space.l, end = Space.l, bottom = Space.l)) {
                if (step.path.isNotEmpty()) {
                    MenuPath(step.path)
                    Spacer(Modifier.height(Space.m))
                }
                Text(step.body, style = MaterialTheme.typography.bodyMedium)
                step.note?.let {
                    Spacer(Modifier.height(Space.s))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (step.uncertain) {
                    Spacer(Modifier.height(Space.s))
                    Text(
                        "Menus differ between models and updates; look for similarly named entries.",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = colors.onSurfaceVariant,
                    )
                }
                extra()
                if (step.kind != StepKind.Test) {
                    Spacer(Modifier.height(Space.m))
                    FilledTonalButton(onClick = { haptics.confirm(); onDone() }) {
                        Text(if (done) "Not done yet" else "Done, next step")
                    }
                }
            }
        }
    }
}

/** "Settings › Device Preferences › About" as small pills, the way the TV shows them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MenuPath(path: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        path.forEachIndexed { i, part ->
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(part, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            if (i < path.lastIndex) {
                Text("›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

@Composable
private fun TestPanel(model: GuideViewModel, onOpenDevices: () -> Unit) {
    Spacer(Modifier.height(Space.m))
    if (!model.hasTv) {
        Text("Choose your TV first: Repertoire usually finds it on the Wi-Fi.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.s))
        Button(onClick = onOpenDevices) { Text("Choose TV") }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.s))
        Text("${model.deviceName} · ${model.selectedHost}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onOpenDevices) { Text("Change") }
    }
    Spacer(Modifier.height(Space.s))
    val result = when {
        model.checkingAdb -> null
        model.adbOk == true -> MessageTone.Info
        model.adbOk == false -> MessageTone.Error
        else -> null
    }
    AnimatedVisibility(result != null) {
        Text(
            if (model.adbOk == true) "Connected. You're all set: Play now tunes this TV." else model.adbDetail,
            style = MaterialTheme.typography.bodyMedium,
            color = if (model.adbOk == true) AppTheme.status.success else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = Space.s),
        )
    }
    Button(onClick = model::checkDevice, enabled = !model.checkingAdb) {
        if (model.checkingAdb) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(Space.s))
            Text("Testing…")
        } else {
            Text(if (model.adbOk == true) "Test again" else "Test connection")
        }
    }
}

@Composable
private fun TroubleItem(trouble: Trouble, highlight: Boolean) {
    var open by rememberSaveable(trouble.problem) { mutableStateOf(highlight) }
    LaunchedEffect(highlight) { if (highlight) open = true }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.l, vertical = Space.xxs)
            .clip(MaterialTheme.shapes.medium)
            .background(if (highlight) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Button) { open = !open }
            .padding(horizontal = Space.l, vertical = Space.m)
            .animateContentSize(Motion.gentle()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(trouble.problem, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
        }
        if (open) {
            Spacer(Modifier.height(Space.s))
            Text(trouble.fix, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
