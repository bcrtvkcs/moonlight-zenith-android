package com.limelight.ui.glass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.Shadow
import com.limelight.ui.background.UiBackgroundManager
import com.limelight.ui.background.UiBackgroundState
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

enum class GlassElevationLevel {
    CARD,
    CHROME,
    ACCENT,
    SHEET
}

/**
 * Core Kyant0/AndroidLiquidGlass modifier wrapper.
 * Reads Snapshot-tracked [LocalGlassTokensState] inside both `effects` and `onDrawSurface`
 * so all 8 optical parameter controls update live.
 *
 * Performance guidelines enforced here:
 * - [enableBlur] can be disabled on small nested chips (e.g. Attect shortcut buttons over an
 *   already-soft panel backdrop) so only single-pass optical `lens()` runs without a second
 *   Gaussian blur pass.
 * - [enableShadow] defaults to `false` for scrolling cards and uses lighter `12–16 dp` radii on
 *   floating chrome/sheets to avoid oversized offscreen BlurMaskFilter FBOs.
 */
@Composable
fun Modifier.liquidGlassSurface(
    backdrop: Backdrop,
    shape: Shape,
    level: GlassElevationLevel = GlassElevationLevel.CARD,
    tintOverride: Color? = null,
    isFocused: Boolean = false,
    enableBlur: Boolean = true,
    enableLens: Boolean = true,
    enableShadow: Boolean = false
): Modifier {
    val tokensState = LocalGlassTokensState.current
    val tokens = tokensState.value
    val primaryColor = MaterialTheme.colorScheme.primary

    val focusBorderMod = if (isFocused) {
        Modifier.border(2.dp, primaryColor, shape)
    } else {
        Modifier
    }

    return if (isRenderEffectSupported()) {
        this
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    val t = tokensState.value
                    val blurDp = if (!enableBlur) 0.dp else when (level) {
                        GlassElevationLevel.CARD -> t.cardBlur
                        GlassElevationLevel.CHROME, GlassElevationLevel.ACCENT -> t.chromeBlur
                        GlassElevationLevel.SHEET -> t.sheetBlur
                    }
                    val refractionHeight = when (level) {
                        GlassElevationLevel.CARD -> t.cardRefractionHeight
                        GlassElevationLevel.CHROME, GlassElevationLevel.ACCENT, GlassElevationLevel.SHEET -> t.chromeRefractionHeight
                    }
                    val refractionAmount = when (level) {
                        GlassElevationLevel.CARD -> t.cardRefractionAmount
                        GlassElevationLevel.CHROME, GlassElevationLevel.ACCENT -> t.chromeRefractionAmount
                        GlassElevationLevel.SHEET -> t.cardRefractionAmount
                    }
                    if (t.vibrancyEnabled && enableBlur) {
                        vibrancy()
                    }
                    if (blurDp > 0.dp) {
                        blur(blurDp.toPx())
                    }
                    if (enableLens && refractionHeight > 0.dp && refractionAmount > 0.dp) {
                        lens(
                            refractionHeight = refractionHeight.toPx(),
                            refractionAmount = refractionAmount.toPx(),
                            depthEffect = t.depthEffect,
                            chromaticAberration = t.chromaticAberration
                        )
                    }
                },
                highlight = {
                    val t = tokensState.value
                    if (t.highlightAlpha > 0.01f || isFocused) {
                        Highlight(
                            width = if (isFocused) 1.35.dp else 0.85.dp,
                            alpha = if (isFocused) 1f else t.highlightAlpha
                        )
                    } else {
                        null
                    }
                },
                shadow = if (enableShadow) {
                    {
                        Shadow(
                            radius = if (level == GlassElevationLevel.SHEET) 16.dp else 12.dp,
                            color = Color.Black.copy(alpha = 0.24f)
                        )
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    val t = tokensState.value
                    val resolvedTint = tintOverride ?: when (level) {
                        GlassElevationLevel.CARD -> t.cardTint
                        GlassElevationLevel.CHROME -> t.chromeTint
                        GlassElevationLevel.ACCENT -> t.accentTint
                        GlassElevationLevel.SHEET -> t.sheetTint
                    }
                    drawRect(resolvedTint)
                }
            )
            .then(focusBorderMod)
    } else {
        val surfaceTint = tintOverride ?: when (level) {
            GlassElevationLevel.CARD -> tokens.cardTint
            GlassElevationLevel.CHROME -> tokens.chromeTint
            GlassElevationLevel.ACCENT -> tokens.accentTint
            GlassElevationLevel.SHEET -> tokens.sheetTint
        }
        val fallbackAlpha = (surfaceTint.alpha + 0.34f).coerceAtMost(0.88f)
        this
            .clip(shape)
            .background(surfaceTint.copy(alpha = fallbackAlpha), shape)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) primaryColor else tokens.fallbackCardBorder,
                shape = shape
            )
    }
}

