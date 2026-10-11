package app.oslo.repertoire.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.data.monogram
import app.oslo.repertoire.ui.theme.Brand
import app.oslo.repertoire.ui.theme.DisplayFamily
import app.oslo.repertoire.ui.theme.Motion
import app.oslo.repertoire.ui.theme.POSTER_RATIO
import app.oslo.repertoire.ui.theme.Radii
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest

/**
 * Animated sheen for placeholders. The sweep is read in the draw phase only, so a screen full
 * of loading posters doesn't recompose every frame.
 */
fun Modifier.shimmer(base: Color, highlight: Color): Modifier = composed {
    val sweep by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "sweep",
    )
    drawWithCache {
        onDrawBehind {
            drawRect(base)
            val x = size.width * sweep
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(x - size.width * 0.6f, 0f),
                    end = Offset(x + size.width * 0.6f, size.height),
                ),
            )
        }
    }
}

@Composable
fun Modifier.posterShimmer(): Modifier {
    val colors = MaterialTheme.colorScheme
    return shimmer(colors.surfaceContainerHigh, colors.onSurface.copy(alpha = 0.06f))
}

/**
 * Poster art: 2:3 (unless sized otherwise), shimmer while loading, and an italic serif monogram
 * card when there is no image, so missing art still looks deliberate.
 */
@Composable
fun PosterArt(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = Radii.Poster,
) {
    var state by remember(url) { mutableStateOf(if (url.isNullOrBlank()) 2 else 0) } // 0 loading, 1 loaded, 2 none
    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        when (state) {
            0 -> Box(Modifier.fillMaxSize().posterShimmer())
            2 -> PosterMonogram(title)
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = {
                    state = when (it) {
                        is AsyncImagePainter.State.Success -> 1
                        is AsyncImagePainter.State.Error -> 2
                        else -> state
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PosterMonogram(title: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(colors.surfaceContainerHighest, colors.surfaceContainerLow))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            monogram(title),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFamily, fontStyle = FontStyle.Italic),
            color = colors.primary.copy(alpha = 0.8f),
            maxLines = 1,
        )
    }
}

/**
 * The poster card used in grids and rows: art first, a quiet title and meta line under it.
 * Pressing sinks it slightly with a springy scale and a warm glow; long press is optional.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    accent: String? = null,
    artModifier: Modifier = Modifier,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, Motion.snappy(), label = "press")
    val haptics = rememberHaptics()
    Column(
        modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClickLabel = onClickLabel,
            onLongClick = onLongClick?.let { { haptics.hold(); it() } },
            onClick = onClick,
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_RATIO)
                .graphicsLayer {
                    val s = 1f - 0.04f * press
                    scaleX = s
                    scaleY = s
                }
                .shadow(
                    elevation = (6 + 10 * press).dp,
                    shape = Radii.Poster,
                    ambientColor = Color.Black,
                    spotColor = if (press > 0.01f) Brand.Amber.copy(alpha = 0.55f * press + 0.2f) else Color.Black,
                ),
        ) {
            PosterArt(url, title, artModifier.fillMaxSize())
            overlay()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        if (!meta.isNullOrBlank()) {
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        if (!accent.isNullOrBlank()) {
            Text(
                accent,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
