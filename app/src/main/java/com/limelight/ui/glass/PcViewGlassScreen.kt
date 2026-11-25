package com.limelight.ui.glass

import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.limelight.PcView
import com.limelight.R
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PcCardModel(
    val uuid: String,
    val name: String,
    val state: ComputerDetails.State,
    val pairState: PairingManager.PairState?,
    val runningGameId: Int,
    val computerObject: PcView.ComputerObject
)

data class PcViewGlassUiState(
    val computers: List<PcCardModel> = emptyList(),
    val showHelpButton: Boolean = true,
    val isTv: Boolean = false,
    val activeMenu: GlassMenuData? = null
)

class PcViewGlassController {
    private val _uiState = MutableStateFlow(PcViewGlassUiState())
    val uiState: StateFlow<PcViewGlassUiState> = _uiState.asStateFlow()

    fun updateComputers(items: List<PcView.ComputerObject>?) {
        val mapped = items.orEmpty().mapIndexedNotNull { index, obj ->
            val details = obj?.details ?: return@mapIndexedNotNull null
            val safeName = details.name.orEmpty()
            val safeUuid = details.uuid?.takeIf { it.isNotBlank() } ?: "${safeName}_$index"
            PcCardModel(
                uuid = safeUuid,
                name = safeName,
                state = details.state ?: ComputerDetails.State.UNKNOWN,
                pairState = details.pairState,
                runningGameId = details.runningGameId,
                computerObject = obj
            )
        }
        _uiState.value = _uiState.value.copy(computers = mapped)
    }

    fun setFlags(showHelpButton: Boolean, isTv: Boolean) {
        _uiState.value = _uiState.value.copy(showHelpButton = showHelpButton, isTv = isTv)
    }

    fun showMenu(menuData: GlassMenuData) {
        _uiState.value = _uiState.value.copy(activeMenu = menuData)
    }

    fun dismissMenu() {
        _uiState.value = _uiState.value.copy(activeMenu = null)
    }
}

interface PcViewGlassCallbacks {
    fun onComputerClicked(computer: PcView.ComputerObject)
    fun onComputerLongClicked(computer: PcView.ComputerObject)
    fun onSettingsClicked()
    fun onAddComputerClicked()
    fun onHelpClicked()
}

object PcViewGlassHost {
    @JvmStatic
    fun createView(
        context: Context,
        controller: PcViewGlassController,
        callbacks: PcViewGlassCallbacks
    ): ComposeView {
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PcViewGlassScreen(controller = controller, callbacks = callbacks)
            }
        }
    }
}

