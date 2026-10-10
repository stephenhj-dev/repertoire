package app.oslo.tivimate.guide.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF07090E)
val Paper = Color(0xFF10141C)
val Card = Color(0xFF171D28)
val Line = Color(0xFF2C3648)
val Amber = Color(0xFFF5C518)
val Foam = Color(0xFFE7EDF6)
val Mist = Color(0xFF8E9AAD)
val Good = Color(0xFF3DDC97)

private val Colors = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    background = Ink,
    onBackground = Foam,
    surface = Paper,
    onSurface = Foam,
    surfaceVariant = Card,
    onSurfaceVariant = Mist,
    outline = Line,
)

@Composable
fun GuideTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
