package com.limelight.ui.glass

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.limelight.R
import com.limelight.ui.background.UiBackgroundManager
import com.limelight.ui.background.UiBackgroundState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Authentic Kyant0/AndroidLiquidGlass-powered Switch (`LiquidToggle` architecture).
 * The glass capsule thumb refracts both the underlying screen backdrop AND the glowing switch track,
 * stretching and turning crystal-clear on press or horizontal drag.
 */
@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    backdrop: Backdrop = LocalContentBackdrop.current,
    onCheckedChange: (Boolean) -> Unit
) {
    val tokensState = LocalGlassTokensState.current
    val density = LocalDensity.current
    val trackWidthDp = 58.dp
    val trackHeightDp = 32.dp
    val thumbWidthDp = 32.dp
    val thumbHeightDp = 24.dp
    val padDp = 4.dp
    val travelPx = with(density) { (trackWidthDp - thumbWidthDp - padDp * 2).toPx() }

    var isPressedOrDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    LaunchedEffect(checked, isPressedOrDragging) {
        if (!isPressedOrDragging) {
            dragFraction = if (checked) 1f else 0f
        }
    }

    val animatedFraction by animateFloatAsState(
        targetValue = if (isPressedOrDragging) dragFraction else if (checked) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.72f),
        label = "liquidToggleFraction"
    )
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressedOrDragging) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.68f),
        label = "liquidTogglePress"
    )

    val trackBackdrop = rememberFramePacedLayerBackdrop()
    val thumbBackdrop = rememberCombinedBackdrop(backdrop, trackBackdrop)
    val primaryColor = MaterialTheme.colorScheme.primary
    val inactiveTrackColor = Color.White.copy(alpha = 0.18f)

    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .size(width = trackWidthDp, height = trackHeightDp)
            .alpha(if (enabled) 1f else 0.40f)
            .pointerInput(enabled, travelPx) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val startChecked = currentChecked
                    val startX = down.position.x
                    val startFraction = if (startChecked) 1f else 0f
                    var moved = false
                    dragFraction = startFraction
                    isPressedOrDragging = true

                    val pointerId = down.id
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            change.consume()
                            val dx = change.position.x - startX
                            if (kotlin.math.abs(dx) > 6f) {
                                moved = true
                            }
                            if (travelPx > 0f && moved) {
                                dragFraction = (startFraction + dx / travelPx).coerceIn(0f, 1f)
                            }
                        }
                        val nextChecked = if (moved) dragFraction >= 0.5f else !startChecked
                        if (nextChecked != startChecked) {
                            currentOnCheckedChange(nextChecked)
                        }
                    } finally {
                        isPressedOrDragging = false
                    }
                }
            }
    ) {
        // Track recorded into trackBackdrop so the refractive thumb magnifies its glowing edge
        val activeColor = lerpColor(inactiveTrackColor, primaryColor.copy(alpha = 0.88f), animatedFraction)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .framePacedLayerBackdrop(trackBackdrop)
                .clip(RoundedCornerShape(16.dp))
                .background(activeColor)
                .border(
                    width = 0.85.dp,
                    color = Color.White.copy(alpha = lerp(0.22f, 0.45f, animatedFraction)),
                    shape = RoundedCornerShape(16.dp)
                )
        )

        // Refractive Liquid Glass capsule thumb
        val thumbShape = RoundedCornerShape(12.dp)
        val thumbModifier = Modifier
            .padding(start = padDp)
            .graphicsLayer {
                translationX = animatedFraction * travelPx
                scaleX = lerp(1f, 1.24f, pressProgress)
                scaleY = lerp(1f, 1.18f, pressProgress)
            }
            .size(width = thumbWidthDp, height = thumbHeightDp)
            .let { base ->
                if (isRenderEffectSupported()) {
                    base
                        .framePacedGlassCache(
                            cacheKey = isPressedOrDragging to checked,
                            padDp = 12.dp
                        )
                        .drawBackdrop(
                            backdrop = thumbBackdrop,
                        shape = { thumbShape },
                        effects = {
                            val t = tokensState.value
                            if (t.vibrancyEnabled) vibrancy()
                            val blurR = lerp(2f, 0.5f, pressProgress).dp.toPx()
                            if (blurR > 0.2f) blur(blurR)
                            val refH = lerp(6f, 11f, pressProgress).dp.toPx()
                            val refA = lerp(12f, 26f, pressProgress).dp.toPx()
                            lens(
                                refractionHeight = refH,
                                refractionAmount = refA,
                                depthEffect = true,
                                chromaticAberration = t.chromaticAberration
                            )
                        },
                        highlight = {
                            val t = tokensState.value
                            Highlight(
                                width = 1.dp,
                                alpha = (t.highlightAlpha * 1.15f).coerceIn(0.45f, 1f)
                            )
                        },
                        shadow = {
                            Shadow(
                                radius = 6.dp,
                                color = Color.Black.copy(alpha = 0.30f)
                            )
                        },
                        onDrawSurface = {
                            val t = tokensState.value
                            val fillAlpha = lerp(0.84f, 0.22f, pressProgress)
                            drawRect(Color.White.copy(alpha = fillAlpha + (t.highlightAlpha * 0.0001f)))
                        }
                    )
                } else {
                    base
                        .clip(thumbShape)
                        .background(Color.White.copy(alpha = 0.92f))
                }
            }

        Box(modifier = thumbModifier)
    }
}

