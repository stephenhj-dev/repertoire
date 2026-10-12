package app.oslo.repertoire.ui.setup

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.R
import app.oslo.repertoire.remote.NavTransport
import app.oslo.repertoire.remote.Support
import app.oslo.repertoire.remote.TvStatus
import app.oslo.repertoire.ui.components.MessageCard
import app.oslo.repertoire.ui.components.MessageTone
import app.oslo.repertoire.ui.devices.PairDialog
import app.oslo.repertoire.ui.devices.deviceList
import app.oslo.repertoire.ui.settings.PortalForm
import app.oslo.repertoire.ui.theme.AppTheme

private const val STEPS = 3

/** Shared frame for setup steps: progress, title, body, content and the action row. */
@Composable
private fun SetupStep(
    step: Int,
    title: String,
    body: String,
    onBack: (() -> Unit)?,
    primary: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            val progress by animateFloatAsState(step / STEPS.toFloat(), tween(400), label = "progress")
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                strokeCap = StrokeCap.Round,
            )
            Text(
                "$step of $STEPS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp) else Modifier),
            content = content,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (secondary != null) TextButton(onClick = onSecondary) { Text(secondary) } else Spacer(Modifier.width(1.dp))
            Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.height(48.dp)) { Text(primary) }
        }
    }
}

@Composable
fun SetupWelcomeScreen(onStart: () -> Unit, onSkip: () -> Unit) {
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        0.35f,
        0.8f,
        infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "pulse",
    )
    val amber = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(260.dp).background(
                    Brush.radialGradient(listOf(amber.copy(alpha = 0.25f * glow), Color.Transparent)),
                    CircleShape,
                ),
            )
            Image(
                painter = painterResource(R.drawable.brand_mark),
                contentDescription = null,
                modifier = Modifier.size(168.dp).clip(RoundedCornerShape(36.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Feature(Icons.Rounded.LiveTv, "Your whole lineup", "Every channel, movie and series from your portal, by country and category.")
        Feature(Icons.Rounded.PlayCircle, "Tap to play on the TV", "Pick something here and TiviMate starts it on the big screen.")
        Feature(Icons.Rounded.SettingsRemote, "A remote in your pocket", "D-pad, volume, channels and playback with haptic feedback.")
        Spacer(Modifier.weight(1f))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Get started") }
        TextButton(onClick = onSkip, modifier = Modifier.padding(vertical = 8.dp)) { Text("Skip setup") }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SetupPortalScreen(model: GuideViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    SetupStep(
        step = 1,
        title = "Sign in to your IPTV service",
        body = "Repertoire reads your channel list from the same Xtream Codes portal TiviMate uses.",
        onBack = onBack,
        primary = "Continue",
        onPrimary = onNext,
        primaryEnabled = model.hasPortal,
        secondary = "Skip for now",
        onSecondary = onNext,
    ) {
        PortalForm(model, submitLabel = if (model.hasPortal) "Sign in again" else "Sign in", onSuccess = onNext)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SetupTvScreen(model: GuideViewModel, tv: TvStatus, onGuide: (String) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    LaunchedEffect(Unit) { if (!model.scanning) model.findTv() }
    SetupStep(
        step = 2,
        title = "Find your TV",
        body = "Pick the Android TV, Google TV or Fire TV that runs TiviMate. It needs to be on the same Wi-Fi.",
        onBack = onBack,
        primary = "Continue",
        onPrimary = onNext,
        primaryEnabled = model.hasTv,
        secondary = "Skip",
        onSecondary = onNext,
        scroll = false,
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            deviceList(model, tv, showConnection = false, onGuide = onGuide)
        }
    }
}

@Composable
fun SetupConnectScreen(model: GuideViewModel, tv: TvStatus, onGuide: (String) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    LaunchedEffect(model.selectedHost) { model.checkDevice() }
    val canPair = model.remoteV2 != Support.No
    SetupStep(
        step = 3,
        title = "Connect to ${model.deviceName}",
        body = "Two quick switches on the TV let this phone tune channels and work as a remote.",
        onBack = onBack,
        primary = "Done",
        onPrimary = onNext,
        secondary = "Skip",
        onSecondary = onNext,
    ) {
        StepCard(
            number = 1,
            title = "Turn on network debugging",
            done = model.adbOk == true,
            busy = model.checkingAdb,
            body = if (model.adbOk == true) {
                "Connected. When the TV asks to allow this phone, tick Always allow."
            } else {
                val guide = model.suggestedGuide
                val debugging = guide?.steps?.firstOrNull { it.kind == app.oslo.repertoire.setup.StepKind.Debugging }
                if (guide != null && debugging != null) {
                    "${guide.name}: ${debugging.path.drop(1).joinToString(" → ")} → ${debugging.title.removePrefix("Turn on ")}.\n" +
                        "Developer options hidden? Open the step-by-step guide."
                } else {
                    "Developer options → network or USB debugging. The step-by-step guide shows where it is on your TV."
                }
            },
            action = if (model.adbOk == true) null else "Check again",
            onAction = model::checkDevice,
        )
        if (model.adbOk != true) {
            app.oslo.repertoire.ui.devices.GuideLink(model, onGuide)
        }
        Spacer(Modifier.height(12.dp))
        if (canPair) {
            StepCard(
                number = 2,
                title = "Pair the remote",
                done = model.paired,
                busy = model.pairing,
                body = if (model.paired) {
                    "Paired. The remote uses the TV's own remote service${if (tv.connected) " and is connected" else ""}."
                } else {
                    "The TV shows a 6-character code. Type it here to link this phone."
                },
                action = if (model.paired) null else "Pair remote",
                onAction = model::pair,
                icon = Icons.Rounded.Link,
            )
        } else if (model.transport == NavTransport.Adb) {
            MessageCard(
                "This TV has no Android TV remote service, so the remote sends keys over network debugging instead. No pairing needed.",
                tone = MessageTone.Info,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
    PairDialog(model, tv)
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    done: Boolean,
    busy: Boolean,
    body: String,
    action: String?,
    onAction: () -> Unit,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (done) AppTheme.status.success else colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    done -> Icon(Icons.Rounded.CheckCircle, contentDescription = "Done", tint = AppTheme.status.onSuccess, modifier = Modifier.size(20.dp))
                    busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else -> Text("$number", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onAction, enabled = !busy) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(action)
            }
        }
    }
}

@Composable
fun SetupDoneScreen(model: GuideViewModel, onFinish: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(24.dp))
        Text("You're all set", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        val lines = buildList {
            add(if (model.hasPortal) "✓ Portal signed in" else "• Portal: add it later in Settings")
            add(if (model.hasTv) "✓ Playing on ${model.deviceName}" else "• TV: choose one later in Settings")
            if (model.hasTv) add(if (model.paired || model.transport == NavTransport.Adb) "✓ Remote ready" else "• Remote: pair it from Devices")
        }
        lines.forEach {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (model.syncing) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Your guide is downloading in the background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Start watching") }
    }
}
