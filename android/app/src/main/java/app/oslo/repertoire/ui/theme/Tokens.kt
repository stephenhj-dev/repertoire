package app.oslo.repertoire.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Spacing scale (4 dp grid). Screens use these instead of ad-hoc values. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp

    /** Horizontal page margin. */
    val page = 20.dp
}

/** Corner radii. Posters stay crisp and small-cornered like prints; cards and sheets are softer. */
object Radii {
    val poster = 10.dp
    val posterLarge = 14.dp
    val chip = 10.dp
    val card = 20.dp
    val sheet = 28.dp

    val Poster = RoundedCornerShape(poster)
    val PosterLarge = RoundedCornerShape(posterLarge)
    val Card = RoundedCornerShape(card)
}

/** 2:3, the shape of a one-sheet. */
const val POSTER_RATIO = 2f / 3f

/**
 * Motion: springs everywhere, so things settle rather than stop. [gentle] for layout and page
 * movement, [snappy] for press feedback, [bouncy] for small delightful things (the wordmark).
 */
object Motion {
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.86f, stiffness = 380f)
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = 300f)

    /** Page slides and fades between destinations. */
    val pageOffset: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = 0.9f, stiffness = 420f, visibilityThreshold = IntOffset(1, 1))
    val pageSize: FiniteAnimationSpec<IntSize> = spring(dampingRatio = 0.9f, stiffness = 420f, visibilityThreshold = IntSize(1, 1))
    val fade: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 500f)
}