/**
 * Authentic Kyant0/AndroidLiquidGlass-powered Slider (`LiquidSlider` architecture).
 * Features a sleek translucent track with glowing active fill and an interactive Liquid Glass
 * capsule thumb that stays magnified and refractive from touch-down until finger lift,
 * regardless of finger movement outside the slider bounds.
 */
@Composable
fun LiquidGlassSlider(
    value: Int,
    minValue: Int,
    maxValue: Int,
    step: Int,
    backdrop: Backdrop = LocalContentBackdrop.current,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokensState = LocalGlassTokensState.current
    val safeMax = maxValue.coerceAtLeast(minValue + 1)
    val safeStep = step.coerceAtLeast(1)
    val range = (safeMax - minValue).toFloat()

    var isDragging by remember { mutableStateOf(false) }
    var rawFraction by remember {
        mutableFloatStateOf(((value - minValue).toFloat() / range).coerceIn(0f, 1f))
    }

    LaunchedEffect(value, minValue, safeMax, isDragging) {
        if (!isDragging) {
            rawFraction = ((value - minValue).toFloat() / range).coerceIn(0f, 1f)
        }
    }

    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    val displayedFraction by animateFloatAsState(
        targetValue = if (isDragging) rawFraction else ((value - minValue).toFloat() / range).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.85f),
        label = "liquidSliderFrac"
    )
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.68f),
        label = "liquidSliderDrag"
    )

    fun commitFraction(frac: Float) {
        val clamped = frac.coerceIn(0f, 1f)
        rawFraction = clamped
        val rawInt = (minValue + clamped * range).roundToInt()
        val stepped = (((rawInt - minValue + safeStep / 2) / safeStep) * safeStep + minValue)
            .coerceIn(minValue, safeMax)
        if (stepped != currentValue) {
            currentOnValueChange(stepped)
        }
    }

    val trackBackdrop = rememberFramePacedLayerBackdrop()
    val thumbBackdrop = rememberCombinedBackdrop(backdrop, trackBackdrop)
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val focusInteraction = remember { MutableInteractionSource() }
    val isFocused by focusInteraction.collectIsFocusedAsState()

    val thumbWidth = 42.dp
    val thumbHeight = 26.dp

    BoxWithConstraints(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .focusable(interactionSource = focusInteraction)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            currentOnValueChange((currentValue - safeStep).coerceIn(minValue, safeMax))
                            true
                        }
                        Key.DirectionRight -> {
                            currentOnValueChange((currentValue + safeStep).coerceIn(minValue, safeMax))
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        val density = LocalDensity.current
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val thumbWidthPx = with(density) { thumbWidth.toPx() }
        val usableWidthPx = (totalWidthPx - thumbWidthPx).coerceAtLeast(1f)

        // Track recorded into trackBackdrop so the lens thumb visibly magnifies the active fill edge
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(minValue, safeMax, safeStep, usableWidthPx, thumbWidthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isDragging = true
                        commitFraction((down.position.x - thumbWidthPx / 2f) / usableWidthPx)

                        val pointerId = down.id
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                // Track X across the entire screen; keep Liquid Glass active strictly
                                // until the finger is actually lifted off the display.
                                commitFraction((change.position.x - thumbWidthPx / 2f) / usableWidthPx)
                            }
                        } finally {
                            isDragging = false
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = thumbWidth / 2f)
                    .height(10.dp)
                    .framePacedLayerBackdrop(trackBackdrop)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(
                        width = if (isFocused) 1.5.dp else 0.75.dp,
                        color = if (isFocused) primaryColor else Color.White.copy(alpha = 0.24f),
                        shape = RoundedCornerShape(5.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayedFraction.coerceIn(0.001f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.78f),
                                    lerpColor(primaryColor, tertiaryColor, 0.28f)
                                )
                            )
                        )
                )
            }

            // Refractive Liquid Glass capsule thumb
            val thumbShape = RoundedCornerShape(13.dp)
            val thumbMod = Modifier
                .graphicsLayer {
                    translationX = displayedFraction * usableWidthPx
                    scaleX = lerp(1f, 1.26f, dragScale)
                    scaleY = lerp(1f, 1.22f, dragScale)
                }
                .size(width = thumbWidth, height = thumbHeight)
                .let { base ->
                    if (isRenderEffectSupported()) {
                        base
                            .framePacedGlassCache(
                                cacheKey = isDragging,
                                padDp = 14.dp
                            )
                            .drawBackdrop(
                                backdrop = thumbBackdrop,
                            shape = { thumbShape },
                            effects = {
                                val t = tokensState.value
                                if (t.vibrancyEnabled) vibrancy()
                                val blurR = lerp(2.5f, 0.5f, dragScale).dp.toPx()
                                if (blurR > 0.2f) blur(blurR)
                                val refH = lerp(8f, 14f, dragScale).dp.toPx()
                                val refA = lerp(16f, 34f, dragScale).dp.toPx()
                                lens(
                                    refractionHeight = refH,
                                    refractionAmount = refA,
                                    depthEffect = true,
                                    chromaticAberration = t.chromaticAberration
                                )
                            },
                            highlight = {
                                val t = tokensState.value
                                Highlight(
                                    width = 1.1.dp,
                                    alpha = (t.highlightAlpha * 1.15f).coerceIn(0.5f, 1f)
                                )
                            },
                            shadow = {
                                Shadow(
                                    radius = 10.dp,
                                    color = Color.Black.copy(alpha = 0.34f)
                                )
                            },
                            onDrawSurface = {
                                val t = tokensState.value
                                val alphaVal = lerp(0.76f, 0.14f, dragScale)
                                drawRect(Color.White.copy(alpha = alphaVal + (t.highlightAlpha * 0.0001f)))
                            }
                        )
                    } else {
                        base
                            .clip(thumbShape)
                            .background(Color.White.copy(alpha = 0.92f))
                    }
                }

            Box(modifier = thumbMod)
        }
    }
}

