package com.limelight.ui.glass

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
 * Automatically uses full AGSL lens + chromatic aberration on API 33+, RenderEffect blur + vibrancy
 * on API 31-32, and a crisp translucent specular surface fallback on older Android versions.
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
    val tokens = LocalGlassTokens.current
    val primaryColor = MaterialTheme.colorScheme.primary

    val blurDp = when (level) {
        GlassElevationLevel.CARD -> tokens.cardBlur
        GlassElevationLevel.CHROME -> tokens.chromeBlur
        GlassElevationLevel.ACCENT -> tokens.chromeBlur
        GlassElevationLevel.SHEET -> tokens.sheetBlur
    }
    val refractionHeight = when (level) {
        GlassElevationLevel.CARD -> tokens.cardRefractionHeight
        GlassElevationLevel.CHROME, GlassElevationLevel.ACCENT -> tokens.chromeRefractionHeight
        GlassElevationLevel.SHEET -> tokens.chromeRefractionHeight
    }
    val refractionAmount = when (level) {
        GlassElevationLevel.CARD -> tokens.cardRefractionAmount
        GlassElevationLevel.CHROME, GlassElevationLevel.ACCENT -> tokens.chromeRefractionAmount
        GlassElevationLevel.SHEET -> tokens.cardRefractionAmount
    }
    val surfaceTint = tintOverride ?: when (level) {
        GlassElevationLevel.CARD -> tokens.cardTint
        GlassElevationLevel.CHROME -> tokens.chromeTint
        GlassElevationLevel.ACCENT -> tokens.accentTint
        GlassElevationLevel.SHEET -> tokens.sheetTint
    }

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
                    if (tokens.vibrancyEnabled) {
                        vibrancy()
                    }
                    if (blurDp > 0.dp) {
                        blur(blurDp.toPx())
                    }
                    if (enableLens && refractionHeight > 0.dp && refractionAmount > 0.dp) {
                        lens(
                            refractionHeight = refractionHeight.toPx(),
                            refractionAmount = refractionAmount.toPx(),
                            depthEffect = tokens.depthEffect,
                            chromaticAberration = tokens.chromaticAberration
                        )
                    }
                },
                highlight = if (tokens.highlightAlpha > 0.01f || isFocused) {
                    {
                        Highlight(
                            width = if (isFocused) 1.25.dp else 0.75.dp,
                            alpha = if (isFocused) 1f else tokens.highlightAlpha
                        )
                    }
                } else {
                    null
                },
                shadow = if (enableShadow) {
                    {
                        Shadow(
                            radius = if (level == GlassElevationLevel.SHEET) 28.dp else 18.dp,
                            color = Color.Black.copy(alpha = 0.26f)
                        )
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    drawRect(surfaceTint)
                }
            )
            .then(focusBorderMod)
    } else {
        // Clean frosted translucent fallback for API 23-30
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
 *
 * Layer 1 (backgroundBackdrop): Custom image / live phone wallpaper / ambient mesh gradient + tint + opacity + blur.
 * Layer 2 (contentBackdrop): Scrollable screen body (cards refract Layer 1).
 * Layer 3 (floatingChrome + overlays): TopBar, Pill TabBar, FAB, NavigationRail, BottomSheets & Dialogs
 *          (refract BOTH Layer 1 and Layer 2 via rememberCombinedBackdrop!).
 */
@Composable
fun LiquidGlassScaffold(
    floatingChrome: @Composable BoxScope.(combinedBackdrop: Backdrop) -> Unit = {},
    overlayContent: @Composable BoxScope.(combinedBackdrop: Backdrop) -> Unit = {},
    body: @Composable BoxScope.(backgroundBackdrop: Backdrop) -> Unit
) {
    val context = LocalContext.current
    val bgState by UiBackgroundManager.state.collectAsState()

    MoonlightLiquidGlassTheme(bgState = bgState) {
        val colorScheme = MaterialTheme.colorScheme
        val surfaceArgb = colorScheme.surface.toArgb()

        // Keep window wallpaper flags & Android 12+ window blur synchronized with live preference/preview changes
        LaunchedEffect(
            bgState.backgroundSource,
            bgState.effectiveBlurDp,
            bgState.pureBlackTheme,
            bgState.themeMode
        ) {
            (context as? Activity)?.let { act ->
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
                // LAYER 1: Edge-to-edge background canvas recorded into backgroundBackdrop
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

                // LAYER 3A: Floating Liquid Glass chrome (TopBar, TabBar, NavRail, FAB)
                floatingChrome(combinedBackdrop)

                // LAYER 3B: Modal Liquid Glass Bottom Sheets & Dialogs
                overlayContent(combinedBackdrop)
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
                // Base theme color underneath so opacity < 100% blends gracefully into theme surface
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.surface)
                )
                val imgModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = opacityFraction }
                    .let { if (blurDp > 0.dp) it.blur(blurDp) else it }

                Image(
                    bitmap = customBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = imgModifier
                )
            }
            useLiveWindowWallpaper -> {
                // System draws live/static phone wallpaper directly behind our transparent window.
                // Lowering opacity darkens the wallpaper softly with the active theme surface color.
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
                // Default Liquid Glass ambient mesh gradient derived from active Material You / Zenith palette
                AmbientThemeMeshBackground(
                    surface = colorScheme.surface,
                    primary = colorScheme.primary,
                    tertiary = colorScheme.tertiary,
                    secondaryContainer = colorScheme.secondaryContainer,
                    pureBlack = bgState.pureBlackTheme
                )
            }
        }

        // User-selected tint overlay over custom image or phone wallpaper
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
    val glowScale = if (pureBlack) 0.22f else 0.42f
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
                    primary.copy(alpha = 0.36f * glowScale * 2f),
                    Color.Transparent
                ),
                center = Offset(w * 0.16f, h * 0.14f),
                radius = maxDim * 0.65f
            ),
            radius = maxDim * 0.65f,
            center = Offset(w * 0.16f, h * 0.14f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    tertiary.copy(alpha = 0.30f * glowScale * 2f),
                    Color.Transparent
                ),
                center = Offset(w * 0.88f, h * 0.36f),
                radius = maxDim * 0.58f
            ),
            radius = maxDim * 0.58f,
            center = Offset(w * 0.88f, h * 0.36f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryContainer.copy(alpha = 0.45f * glowScale * 2f),
                    Color.Transparent
                ),
                center = Offset(w * 0.42f, h * 0.88f),
                radius = maxDim * 0.62f
            ),
            radius = maxDim * 0.62f,
            center = Offset(w * 0.42f, h * 0.88f)
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
        targetValue = if (isPressed) 0.92f else if (isFocused) 1.05f else 1f,
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
        targetValue = if (isPressed) 0.94f else if (isFocused) 1.04f else 1f,
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
                targetValue = if (isPressed) 0.94f else if (isSelected) 1.02f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
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
                        enableLens = isSelected,
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

@Composable
fun GlassSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "glassSwitchOffset"
    )
    val trackColor = if (checked) {
        MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.85f else 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (enabled) 0.75f else 0.35f)
    }
    val thumbColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (checked) 0.35f else 0.16f),
                shape = RoundedCornerShape(15.dp)
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
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
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
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
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
        ) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
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
    scrimAlpha: Float = 0.45f,
    content: @Composable ColumnScope.() -> Unit
) {
    if (visible) {
        BackHandler(onBack = onDismiss)
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(24.dp)
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(initialScale = 0.92f) + fadeIn(),
                exit = scaleOut(targetScale = 0.94f) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp)
                        .heightIn(max = 580.dp)
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
    val isFocused by interactionSource.collectIsFocusedAsState()
    val tint = when {
        destructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
        primary -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        else -> LocalGlassTokens.current.chromeTint
    }
    val textColor = when {
        destructive -> MaterialTheme.colorScheme.onErrorContainer
        primary -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(46.dp)
            .liquidGlassSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(23.dp),
                level = if (primary) GlassElevationLevel.ACCENT else GlassElevationLevel.CHROME,
                tintOverride = tint,
                isFocused = isFocused,
                enableLens = primary,
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