/**
 * Lightweight translucent glass surface for controls nested INSIDE an already-blurred Liquid Glass
 * container (TopBar icon buttons, Landscape NavRail buttons, Dialog/BottomSheet rows & buttons).
 * Eliminates redundant stacked multi-pass GPU blurs & shadow FBOs while keeping crisp specular rims.
 */
@Composable
fun Modifier.nestedGlassSurface(
    shape: Shape,
    fillColor: Color,
    isFocused: Boolean = false
): Modifier {
    val tokens = LocalGlassTokens.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val rimAlpha = (tokens.highlightAlpha * 0.34f).coerceIn(0.10f, 0.42f)
    return this
        .clip(shape)
        .background(fillColor, shape)
        .border(
            width = if (isFocused) 1.75.dp else 0.85.dp,
            color = if (isFocused) primaryColor else Color.White.copy(alpha = rimAlpha),
            shape = shape
        )
}

/**
 * Root dual-backdrop Liquid Glass scaffold used by PcView, AppView, StreamSettings, and AddComputerManually.
 */
@Composable
fun LiquidGlassScaffold(
    floatingChrome: @Composable BoxScope.(combinedBackdrop: Backdrop) -> Unit = {},
    overlayContent: @Composable BoxScope.(combinedBackdrop: Backdrop) -> Unit = {},
    body: @Composable BoxScope.(backgroundBackdrop: Backdrop) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val bgState by UiBackgroundManager.state.collectAsState()

    MoonlightLiquidGlassTheme(bgState = bgState) {
        val colorScheme = MaterialTheme.colorScheme
        val surfaceArgb = colorScheme.surface.toArgb()

        LaunchedEffect(
            bgState.backgroundSource,
            bgState.effectiveBlurDp,
            bgState.pureBlackTheme,
            bgState.themeMode
        ) {
            activity?.let { act ->
                UiBackgroundManager.applyWindowWallpaperFlags(act, surfaceArgb)
            }
        }

        val backgroundBackdrop = rememberLayerBackdrop()
        val contentBackdrop = rememberLayerBackdrop()
        val combinedBackdrop = rememberCombinedBackdrop(backgroundBackdrop, contentBackdrop)

        CompositionLocalProvider(
            LocalBackgroundBackdrop provides backgroundBackdrop,
            LocalContentBackdrop provides combinedBackdrop
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // LAYER 1: Edge-to-edge static background recorded into backgroundBackdrop
                LiquidGlassBackgroundLayer(
                    bgState = bgState,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backgroundBackdrop)
                )

                // LAYER 2: Main scrollable content recorded into contentBackdrop
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(contentBackdrop)
                ) {
                    body(backgroundBackdrop)
                }

                // LAYER 3A: Floating Liquid Glass chrome (TopBar, Pill TabBar / Floating Bottom Bar, NavRail, FAB)
                floatingChrome(combinedBackdrop)

                // LAYER 3B: Screen-specific Modal Liquid Glass Bottom Sheets & Dialogs
                overlayContent(combinedBackdrop)

                // LAYER 3C: Global Portal for Quit / Delete / Alert / Spinner Liquid Glass Dialogs
                GlassDialogPortalOverlay(
                    activity = activity,
                    backdrop = combinedBackdrop
                )
            }
        }
    }
}

