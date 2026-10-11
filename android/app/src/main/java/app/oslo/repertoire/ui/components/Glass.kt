package app.oslo.repertoire.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** The app-wide glass source: everything drawn under translucent bars. Null in previews. */
val LocalGlass = staticCompositionLocalOf<HazeState?> { null }

/** Marks the content that glass bars blur (the NavHost). */
fun Modifier.glassSource(state: HazeState): Modifier = hazeSource(state)

/**
 * Frosted glass: the content behind, blurred and tinted with the surface (RenderEffect on
 * Android 12+; a denser translucent tint on older versions, where blur isn't available).
 */
@Composable
fun Modifier.glass(strength: Float = 1f, source: HazeState? = null): Modifier {
    val state = source ?: LocalGlass.current
    val surface = MaterialTheme.colorScheme.surface
    if (state == null) return this
    val style = HazeStyle(
        backgroundColor = surface,
        tint = HazeTint(surface.copy(alpha = 0.62f)),
        blurRadius = 26.dp * strength,
        noiseFactor = 0.06f,
        fallbackTint = HazeTint(surface.copy(alpha = 0.93f)),
    )
    return this.hazeEffect(state, style)
}
