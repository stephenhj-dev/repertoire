package app.oslo.repertoire.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.data.monogram
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

/** Channel logo or poster from the portal, with a monogram until (or instead of) the image. */
@Composable
fun ChannelLogo(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    width: Dp = 48.dp,
    height: Dp = 48.dp,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .size(width, height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                monogram(title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = if (height > width) ContentScale.Crop else ContentScale.Fit,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier.fillMaxSize().padding(if (height > width) 0.dp else 4.dp),
            )
        }
    }
}

/** Round tonal badge with a short code ("US", "4K"). */
@Composable
fun CodeBadge(code: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            code.take(4),
            style = if (code.length > 3) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
        )
    }
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp)
            .semantics { heading() },
    )
}

/** Centered illustration-style message with an optional action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(action) }
        }
        if (secondary != null) {
            TextButton(onClick = onSecondary) { Text(secondary) }
        }
    }
}

enum class MessageTone { Info, Error }

/** Inline card for an error or a hint, with up to two actions. */
@Composable
fun MessageCard(
    text: String,
    modifier: Modifier = Modifier,
    tone: MessageTone = MessageTone.Error,
    title: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        MessageTone.Error -> colors.errorContainer to colors.onErrorContainer
        MessageTone.Info -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = container, contentColor = content) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (action != null) 4.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    if (tone == MessageTone.Error) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
                    contentDescription = if (tone == MessageTone.Error) "Error" else null,
                    modifier = Modifier.padding(top = 1.dp).size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (action != null || secondary != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (secondary != null) TextButton(onClick = onSecondary) { Text(secondary, color = content) }
                    if (action != null) TextButton(onClick = onAction) { Text(action, color = content) }
                }
            }
        }
    }
}

/** Shimmering placeholder rows shown while a list loads. */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, rows: Int = 8, poster: Boolean = false) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = pulse }
            .semantics { contentDescription = "Loading" },
    ) {
        repeat(rows) { index ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp, if (poster) 64.dp else 48.dp).clip(RoundedCornerShape(12.dp)).background(block))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(if (index % 3 == 0) 0.7f else 0.5f).height(14.dp).clip(CircleShape).background(block))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.3f).height(10.dp).clip(CircleShape).background(block))
                }
            }
        }
    }
}

/** A small coloured dot used in status chips. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}