data class SettingsNavTabItem(
    val key: String,
    val shortLabel: String,
    @DrawableRes val iconRes: Int
)

/**
 * Resolves the icon & compact 1-word label for each of Moonlight Zenith's 7 settings categories.
 */
@Composable
fun resolveSettingsNavTabItem(categoryKey: String?, fallbackTitle: String): SettingsNavTabItem {
    return when (categoryKey) {
        "category_basic_settings" -> SettingsNavTabItem(
            key = categoryKey,
            shortLabel = stringResource(R.string.nav_short_basic),
            iconRes = R.drawable.ic_nav_basic
        )
        "category_audio_settings" -> SettingsNavTabItem(
            key = categoryKey,
            shortLabel = stringResource(R.string.nav_short_audio),
            iconRes = R.drawable.ic_nav_audio
        )
        "category_gamepad_settings" -> SettingsNavTabItem(
            key = categoryKey,
            shortLabel = stringResource(R.string.nav_short_gamepad),
            iconRes = R.drawable.ic_nav_gamepad
        )
        "category_input_settings" -> SettingsNavTabItem(
            key = categoryKey,
            shortLabel = stringResource(R.string.nav_short_input),
            iconRes = R.drawable.ic_nav_input
        )
        "category_onscreen_controls" -> SettingsNavTabItem(
            key = categoryKey,
            shortLabel = stringResource(R.string.nav_short_osc),
            iconRes = R.drawable.ic_nav_osc
        )
        "category_ui_settings" -> SettingsNavTabItem(
            key = categoryKey,
            shortLabel = stringResource(R.string.nav_short_ui),
            iconRes = R.drawable.ic_nav_ui
        )
        "category_advanced_settings" -> SettingsNavTabItem(
            key = categoryKey,
            shortLabel = stringResource(R.string.nav_short_advanced),
            iconRes = R.drawable.ic_nav_advanced
        )
        else -> SettingsNavTabItem(
            key = categoryKey.orEmpty(),
            shortLabel = fallbackTitle.take(10),
            iconRes = R.drawable.ic_settings
        )
    }
}

