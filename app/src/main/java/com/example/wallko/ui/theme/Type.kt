package com.example.wallko.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.wallko.R

@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: FontWeight) = Font(
    resId = R.font.nunito,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Nunito variable font (res/font/nunito.ttf), one instance per weight we use. */
val Nunito = FontFamily(
    nunito(FontWeight.Normal),
    nunito(FontWeight.SemiBold),
    nunito(FontWeight.Bold),
    nunito(FontWeight.ExtraBold),
    nunito(FontWeight.Black),
)

private val base = Typography()

private fun TextStyle.nunito(weight: FontWeight) = copy(fontFamily = Nunito, fontWeight = weight)

val AppTypography = Typography(
    displayLarge = base.displayLarge.nunito(FontWeight.Black),
    displayMedium = base.displayMedium.nunito(FontWeight.Black),
    displaySmall = base.displaySmall.nunito(FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.nunito(FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.nunito(FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.nunito(FontWeight.ExtraBold),
    titleLarge = base.titleLarge.nunito(FontWeight.Bold),
    titleMedium = base.titleMedium.nunito(FontWeight.Bold),
    titleSmall = base.titleSmall.nunito(FontWeight.Bold),
    bodyLarge = base.bodyLarge.nunito(FontWeight.Normal),
    bodyMedium = base.bodyMedium.nunito(FontWeight.Normal),
    bodySmall = base.bodySmall.nunito(FontWeight.Normal),
    labelLarge = base.labelLarge.nunito(FontWeight.Bold),
    labelMedium = base.labelMedium.nunito(FontWeight.SemiBold),
    labelSmall = base.labelSmall.nunito(FontWeight.SemiBold),
)
