package com.limelight.ui.glass

import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.limelight.ui.background.UiBackgroundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import studio.attect.limelight.GameAttectActivity
import studio.attect.limelight.R as AttectR

private enum class AttectChipTone {
    STANDARD,
    PRIMARY,
    DESTRUCTIVE,
    NVIDIA
}

private data class AttectGlassButtonSpec(
    @StringRes val labelRes: Int,
    val actionId: Int,
    val longPressActionId: Int? = null,
    val weight: Float = 1f,
    val tone: AttectChipTone = AttectChipTone.STANDARD
)

/**
 * Genuine Kyant0/AndroidLiquidGlass Compose host for the in-stream Attect overflow menu.
 *
 * Key properties:
 * - Zero latency during gameplay: the ComponentDialog is created fresh ONLY when opened and
 *   completely dismissed when closed.
 * - Non-focusable (`FLAG_NOT_FOCUSABLE`) so Game never loses pointer capture or immersive bars.
 * - Keeps the live game stream behind the menu completely sharp (no fullscreen blur-behind or dim).
 * - All 28 shortcut buttons and the outer panel render with real AGSL Liquid Glass (`drawBackdrop`).
 */
class AttectGlassMenuHost private constructor(
    private val activity: GameAttectActivity
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeDialog: ComponentDialog? = null
    private var visibleFlow: MutableStateFlow<Boolean>? = null

    private fun handleToggle(show: Boolean): Boolean {
        if (show) {
            showMenu()
        } else {
            dismissMenu(notifyActivity = false)
        }
        return true
    }

    private fun showMenu() {
        if (activity.isFinishing || activity.isDestroyed) return
        UiBackgroundManager.refreshFromPreferences(activity)
        dismissImmediate()

        val stateVisible = MutableStateFlow(true)
        visibleFlow = stateVisible

        try {
            val dlg = ComponentDialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
            dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dlg.setCancelable(false)
            dlg.setCanceledOnTouchOutside(false)

            val composeView = ComposeView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    AttectGlassMenuScreen(
                        visibleFlow = stateVisible,
                        onAction = { actionId ->
                            dismissMenu(notifyActivity = true)
                            activity.performAttectAction(actionId)
                        },
                        onDismiss = {
                            dismissMenu(notifyActivity = true)
                        }
                    )
                }
            }

            dlg.setContentView(composeView)

            dlg.window?.let { win ->
                win.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                win.setFormat(PixelFormat.TRANSLUCENT)
                win.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                win.decorView.setBackgroundColor(AndroidColor.TRANSPARENT)
                win.clearFlags(
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                            or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                )
                win.addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                )
                val lp = win.attributes
                if (lp != null) {
                    lp.dimAmount = 0f
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        lp.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                    win.attributes = lp
                }
            }

            activeDialog = dlg
            dlg.show()
        } catch (_: Throwable) {
            activity.onExternalOverflowMenuDismissed()
        }
    }

    private fun dismissMenu(notifyActivity: Boolean) {
        val dlg = activeDialog
        visibleFlow?.value = false
        if (notifyActivity) {
            activity.onExternalOverflowMenuDismissed()
        }
        if (dlg != null) {
            activeDialog = null
            visibleFlow = null
            mainHandler.postDelayed({
                try {
                    if (dlg.isShowing) {
                        dlg.dismiss()
                    }
                } catch (_: Throwable) {
                }
            }, 170L)
        }
    }

    fun dismissImmediate() {
        val dlg = activeDialog
        activeDialog = null
        visibleFlow = null
        if (dlg != null) {
            try {
                if (dlg.isShowing) {
                    dlg.dismiss()
                }
            } catch (_: Throwable) {
            }
        }
    }

    companion object {
        @JvmStatic
        fun attach(activity: GameAttectActivity): AttectGlassMenuHost {
            val host = AttectGlassMenuHost(activity)
            activity.customOverflowMenuHandler = { show -> host.handleToggle(show) }
            return host
        }
    }
}

