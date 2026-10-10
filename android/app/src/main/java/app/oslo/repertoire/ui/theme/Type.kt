package app.oslo.repertoire.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

private val Base = Typography()

private fun TextStyle.tight(weight: FontWeight, spacing: Double) =
    copy(fontWeight = weight, letterSpacing = spacing.em)

/** The Material 3 type scale with tighter, heavier display and headline styles. */
val AppTypography = Typography(
    displayLarge = Base.displayLarge.tight(FontWeight.SemiBold, -0.025),
    displayMedium = Base.displayMedium.tight(FontWeight.SemiBold, -0.02),
    displaySmall = Base.displaySmall.tight(FontWeight.SemiBold, -0.015),
    headlineLarge = Base.headlineLarge.tight(FontWeight.SemiBold, -0.015),
    headlineMedium = Base.headlineMedium.tight(FontWeight.SemiBold, -0.01),
    headlineSmall = Base.headlineSmall.tight(FontWeight.SemiBold, -0.005),
    titleLarge = Base.titleLarge.tight(FontWeight.SemiBold, -0.005),
    titleMedium = Base.titleMedium.tight(FontWeight.SemiBold, 0.0),
    titleSmall = Base.titleSmall.tight(FontWeight.SemiBold, 0.005),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge.tight(FontWeight.SemiBold, 0.01),
    labelMedium = Base.labelMedium.tight(FontWeight.Medium, 0.02),
    labelSmall = Base.labelSmall.tight(FontWeight.Medium, 0.03),
)
