package app.oslo.repertoire.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.oslo.repertoire.R

/*
 * Two bundled variable fonts (both SIL Open Font License 1.1, texts in assets/licenses):
 * Fraunces, a soft "old style" display serif for titles and the wordmark, and Inter for UI.
 */

@OptIn(ExperimentalTextApi::class)
private fun fraunces(weight: Int, opsz: Float, italic: Boolean = false) = Font(
    if (italic) R.font.fraunces_italic else R.font.fraunces,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opsz),
        // A touch of softness keeps it warm rather than bookish.
        FontVariation.Setting("SOFT", 50f),
        FontVariation.Setting("WONK", 0f),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Display serif: page titles, title names, the wordmark. */
val DisplayFamily = FontFamily(
    fraunces(400, 72f),
    fraunces(500, 72f),
    fraunces(600, 72f),
    fraunces(700, 72f),
    fraunces(400, 72f, italic = true),
    fraunces(600, 72f, italic = true),
)

/** Fraunces at its largest optical size, for the launch wordmark. */
val WordmarkFamily = FontFamily(fraunces(600, 144f), fraunces(300, 144f, italic = true))

/** UI sans. */
val TextFamily = FontFamily(inter(400), inter(500), inter(600), inter(700))

private val Base = Typography()

private fun TextStyle.display(weight: FontWeight, spacing: Double) =
    copy(fontFamily = DisplayFamily, fontWeight = weight, letterSpacing = spacing.em)

private fun TextStyle.text(weight: FontWeight, spacing: Double) =
    copy(fontFamily = TextFamily, fontWeight = weight, letterSpacing = spacing.em)

/**
 * Type ramp: serif display and headline styles (and titleLarge, used for screen and section
 * titles), Inter for everything that is UI.
 */
val AppTypography = Typography(
    displayLarge = Base.displayLarge.display(FontWeight.SemiBold, -0.02),
    displayMedium = Base.displayMedium.display(FontWeight.SemiBold, -0.015),
    displaySmall = Base.displaySmall.display(FontWeight.SemiBold, -0.01),
    headlineLarge = Base.headlineLarge.display(FontWeight.SemiBold, -0.01),
    headlineMedium = Base.headlineMedium.display(FontWeight.SemiBold, -0.005),
    headlineSmall = Base.headlineSmall.display(FontWeight.SemiBold, 0.0),
    titleLarge = Base.titleLarge.display(FontWeight.SemiBold, 0.0).copy(fontSize = 22.sp),
    titleMedium = Base.titleMedium.text(FontWeight.SemiBold, -0.005),
    titleSmall = Base.titleSmall.text(FontWeight.SemiBold, 0.0),
    bodyLarge = Base.bodyLarge.text(FontWeight.Normal, -0.005),
    bodyMedium = Base.bodyMedium.text(FontWeight.Normal, 0.0),
    bodySmall = Base.bodySmall.text(FontWeight.Normal, 0.005),
    labelLarge = Base.labelLarge.text(FontWeight.SemiBold, 0.005),
    labelMedium = Base.labelMedium.text(FontWeight.Medium, 0.02),
    labelSmall = Base.labelSmall.text(FontWeight.Medium, 0.03),
)

/** Small caps-like overline for section labels ("FILMOGRAPHY"). */
val OverlineStyle = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.14.em)