/**
 * Renders the app background with ZERO per-frame GPU blur cost:
 * Custom images (and API <= 32 static wallpapers) use a static pre-blurred Bitmap computed once
 * on a background thread by [UiBackgroundManager.loadDisplayBitmapSync], rendered opaque at 100%
 * visibility under an optional color tint scrim.
 */
@Composable
fun LiquidGlassBackgroundLayer(
    bgState: UiBackgroundState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val tintAlpha = bgState.effectiveTintOpacity / 100f

    val tintColorInt = remember(bgState.tintMode, colorScheme.surface, colorScheme.primary) {
        UiBackgroundManager.resolveTintColorInt(
            bgState.tintMode,
            colorScheme.surface.toArgb(),
            colorScheme.primary.toArgb()
        )
    }

    val displayBitmap = remember(
        bgState.backgroundSource,
        bgState.hasCustomImage,
        bgState.imageVersion,
        bgState.blurredVersion,
        bgState.effectiveBlurDp
    ) {
        UiBackgroundManager.loadDisplayBitmapSync(context, bgState)
    }
    val useLiveWindowWallpaper = bgState.isWallpaperActive && displayBitmap == null

    Box(modifier = modifier) {
        when {
            displayBitmap != null && !displayBitmap.isRecycled -> {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            useLiveWindowWallpaper -> {
                // Transparent hole so Android FLAG_SHOW_WALLPAPER renders cleanly behind the window
            }
            else -> {
                AmbientThemeMeshBackground(
                    surface = colorScheme.surface,
                    primary = colorScheme.primary,
                    tertiary = colorScheme.tertiary,
                    secondaryContainer = colorScheme.secondaryContainer,
                    pureBlack = bgState.pureBlackTheme
                )
            }
        }

        if ((bgState.isCustomImageActive || bgState.isWallpaperActive) &&
            tintColorInt != null && tintAlpha > 0.005f
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(tintColorInt).copy(alpha = tintAlpha))
            )
        }
    }
}

@Composable
private fun AmbientThemeMeshBackground(
    surface: Color,
    primary: Color,
    tertiary: Color,
    secondaryContainer: Color,
    pureBlack: Boolean
) {
    val glowScale = if (pureBlack) 0.34f else 0.62f
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(surface)
    ) {
        val w = size.width
        val h = size.height
        val maxDim = maxOf(w, h)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.55f * glowScale),
                    primary.copy(alpha = 0.16f * glowScale),
                    Color.Transparent
                ),
                center = Offset(w * 0.14f, h * 0.15f),
                radius = maxDim * 0.52f
            ),
            radius = maxDim * 0.52f,
            center = Offset(w * 0.14f, h * 0.15f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    tertiary.copy(alpha = 0.52f * glowScale),
                    tertiary.copy(alpha = 0.14f * glowScale),
                    Color.Transparent
                ),
                center = Offset(w * 0.88f, h * 0.34f),
                radius = maxDim * 0.48f
            ),
            radius = maxDim * 0.48f,
            center = Offset(w * 0.88f, h * 0.34f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryContainer.copy(alpha = 0.65f * glowScale),
                    primary.copy(alpha = 0.22f * glowScale),
                    Color.Transparent
                ),
                center = Offset(w * 0.36f, h * 0.84f),
                radius = maxDim * 0.54f
            ),
            radius = maxDim * 0.54f,
            center = Offset(w * 0.36f, h * 0.84f)
        )
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    primary.copy(alpha = 0.30f * glowScale),
                    tertiary.copy(alpha = 0.28f * glowScale),
                    Color.Transparent
                ),
                start = Offset(0f, h * 0.28f),
                end = Offset(w, h * 0.68f)
            ),
            start = Offset(-w * 0.1f, h * 0.25f),
            end = Offset(w * 1.1f, h * 0.72f),
            strokeWidth = maxDim * 0.09f
        )
    }
}