@Composable
private fun AttectGlassMenuScreen(
    visibleFlow: StateFlow<Boolean>,
    onAction: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val bgState by UiBackgroundManager.state.collectAsState()
    val visible by visibleFlow.collectAsState()
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(16)
        entered = true
    }

    val row1 = remember {
        listOf(
            AttectGlassButtonSpec(AttectR.string.virtual_key_esc, GameAttectActivity.ACTION_ESC, weight = 0.85f, tone = AttectChipTone.PRIMARY),
            AttectGlassButtonSpec(AttectR.string.virtual_key_win, GameAttectActivity.ACTION_WIN, weight = 0.85f, tone = AttectChipTone.PRIMARY),
            AttectGlassButtonSpec(AttectR.string.virtual_key_system_menu, GameAttectActivity.ACTION_SYSTEM_MENU, weight = 1.15f),
            AttectGlassButtonSpec(AttectR.string.virtual_mouse_middle_key, GameAttectActivity.ACTION_MIDDLE_MOUSE, weight = 1.0f),
            AttectGlassButtonSpec(AttectR.string.on_screen_game_pad, GameAttectActivity.ACTION_ONSCREEN_GAMEPAD, weight = 1.45f, tone = AttectChipTone.PRIMARY),
            AttectGlassButtonSpec(AttectR.string.input_touchscreen_mode, GameAttectActivity.ACTION_TOUCHSCREEN_MODE, weight = 1.45f, tone = AttectChipTone.PRIMARY),
            AttectGlassButtonSpec(AttectR.string.input_touchpad_mode, GameAttectActivity.ACTION_TOUCHPAD_MODE, weight = 1.45f, tone = AttectChipTone.PRIMARY),
            AttectGlassButtonSpec(AttectR.string.toggle_soft_keyboard, GameAttectActivity.ACTION_TOGGLE_KEYBOARD, weight = 1.45f, tone = AttectChipTone.PRIMARY)
        )
    }
    val row2 = remember {
        listOf(
            AttectGlassButtonSpec(AttectR.string.virtual_key_exit_program, GameAttectActivity.ACTION_EXIT_PROGRAM, tone = AttectChipTone.DESTRUCTIVE),
            AttectGlassButtonSpec(AttectR.string.virtual_key_screenshot, GameAttectActivity.ACTION_SCREENSHOT, longPressActionId = GameAttectActivity.ACTION_SCREENSHOT_WIN),
            AttectGlassButtonSpec(AttectR.string.virtual_key_taskmgr, GameAttectActivity.ACTION_TASK_MANAGER),
            AttectGlassButtonSpec(AttectR.string.virtual_key_explorer, GameAttectActivity.ACTION_EXPLORER),
            AttectGlassButtonSpec(AttectR.string.virtual_key_copy, GameAttectActivity.ACTION_COPY),
            AttectGlassButtonSpec(AttectR.string.virtual_key_cut, GameAttectActivity.ACTION_CUT),
            AttectGlassButtonSpec(AttectR.string.virtual_key_paste, GameAttectActivity.ACTION_PASTE),
            AttectGlassButtonSpec(AttectR.string.virtual_key_select_all, GameAttectActivity.ACTION_SELECT_ALL)
        )
    }
    val row3 = remember {
        listOf(
            AttectGlassButtonSpec(AttectR.string.virtual_key_desktop, GameAttectActivity.ACTION_SHOW_DESKTOP),
            AttectGlassButtonSpec(AttectR.string.virtual_key_task_view, GameAttectActivity.ACTION_TASK_VIEW),
            AttectGlassButtonSpec(AttectR.string.virtual_key_new_desktop, GameAttectActivity.ACTION_NEW_DESKTOP),
            AttectGlassButtonSpec(AttectR.string.virtual_key_delete_desktop, GameAttectActivity.ACTION_DELETE_DESKTOP),
            AttectGlassButtonSpec(AttectR.string.virtual_key_left_desktop, GameAttectActivity.ACTION_LEFT_DESKTOP),
            AttectGlassButtonSpec(AttectR.string.virtual_key_right_desktop, GameAttectActivity.ACTION_RIGHT_DESKTOP)
        )
    }
    val row4 = remember {
        listOf(
            AttectGlassButtonSpec(AttectR.string.virtual_key_nvidia_overflow, GameAttectActivity.ACTION_NVIDIA_OVERFLOW, tone = AttectChipTone.NVIDIA),
            AttectGlassButtonSpec(AttectR.string.virtual_key_nvidia_screenshot, GameAttectActivity.ACTION_NVIDIA_SCREENSHOT),
            AttectGlassButtonSpec(AttectR.string.virtual_key_nvidia_photo_mode, GameAttectActivity.ACTION_NVIDIA_PHOTO_MODE),
            AttectGlassButtonSpec(AttectR.string.virtual_key_nvidia_game_filter, GameAttectActivity.ACTION_NVIDIA_GAME_FILTER),
            AttectGlassButtonSpec(AttectR.string.virtual_key_nvidia_instantly_relay, GameAttectActivity.ACTION_NVIDIA_INSTANT_REPLAY),
            AttectGlassButtonSpec(AttectR.string.virtual_key_nvidia_record, GameAttectActivity.ACTION_NVIDIA_RECORD)
        )
    }

    MoonlightLiquidGlassTheme(bgState = bgState) {
        val colorScheme = MaterialTheme.colorScheme
        val panelBackdrop = rememberLayerBackdrop()

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            AnimatedVisibility(
                visible = entered && visible,
                enter = scaleIn(
                    initialScale = 0.90f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.78f)
                ) + fadeIn(animationSpec = tween(180)),
                exit = scaleOut(
                    targetScale = 0.93f,
                    animationSpec = tween(140)
                ) + fadeOut(animationSpec = tween(140))
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .fillMaxWidth()
                        .widthIn(max = 980.dp)
                ) {
                    // Contained strictly inside the Attect panel bounds so the live game stream
                    // outside remains 100% clear/unblurred while the panel & all 28 buttons refract glass.
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(26.dp))
                            .layerBackdrop(panelBackdrop)
                    ) {
                        val w = size.width
                        val h = size.height
                        drawRect(colorScheme.surface.copy(alpha = 0.42f))
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colorScheme.primary.copy(alpha = 0.34f), Color.Transparent),
                                center = Offset(w * 0.16f, h * 0.28f),
                                radius = w * 0.38f
                            ),
                            radius = w * 0.38f,
                            center = Offset(w * 0.16f, h * 0.28f)
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colorScheme.tertiary.copy(alpha = 0.30f), Color.Transparent),
                                center = Offset(w * 0.84f, h * 0.72f),
                                radius = w * 0.36f
                            ),
                            radius = w * 0.36f,
                            center = Offset(w * 0.84f, h * 0.72f)
                        )
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.18f),
                                    colorScheme.primary.copy(alpha = 0.24f),
                                    Color.Transparent
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(w, h)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w, h),
                            strokeWidth = h * 0.26f
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassSurface(
                                backdrop = panelBackdrop,
                                shape = RoundedCornerShape(26.dp),
                                level = GlassElevationLevel.SHEET,
                                enableLens = true,
                                enableShadow = true
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                            .padding(12.dp)
                    ) {
                        AttectGlassRow(row1, panelBackdrop, onAction)
                        AttectGlassRow(row2, panelBackdrop, onAction)
                        AttectGlassRow(row3, panelBackdrop, onAction)
                        AttectGlassRow(row4, panelBackdrop, onAction)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttectGlassRow(
    specs: List<AttectGlassButtonSpec>,
    backdrop: Backdrop,
    onAction: (Int) -> Unit
) {
    val tokens = LocalGlassTokens.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        specs.forEach { spec ->
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.93f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "attectChipScale"
            )

            val chipTint = when (spec.tone) {
                AttectChipTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.44f)
                AttectChipTone.DESTRUCTIVE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.48f)
                AttectChipTone.NVIDIA -> Color(0xFF1A4B2E).copy(alpha = 0.44f)
                AttectChipTone.STANDARD -> tokens.cardTint.copy(alpha = (tokens.cardTint.alpha * 0.85f).coerceIn(0.10f, 0.36f))
            }
            val textColor = when (spec.tone) {
                AttectChipTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
                AttectChipTone.DESTRUCTIVE -> MaterialTheme.colorScheme.error
                AttectChipTone.NVIDIA -> Color(0xFF7BE89C)
                AttectChipTone.STANDARD -> MaterialTheme.colorScheme.onSurface
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(spec.weight)
                    .height(44.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .liquidGlassSurface(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        level = if (spec.tone == AttectChipTone.STANDARD) GlassElevationLevel.CHROME else GlassElevationLevel.ACCENT,
                        tintOverride = chipTint,
                        enableBlur = false,
                        enableLens = true,
                        enableShadow = false
                    )
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onAction(spec.actionId) },
                        onLongClick = spec.longPressActionId?.let { longId -> { onAction(longId) } }
                    )
                    .padding(horizontal = 6.dp)
            ) {
                Text(
                    text = stringResource(id = spec.labelRes),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = if (spec.tone != AttectChipTone.STANDARD) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