/**
 * Liquid Glass Floating Bottom Navigation Bar.
 *
 * Features:
 * - Outer full-capsule Liquid Glass dock floating above the gesture bar (`combinedBackdrop` refracts
 *   scrolling settings cards right behind it).
 * - Inner sliding **Liquid Glass lens bubble** that glides smoothly with spring physics on tap,
 *   tracks pager swipes 1:1, and supports **press-and-drag scrubbing across tabs**.
 * - Pressing & holding on the active tab swells the entire dock by ~5% and dynamically shifts the
 *   dock horizontally toward the finger's screen X offset (centered = 0 shift, right = slight right
 *   shift, left = slight left shift), springing smoothly back to rest on finger lift.
 */
@Composable
fun LiquidFloatingBottomBar(
    tabs: List<SettingsNavTabItem>,
    selectedIndex: Int,
    pageOffsetFraction: Float,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop = LocalContentBackdrop.current,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    val tokensState = LocalGlassTokensState.current
    val navPadding = WindowInsets.navigationBars.asPaddingValues()
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // +20% larger default floating capsule proportions
    val itemWidthDp = 90.dp
    val itemWidthPx = with(density) { itemWidthDp.toPx() }
    val dockPaddingHorizontal = 7.dp
    val dockPaddingVertical = 6.dp
    val dockPadPx = with(density) { dockPaddingHorizontal.toPx() }
    val dockHeight = 80.dp
    val maxShiftPx = with(density) { 16.dp.toPx() }

    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    var isHoldingActiveTab by remember { mutableStateOf(false) }
    var scrubIndexFloat by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var targetDockShiftPx by remember { mutableFloatStateOf(0f) }
    var lastPointerLocalX by remember { mutableFloatStateOf(0f) }
    var dockLeftInScreenPx by remember { mutableFloatStateOf(0f) }
    var dockWidthPx by remember { mutableFloatStateOf(0f) }

    val targetPos = if (isHoldingActiveTab) {
        scrubIndexFloat.coerceIn(0f, tabs.lastIndex.toFloat())
    } else {
        (selectedIndex + pageOffsetFraction).coerceIn(0f, tabs.lastIndex.toFloat())
    }

    val animatedIndicatorIndex by animateFloatAsState(
        targetValue = targetPos,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.78f),
        label = "glassDockLensPos"
    )
    val scrubScale by animateFloatAsState(
        targetValue = if (isHoldingActiveTab) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.68f),
        label = "glassDockLensScale"
    )
    val dockScale by animateFloatAsState(
        targetValue = if (isHoldingActiveTab) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.66f),
        label = "glassDockHoldScale"
    )
    val animatedDockShiftX by animateFloatAsState(
        targetValue = if (isHoldingActiveTab) targetDockShiftPx else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.72f),
        label = "glassDockHoldShiftX"
    )

    // Smoothly keep the active tab centered inside the scrollable capsule when not actively dragging
    LaunchedEffect(selectedIndex, tabs.size, isHoldingActiveTab) {
        if (!isHoldingActiveTab && scrollState.maxValue > 0) {
            val targetScroll = ((selectedIndex * itemWidthPx) - itemWidthPx * 1.4f)
                .roundToInt()
                .coerceIn(0, scrollState.maxValue)
            scrollState.animateScrollTo(targetScroll)
        }
    }

    // Gentle edge auto-scroll when scrubbing the active tab near the left/right edges of the dock
    LaunchedEffect(isHoldingActiveTab) {
        if (!isHoldingActiveTab) return@LaunchedEffect
        val edgeZonePx = with(density) { 48.dp.toPx() }
        val maxSpeedPxPerFrame = with(density) { 9.dp.toPx() }
        while (isHoldingActiveTab) {
            delay(16L)
            val width = dockWidthPx
            if (width > 0f && scrollState.maxValue > 0) {
                val x = lastPointerLocalX
                val delta = when {
                    x < edgeZonePx -> {
                        val factor = ((edgeZonePx - x) / edgeZonePx).coerceIn(0f, 1.5f)
                        -(maxSpeedPxPerFrame * factor)
                    }
                    x > width - edgeZonePx -> {
                        val factor = ((x - (width - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1.5f)
                        maxSpeedPxPerFrame * factor
                    }
                    else -> 0f
                }
                if (delta != 0f) {
                    val nextScroll = (scrollState.value + delta).roundToInt().coerceIn(0, scrollState.maxValue)
                    if (nextScroll != scrollState.value) {
                        scrollState.scrollTo(nextScroll)
                        val contentX = (x - dockPadPx) + nextScroll
                        val nextScrub = (contentX / itemWidthPx - 0.5f).coerceIn(0f, tabs.lastIndex.toFloat())
                        scrubIndexFloat = nextScrub
                        val hovered = nextScrub.roundToInt().coerceIn(0, tabs.lastIndex)
                        if (hovered != currentSelectedIndex) {
                            currentOnTabSelected(hovered)
                        }
                    }
                }
            }
        }
    }

    val dockContentBackdrop = rememberFramePacedLayerBackdrop()
    val lensBackdrop = rememberCombinedBackdrop(backdrop, dockContentBackdrop)
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = navPadding.calculateBottomPadding() + 14.dp)
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val maxDockWidthDp = (maxWidth - 28.dp).coerceAtMost(660.dp).coerceAtLeast(120.dp)

        // Untransformed hit wrapper so pointer X stays 100% free of transform feedback loops
        Box(
            modifier = Modifier
                .widthIn(max = maxDockWidthDp)
                .height(dockHeight)
                .onGloballyPositioned { coords ->
                    dockLeftInScreenPx = coords.positionInParent().x
                    dockWidthPx = coords.size.width.toFloat()
                }
                .pointerInput(tabs.size, itemWidthPx, dockPadPx, screenWidthPx, maxShiftPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        val activeLeftPx = dockPadPx + (currentSelectedIndex * itemWidthPx) - scrollState.value
                        val activeRightPx = activeLeftPx + itemWidthPx

                        if (down.position.x !in activeLeftPx..activeRightPx) {
                            // Touch started on an inactive tab: let normal tap & horizontal scroll run
                            return@awaitEachGesture
                        }

                        down.consume()
                        isHoldingActiveTab = true
                        lastPointerLocalX = down.position.x
                        scrubIndexFloat = currentSelectedIndex.toFloat()

                        fun computeDockShiftFromLocalX(localX: Float): Float {
                            val fingerScreenX = dockLeftInScreenPx + localX
                            val halfScreen = (screenWidthPx / 2f).coerceAtLeast(1f)
                            val normalized = ((fingerScreenX - halfScreen) / halfScreen).coerceIn(-1f, 1f)
                            return normalized * maxShiftPx
                        }

                        targetDockShiftPx = computeDockShiftFromLocalX(down.position.x)

                        val pointerId = down.id
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                val localX = change.position.x
                                lastPointerLocalX = localX
                                targetDockShiftPx = computeDockShiftFromLocalX(localX)

                                val contentX = (localX - dockPadPx) + scrollState.value
                                val nextScrub = (contentX / itemWidthPx - 0.5f)
                                    .coerceIn(0f, tabs.lastIndex.toFloat())
                                scrubIndexFloat = nextScrub
                                val hovered = nextScrub.roundToInt().coerceIn(0, tabs.lastIndex)
                                if (hovered != currentSelectedIndex) {
                                    currentOnTabSelected(hovered)
                                }
                            }
                        } finally {
                            val finalTarget = scrubIndexFloat.roundToInt().coerceIn(0, tabs.lastIndex)
                            isHoldingActiveTab = false
                            targetDockShiftPx = 0f
                            if (finalTarget != currentSelectedIndex) {
                                currentOnTabSelected(finalTarget)
                            }
                        }
                    }
                }
        ) {
            // Outer floating Liquid Glass capsule dock (scales +5% and shifts dynamically on active-tab hold)
            Box(
                modifier = Modifier
                    .height(dockHeight)
                    .graphicsLayer {
                        scaleX = dockScale
                        scaleY = dockScale
                        translationX = animatedDockShiftX
                    }
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .liquidGlassSurface(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(40.dp),
                            level = GlassElevationLevel.CHROME,
                            enableLens = true,
                            enableShadow = true
                        )
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = dockPaddingHorizontal, vertical = dockPaddingVertical)
                        .horizontalScroll(scrollState)
                ) {
                    // Sliding Liquid Glass active lens bubble behind/over active item
                    val pillShape = RoundedCornerShape(34.dp)
                    val lensMod = Modifier
                        .graphicsLayer {
                            translationX = animatedIndicatorIndex * itemWidthPx
                            scaleX = lerp(1f, 1.12f, scrubScale)
                            scaleY = lerp(1f, 1.09f, scrubScale)
                        }
                        .size(width = itemWidthDp, height = dockHeight - (dockPaddingVertical * 2))
                        .let { base ->
                            if (isRenderEffectSupported()) {
                                base
                                    .framePacedGlassCache(
                                        cacheKey = isHoldingActiveTab to selectedIndex,
                                        padDp = 6.dp
                                    )
                                    .drawBackdrop(
                                        backdrop = lensBackdrop,
                                    shape = { pillShape },
                                    effects = {
                                        val t = tokensState.value
                                        if (t.vibrancyEnabled) vibrancy()
                                        val b = lerp(2.5f, 0.5f, scrubScale).dp.toPx()
                                        if (b > 0.2f) blur(b)
                                        val refH = lerp(12f, 18f, scrubScale).dp.toPx()
                                        val refA = lerp(22f, 40f, scrubScale).dp.toPx()
                                        lens(
                                            refractionHeight = refH,
                                            refractionAmount = refA,
                                            depthEffect = true,
                                            chromaticAberration = t.chromaticAberration
                                        )
                                    },
                                    highlight = {
                                        val t = tokensState.value
                                        Highlight(
                                            width = 1.dp,
                                            alpha = (t.highlightAlpha * 1.1f).coerceIn(0.42f, 1f)
                                        )
                                    },
                                    shadow = null,
                                    onDrawSurface = {
                                        val t = tokensState.value
                                        drawRect(
                                            lerpColor(
                                                primaryColor.copy(alpha = 0.24f),
                                                Color.White.copy(alpha = 0.16f),
                                                scrubScale
                                            ).copy(alpha = lerp(0.24f, 0.16f, scrubScale) + t.highlightAlpha * 0.0001f)
                                        )
                                    }
                                )
                            } else {
                                base
                                    .clip(pillShape)
                                    .background(primaryColor.copy(alpha = 0.28f))
                                    .border(1.dp, Color.White.copy(alpha = 0.30f), pillShape)
                            }
                        }

                    Box(modifier = lensMod)

                    // Tab icons + compact labels recorded into dockContentBackdrop
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxHeight()
                            .framePacedLayerBackdrop(dockContentBackdrop)
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = index == selectedIndex
                            val itemInteraction = remember { MutableInteractionSource() }
                            val isFocused by itemInteraction.collectIsFocusedAsState()
                            val itemColor = if (isSelected) primaryColor else onSurfaceVariant.copy(alpha = 0.85f)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .width(itemWidthDp)
                                    .fillMaxHeight()
                                    .clip(pillShape)
                                    .then(
                                        if (isFocused) Modifier.border(1.5.dp, primaryColor, pillShape)
                                        else Modifier
                                    )
                                    .focusable(interactionSource = itemInteraction)
                                    .clickable(
                                        interactionSource = itemInteraction,
                                        indication = null
                                    ) { onTabSelected(index) }
                                    .padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = tab.iconRes),
                                    contentDescription = tab.shortLabel,
                                    tint = if (isSelected) onSurface else itemColor,
                                    modifier = Modifier.size(25.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = tab.shortLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) onSurface else itemColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive live Liquid Glass optical testbench card shown in UI Settings & parameter dialogs.
 * Lets the user drag a real refractive Liquid Glass lens across a high-contrast background
 * (custom image if set, otherwise vivid aurora + grid) to verify all 8 parameters in real time.
 */