@Composable
fun GlassIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    iconSize: Dp = 22.dp,
    backdrop: Backdrop = LocalContentBackdrop.current,
    level: GlassElevationLevel = GlassElevationLevel.CHROME,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    nestedInGlass: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else if (isFocused) 1.06f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "glassIconScale"
    )

    val nestedFill = when {
        level == GlassElevationLevel.ACCENT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        isPressed -> Color.White.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (nestedInGlass) {
                    Modifier.nestedGlassSurface(
                        shape = CircleShape,
                        fillColor = nestedFill,
                        isFocused = isFocused
                    )
                } else {
                    Modifier.liquidGlassSurface(
                        backdrop = backdrop,
                        shape = CircleShape,
                        level = level,
                        isFocused = isFocused,
                        enableShadow = true
                    )
                }
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun GlassTopBar(
    title: String,
    backdrop: Backdrop = LocalContentBackdrop.current,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val topInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = topInsets.calculateTopPadding() + 8.dp,
                start = 16.dp,
                end = 16.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .liquidGlassSurface(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(30.dp),
                    level = GlassElevationLevel.CHROME,
                    enableShadow = true
                )
                .padding(horizontal = 10.dp)
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
    }
}

@Composable
fun GlassExtendedFab(
    text: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalContentBackdrop.current
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else if (isFocused) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "glassFabScale"
    )
    val navPadding = WindowInsets.navigationBars.asPaddingValues()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(bottom = navPadding.calculateBottomPadding() + 18.dp, start = 16.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(56.dp)
            .liquidGlassSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(28.dp),
                level = GlassElevationLevel.ACCENT,
                isFocused = isFocused,
                enableShadow = true
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1
        )
    }
}

/**
 * Unified single-capsule Liquid Glass Tab Bar with 1:1 [pagerPosition]-synchronized sliding active
 * lens pill. Replaces 7 independent per-tab `drawBackdrop` blur passes with 1 outer glass capsule
 * + 1 single-pass refractive sliding lens that morphs width and glides in lockstep with page swipes
 * and tab taps.
 */
