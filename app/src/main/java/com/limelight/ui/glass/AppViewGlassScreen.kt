package com.limelight.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.Backdrop
import com.limelight.AppView
import com.limelight.R
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.ui.CenterContentImageView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppCardModel(
    val appId: Int,
    val appName: String,
    val isRunning: Boolean,
    val isHidden: Boolean,
    val appObject: AppView.AppObject
)

data class AppViewGlassUiState(
    val computerName: String = "",
    val apps: List<AppCardModel> = emptyList(),
    val smallIconMode: Boolean = false,
    val isTv: Boolean = false,
    val loader: CachedAppAssetLoader? = null,
    val activeMenu: GlassMenuData? = null
)

class AppViewGlassController {
    private val _uiState = MutableStateFlow(AppViewGlassUiState())
    val uiState: StateFlow<AppViewGlassUiState> = _uiState.asStateFlow()

    fun setHeader(computerName: String?, smallIconMode: Boolean, isTv: Boolean) {
        _uiState.value = _uiState.value.copy(
            computerName = computerName.orEmpty(),
            smallIconMode = smallIconMode,
            isTv = isTv
        )
    }

    fun updateApps(items: List<AppView.AppObject>?, loader: CachedAppAssetLoader?, smallIconMode: Boolean) {
        val mapped = items.orEmpty().mapNotNull { obj ->
            val app = obj?.app ?: return@mapNotNull null
            AppCardModel(
                appId = app.appId,
                appName = app.appName.orEmpty(),
                isRunning = obj.isRunning,
                isHidden = obj.isHidden,
                appObject = obj
            )
        }
        _uiState.value = _uiState.value.copy(
            apps = mapped,
            loader = loader,
            smallIconMode = smallIconMode
        )
    }

    fun showMenu(menuData: GlassMenuData) {
        _uiState.value = _uiState.value.copy(activeMenu = menuData)
    }

    fun dismissMenu() {
        _uiState.value = _uiState.value.copy(activeMenu = null)
    }
}

interface AppViewGlassCallbacks {
    fun onBackClicked()
    fun onAppClicked(app: AppView.AppObject, boxArtBitmap: Bitmap?)
    fun onAppLongClicked(app: AppView.AppObject, boxArtBitmap: Bitmap?)
}

object AppViewGlassHost {
    @JvmStatic
    fun createView(
        context: Context,
        controller: AppViewGlassController,
        callbacks: AppViewGlassCallbacks
    ): ComposeView {
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppViewGlassScreen(controller = controller, callbacks = callbacks)
            }
        }
    }
}

@Composable
fun AppViewGlassScreen(
    controller: AppViewGlassController,
    callbacks: AppViewGlassCallbacks
) {
    val state by controller.uiState.collectAsState()
    val topInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues()
    val navInsets = WindowInsets.navigationBars.asPaddingValues()

    LiquidGlassScaffold(
        floatingChrome = { combinedBackdrop ->
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                GlassTopBar(
                    title = state.computerName,
                    backdrop = combinedBackdrop,
                    navigationIcon = {
                        GlassIconButton(
                            iconRes = R.drawable.ic_m3_back,
                            contentDescription = "Back",
                            onClick = callbacks::onBackClicked,
                            size = 42.dp,
                            iconSize = 20.dp,
                            backdrop = combinedBackdrop,
                            nestedInGlass = true
                        )
                    }
                )
            }
        },
        overlayContent = { combinedBackdrop ->
            GlassMenuOverlay(
                menuData = state.activeMenu,
                isTv = state.isTv,
                backdrop = combinedBackdrop
            )
        }
    ) { backgroundBackdrop ->
        val minCellWidth = if (state.smallIconMode) 108.dp else 156.dp
        val cardHeight = if (state.smallIconMode) 136.dp else 174.dp

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = minCellWidth),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topInsets.calculateTopPadding() + 84.dp,
                bottom = navInsets.calculateBottomPadding() + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = state.apps,
                key = { index, item -> "${item.appId}_$index" }
            ) { _, item ->
                GlassAppCard(
                    item = item,
                    loader = state.loader,
                    cardHeight = cardHeight,
                    backdrop = backgroundBackdrop,
                    onClick = { bmp -> callbacks.onAppClicked(item.appObject, bmp) },
                    onLongClick = { bmp -> callbacks.onAppLongClicked(item.appObject, bmp) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassAppCard(
    item: AppCardModel,
    loader: CachedAppAssetLoader?,
    cardHeight: androidx.compose.ui.unit.Dp,
    backdrop: Backdrop,
    onClick: (Bitmap?) -> Unit,
    onLongClick: (Bitmap?) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else if (isFocused) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "appCardScale"
    )

    val imageViewHolder = remember { mutableStateOf<CenterContentImageView?>(null) }
    val onSurfaceArgb = MaterialTheme.colorScheme.onSurface.toArgb()

    fun currentBitmap(): Bitmap? {
        val drawable = imageViewHolder.value?.drawable
        return (drawable as? BitmapDrawable)?.bitmap
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .alpha(if (item.isHidden) 0.40f else 1.0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlassSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(22.dp),
                level = GlassElevationLevel.CARD,
                isFocused = isFocused
            )
            .focusable(interactionSource = interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(currentBitmap()) },
                onLongClick = { onLongClick(currentBitmap()) }
            )
    ) {
        // Reuse Moonlight Zenith's CenterContentImageView & CachedAppAssetLoader for exact poster centering
        AndroidView(
            factory = { ctx ->
                val container = FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                }
                val imgView = CenterContentImageView(ctx).apply {
                    id = R.id.grid_image
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    cropToPadding = false
                }
                val padPx = (12 * ctx.resources.displayMetrics.density).toInt()
                val txtView = TextView(ctx).apply {
                    id = R.id.grid_text
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    gravity = Gravity.CENTER
                    setPadding(padPx, padPx, padPx, padPx)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(onSurfaceArgb)
                }
                container.addView(imgView)
                container.addView(txtView)
                imageViewHolder.value = imgView
                container
            },
            update = { container ->
                val imgView = container.findViewById<CenterContentImageView>(R.id.grid_image)
                val txtView = container.findViewById<TextView>(R.id.grid_text)
                txtView.setTextColor(onSurfaceArgb)
                imageViewHolder.value = imgView
                if (loader != null) {
                    loader.populateImageView(item.appObject.app, imgView, txtView)
                } else {
                    txtView.text = item.appName
                    txtView.visibility = View.VISIBLE
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
        )

        // Running app floating glass badge
        if (item.isRunning) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_play),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