@Composable
fun PcViewGlassScreen(
    controller: PcViewGlassController,
    callbacks: PcViewGlassCallbacks
) {
    val state by controller.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || state.isTv
    val topInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues()
    val navInsets = WindowInsets.navigationBars.asPaddingValues()

    LiquidGlassScaffold(
        floatingChrome = { combinedBackdrop ->
            if (isLandscape) {
                // Floating Left Navigation Rail for Landscape & TV
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .padding(
                            top = topInsets.calculateTopPadding() + 14.dp,
                            bottom = navInsets.calculateBottomPadding() + 14.dp,
                            start = 14.dp
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .width(74.dp)
                            .fillMaxHeight()
                            .liquidGlassSurface(
                                backdrop = combinedBackdrop,
                                shape = RoundedCornerShape(37.dp),
                                level = GlassElevationLevel.CHROME,
                                enableShadow = true
                            )
                            .padding(vertical = 18.dp)
                    ) {
                        GlassIconButton(
                            iconRes = R.drawable.ic_m3_add,
                            contentDescription = stringResource(R.string.title_add_pc),
                            onClick = callbacks::onAddComputerClicked,
                            size = 52.dp,
                            iconSize = 26.dp,
                            backdrop = combinedBackdrop,
                            level = GlassElevationLevel.ACCENT,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            nestedInGlass = true
                        )
                        HorizontalDivider(
                            modifier = Modifier.width(32.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        )
                        GlassIconButton(
                            iconRes = R.drawable.ic_settings,
                            contentDescription = "Settings",
                            onClick = callbacks::onSettingsClicked,
                            size = 48.dp,
                            iconSize = 23.dp,
                            backdrop = combinedBackdrop,
                            nestedInGlass = true
                        )
                        if (state.showHelpButton) {
                            GlassIconButton(
                                iconRes = R.drawable.ic_help,
                                contentDescription = stringResource(R.string.help),
                                onClick = callbacks::onHelpClicked,
                                size = 48.dp,
                                iconSize = 23.dp,
                                backdrop = combinedBackdrop,
                                nestedInGlass = true
                            )
                        }
                    }
                }

                // Floating Top Title Pill to the right of Nav Rail
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            top = topInsets.calculateTopPadding() + 14.dp,
                            start = 104.dp,
                            end = 16.dp
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .liquidGlassSurface(
                                backdrop = combinedBackdrop,
                                shape = RoundedCornerShape(24.dp),
                                level = GlassElevationLevel.CHROME,
                                enableShadow = true
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_label),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            } else {
                // Portrait: Floating Glass Top Bar + Bottom-Start Extended Glass FAB
                Box(modifier = Modifier.align(Alignment.TopCenter)) {
                    GlassTopBar(
                        title = stringResource(R.string.app_label),
                        backdrop = combinedBackdrop,
                        actions = {
                            if (state.showHelpButton) {
                                GlassIconButton(
                                    iconRes = R.drawable.ic_help,
                                    contentDescription = stringResource(R.string.help),
                                    onClick = callbacks::onHelpClicked,
                                    size = 42.dp,
                                    iconSize = 20.dp,
                                    backdrop = combinedBackdrop,
                                    nestedInGlass = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            GlassIconButton(
                                iconRes = R.drawable.ic_settings,
                                contentDescription = "Settings",
                                onClick = callbacks::onSettingsClicked,
                                size = 42.dp,
                                iconSize = 20.dp,
                                backdrop = combinedBackdrop,
                                nestedInGlass = true
                            )
                        }
                    )
                }

                GlassExtendedFab(
                    text = stringResource(R.string.title_add_pc),
                    iconRes = R.drawable.ic_m3_add,
                    onClick = callbacks::onAddComputerClicked,
                    backdrop = combinedBackdrop,
                    modifier = Modifier.align(Alignment.BottomStart)
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
        val contentPadding = if (isLandscape) {
            PaddingValues(
                start = 104.dp,
                end = 20.dp,
                top = topInsets.calculateTopPadding() + 74.dp,
                bottom = navInsets.calculateBottomPadding() + 20.dp
            )
        } else {
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topInsets.calculateTopPadding() + 84.dp,
                bottom = navInsets.calculateBottomPadding() + 92.dp
            )
        }

        if (state.computers.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .liquidGlassSurface(
                            backdrop = backgroundBackdrop,
                            shape = RoundedCornerShape(28.dp),
                            level = GlassElevationLevel.CARD
                        )
                        .padding(horizontal = 32.dp, vertical = 28.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.searching_pc),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = state.computers,
                    key = { index, pc -> "${pc.uuid}_$index" }
                ) { _, pc ->
                    GlassPcCard(
                        pc = pc,
                        backdrop = backgroundBackdrop,
                        onClick = { callbacks.onComputerClicked(pc.computerObject) },
                        onLongClick = { callbacks.onComputerLongClicked(pc.computerObject) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassPcCard(
    pc: PcCardModel,
    backdrop: Backdrop,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else if (isFocused) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pcCardScale"
    )

    val isOnline = pc.state == ComputerDetails.State.ONLINE
    val isOffline = pc.state == ComputerDetails.State.OFFLINE
    val isUnknown = pc.state == ComputerDetails.State.UNKNOWN
    val isUnpaired = isOnline && pc.pairState == PairingManager.PairState.NOT_PAIRED

    val statusText = when (pc.state) {
        ComputerDetails.State.ONLINE -> stringResource(R.string.pcview_menu_header_online)
        ComputerDetails.State.OFFLINE -> stringResource(R.string.pcview_menu_header_offline)
        else -> stringResource(R.string.pcview_menu_header_unknown)
    }
    val statusDotColor = when (pc.state) {
        ComputerDetails.State.ONLINE -> Color(0xFF6DD58C)
        ComputerDetails.State.OFFLINE -> Color(0xFF8E9099)
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(174.dp)
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
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(82.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.09f))
        ) {
            if (isUnknown) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(34.dp)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_computer),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(46.dp)
                        .alpha(if (isOnline) 1f else 0.42f)
                )
            }

            if (isOffline || isUnpaired) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isOffline) R.drawable.ic_pc_offline else R.drawable.ic_lock
                        ),
                        contentDescription = null,
                        tint = if (isOffline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = pc.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isOnline) 1f else 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(statusDotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isOnline) 0.9f else 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