@Composable
fun GlassPillTabBar(
    tabs: List<String>,
    pagerPosition: Float,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop = LocalContentBackdrop.current,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    val tokensState = LocalGlassTokensState.current
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val tabBounds = remember(tabs) { mutableStateMapOf<Int, Pair<Float, Float>>() }
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }

    val clampedPos = pagerPosition.coerceIn(0f, tabs.lastIndex.toFloat())
    val floorIdx = floor(clampedPos).toInt().coerceIn(0, tabs.lastIndex)
    val ceilIdx = (floorIdx + 1).coerceIn(0, tabs.lastIndex)
    val frac = (clampedPos - floorIdx).coerceIn(0f, 1f)

    val startBounds = tabBounds[floorIdx]
    val endBounds = tabBounds[ceilIdx] ?: startBounds
    val pillLeftPx = if (startBounds != null && endBounds != null) {
        lerp(startBounds.first, endBounds.first, frac)
    } else 0f
    val pillWidthPx = if (startBounds != null && endBounds != null) {
        lerp(startBounds.second, endBounds.second, frac)
    } else 0f

    // Continuously keep the sliding pill centered in lockstep with page transitions
    LaunchedEffect(clampedPos, viewportWidthPx, startBounds, endBounds) {
        val maxScroll = scrollState.maxValue
        if (maxScroll > 0 && viewportWidthPx > 0f && pillWidthPx > 0f) {
            val pillCenter = pillLeftPx + pillWidthPx / 2f
            val targetScroll = (pillCenter - viewportWidthPx / 2f).roundToInt().coerceIn(0, maxScroll)
            if (abs(targetScroll - scrollState.value) > 1) {
                scrollState.scrollTo(targetScroll)
            }
        }
    }

    val tabContentBackdrop = rememberLayerBackdrop()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .liquidGlassSurface(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(24.dp),
                    level = GlassElevationLevel.CHROME,
                    enableLens = true,
                    enableShadow = true
                )
                .padding(horizontal = 5.dp, vertical = 4.dp)
                .onGloballyPositioned { coords ->
                    viewportWidthPx = coords.size.width.toFloat()
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalScroll(scrollState)
            ) {
                // Single sliding Liquid Glass active lens (single-pass lens over tab labels, zero double blur)
                if (pillWidthPx > 1f) {
                    val pillWidthDp = with(density) { pillWidthPx.toDp() }
                    val pillShape = RoundedCornerShape(20.dp)
                    val pillMod = Modifier
                        .graphicsLayer {
                            translationX = pillLeftPx
                        }
                        .width(pillWidthDp)
                        .fillMaxHeight()
                        .let { base ->
                            if (isRenderEffectSupported()) {
                                base.drawBackdrop(
                                    backdrop = tabContentBackdrop,
                                    shape = { pillShape },
                                    effects = {
                                        val t = tokensState.value
                                        lens(
                                            refractionHeight = 10.dp.toPx(),
                                            refractionAmount = 18.dp.toPx(),
                                            depthEffect = t.depthEffect,
                                            chromaticAberration = t.chromaticAberration
                                        )
                                    },
                                    highlight = {
                                        val t = tokensState.value
                                        Highlight(
                                            width = 0.9.dp,
                                            alpha = (t.highlightAlpha * 1.05f).coerceIn(0.38f, 1f)
                                        )
                                    },
                                    shadow = null,
                                    onDrawSurface = {
                                        val t = tokensState.value
                                        drawRect(t.pillTint.copy(alpha = (t.pillTint.alpha + 0.04f).coerceAtMost(0.55f)))
                                    }
                                )
                            } else {
                                base
                                    .clip(pillShape)
                                    .background(primaryColor.copy(alpha = 0.30f))
                                    .border(0.9.dp, Color.White.copy(alpha = 0.30f), pillShape)
                            }
                        }
                    Box(modifier = pillMod)
                }

                // Tab labels recorded into tabContentBackdrop
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxHeight()
                        .layerBackdrop(tabContentBackdrop)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val activeWeight = (1f - abs(clampedPos - index)).coerceIn(0f, 1f)
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        val labelColor = Color(
                            red = lerp(onSurfaceVariant.red, onPrimaryContainer.red, activeWeight),
                            green = lerp(onSurfaceVariant.green, onPrimaryContainer.green, activeWeight),
                            blue = lerp(onSurfaceVariant.blue, onPrimaryContainer.blue, activeWeight),
                            alpha = lerp(0.84f, 1f, activeWeight)
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxHeight()
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInParent().x
                                    val w = coords.size.width.toFloat()
                                    val prev = tabBounds[index]
                                    if (prev == null || abs(prev.first - pos) > 0.5f || abs(prev.second - w) > 0.5f) {
                                        tabBounds[index] = pos to w
                                    }
                                }
                                .clip(RoundedCornerShape(20.dp))
                                .then(
                                    if (isFocused) Modifier.border(1.5.dp, primaryColor, RoundedCornerShape(20.dp))
                                    else Modifier
                                )
                                .focusable(interactionSource = interactionSource)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) { onTabSelected(index) }
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (activeWeight >= 0.5f) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = labelColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Delegates to the authentic refractive [LiquidGlassSwitch] built with Kyant0/AndroidLiquidGlass.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    backdrop: Backdrop = LocalContentBackdrop.current,
    onCheckedChange: (Boolean) -> Unit
) {
    LiquidGlassSwitch(
        checked = checked,
        enabled = enabled,
        backdrop = backdrop,
        onCheckedChange = onCheckedChange
    )
}

