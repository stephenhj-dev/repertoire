package app.oslo.repertoire.ui.details

import app.oslo.repertoire.ui.components.glass
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.data.monogram
import app.oslo.repertoire.ui.theme.LocalStatusColors
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

/** Poster with a shimmer while loading and a monogram card without art, 2:3 by default. */
@Composable
fun PosterImage(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    width: Dp = 48.dp,
    height: Dp = 72.dp,
    corner: Dp = 10.dp,
) {
    app.oslo.repertoire.ui.components.PosterArt(url, title, modifier.size(width, height), RoundedCornerShape(corner))
}

/** Round profile photo with a person icon until (or instead of) the image. */
@Composable
fun ProfileImage(url: String?, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 2))
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Full-bleed backdrop for the top of a title or person page. It drifts at half the scroll speed
 * ([scrolled] is the header's scroll offset in px) and its lower half dissolves into whatever is
 * behind it, normally [BackdropAura], so the artwork melts into the page.
 */
@Composable
fun BackdropImage(url: String?, modifier: Modifier = Modifier, scrolled: () -> Float = { 0f }) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .graphicsLayer {
                translationY = scrolled() * 0.45f
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 0.5f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerHigh))))
        }
        // Keeps the status bar and back button legible on bright art.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.5f), 0.35f to Color.Transparent)))
    }
}

/**
 * The page's ambient light: the backdrop again, heavily blurred (Android 12+) and faded into the
 * surface, behind everything, so the colours of the artwork carry down the page.
 */
@Composable
fun BackdropAura(url: String?, modifier: Modifier = Modifier, height: Dp = 640.dp) {
    if (url.isNullOrBlank()) return
    val surface = MaterialTheme.colorScheme.surface
    val blurs = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    Box(modifier.fillMaxWidth().height(height)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (blurs) 0.55f else 0.22f }
                .then(if (blurs) Modifier.blur(72.dp, BlurredEdgeTreatment.Rectangle) else Modifier),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to surface.copy(alpha = 0.15f), 0.55f to surface.copy(alpha = 0.6f), 1f to surface)))
    }
}

/**
 * Top bar that frosts in once the hero has scrolled away: back button, and the page title in
 * the display serif. Before that, only the round back button floats over the artwork.
 */
@Composable
fun HeroTopBar(title: String, solid: Boolean, onBack: () -> Unit, source: dev.chrisbanes.haze.HazeState?, modifier: Modifier = Modifier) {
    val shown by androidx.compose.animation.core.animateFloatAsState(if (solid) 1f else 0f, app.oslo.repertoire.ui.theme.Motion.gentle(), label = "bar")
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = shown }
                .then(if (shown > 0.01f) Modifier.glass(source = source) else Modifier),
        )
        Row(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = onBack,
                colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.35f * (1f - shown)),
                    contentColor = androidx.compose.ui.graphics.lerp(Color.White, MaterialTheme.colorScheme.onSurface, shown),
                ),
            ) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = shown; translationY = (1f - shown) * 12f },
            )
        }
    }
}

/** "In your library" pill. */
@Composable
fun LibraryBadge(modifier: Modifier = Modifier, label: String = "In your library") {
    val status = LocalStatusColors.current
    Surface(modifier, shape = CircleShape, color = status.successContainer, contentColor = status.onSuccessContainer) {
        Row(Modifier.padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** Small tonal pill, used for genres. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Body text that collapses to [collapsedLines] with a More/Less toggle when it is longer. */
@Composable
fun ExpandableText(text: String, modifier: Modifier = Modifier, collapsedLines: Int = 4) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(
        modifier.animateContentSize().clickable(
            enabled = overflows || expanded,
            role = Role.Button,
            onClickLabel = if (expanded) "Show less" else "Show more",
        ) { expanded = !expanded },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                if (expanded) "Less" else "More",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** TMDB's required attribution line, shown under TMDB-backed pages. */
@Composable
fun TmdbFootnote(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        TmdbMark(height = 14.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            "Data and images from TMDB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Text wordmark in TMDB's brand colours (their logo asset can replace it later). */
@Composable
fun TmdbMark(modifier: Modifier = Modifier, height: Dp = 18.dp) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 3))
            .background(Brush.horizontalGradient(listOf(Color(0xFF90CEA1), Color(0xFF01B4E4)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "TMDB",
            color = Color(0xFF0D253F),
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * (height.value / 16f)),
            modifier = Modifier.padding(horizontal = height / 3),
            maxLines = 1,
        )
    }
}

/** Placeholder bars while TMDB text loads. */
@Composable
fun TextSkeleton(lines: Int = 4, modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier) {
        repeat(lines) { index ->
            Box(
                Modifier.fillMaxWidth(if (index == lines - 1) 0.6f else 1f).height(12.dp).clip(CircleShape).background(block),
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}
