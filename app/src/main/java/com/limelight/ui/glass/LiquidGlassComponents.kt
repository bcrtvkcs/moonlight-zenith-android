package com.limelight.ui.glass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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

enum class GlassElevationLevel {
    CARD,
    CHROME,
    ACCENT,
    SHEET
}

/**
 * Core Kyant0/AndroidLiquidGlass modifier wrapper.
 * Reads Snapshot-tracked [LocalGlassTokensState] directly inside both `effects` and `onDrawSurface`
 * so EVERY optical parameter change (blur, lens refraction amount/height, chromatic aberration,
 * 3D depth effect, vibrancy, surface opacity, specular highlight) immediately triggers both
 * `DrawBackdropNode.updateEffects()` and a live Compose draw invalidation at 60/120fps.
 */
@Composable
fun Modifier.liquidGlassSurface(
    backdrop: Backdrop,
    shape: Shape,
    level: GlassElevationLevel = GlassElevationLevel.CARD,
    tintOverride: Color? = null,
    isFocused: Boolean = false,
    enableLens: Boolean = true,
    enableShadow: Boolean = true
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
                    val blurDp = when (level) {
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
                    if (t.vibrancyEnabled) {
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
                            radius = if (level == GlassElevationLevel.SHEET) 28.dp else 18.dp,
                            color = Color.Black.copy(alpha = 0.28f)
                        )
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    // Reading tokensState.value here guarantees ContentDrawScope.draw() is invalidated
                    // whenever any of the 8 optical parameters changes, fixing DrawBackdropNode's
                    // updateEffects() redraw gap when padding stays unchanged.
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

        val backgroundBackdrop = rememberFramePacedLayerBackdrop()
        val contentBackdrop = rememberFramePacedLayerBackdrop()
        val combinedBackdrop = rememberCombinedBackdrop(backgroundBackdrop, contentBackdrop)

        CompositionLocalProvider(
            LocalBackgroundBackdrop provides backgroundBackdrop,
            LocalContentBackdrop provides combinedBackdrop
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // LAYER 1: Edge-to-edge background canvas recorded into backgroundBackdrop
                LiquidGlassBackgroundLayer(
                    bgState = bgState,
                    modifier = Modifier
                        .fillMaxSize()
                        .framePacedLayerBackdrop(backgroundBackdrop)
                )

                // LAYER 2: Main scrollable content recorded into contentBackdrop
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .framePacedLayerBackdrop(contentBackdrop)
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

@Composable
fun LiquidGlassBackgroundLayer(
    bgState: UiBackgroundState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    val opacityFraction = bgState.effectiveOpacity / 100f
    val tintAlpha = bgState.effectiveTintOpacity / 100f
    val blurDp = bgState.effectiveBlurDp.dp
    val blurPx = with(LocalDensity.current) { blurDp.toPx() }.coerceIn(0f, 150f)

    val tintColorInt = remember(bgState.tintMode, colorScheme.surface, colorScheme.primary) {
        UiBackgroundManager.resolveTintColorInt(
            bgState.tintMode,
            colorScheme.surface.toArgb(),
            colorScheme.primary.toArgb()
        )
    }

    val customBitmap = remember(bgState.backgroundSource, bgState.hasCustomImage, bgState.imageVersion) {
        when {
            bgState.isCustomImageActive -> UiBackgroundManager.loadCustomBitmapSync(context)
            bgState.isWallpaperActive -> UiBackgroundManager.tryLoadWallpaperBitmap(context)
            else -> null
        }
    }
    val useLiveWindowWallpaper = bgState.isWallpaperActive && customBitmap == null

    Box(modifier = modifier) {
        when {
            customBitmap != null && !customBitmap.isRecycled -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.surface)
                )
                Image(
                    bitmap = customBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = opacityFraction
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurPx > 0.5f) {
                                renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                                clip = true
                            } else {
                                renderEffect = null
                                clip = false
                            }
                        }
                )
            }
            useLiveWindowWallpaper -> {
                val inverseScrim = (1f - opacityFraction).coerceIn(0f, 1f)
                if (inverseScrim > 0.005f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.surface.copy(alpha = inverseScrim))
                    )
                }
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

        // Luminous aurora orbs + diagonal specular light ribbon so lens refraction & RGB prism
        // visibly bend high-contrast gradients even when no custom image is set.
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
    iconTint: Color = MaterialTheme.colorScheme.onSurface
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else if (isFocused) 1.06f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "glassIconScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlassSurface(
                backdrop = backdrop,
                shape = CircleShape,
                level = level,
                isFocused = isFocused
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
                    level = GlassElevationLevel.CHROME
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
                isFocused = isFocused
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

