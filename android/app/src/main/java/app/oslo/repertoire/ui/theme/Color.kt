package app.oslo.repertoire.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brand palette taken from the app icon: near-black ink with a warm amber/gold mark. */
object Brand {
    val Ink = Color(0xFF07090E)
    val Amber = Color(0xFFF5C518)
    val AmberDeep = Color(0xFF765B00)
    val Foam = Color(0xFFE7EDF6)
}

internal val BrandDark = darkColorScheme(
    primary = Brand.Amber,
    onPrimary = Color(0xFF3D2E00),
    primaryContainer = Color(0xFF584400),
    onPrimaryContainer = Color(0xFFFFE08A),
    inversePrimary = Brand.AmberDeep,
    secondary = Color(0xFFD8C4A0),
    onSecondary = Color(0xFF3B2F15),
    secondaryContainer = Color(0xFF3A3324),
    onSecondaryContainer = Color(0xFFF5E0BB),
    tertiary = Color(0xFF9FD3B0),
    onTertiary = Color(0xFF053920),
    tertiaryContainer = Color(0xFF225036),
    onTertiaryContainer = Color(0xFFBBF0CB),
    background = Brand.Ink,
    onBackground = Brand.Foam,
    surface = Brand.Ink,
    onSurface = Brand.Foam,
    surfaceVariant = Color(0xFF1E2430),
    onSurfaceVariant = Color(0xFFA9B3C3),
    surfaceTint = Brand.Amber,
    inverseSurface = Brand.Foam,
    inverseOnSurface = Color(0xFF171D28),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF5E697B),
    outlineVariant = Color(0xFF2C3648),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A3140),
    surfaceDim = Brand.Ink,
    surfaceContainerLowest = Color(0xFF04060A),
    surfaceContainerLow = Color(0xFF0D1117),
    surfaceContainer = Color(0xFF11161F),
    surfaceContainerHigh = Color(0xFF171D28),
    surfaceContainerHighest = Color(0xFF1F2633),
)

internal val BrandLight = lightColorScheme(
    primary = Brand.AmberDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE08A),
    onPrimaryContainer = Color(0xFF241A00),
    inversePrimary = Brand.Amber,
    secondary = Color(0xFF695D3F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E1BB),
    onSecondaryContainer = Color(0xFF221B04),
    tertiary = Color(0xFF3B6A4C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBDF0CB),
    onTertiaryContainer = Color(0xFF00210F),
    background = Color(0xFFFFF8F0),
    onBackground = Color(0xFF1E1B13),
    surface = Color(0xFFFFF8F0),
    onSurface = Color(0xFF1E1B13),
    surfaceVariant = Color(0xFFECE1CF),
    onSurfaceVariant = Color(0xFF4D4639),
    surfaceTint = Brand.AmberDeep,
    inverseSurface = Color(0xFF333027),
    inverseOnSurface = Color(0xFFF7F0E4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF7F7667),
    outlineVariant = Color(0xFFD0C5B4),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFFF8F0),
    surfaceDim = Color(0xFFE0D9CC),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF3E7),
    surfaceContainer = Color(0xFFF4EDE1),
    surfaceContainerHigh = Color(0xFFEEE7DB),
    surfaceContainerHighest = Color(0xFFE9E2D6),
)

/** Status colours Material 3 has no slot for. */
@Immutable
data class StatusColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
)

internal val DarkStatus = StatusColors(
    success = Color(0xFF3DDC97),
    onSuccess = Color(0xFF003822),
    successContainer = Color(0xFF0F3B2A),
    onSuccessContainer = Color(0xFFA6F5CF),
    warning = Color(0xFFFFB95C),
)

internal val LightStatus = StatusColors(
    success = Color(0xFF006C46),
    onSuccess = Color.White,
    successContainer = Color(0xFFB7F2D4),
    onSuccessContainer = Color(0xFF002113),
    warning = Color(0xFF8A5100),
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }
