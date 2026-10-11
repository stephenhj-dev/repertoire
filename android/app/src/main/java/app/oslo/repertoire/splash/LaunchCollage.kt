package app.oslo.repertoire.splash

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.oslo.repertoire.R
import app.oslo.repertoire.ui.theme.Brand
import app.oslo.repertoire.ui.theme.Motion
import app.oslo.repertoire.ui.theme.TextFamily
import app.oslo.repertoire.ui.theme.WordmarkFamily
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COLUMNS = 9
private const val TILT = -12f

/**
 * Cold-start intro: a tilted, slowly drifting wall of canonical film posters under a dark
 * scrim, with the wordmark rising in the middle. Holds briefly, then blurs and dims away while
 * the app (composed underneath the whole time) rises into place. Tap to skip. Without cached
 * posters it shows the brand mark instead.
 *
 * Drawing is one Canvas of pre-rounded fixed-size bitmaps; the drift clock is read only in the
 * draw phase, so frames never recompose.
 */
@Composable
fun LaunchCollage(
    posters: List<ImageBitmap>,
    onReveal: () -> Unit,
    onFinished: () -> Unit,
    holdMs: Long = if (posters.isEmpty()) 1_100 else 1_900,
) {
    val reveal by rememberUpdatedState(onReveal)
    val finished by rememberUpdatedState(onFinished)
    val scope = rememberCoroutineScope()
    val wall = remember { Animatable(0f) }
    val mark = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    val clock = remember { mutableFloatStateOf(0f) }

    fun leave() {
        if (leaving) return
        leaving = true
        reveal()
        scope.launch {
            exit.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
            finished()
        }
    }

    LaunchedEffect(Unit) {
        launch { wall.animateTo(1f, tween(520, easing = LinearEasing)) }
        launch { delay(180); mark.animateTo(1f, Motion.bouncy()) }
        launch { delay(560); tagline.animateTo(1f, tween(500)) }
        launch {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { clock.floatValue = (it - start) / 1_000_000_000f }
        }
        delay(holdMs)
        leave()
    }

    val columns = remember(posters) { CollageCanon.columns(posters, COLUMNS, (posters.size / COLUMNS).coerceIn(4, 9)) }
    // Each column drifts at its own speed and direction (parallax); dp per second.
    val speeds = remember { FloatArray(COLUMNS) { i -> (if (i % 2 == 0) 1f else -1f) * (10f + (i * 7 % 5) * 4f) } }
    val density = LocalDensity.current
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Brand.Ink.copy(alpha = 1f - exit.value)) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Skip") { leave() }
            .semantics { contentDescription = "Repertoire" },
    ) {
        if (columns.any { it.isNotEmpty() }) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val e = exit.value
                        alpha = wall.value * (1f - e)
                        scaleX = 1.04f + 0.08f * e
                        scaleY = scaleX
                        if (blurs && e > 0.01f) {
                            val r = with(density) { (28.dp * e).toPx() }
                            renderEffect = BlurEffect(r, r, TileMode.Decal)
                        }
                    },
            ) {
                val t = clock.floatValue
                val diag = sqrt(size.width * size.width + size.height * size.height)
                val gap = 6.dp.toPx()
                val cellW = diag / COLUMNS
                val w = cellW - gap
                val h = w * 1.5f
                val step = h + gap
                val left = (size.width - diag) / 2f
                val top = (size.height - diag) / 2f
                rotate(TILT) {
                    columns.forEachIndexed { c, column ->
                        if (column.isEmpty()) return@forEachIndexed
                        val loop = column.size * step
                        val shift = ((speeds[c] * t).dp.toPx() + c * step * 0.37f) % loop
                        val x = left + c * cellW + gap / 2f
                        var y = top - loop + shift
                        if (y > top) y -= loop
                        var i = 0
                        while (y < top + diag) {
                            if (y + h > top) {
                                val image = column[i % column.size]
                                // Sub-pixel positions keep the slow drift smooth.
                                withTransform({
                                    translate(x, y)
                                    scale(w / image.width, h / image.height, pivot = Offset.Zero)
                                }) { drawImage(image) }
                            }
                            y += step
                            i++
                        }
                    }
                }
                // Scrim: dark overall, darkest in the middle where the wordmark sits, warm at the edges.
                drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.5f to Color.Black.copy(alpha = 0.45f), 1f to Color.Black.copy(alpha = 0.9f)))
                drawRect(
                    Brush.radialGradient(
                        0f to Color.Black.copy(alpha = 0.7f),
                        1f to Color.Transparent,
                        center = Offset(size.width / 2f, size.height * 0.48f),
                        radius = size.width * 0.75f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        0f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to Brand.Amber.copy(alpha = 0.10f),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = diag / 2f,
                    ),
                )
            }
        } else {
            BrandGlow(Modifier.align(Alignment.Center).graphicsLayer { alpha = wall.value * (1f - exit.value) })
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val m = mark.value
                    val e = exit.value
                    alpha = m.coerceIn(0f, 1f) * (1f - e)
                    val s = 0.92f + 0.08f * m - 0.04f * e
                    scaleX = s
                    scaleY = s
                    translationY = with(density) { (12.dp * (1f - m) - 24.dp * e).toPx() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (columns.all { it.isEmpty() }) Spacer(Modifier.height(170.dp))
            Text(
                "Repertoire",
                style = TextStyle(fontFamily = WordmarkFamily, fontWeight = FontWeight.SemiBold, fontSize = 54.sp, letterSpacing = (-0.02).em, color = Color(0xFFF6F1E7)),
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(44.dp).height(2.dp).graphicsLayer { scaleX = tagline.value }.background(Brand.Amber))
            Spacer(Modifier.height(12.dp))
            Text(
                "the great films, on your screen",
                style = TextStyle(fontFamily = WordmarkFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light, fontSize = 18.sp, color = Color(0xFFF6F1E7).copy(alpha = 0.78f)),
                modifier = Modifier.graphicsLayer { alpha = tagline.value; translationY = (1f - tagline.value) * 8f },
            )
        }
        Text(
            "Posters from TMDB",
            style = TextStyle(fontFamily = TextFamily, fontSize = 10.sp, letterSpacing = 0.12.em, color = Color.White.copy(alpha = 0.35f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = if (columns.any { it.isNotEmpty() }) tagline.value * (1f - exit.value) else 0f; translationY = -with(density) { 40.dp.toPx() } },
        )
    }
}

/** The app icon's mark with a slow amber glow, for launches before any posters are cached. */
@Composable
private fun BrandGlow(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(260.dp).drawBehind {
                drawCircle(Brush.radialGradient(listOf(Brand.Amber.copy(alpha = 0.22f * pulse), Color.Transparent)))
            },
        )
        Image(
            painterResource(R.drawable.brand_mark),
            contentDescription = null,
            modifier = Modifier.size(132.dp).graphicsLayer { translationY = -with(this) { 70.dp.toPx() } },
        )
    }
}
