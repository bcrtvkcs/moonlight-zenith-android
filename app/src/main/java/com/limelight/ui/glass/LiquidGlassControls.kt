package com.limelight.ui.glass

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatorMutex
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
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpGraphicsColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.limelight.R
import com.limelight.ui.background.UiBackgroundManager
import com.limelight.ui.background.UiBackgroundState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Exact damped drag physics engine from `Kyant0/AndroidLiquidGlass` (`DampedDragAnimation.kt`).
 * Drives press swell (`1f -> 1.5f`), asymmetric squash-and-stretch springs (`scaleX` / `scaleY`),
 * velocity-coupled horizontal jelly deformation, and deferred release settle.
 */
private class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { _, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() } }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val targetValue = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            System.currentTimeMillis(),
            Offset(value, 0f)
        )
        val targetVelocity =
            velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

private suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(requireUnconsumed = false)
        initialDown.consume()
        onDragStart(initialDown)

        val pointerId = initialDown.id
        var lastChange = initialDown
        var completed = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                lastChange = change
                if (!change.pressed) {
                    change.consume()
                    completed = true
                    break
                }
                val dragAmount = change.positionChange()
                if (dragAmount != Offset.Zero) {
                    change.consume()
                    onDrag(change, dragAmount)
                }
            }
        } finally {
            if (completed) {
                onDragEnd(lastChange)
            } else {
                onDragCancel()
            }
        }
    }
}

/**
 * Exact `Kyant0/AndroidLiquidGlass` `LiquidToggle` implementation, customized only with
 * an exact neutral gray (`#808080`) active track color.
 */