@Composable
fun GlassPillTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop = LocalContentBackdrop.current,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val tokens = LocalGlassTokens.current

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in tabs.indices) {
            listState.animateScrollToItem(
                index = (selectedIndex - 1).coerceAtLeast(0)
            )
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(tabs) { index, title ->
            val isSelected = index == selectedIndex
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.93f else if (isSelected) 1.03f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "glassTabScale"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .height(42.dp)
                    .liquidGlassSurface(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(21.dp),
                        level = if (isSelected) GlassElevationLevel.ACCENT else GlassElevationLevel.CHROME,
                        tintOverride = if (isSelected) tokens.pillTint else tokens.chromeTint.copy(alpha = tokens.chromeTint.alpha * 0.72f),
                        isFocused = isFocused,
                        enableLens = true,
                        enableShadow = isSelected
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
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
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
                        level = GlassElevationLevel.SHEET
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
                        level = GlassElevationLevel.SHEET
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
    backdrop: Backdrop = LocalContentBackdrop.current
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
            .liquidGlassSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(23.dp),
                level = if (primary || destructive) GlassElevationLevel.ACCENT else GlassElevationLevel.CHROME,
                tintOverride = tint,
                isFocused = isFocused,
                enableLens = true,
                enableShadow = false
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

/**
 * Frame-paced [Backdrop] recorder that lets users cap dynamic Liquid Glass refraction refresh rate
 * (15 / 30 / 60 / 90 / 120 / 144 / 144+ FPS) for battery & GPU savings while keeping scrolling and
 * touch interactions at full native display refresh rate.
 */
@Stable
class FramePacedLayerBackdrop(
    val graphicsLayer: GraphicsLayer,
    private val maxFpsProvider: () -> Int
) : Backdrop {
    override val isCoordinatesDependent: Boolean = true
    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var catchUpTick by mutableIntStateOf(0)

    private var lastRecordNanos: Long = 0L
    private var hasRecorded: Boolean = false
    private var catchUpScheduled: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val catchUpRunnable = Runnable {
        catchUpScheduled = false
        catchUpTick++
    }

    internal fun cancelPending() {
        mainHandler.removeCallbacks(catchUpRunnable)
        catchUpScheduled = false
    }

    internal fun shouldRecordNow(): Boolean {
        val fps = maxFpsProvider()
        val now = System.nanoTime()
        val minIntervalNs = if (fps > 0) 1_000_000_000L / fps else 0L
        if (!hasRecorded || minIntervalNs <= 0L || (now - lastRecordNanos) >= minIntervalNs) {
            lastRecordNanos = now
            hasRecorded = true
            if (catchUpScheduled) {
                mainHandler.removeCallbacks(catchUpRunnable)
                catchUpScheduled = false
            }
            return true
        }
        if (!catchUpScheduled) {
            catchUpScheduled = true
            val delayMs = ((minIntervalNs - (now - lastRecordNanos)) / 1_000_000L).coerceIn(1L, 120L)
            mainHandler.postDelayed(catchUpRunnable, delayMs)
        }
        return false
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        @Suppress("UNUSED_VARIABLE")
        val tick = catchUpTick
        if (!hasRecorded) return
        val coords = coordinates ?: return
        val layerCoords = layerCoordinates ?: return
        if (!coords.isAttached || !layerCoords.isAttached) return
        val offset = try {
            layerCoords.localPositionOf(coords)
        } catch (_: Exception) {
            coords.positionInWindow() - layerCoords.positionInWindow()
        }
        withTransform({
            translate(-offset.x, -offset.y)
        }) {
            drawLayer(graphicsLayer)
        }
    }
}

@Composable
fun rememberFramePacedLayerBackdrop(): FramePacedLayerBackdrop {
    val tokensState = LocalGlassTokensState.current
    val graphicsLayer = rememberGraphicsLayer()
    val backdrop = remember(graphicsLayer) {
        FramePacedLayerBackdrop(graphicsLayer) { tokensState.value.glassMaxFps }
    }
    DisposableEffect(backdrop) {
        onDispose {
            backdrop.cancelPending()
            backdrop.layerCoordinates = null
        }
    }
    return backdrop
}

fun Modifier.framePacedLayerBackdrop(backdrop: FramePacedLayerBackdrop): Modifier =
    this
        .onGloballyPositioned { coords ->
            if (coords.isAttached) {
                backdrop.layerCoordinates = coords
            }
        }
        .drawWithContent {
            @Suppress("UNUSED_VARIABLE")
            val tick = backdrop.catchUpTick
            drawContent()
            val w = size.width.toInt()
            val h = size.height.toInt()
            if (w > 0 && h > 0 && backdrop.shouldRecordNow()) {
                backdrop.graphicsLayer.record(size = IntSize(w, h)) {
                    this@drawWithContent.drawContent()
                }
            }
        }