@Composable
fun GlassBottomSheetOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop = LocalContentBackdrop.current,
    content: @Composable ColumnScope.() -> Unit
) {
    if (visible) {
        BackHandler(onBack = onDismiss)
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(160))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.82f)
        ) + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(170)
        ) + fadeOut(animationSpec = tween(150))
    ) {
        val navPadding = WindowInsets.navigationBars.asPaddingValues()
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = navPadding.calculateBottomPadding() + 12.dp
                    )
                    .liquidGlassSurface(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(28.dp),
                        level = GlassElevationLevel.SHEET,
                        enableShadow = true
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f))
                )
                Spacer(modifier = Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
fun GlassModalDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop = LocalContentBackdrop.current,
    scrimAlpha: Float = 0.42f,
    cancelable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (visible && cancelable) {
        BackHandler(onBack = onDismiss)
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(190)),
        exit = fadeOut(animationSpec = tween(150))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (cancelable) onDismiss() }
                )
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.88f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.78f)
        ) + fadeIn(animationSpec = tween(190)),
        exit = scaleOut(
            targetScale = 0.91f,
            animationSpec = tween(150)
        ) + fadeOut(animationSpec = tween(140))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .widthIn(max = 460.dp)
                    .liquidGlassSurface(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(28.dp),
                        level = GlassElevationLevel.SHEET,
                        enableShadow = true
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(22.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * State-retaining animated Liquid Glass modal dialog so both enter AND exit spring animations
 * always play smoothly instead of snapping out of the composition tree on dismiss.
 */
@Composable
fun <T : Any> GlassAnimatedModalDialog(
    targetState: T?,
    onDismiss: () -> Unit,
    backdrop: Backdrop = LocalContentBackdrop.current,
    scrimAlpha: (T) -> Float = { 0.42f },
    cancelable: (T) -> Boolean = { true },
    content: @Composable ColumnScope.(T) -> Unit
) {
    var retained by remember { mutableStateOf(targetState) }
    if (targetState != null) {
        retained = targetState
    }
    val active = targetState ?: retained
    GlassModalDialog(
        visible = targetState != null,
        onDismiss = onDismiss,
        backdrop = backdrop,
        scrimAlpha = active?.let(scrimAlpha) ?: 0.42f,
        cancelable = active?.let(cancelable) ?: true
    ) {
        if (active != null) {
            content(active)
        }
    }
}

/**
 * State-retaining animated Liquid Glass bottom sheet so both slide-in AND slide-out animations
 * play smoothly when dismissing.
 */
@Composable
fun <T : Any> GlassAnimatedBottomSheet(
    targetState: T?,
    onDismiss: () -> Unit,
    backdrop: Backdrop = LocalContentBackdrop.current,
    content: @Composable ColumnScope.(T) -> Unit
) {
    var retained by remember { mutableStateOf(targetState) }
    if (targetState != null) {
        retained = targetState
    }
    val active = targetState ?: retained
    GlassBottomSheetOverlay(
        visible = targetState != null,
        onDismiss = onDismiss,
        backdrop = backdrop
    ) {
        if (active != null) {
            content(active)
        }
    }
}

@Composable
fun GlassActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    backdrop: Backdrop = LocalContentBackdrop.current,
    nestedInGlass: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else if (isFocused) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "glassBtnScale"
    )

    val tint = when {
        destructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.56f)
        primary -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        else -> null
    }
    val nestedFill = when {
        destructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isPressed) 0.68f else 0.54f)
        primary -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isPressed) 0.72f else 0.58f)
        else -> Color.White.copy(alpha = if (isPressed) 0.22f else 0.12f)
    }
    val textColor = when {
        destructive -> MaterialTheme.colorScheme.onErrorContainer
        primary -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(46.dp)
            .then(
                if (nestedInGlass) {
                    Modifier.nestedGlassSurface(
                        shape = RoundedCornerShape(23.dp),
                        fillColor = nestedFill,
                        isFocused = isFocused
                    )
                } else {
                    Modifier.liquidGlassSurface(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(23.dp),
                        level = if (primary || destructive) GlassElevationLevel.ACCENT else GlassElevationLevel.CHROME,
                        tintOverride = tint,
                        isFocused = isFocused,
                        enableLens = true,
                        enableShadow = false
                    )
                }
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            maxLines = 1
        )
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}
