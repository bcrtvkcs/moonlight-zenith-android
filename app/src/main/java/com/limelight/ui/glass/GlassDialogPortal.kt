package com.limelight.ui.glass

import android.app.Activity
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.limelight.ui.background.UiBackgroundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

sealed class GlassDialogRequest {
    abstract val id: Long
    abstract val activityId: Int
    abstract val cancelable: Boolean

    data class Confirmation(
        override val id: Long,
        override val activityId: Int,
        val title: String?,
        val message: String,
        val positiveText: String,
        val negativeText: String,
        val destructive: Boolean,
        val onPositive: Runnable?,
        val onNegative: Runnable?,
        override val cancelable: Boolean = true
    ) : GlassDialogRequest()

    data class Alert(
        override val id: Long,
        override val activityId: Int,
        val title: String?,
        val message: String,
        val okText: String,
        val helpText: String?,
        val onOk: Runnable?,
        val onHelp: Runnable?,
        override val cancelable: Boolean = false
    ) : GlassDialogRequest()

    data class Spinner(
        override val id: Long,
        override val activityId: Int,
        val title: String?,
        val message: String,
        override val cancelable: Boolean,
        val onCancel: Runnable?
    ) : GlassDialogRequest()
}

interface GlassDialogHandle {
    fun dismiss()
    fun updateMessage(message: String)
}

/**
 * Unified Liquid Glass Dialog & Spinner host.
 * - When called from a Liquid Glass Compose screen (PcView, AppView, StreamSettings, AddComputerManually),
 *   renders directly inside `LiquidGlassScaffold` over `combinedBackdrop` with full AGSL refraction &
 *   spring open/close animations.
 * - When called from a non-Compose Activity (Game, ShortcutTrampoline, HelpActivity), renders inside a
 *   transparent fullscreen `ComponentDialog` with Android 12+ blur-behind + smooth Compose Liquid Glass
 *   animations and zero Material You flat gray surfaces.
 */
object GlassDialogPortal {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val nextId = AtomicLong(1L)