@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    backdrop: Backdrop = LocalContentBackdrop.current,
    onCheckedChange: (Boolean) -> Unit
) {
    val accentColor = Color(0xFF808080)
    val trackColor = Color(0xFF787880).copy(alpha = 0.36f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val dragSlopPx = with(density) { 3f.dp.toPx() }
    val animationScope = rememberCoroutineScope()

    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    var didDrag by remember { mutableStateOf(false) }
    var cumulativeDragX by remember { mutableFloatStateOf(0f) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    val dampedDragAnimation = remember(animationScope, dragWidth, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {
                didDrag = false
                cumulativeDragX = 0f
            },
            onDragStopped = {
                val nextSelected = if (didDrag) {
                    didDrag = false
                    targetValue >= 0.5f
                } else {
                    !currentChecked
                }
                fraction = if (nextSelected) 1f else 0f
                if (nextSelected != currentChecked) {
                    currentOnCheckedChange(nextSelected)
                }
            },
            onDrag = { _, dragAmount ->
                cumulativeDragX += dragAmount.x
                if (!didDrag && abs(cumulativeDragX) >= dragSlopPx) {
                    didDrag = true
                }
                if (didDrag) {
                    val delta = dragAmount.x / dragWidth
                    fraction =
                        if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                        else (fraction - delta).fastCoerceIn(0f, 1f)
                }
            }
        )
    }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { value ->
                dampedDragAnimation.updateValue(value)
            }
    }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentChecked }
            .collectLatest { isSelected ->
                val target = if (isSelected) 1f else 0f
                if (target != fraction) {
                    fraction = target
                    dampedDragAnimation.animateToValue(target)
                }
            }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.40f)
            .semantics {
                role = Role.Switch
            }
            .then(if (enabled) dampedDragAnimation.modifier else Modifier)
    ) {
        Box(
            modifier = Modifier
                .layerBackdrop(trackBackdrop)
                .clip(CircleShape)
                .drawBehind {
                    val value = dampedDragAnimation.value
                    drawRect(lerpGraphicsColor(trackColor, accentColor, value))
                }
                .size(64f.dp, 28f.dp)
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    val value = dampedDragAnimation.value
                    val padding = 2f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, value)
                        else lerp(-padding, -(padding + dragWidth), value)
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 0.75f, progress)
                            val scaleY = lerp(0f, 0.75f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { CircleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            5f.dp.toPx() * progress,
                            10f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}

/**
 * Authentic Kyant0/AndroidLiquidGlass-powered Slider (`LiquidSlider` architecture).
 *
 * Uses a single unified `awaitEachGesture` loop so the magnified refractive Liquid Glass capsule
 * stays active strictly until the user lifts their finger off the display (never cancelling when
 * dragging outside slider bounds), and skips per-frame backdrop/shadow work while completely idle.
 */
@Composable
fun LiquidGlassSlider(
    value: Int,
    minValue: Int,
    maxValue: Int,
    step: Int,
    onValueChange: (Int) -> Unit,
    backdrop: Backdrop = LocalContentBackdrop.current,
    modifier: Modifier = Modifier
) {
    val tokensState = LocalGlassTokensState.current
    val safeMax = maxValue.coerceAtLeast(minValue + 1)
    val safeStep = step.coerceAtLeast(1)
    val range = (safeMax - minValue).toFloat()

    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    var isDragging by remember { mutableStateOf(false) }
    var rawFraction by remember(minValue, safeMax) {
        mutableFloatStateOf(((value - minValue).toFloat() / range).coerceIn(0f, 1f))
    }

    LaunchedEffect(value, isDragging, minValue, safeMax) {
        if (!isDragging) {
            rawFraction = ((value - minValue).toFloat() / range).coerceIn(0f, 1f)
        }
    }

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

    val isGlassActive = isRenderEffectSupported() && (isDragging || dragScale > 0.01f)

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

    val trackBackdrop = rememberLayerBackdrop()
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
                    .then(if (isGlassActive) Modifier.layerBackdrop(trackBackdrop) else Modifier)
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

            val thumbShape = RoundedCornerShape(13.dp)
            val thumbMod = Modifier
                .graphicsLayer {
                    translationX = displayedFraction * usableWidthPx
                    scaleX = lerp(1f, 1.26f, dragScale)
                    scaleY = lerp(1f, 1.22f, dragScale)
                }
                .size(width = thumbWidth, height = thumbHeight)
                .let { base ->
                    if (isGlassActive) {
                        base.drawBackdrop(
                            backdrop = thumbBackdrop,
                            shape = { thumbShape },
                            effects = {
                                val t = tokensState.value
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
                            shadow = null,
                            onDrawSurface = {
                                val t = tokensState.value
                                val alphaVal = lerp(0.82f, 0.14f, dragScale)
                                drawRect(Color.White.copy(alpha = alphaVal + (t.highlightAlpha * 0.0001f)))
                            }
                        )
                    } else {
                        base
                            .clip(thumbShape)
                            .background(Color.White.copy(alpha = 0.94f))
                            .border(0.9.dp, Color.White.copy(alpha = 0.55f), thumbShape)
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
 * Liquid Glass Floating Bottom Navigation Bar with true 1:1 [PagerState] synchronization.
 *
 * Features:
 * - Outer full-capsule Liquid Glass dock floating above the gesture bar (`backdrop` refracts
 *   scrolling settings cards right behind it).
 * - Inner sliding **Liquid Glass lens bubble** driven directly 1:1 by [pagerState] (zero double-
 *   animation lag) using single-pass `lens()` over `dockContentBackdrop` (eliminating triple blur).
 * - Pressing & dragging the active tab swells the entire dock by +5%, dynamically shifts the dock
 *   horizontally toward the finger's screen X offset (0 at screen center, ±16dp at screen edges),
 *   and drives [pagerState.scrollToPage] directly 1:1 via a single conflated `snapshotFlow`
 *   collector (zero coroutine cancellations or mid-transition stalls!), snapping smoothly on lift.
 */
@Composable
fun LiquidFloatingBottomBar(
    tabs: List<SettingsNavTabItem>,
    pagerState: PagerState,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop = LocalContentBackdrop.current,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    val tokensState = LocalGlassTokensState.current
    val navPadding = WindowInsets.navigationBars.asPaddingValues()
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val itemWidthDp = 90.dp
    val itemWidthPx = with(density) { itemWidthDp.toPx() }
    val dockPaddingHorizontal = 7.dp
    val dockPaddingVertical = 6.dp
    val dockPadPx = with(density) { dockPaddingHorizontal.toPx() }
    val dockHeight = 80.dp
    val maxShiftPx = with(density) { 16.dp.toPx() }

    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    var isHoldingActiveTab by remember { mutableStateOf(false) }
    var scrubIndexFloat by remember { mutableFloatStateOf(pagerState.currentPage.toFloat()) }
    var hasPendingDragSnap by remember { mutableStateOf(false) }
    var pendingDragSnapTarget by remember { mutableIntStateOf(pagerState.currentPage) }
    var targetDockShiftPx by remember { mutableFloatStateOf(0f) }
    var lastPointerLocalX by remember { mutableFloatStateOf(0f) }
    var dockLeftInScreenPx by remember { mutableFloatStateOf(0f) }
    var dockWidthPx by remember { mutableFloatStateOf(0f) }

    val exactPagerPos = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, tabs.lastIndex.toFloat())
    val activeIndicatorPos = if (isHoldingActiveTab) {
        scrubIndexFloat.coerceIn(0f, tabs.lastIndex.toFloat())
    } else {
        exactPagerPos
    }

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

    // Single sequential owner of BOTH active-tab drag scrolling AND post-release snap animation.
    // Because Compose cancels the active branch before entering the release branch, `scrollToPage`
    // and `animateScrollToPage` can NEVER race or leave the pager stranded halfway between pages.
    LaunchedEffect(isHoldingActiveTab, pagerState, tabs.size) {
        if (isHoldingActiveTab) {
            snapshotFlow { scrubIndexFloat.coerceIn(0f, tabs.lastIndex.toFloat()) }
                .collect { pos ->
                    val page = pos.roundToInt().coerceIn(0, tabs.lastIndex)
                    val offsetFrac = (pos - page).coerceIn(-0.5f, 0.5f)
                    pagerState.scrollToPage(page = page, pageOffsetFraction = offsetFrac)
                }
        } else if (hasPendingDragSnap) {
            val target = pendingDragSnapTarget.coerceIn(0, tabs.lastIndex)
            hasPendingDragSnap = false
            if (pagerState.currentPage != target || abs(pagerState.currentPageOffsetFraction) > 0.001f) {
                pagerState.animateScrollToPage(
                    page = target,
                    pageOffsetFraction = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = 0.86f
                    )
                )
            }
        }
    }

    // Secondary safety net: whenever no drag or scroll is in progress, guarantee the pager never
    // rests at a fractional mid-page offset — always smoothly animate to whichever page is closest.
    LaunchedEffect(pagerState.isScrollInProgress, isHoldingActiveTab, tabs.size) {
        if (!isHoldingActiveTab && !pagerState.isScrollInProgress && abs(pagerState.currentPageOffsetFraction) > 0.001f) {
            val nearest = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, tabs.lastIndex.toFloat())
                .roundToInt()
                .coerceIn(0, tabs.lastIndex)
            pagerState.animateScrollToPage(
                page = nearest,
                pageOffsetFraction = 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = 0.86f
                )
            )
        }
    }

    // Continuously keep the active lens centered inside the scrollable dock in 1:1 sync with pager
    LaunchedEffect(pagerState, isHoldingActiveTab, tabs.size, itemWidthPx, dockWidthPx) {
        if (isHoldingActiveTab) return@LaunchedEffect
        snapshotFlow {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, tabs.lastIndex.toFloat())
        }.collect { pos ->
            val maxScroll = scrollState.maxValue
            if (maxScroll > 0 && dockWidthPx > 0f) {
                val centerX = pos * itemWidthPx + itemWidthPx / 2f
                val viewportW = (dockWidthPx - dockPadPx * 2f).coerceAtLeast(1f)
                val targetScroll = (centerX - viewportW / 2f).roundToInt().coerceIn(0, maxScroll)
                if (abs(targetScroll - scrollState.value) > 1) {
                    scrollState.scrollTo(targetScroll)
                }
            }
        }
    }

    // Gentle edge auto-scroll when scrubbing the active tab near the left/right edges of the dock
    LaunchedEffect(isHoldingActiveTab, tabs.size) {
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
                        scrubIndexFloat = (contentX / itemWidthPx - 0.5f)
                            .coerceIn(0f, tabs.lastIndex.toFloat())
                    }
                }
            }
        }
    }

    val dockContentBackdrop = rememberLayerBackdrop()
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
                        val livePagerPos = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                            .coerceIn(0f, tabs.lastIndex.toFloat())
                        val activeLeftPx = dockPadPx + (livePagerPos * itemWidthPx) - scrollState.value
                        val activeRightPx = activeLeftPx + itemWidthPx

                        if (down.position.x !in activeLeftPx..activeRightPx) {
                            // Touch started on an inactive tab: let normal tap & horizontal scroll run
                            return@awaitEachGesture
                        }

                        down.consume()
                        isHoldingActiveTab = true
                        lastPointerLocalX = down.position.x
                        scrubIndexFloat = livePagerPos

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
                                scrubIndexFloat = (contentX / itemWidthPx - 0.5f)
                                    .coerceIn(0f, tabs.lastIndex.toFloat())
                            }
                        } finally {
                            pendingDragSnapTarget = scrubIndexFloat.roundToInt().coerceIn(0, tabs.lastIndex)
                            hasPendingDragSnap = true
                            isHoldingActiveTab = false
                            targetDockShiftPx = 0f
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
                    .liquidGlassSurface(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(40.dp),
                        level = GlassElevationLevel.CHROME,
                        enableLens = true,
                        enableShadow = true
                    )
                    .padding(horizontal = dockPaddingHorizontal, vertical = dockPaddingVertical)
            ) {
                Box(
                    modifier = Modifier.horizontalScroll(scrollState)
                ) {
                    // Sliding Liquid Glass active lens bubble: single-pass lens over dockContentBackdrop
                    // (no redundant second full-screen blur!)
                    val pillShape = RoundedCornerShape(34.dp)
                    val lensMod = Modifier
                        .graphicsLayer {
                            translationX = activeIndicatorPos * itemWidthPx
                            scaleX = lerp(1f, 1.12f, scrubScale)
                            scaleY = lerp(1f, 1.09f, scrubScale)
                        }
                        .size(width = itemWidthDp, height = dockHeight - (dockPaddingVertical * 2))
                        .let { base ->
                            if (isRenderEffectSupported()) {
                                base.drawBackdrop(
                                    backdrop = dockContentBackdrop,
                                    shape = { pillShape },
                                    effects = {
                                        val t = tokensState.value
                                        val refH = lerp(11f, 18f, scrubScale).dp.toPx()
                                        val refA = lerp(20f, 38f, scrubScale).dp.toPx()
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

                    // Tab icons + compact labels recorded into dockContentBackdrop with smooth 1:1 color sync
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxHeight()
                            .layerBackdrop(dockContentBackdrop)
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val activeWeight = (1f - abs(activeIndicatorPos - index)).coerceIn(0f, 1f)
                            val itemInteraction = remember { MutableInteractionSource() }
                            val isFocused by itemInteraction.collectIsFocusedAsState()
                            val contentColor = lerpColor(
                                onSurfaceVariant.copy(alpha = 0.85f),
                                onSurface,
                                activeWeight
                            )

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
                                    ) { currentOnTabSelected(index) }
                                    .padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = tab.iconRes),
                                    contentDescription = tab.shortLabel,
                                    tint = contentColor,
                                    modifier = Modifier.size(25.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = tab.shortLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = if (activeWeight >= 0.5f) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = contentColor,
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
    val previewBackdrop = rememberLayerBackdrop()
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(previewBackdrop)
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
            backdrop = backdrop,
            nestedInGlass = true
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