@Composable
fun LiquidGlassLivePreviewCard(
    bgState: UiBackgroundState,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewBackdrop = rememberFramePacedLayerBackdrop()
    val customBitmap = remember(bgState.hasCustomImage, bgState.imageVersion) {
        if (bgState.hasCustomImage) UiBackgroundManager.loadCustomBitmapSync(context) else null
    }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    var lensOffsetX by remember { mutableFloatStateOf(0f) }
    var lensOffsetY by remember { mutableFloatStateOf(0f) }

    val cardHeight = if (compact) 112.dp else 164.dp
    val lensWidth = if (compact) 136.dp else 172.dp
    val lensHeight = if (compact) 54.dp else 68.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
        ) {
            val density = LocalDensity.current
            val maxDx = with(density) { ((maxWidth - lensWidth) / 2f - 10.dp).toPx().coerceAtLeast(10f) }
            val maxDy = with(density) { ((maxHeight - lensHeight) / 2f - 8.dp).toPx().coerceAtLeast(6f) }

            // High-contrast scene recorded into previewBackdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .framePacedLayerBackdrop(previewBackdrop)
            ) {
                if (customBitmap != null && !customBitmap.isRecycled) {
                    Image(
                        bitmap = customBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0E1220))
                    ) {
                        val w = size.width
                        val h = size.height
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFF5E8E), Color.Transparent),
                                center = Offset(w * 0.22f, h * 0.30f),
                                radius = w * 0.42f
                            ),
                            radius = w * 0.42f,
                            center = Offset(w * 0.22f, h * 0.30f)
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF3CE8FF), Color.Transparent),
                                center = Offset(w * 0.78f, h * 0.68f),
                                radius = w * 0.46f
                            ),
                            radius = w * 0.46f,
                            center = Offset(w * 0.78f, h * 0.68f)
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(primary, tertiary, Color.Transparent),
                                center = Offset(w * 0.54f, h * 0.25f),
                                radius = w * 0.38f
                            ),
                            radius = w * 0.38f,
                            center = Offset(w * 0.54f, h * 0.25f)
                        )
                        // High-contrast crisp grid & diagonal stripe so lens refraction + RGB prism pop unmistakably
                        val step = 22.dp.toPx()
                        var x = step
                        while (x < w) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.28f),
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = 1.2.dp.toPx()
                            )
                            x += step
                        }
                        var y = step
                        while (y < h) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.28f),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1.2.dp.toPx()
                            )
                            y += step
                        }
                    }
                }

                // Crisp high-contrast text behind the draggable glass lens
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp)
                ) {
                    Text(
                        text = "ZENITH",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White.copy(alpha = 0.90f)
                    )
                    Text(
                        text = "GLASS",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = Color(0xFF7BE8FF).copy(alpha = 0.92f)
                    )
                }
            }

            // Draggable live Liquid Glass capsule refracting previewBackdrop with all 8 active parameters!
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(lensOffsetX.roundToInt(), lensOffsetY.roundToInt()) }
                    .size(width = lensWidth, height = lensHeight)
                    .liquidGlassSurface(
                        backdrop = previewBackdrop,
                        shape = RoundedCornerShape(lensHeight / 2f),
                        level = GlassElevationLevel.CHROME,
                        enableLens = true,
                        enableShadow = true
                    )
                    .pointerInput(maxDx, maxDy) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            lensOffsetX = (lensOffsetX + dragAmount.x).coerceIn(-maxDx, maxDx)
                            lensOffsetY = (lensOffsetY + dragAmount.y).coerceIn(-maxDy, maxDy)
                        }
                    }
            ) {
                Text(
                    text = "Liquid Glass",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }

        if (!compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.36f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.glass_live_preview_title),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.glass_live_preview_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Concise Android 13+ notice shown when Phone Wallpaper + Liquid Glass are active together.
 */
@Composable
fun GlassWallpaperNoticeCard(
    backdrop: Backdrop,
    onPickCustomImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(20.dp),
                level = GlassElevationLevel.ACCENT,
                tintOverride = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                enableShadow = false
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_m3_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.glass_wallpaper_refraction_notice),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        GlassActionButton(
            text = stringResource(R.string.ui_background_pick_action),
            onClick = onPickCustomImage,
            primary = true,
            backdrop = backdrop
        )
    }
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = lerp(start.red, stop.red, t),
        green = lerp(start.green, stop.green, t),
        blue = lerp(start.blue, stop.blue, t),
        alpha = lerp(start.alpha, stop.alpha, t)
    )
}