    private val attachedActivities = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    )

    private val _requests = MutableStateFlow<List<GlassDialogRequest>>(emptyList())
    val requests: StateFlow<List<GlassDialogRequest>> = _requests.asStateFlow()

    internal fun registerHost(activity: Activity) {
        attachedActivities.add(activity)
    }

    internal fun unregisterHost(activity: Activity) {
        attachedActivities.remove(activity)
        val actId = System.identityHashCode(activity)
        _requests.value = _requests.value.filterNot { it.activityId == actId }
    }

    private fun isHostedInScaffold(activity: Activity): Boolean =
        attachedActivities.contains(activity)

    internal fun dismissById(id: Long) {
        _requests.value = _requests.value.filterNot { it.id == id }
    }

    @JvmStatic
    fun showConfirmation(
        activity: Activity,
        title: String?,
        message: String,
        positiveText: String,
        negativeText: String,
        destructive: Boolean,
        onPositive: Runnable?,
        onNegative: Runnable?
    ): GlassDialogHandle {
        val req = GlassDialogRequest.Confirmation(
            id = nextId.getAndIncrement(),
            activityId = System.identityHashCode(activity),
            title = title,
            message = message,
            positiveText = positiveText,
            negativeText = negativeText,
            destructive = destructive,
            onPositive = onPositive,
            onNegative = onNegative
        )
        return dispatch(activity, req)
    }

    @JvmStatic
    fun showAlert(
        activity: Activity,
        title: String?,
        message: String,
        okText: String,
        helpText: String?,
        onOk: Runnable?,
        onHelp: Runnable?
    ): GlassDialogHandle {
        val req = GlassDialogRequest.Alert(
            id = nextId.getAndIncrement(),
            activityId = System.identityHashCode(activity),
            title = title,
            message = message,
            okText = okText,
            helpText = helpText,
            onOk = onOk,
            onHelp = onHelp
        )
        return dispatch(activity, req)
    }

    @JvmStatic
    fun showSpinner(
        activity: Activity,
        title: String?,
        message: String,
        cancelable: Boolean,
        onCancel: Runnable?
    ): GlassDialogHandle {
        val req = GlassDialogRequest.Spinner(
            id = nextId.getAndIncrement(),
            activityId = System.identityHashCode(activity),
            title = title,
            message = message,
            cancelable = cancelable,
            onCancel = onCancel
        )
        return dispatch(activity, req)
    }

    private fun dispatch(activity: Activity, request: GlassDialogRequest): GlassDialogHandle {
        if (isHostedInScaffold(activity)) {
            _requests.value = _requests.value + request
            return object : GlassDialogHandle {
                override fun dismiss() {
                    mainHandler.post { dismissById(request.id) }
                }

                override fun updateMessage(message: String) {
                    mainHandler.post {
                        _requests.value = _requests.value.map { item ->
                            if (item.id == request.id && item is GlassDialogRequest.Spinner) {
                                item.copy(message = message)
                            } else {
                                item
                            }
                        }
                    }
                }
            }
        }

        // Non-Compose Activity (Game / ShortcutTrampoline / HelpActivity): host in transparent ComponentDialog
        val stateFlow = MutableStateFlow<GlassDialogRequest?>(request)
        val visibleFlow = MutableStateFlow(true)
        var dialogRef: ComponentDialog? = null

        val handle = object : GlassDialogHandle {
            override fun dismiss() {
                mainHandler.post {
                    visibleFlow.value = false
                    mainHandler.postDelayed({
                        try {
                            if (dialogRef?.isShowing == true) {
                                dialogRef?.dismiss()
                            }
                        } catch (_: Throwable) {
                        }
                        dialogRef = null
                    }, 180L)
                }
            }

            override fun updateMessage(message: String) {
                mainHandler.post {
                    val cur = stateFlow.value
                    if (cur is GlassDialogRequest.Spinner) {
                        stateFlow.value = cur.copy(message = message)
                    }
                }
            }
        }

        mainHandler.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            try {
                val dlg = ComponentDialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
                dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dlg.setCancelable(request.cancelable)
                dlg.setCanceledOnTouchOutside(false)
                dlg.setOnCancelListener {
                    if (request is GlassDialogRequest.Spinner) {
                        request.onCancel?.run()
                    }
                }

                val composeView = ComposeView(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        StandaloneLiquidGlassDialogContent(
                            requestFlow = stateFlow,
                            visibleFlow = visibleFlow,
                            onRequestDismiss = {
                                handle.dismiss()
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            val blurPx = (24 * activity.resources.displayMetrics.density)
                                .toInt()
                                .coerceIn(1, 120)
                            win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                            val lp = win.attributes
                            if (lp != null) {
                                lp.blurBehindRadius = blurPx
                                win.attributes = lp
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                dialogRef = dlg
                dlg.show()
            } catch (_: Throwable) {
            }
        }

        return handle
    }
}

@Composable
internal fun GlassDialogPortalOverlay(
    activity: Activity?,
    backdrop: Backdrop
) {
    if (activity == null) return
    val actId = remember(activity) { System.identityHashCode(activity) }

    DisposableEffect(activity) {
        GlassDialogPortal.registerHost(activity)
        onDispose {
            GlassDialogPortal.unregisterHost(activity)
        }
    }

    val allRequests by GlassDialogPortal.requests.collectAsState()
    val currentRequest = allRequests.lastOrNull { it.activityId == actId }

    GlassAnimatedModalDialog(
        targetState = currentRequest,
        onDismiss = {
            currentRequest?.let { req ->
                if (req.cancelable) {
                    GlassDialogPortal.dismissById(req.id)
                    when (req) {
                        is GlassDialogRequest.Confirmation -> req.onNegative?.run()
                        is GlassDialogRequest.Spinner -> req.onCancel?.run()
                        is GlassDialogRequest.Alert -> Unit
                    }
                }
            }
        },
        backdrop = backdrop,
        cancelable = { it.cancelable }
    ) { req ->
        GlassDialogRequestBody(
            request = req,
            backdrop = backdrop,
            onDismissRequest = { GlassDialogPortal.dismissById(req.id) }
        )
    }
}

@Composable
private fun StandaloneLiquidGlassDialogContent(
    requestFlow: StateFlow<GlassDialogRequest?>,
    visibleFlow: StateFlow<Boolean>,
    onRequestDismiss: () -> Unit
) {
    val bgState by UiBackgroundManager.state.collectAsState()
    val request by requestFlow.collectAsState()
    val visible by visibleFlow.collectAsState()
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Trigger smooth spring enter animation on the very next frame after attaching
        delay(16)
        entered = true
    }

    MoonlightLiquidGlassTheme(bgState = bgState) {
        val colorScheme = MaterialTheme.colorScheme
        val standaloneBackdrop = rememberLayerBackdrop()

        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle luminous ambient layer so standalone dialogs over black streaming screens still
            // show authentic Liquid Glass refraction, prism dispersion, and specular highlights
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(standaloneBackdrop)
            ) {
                val w = size.width
                val h = size.height
                val maxDim = maxOf(w, h)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colorScheme.primary.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.35f, h * 0.36f),
                        radius = maxDim * 0.36f
                    ),
                    radius = maxDim * 0.36f,
                    center = Offset(w * 0.35f, h * 0.36f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colorScheme.tertiary.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.68f, h * 0.62f),
                        radius = maxDim * 0.32f
                    ),
                    radius = maxDim * 0.32f,
                    center = Offset(w * 0.68f, h * 0.62f)
                )
            }

            GlassModalDialog(
                visible = entered && visible && request != null,
                onDismiss = {
                    val req = request
                    if (req != null && req.cancelable) {
                        onRequestDismiss()
                        when (req) {
                            is GlassDialogRequest.Confirmation -> req.onNegative?.run()
                            is GlassDialogRequest.Spinner -> req.onCancel?.run()
                            is GlassDialogRequest.Alert -> Unit
                        }
                    }
                },
                backdrop = standaloneBackdrop,
                scrimAlpha = 0.30f,
                cancelable = request?.cancelable ?: true
            ) {
                request?.let { req ->
                    GlassDialogRequestBody(
                        request = req,
                        backdrop = standaloneBackdrop,
                        onDismissRequest = onRequestDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassDialogRequestBody(
    request: GlassDialogRequest,
    backdrop: Backdrop,
    onDismissRequest: () -> Unit
) {
    when (request) {
        is GlassDialogRequest.Confirmation -> {
            if (!request.title.isNullOrBlank()) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(
                text = request.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(22.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                GlassActionButton(
                    text = request.negativeText,
                    onClick = {
                        onDismissRequest()
                        request.onNegative?.run()
                    },
                    backdrop = backdrop,
                    nestedInGlass = true,
                    modifier = Modifier.weight(1f)
                )
                GlassActionButton(
                    text = request.positiveText,
                    onClick = {
                        onDismissRequest()
                        request.onPositive?.run()
                    },
                    primary = !request.destructive,
                    destructive = request.destructive,
                    backdrop = backdrop,
                    nestedInGlass = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is GlassDialogRequest.Alert -> {
            if (!request.title.isNullOrBlank()) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(
                text = request.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(22.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!request.helpText.isNullOrBlank()) {
                    GlassActionButton(
                        text = request.helpText,
                        onClick = {
                            onDismissRequest()
                            request.onHelp?.run()
                        },
                        backdrop = backdrop,
                        nestedInGlass = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                GlassActionButton(
                    text = request.okText,
                    onClick = {
                        onDismissRequest()
                        request.onOk?.run()
                    },
                    primary = true,
                    backdrop = backdrop,
                    nestedInGlass = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is GlassDialogRequest.Spinner -> {
            if (!request.title.isNullOrBlank()) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.14f),
                    strokeWidth = 3.5.dp,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = request.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
