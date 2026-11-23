package com.limelight.ui.glass

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.limelight.ui.background.UiBackgroundState

private val ZenithDarkColorScheme = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF172678),
    primaryContainer = Color(0xFF303E90),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE6BAD7),
    onTertiary = Color(0xFF45263D),
    tertiaryContainer = Color(0xFF5E3C54),
    onTertiaryContainer = Color(0xFFFFD7F0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF11131A),
    onBackground = Color(0xFFE3E1EC),
    surface = Color(0xFF11131A),
    onSurface = Color(0xFFE3E1EC),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceContainerLowest = Color(0xFF0C0E14),
    surfaceContainerLow = Color(0xFF191B23),
    surfaceContainer = Color(0xFF1D1F28),
    surfaceContainerHigh = Color(0xFF282A33),
    surfaceContainerHighest = Color(0xFF33343E),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F)
)

@Immutable
data class GlassTokens(
    val cardBlur: Dp,
    val chromeBlur: Dp,
    val sheetBlur: Dp,
    val cardRefractionHeight: Dp,
    val cardRefractionAmount: Dp,
    val chromeRefractionHeight: Dp,
    val chromeRefractionAmount: Dp,
    val chromaticAberration: Boolean,
    val depthEffect: Boolean,
    val vibrancyEnabled: Boolean,
    val cardTint: Color,
    val chromeTint: Color,
    val pillTint: Color,
    val accentTint: Color,
    val sheetTint: Color,
    val highlightAlpha: Float,
    val fallbackCardBorder: Color
)

val LocalGlassTokens = staticCompositionLocalOf {
    buildGlassTokens(UiBackgroundState(), ZenithDarkColorScheme)
}

val LocalBackgroundBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }
val LocalContentBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

fun buildGlassTokens(bgState: UiBackgroundState, scheme: ColorScheme): GlassTokens {
    val isTintedBase = bgState.liquidGlassStyle == "tinted"
    val baseGlassColor = if (isTintedBase) scheme.primaryContainer else scheme.surfaceContainerHigh
    val primary = scheme.primary
    val primaryContainer = scheme.primaryContainer

    val blurVal = bgState.effectiveGlassBlurDp.toFloat()
    val refAmount = bgState.effectiveGlassRefractionAmountDp.toFloat()
    val refHeight = bgState.effectiveGlassRefractionHeightDp.toFloat()
    val surfaceAlpha = (bgState.effectiveGlassSurfaceOpacity / 100f).coerceIn(0.05f, 0.75f)
    val highlightAlpha = (bgState.effectiveGlassHighlightIntensity / 100f).coerceIn(0f, 1f)

    return GlassTokens(
        cardBlur = blurVal.dp,
        chromeBlur = (blurVal + 2f).coerceAtMost(36f).dp,
        sheetBlur = (blurVal + 6f).coerceAtMost(40f).dp,
        cardRefractionHeight = refHeight.dp,
        cardRefractionAmount = refAmount.dp,
        chromeRefractionHeight = (refHeight * 1.18f).coerceAtMost(32f).dp,
        chromeRefractionAmount = (refAmount * 1.25f).coerceAtMost(56f).dp,
        chromaticAberration = bgState.glassChromaticAberration,
        depthEffect = bgState.glassDepthEffect,
        vibrancyEnabled = bgState.glassVibrancy,
        cardTint = baseGlassColor.copy(alpha = surfaceAlpha),
        chromeTint = baseGlassColor.copy(alpha = (surfaceAlpha + 0.06f).coerceAtMost(0.82f)),
        pillTint = primary.copy(alpha = (surfaceAlpha * 0.65f).coerceIn(0.16f, 0.42f)),
        accentTint = primaryContainer.copy(alpha = (surfaceAlpha + 0.14f).coerceAtMost(0.84f)),
        sheetTint = scheme.surfaceContainer.copy(alpha = (surfaceAlpha + 0.18f).coerceAtMost(0.86f)),
        highlightAlpha = highlightAlpha,
        fallbackCardBorder = (if (isTintedBase) primary else Color.White)
            .copy(alpha = (highlightAlpha * 0.34f).coerceIn(0.06f, 0.45f))
    )
}

@Composable
fun MoonlightLiquidGlassTheme(
    bgState: UiBackgroundState,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isZenith = bgState.themeMode == "zenith"

    val baseScheme = if (!isZenith && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        ZenithDarkColorScheme
    }

    val colorScheme = if (bgState.pureBlackTheme) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0B0F),
            surfaceContainer = Color(0xFF12131A),
            surfaceContainerHigh = Color(0xFF1A1C24),
            surfaceContainerHighest = Color(0xFF242630)
        )
    } else {
        baseScheme
    }

    val glassTokens = buildGlassTokens(bgState, colorScheme)

    CompositionLocalProvider(LocalGlassTokens provides glassTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
