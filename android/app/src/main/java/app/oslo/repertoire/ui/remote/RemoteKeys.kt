package app.oslo.repertoire.ui.remote

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.ui.components.rememberHaptics
import kotlinx.coroutines.launch

/**
 * A remote button. With [onHold] it reports press/release (volume, seek and channel keys repeat
 * while held); otherwise it is a plain tap. Both give haptic feedback and a springy press.
 */
@Composable
fun RemoteKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onHold: ((Boolean) -> Unit)? = null,
    shape: Shape = CircleShape,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val hold by rememberUpdatedState(onHold)
    val tap by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()
    val gesture = if (onHold != null) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        scope.launch { interaction.emit(press) }
                        haptics.tap()
                        hold?.invoke(true)
                        tryAwaitRelease()
                        hold?.invoke(false)
                        scope.launch { interaction.emit(PressInteraction.Release(press)) }
                    },
                )
            }
            .indication(interaction, ripple())
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick(label) {
                    hold?.invoke(true)
                    hold?.invoke(false)
                    true
                }
            }
    } else {
        Modifier
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button) {
                haptics.tap()
                tap?.invoke()
            }
            .semantics { contentDescription = label }
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(container)
            .then(gesture),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

@Composable
fun IconKey(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    onClick: (() -> Unit)? = null,
    onHold: ((Boolean) -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    RemoteKey(
        label = label,
        modifier = modifier.size(size),
        onClick = onClick,
        onHold = onHold,
        container = container,
        contentColor = contentColor,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.42f))
    }
}

/** Tall pill with + on top and − below, e.g. volume or channel. Both halves repeat while held. */
@Composable
fun Rocker(
    title: String,
    upIcon: ImageVector,
    downIcon: ImageVector,
    upLabel: String,
    downLabel: String,
    onUp: (Boolean) -> Unit,
    onDown: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Column(
        modifier
            .width(76.dp)
            .clip(RoundedCornerShape(38.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RemoteKey(
            upLabel,
            Modifier.fillMaxWidth().size(76.dp),
            onHold = onUp,
            shape = RoundedCornerShape(38.dp),
            container = Color.Transparent,
        ) { Icon(upIcon, contentDescription = null, modifier = Modifier.size(30.dp)) }
        Text(
            caption ?: title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        RemoteKey(
            downLabel,
            Modifier.fillMaxWidth().size(76.dp),
            onHold = onDown,
            shape = RoundedCornerShape(38.dp),
            container = Color.Transparent,
        ) { Icon(downIcon, contentDescription = null, modifier = Modifier.size(30.dp)) }
    }
}
