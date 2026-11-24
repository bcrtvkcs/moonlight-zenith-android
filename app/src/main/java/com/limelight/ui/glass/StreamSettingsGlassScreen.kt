package com.limelight.ui.glass

import android.content.Context
import android.os.Build
import android.preference.CheckBoxPreference
import android.preference.ListPreference
import android.preference.Preference
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.limelight.R
import com.limelight.preferences.ConfirmDeleteOscPreference
import com.limelight.preferences.M3AudioOutputPreference
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.SeekBarPreference
import com.limelight.preferences.StreamSettings
import com.limelight.ui.background.UiBackgroundManager
import com.limelight.ui.background.UiBackgroundState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StreamSettingsGlassUiState(
    val categories: List<StreamSettings.CategoryTab> = emptyList(),
    val initialCategoryKey: String = "category_basic_settings",
    val revision: Int = 0
)

class StreamSettingsGlassController {
    private val _uiState = MutableStateFlow(StreamSettingsGlassUiState())
    val uiState: StateFlow<StreamSettingsGlassUiState> = _uiState.asStateFlow()

    fun setCategories(categories: List<StreamSettings.CategoryTab>?, currentKey: String?) {
        _uiState.value = _uiState.value.copy(
            categories = categories.orEmpty().filterNotNull(),
            initialCategoryKey = currentKey ?: "category_basic_settings",
            revision = _uiState.value.revision + 1
        )
    }

    fun bumpRevision() {
        _uiState.value = _uiState.value.copy(revision = _uiState.value.revision + 1)
    }
}

interface StreamSettingsGlassCallbacks {
    fun onBackClicked()
    fun onCategorySelected(categoryKey: String)
    fun onPickCustomBackgroundImage()
    fun onClearCustomBackgroundImage()
    fun onPreferenceMutated(key: String?)
}

object StreamSettingsGlassHost {
    @JvmStatic
    fun createView(
        context: Context,
        controller: StreamSettingsGlassController,
        callbacks: StreamSettingsGlassCallbacks
    ): ComposeView {
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                StreamSettingsGlassScreen(controller = controller, callbacks = callbacks)
            }
        }
    }
}

private sealed class SettingsDialogState {
    data class SingleChoice(val pref: ListPreference) : SettingsDialogState()
    data class AudioOutput(val pref: M3AudioOutputPreference) : SettingsDialogState()
    data class SliderDialog(val pref: SeekBarPreference) : SettingsDialogState()
    data class ConfirmOscReset(val pref: ConfirmDeleteOscPreference) : SettingsDialogState()
}

private fun isGlassOpticalSliderKey(key: String?): Boolean {
    return key == PreferenceConfiguration.GLASS_BLUR_PREF_STRING ||
            key == PreferenceConfiguration.GLASS_REFRACTION_AMOUNT_PREF_STRING ||
            key == PreferenceConfiguration.GLASS_REFRACTION_HEIGHT_PREF_STRING ||
            key == PreferenceConfiguration.GLASS_SURFACE_OPACITY_PREF_STRING ||
            key == PreferenceConfiguration.GLASS_HIGHLIGHT_INTENSITY_PREF_STRING
}

private fun isLivePreviewSliderKey(key: String?): Boolean {
    return isGlassOpticalSliderKey(key) ||
            key == PreferenceConfiguration.UI_BACKGROUND_OPACITY_PREF_STRING ||
            key == PreferenceConfiguration.UI_BACKGROUND_TINT_OPACITY_PREF_STRING ||
            key == PreferenceConfiguration.UI_BACKGROUND_BLUR_PREF_STRING
}

@Composable
fun StreamSettingsGlassScreen(
    controller: StreamSettingsGlassController,
    callbacks: StreamSettingsGlassCallbacks
) {
    val uiState by controller.uiState.collectAsState()
    val bgState by UiBackgroundManager.state.collectAsState()
    val categories = uiState.categories
    val useFloatingBottomBar = bgState.isFloatingBottomBarNav

    val initialPage = remember(categories, uiState.initialCategoryKey) {
        val idx = categories.indexOfFirst { it.key == uiState.initialCategoryKey }
        if (idx >= 0) idx else 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { categories.size.coerceAtLeast(1) }
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(categories, uiState.initialCategoryKey) {
        val target = categories.indexOfFirst { it.key == uiState.initialCategoryKey }
        if (target >= 0 && target != pagerState.currentPage) {
            pagerState.scrollToPage(target)
        }
    }

    LaunchedEffect(pagerState.currentPage, categories) {
        if (pagerState.currentPage in categories.indices) {
            categories[pagerState.currentPage].key?.let { callbacks.onCategorySelected(it) }
        }
    }

    var activeDialog by remember { mutableStateOf<SettingsDialogState?>(null) }

    val activeTitle = if (categories.isNotEmpty() && pagerState.currentPage in categories.indices) {
        categories[pagerState.currentPage].title?.toString().orEmpty()
    } else {
        stringResource(R.string.category_basic_settings)
    }

    val navTabItems = categories.map { cat ->
        resolveSettingsNavTabItem(cat.key, cat.title?.toString().orEmpty())
    }

    val topInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues()
    val navInsets = WindowInsets.navigationBars.asPaddingValues()

    LiquidGlassScaffold(
        floatingChrome = { combinedBackdrop ->
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                GlassTopBar(
                    title = activeTitle,
                    backdrop = combinedBackdrop,
                    navigationIcon = {
                        GlassIconButton(
                            iconRes = R.drawable.ic_m3_back,
                            contentDescription = "Back",
                            onClick = callbacks::onBackClicked,
                            size = 42.dp,
                            iconSize = 20.dp,
                            backdrop = combinedBackdrop
                        )
                    }
                )
                if (!useFloatingBottomBar && categories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    GlassPillTabBar(
                        tabs = categories.map { it.title?.toString().orEmpty() },
                        selectedIndex = pagerState.currentPage.coerceIn(0, categories.lastIndex),
                        onTabSelected = { targetIndex ->
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetIndex)
                            }
                        },
                        backdrop = combinedBackdrop
                    )
                }
            }

            if (useFloatingBottomBar && navTabItems.isNotEmpty()) {
                LiquidFloatingBottomBar(
                    tabs = navTabItems,
                    selectedIndex = pagerState.currentPage.coerceIn(0, navTabItems.lastIndex),
                    pageOffsetFraction = pagerState.currentPageOffsetFraction,
                    onTabSelected = { targetIndex ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetIndex)
                        }
                    },
                    backdrop = combinedBackdrop,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        },
        overlayContent = { combinedBackdrop ->
            // State-retaining animated dialog so both enter AND exit spring transitions always play smoothly!
            GlassAnimatedModalDialog(
                targetState = activeDialog,
                onDismiss = {
                    if (activeDialog is SettingsDialogState.SliderDialog) {
                        UiBackgroundManager.clearLivePreview()
                    }
                    activeDialog = null
                },
                backdrop = combinedBackdrop,
                scrimAlpha = { dlg ->
                    if (dlg is SettingsDialogState.SliderDialog && isLivePreviewSliderKey(dlg.pref.key)) {
                        0.16f
                    } else {
                        0.42f
                    }
                }
            ) { dlg ->
                when (dlg) {
                    is SettingsDialogState.SingleChoice -> {
                        GlassSingleChoicePrefBody(
                            pref = dlg.pref,
                            backdrop = combinedBackdrop,
                            onDismiss = { activeDialog = null },
                            onSelected = { newValue ->
                                val listener = dlg.pref.onPreferenceChangeListener
                                val allow = listener?.onPreferenceChange(dlg.pref, newValue) ?: true
                                if (allow) {
                                    dlg.pref.value = newValue
                                    callbacks.onPreferenceMutated(dlg.pref.key)
                                    controller.bumpRevision()
                                }
                                activeDialog = null
                            }
                        )
                    }
                    is SettingsDialogState.AudioOutput -> {
                        GlassAudioOutputPrefBody(
                            pref = dlg.pref,
                            backdrop = combinedBackdrop,
                            onDismiss = { activeDialog = null },
                            onConfirm = { clientEnabled, hostEnabled ->
                                dlg.pref.applySelection(clientEnabled, hostEnabled)
                                callbacks.onPreferenceMutated(dlg.pref.key)
                                controller.bumpRevision()
                                activeDialog = null
                            }
                        )
                    }
                    is SettingsDialogState.SliderDialog -> {
                        GlassSeekBarPrefBody(
                            pref = dlg.pref,
                            bgState = bgState,
                            backdrop = combinedBackdrop,
                            onDismiss = {
                                UiBackgroundManager.clearLivePreview()
                                activeDialog = null
                            },
                            onConfirm = { finalVal ->
                                UiBackgroundManager.clearLivePreview()
                                dlg.pref.applyValueFromExternal(finalVal)
                                callbacks.onPreferenceMutated(dlg.pref.key)
                                controller.bumpRevision()
                                activeDialog = null
                            }
                        )
                    }
                    is SettingsDialogState.ConfirmOscReset -> {
                        Text(
                            text = (dlg.pref.dialogTitle ?: dlg.pref.title)?.toString().orEmpty(),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = dlg.pref.dialogMessage?.toString().orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            GlassActionButton(
                                text = (dlg.pref.negativeButtonText ?: stringResource(R.string.no)).toString(),
                                onClick = { activeDialog = null },
                                backdrop = combinedBackdrop,
                                modifier = Modifier.weight(1f)
                            )
                            GlassActionButton(
                                text = (dlg.pref.positiveButtonText ?: stringResource(R.string.yes)).toString(),
                                onClick = {
                                    dlg.pref.resetOscConfiguration()
                                    activeDialog = null
                                },
                                destructive = true,
                                backdrop = combinedBackdrop,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    ) { backgroundBackdrop ->
        if (categories.isNotEmpty()) {
            val topPad = topInsets.calculateTopPadding() + if (useFloatingBottomBar) 82.dp else 132.dp
            val bottomPad = navInsets.calculateBottomPadding() + if (useFloatingBottomBar) 98.dp else 28.dp

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val cat = categories[pageIndex]
                val prefsList = remember(cat, uiState.revision) {
                    val screen = cat.screen
                    if (screen == null) {
                        emptyList()
                    } else {
                        List(screen.preferenceCount) { idx -> screen.getPreference(idx) }.filterNotNull()
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = topPad,
                        bottom = bottomPad
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = prefsList,
                        key = { idx, pref -> "${pref.key ?: pref.title?.toString() ?: "pref"}_$idx" }
                    ) { _, pref ->
                        if (pref.key == PreferenceConfiguration.LIQUID_GLASS_STYLE_PREF_STRING) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                LiquidGlassLivePreviewCard(bgState = bgState, compact = false)
                                GlassPreferenceItemRow(
                                    preference = pref,
                                    revision = uiState.revision,
                                    backdrop = backgroundBackdrop,
                                    onClick = {
                                        if (pref is ListPreference) {
                                            activeDialog = SettingsDialogState.SingleChoice(pref)
                                        }
                                    }
                                )
                            }
                        } else if (pref.key == PreferenceConfiguration.UI_BACKGROUND_PICK_IMAGE_PREF_STRING) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && bgState.isWallpaperActive) {
                                    GlassWallpaperNoticeCard(
                                        backdrop = backgroundBackdrop,
                                        onPickCustomImage = callbacks::onPickCustomBackgroundImage
                                    )
                                }
                                GlassCustomBackgroundPickerCard(
                                    hasCustomImage = bgState.hasCustomImage,
                                    imageVersion = bgState.imageVersion,
                                    backdrop = backgroundBackdrop,
                                    onPickImage = callbacks::onPickCustomBackgroundImage,
                                    onClearImage = callbacks::onClearCustomBackgroundImage
                                )
                            }
                        } else {
                            GlassPreferenceItemRow(
                                preference = pref,
                                revision = uiState.revision,
                                backdrop = backgroundBackdrop,
                                onClick = {
                                    when (pref) {
                                        is CheckBoxPreference -> {
                                            val newChecked = !pref.isChecked
                                            val listener = pref.onPreferenceChangeListener
                                            val allow = listener?.onPreferenceChange(pref, newChecked) ?: true
                                            if (allow) {
                                                pref.isChecked = newChecked
                                                callbacks.onPreferenceMutated(pref.key)
                                                controller.bumpRevision()
                                            }
                                        }
                                        is ListPreference -> {
                                            activeDialog = SettingsDialogState.SingleChoice(pref)
                                        }
                                        is M3AudioOutputPreference -> {
                                            activeDialog = SettingsDialogState.AudioOutput(pref)
                                        }
                                        is SeekBarPreference -> {
                                            activeDialog = SettingsDialogState.SliderDialog(pref)
                                        }
                                        is ConfirmDeleteOscPreference -> {
                                            activeDialog = SettingsDialogState.ConfirmOscReset(pref)
                                        }
                                        else -> {
                                            pref.onPreferenceClickListener?.onPreferenceClick(pref)
                                            callbacks.onPreferenceMutated(pref.key)
                                            controller.bumpRevision()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCustomBackgroundPickerCard(
    hasCustomImage: Boolean,
    imageVersion: Long,
    backdrop: Backdrop,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit
) {
    val context = LocalContext.current
    val previewBmp = remember(hasCustomImage, imageVersion) {
        if (hasCustomImage) UiBackgroundManager.loadCustomBitmapSync(context) else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(22.dp),
                level = GlassElevationLevel.CARD
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.title_ui_background_pick_image),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (hasCustomImage) R.string.summary_ui_background_pick_image_set
                        else R.string.summary_ui_background_pick_image_empty
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (previewBmp != null && !previewBmp.isRecycled) {
                Spacer(modifier = Modifier.width(12.dp))
                Image(
                    bitmap = previewBmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            GlassActionButton(
                text = stringResource(R.string.ui_background_pick_action),
                onClick = onPickImage,
                primary = true,
                backdrop = backdrop,
                modifier = Modifier.weight(1f)
            )
            if (hasCustomImage) {
                GlassActionButton(
                    text = stringResource(R.string.ui_background_clear_action),
                    onClick = onClearImage,
                    destructive = true,
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GlassPreferenceItemRow(
    preference: Preference,
    revision: Int,
    backdrop: Backdrop,
    onClick: () -> Unit
) {
    val _rev = revision
    val enabled = preference.isEnabled
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val valueBadgeText = remember(preference, _rev) {
        when (preference) {
            is ListPreference -> preference.entry?.toString()
            is M3AudioOutputPreference -> preference.displayBadgeText
            is SeekBarPreference -> preference.formatDisplayValue(preference.progress)
            else -> null
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.42f)
            .liquidGlassSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(20.dp),
                level = GlassElevationLevel.CARD,
                isFocused = isFocused,
                enableShadow = false
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = preference.title?.toString().orEmpty(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!valueBadgeText.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = valueBadgeText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val summary = preference.summary?.toString()
            if (!summary.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (preference is CheckBoxPreference) {
            LiquidGlassSwitch(
                checked = preference.isChecked,
                enabled = enabled,
                backdrop = backdrop,
                onCheckedChange = { onClick() }
            )
        }
    }
}

@Composable
private fun GlassSingleChoicePrefBody(
    pref: ListPreference,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    val entries = pref.entries ?: emptyArray()
    val values = pref.entryValues ?: emptyArray()
    val currentVal = pref.value
    val tokens = LocalGlassTokens.current

    Text(
        text = (pref.dialogTitle ?: pref.title)?.toString().orEmpty(),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(14.dp))

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .verticalScroll(rememberScrollState())
    ) {
        entries.forEachIndexed { idx, label ->
            if (idx >= values.size) return@forEachIndexed
            val itemVal = values[idx]?.toString().orEmpty()
            val isSelected = itemVal == currentVal
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.97f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "choiceRowScale"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .liquidGlassSurface(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        level = if (isSelected) GlassElevationLevel.ACCENT else GlassElevationLevel.CHROME,
                        tintOverride = when {
                            isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.44f)
                            isFocused -> Color.White.copy(alpha = 0.16f)
                            else -> tokens.cardTint.copy(alpha = (tokens.cardTint.alpha * 0.65f).coerceIn(0.06f, 0.28f))
                        },
                        isFocused = isFocused,
                        enableLens = true,
                        enableShadow = false
                    )
                    .focusable(interactionSource = interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null) {
                        onSelected(itemVal)
                    }
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = CircleShape
                        )
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label?.toString().orEmpty(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_m3_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        GlassActionButton(
            text = stringResource(android.R.string.cancel),
            onClick = onDismiss,
            backdrop = backdrop
        )
    }
}

@Composable
private fun GlassSeekBarPrefBody(
    pref: SeekBarPreference,
    bgState: UiBackgroundState,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val minVal = pref.minValue
    val maxVal = pref.maxValue.coerceAtLeast(minVal + 1)
    val step = pref.stepSize.coerceAtLeast(1)
    var currentVal by remember(pref) { mutableIntStateOf(pref.progress.coerceIn(minVal, maxVal)) }

    Text(
        text = (pref.dialogTitle ?: pref.title)?.toString().orEmpty(),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
    )

    val msg = pref.dialogMessageText
    if (!msg.isNullOrEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = msg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (isGlassOpticalSliderKey(pref.key)) {
        Spacer(modifier = Modifier.height(14.dp))
        LiquidGlassLivePreviewCard(bgState = bgState, compact = true)
    }

    Spacer(modifier = Modifier.height(16.dp))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .liquidGlassSurface(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(16.dp),
                    level = GlassElevationLevel.ACCENT,
                    tintOverride = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    enableShadow = false
                )
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = pref.formatDisplayValue(currentVal).orEmpty(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    LiquidGlassSlider(
        value = currentVal,
        minValue = minVal,
        maxValue = maxVal,
        step = step,
        backdrop = backdrop,
        onValueChange = { stepped ->
            currentVal = stepped
            when (pref.key) {
                PreferenceConfiguration.UI_BACKGROUND_OPACITY_PREF_STRING ->
                    UiBackgroundManager.setLivePreview(opacity = stepped)
                PreferenceConfiguration.UI_BACKGROUND_TINT_OPACITY_PREF_STRING ->
                    UiBackgroundManager.setLivePreview(tintOpacity = stepped)
                PreferenceConfiguration.UI_BACKGROUND_BLUR_PREF_STRING ->
                    UiBackgroundManager.setLivePreview(blurDp = stepped)
                PreferenceConfiguration.GLASS_BLUR_PREF_STRING ->
                    UiBackgroundManager.setGlassLivePreview(glassBlurDp = stepped)
                PreferenceConfiguration.GLASS_REFRACTION_AMOUNT_PREF_STRING ->
                    UiBackgroundManager.setGlassLivePreview(refractionAmountDp = stepped)
                PreferenceConfiguration.GLASS_REFRACTION_HEIGHT_PREF_STRING ->
                    UiBackgroundManager.setGlassLivePreview(refractionHeightDp = stepped)
                PreferenceConfiguration.GLASS_SURFACE_OPACITY_PREF_STRING ->
                    UiBackgroundManager.setGlassLivePreview(surfaceOpacity = stepped)
                PreferenceConfiguration.GLASS_HIGHLIGHT_INTENSITY_PREF_STRING ->
                    UiBackgroundManager.setGlassLivePreview(highlightIntensity = stepped)
            }
        }
    )

    Spacer(modifier = Modifier.height(18.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        GlassActionButton(
            text = stringResource(android.R.string.cancel),
            onClick = onDismiss,
            backdrop = backdrop,
            modifier = Modifier.weight(1f)
        )
        GlassActionButton(
            text = stringResource(android.R.string.ok),
            onClick = { onConfirm(currentVal) },
            primary = true,
            backdrop = backdrop,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GlassAudioOutputPrefBody(
    pref: M3AudioOutputPreference,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onConfirm: (clientEnabled: Boolean, hostEnabled: Boolean) -> Unit
) {
    var clientChecked by remember(pref) { mutableStateOf(pref.isClientEnabled) }
    var hostChecked by remember(pref) { mutableStateOf(pref.isHostEnabled) }
    val tokens = LocalGlassTokens.current

    Text(
        text = pref.title?.toString().orEmpty(),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.summary_audio_output),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))

    val rows = listOf(
        Triple(stringResource(R.string.audio_output_client), clientChecked) { clientChecked = !clientChecked },
        Triple(stringResource(R.string.audio_output_host), hostChecked) { hostChecked = !hostChecked }
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { (label, checked, onToggle) ->
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.97f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "audioCheckRowScale"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .liquidGlassSurface(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(18.dp),
                        level = if (checked) GlassElevationLevel.ACCENT else GlassElevationLevel.CHROME,
                        tintOverride = when {
                            checked -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.44f)
                            isFocused -> Color.White.copy(alpha = 0.16f)
                            else -> tokens.cardTint.copy(alpha = (tokens.cardTint.alpha * 0.65f).coerceIn(0.06f, 0.28f))
                        },
                        isFocused = isFocused,
                        enableLens = true,
                        enableShadow = false
                    )
                    .focusable(interactionSource = interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Rounded Liquid Glass checkbox indicator
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (checked) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .border(
                            width = 2.dp,
                            color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(7.dp)
                        )
                ) {
                    if (checked) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_m3_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (checked) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        GlassActionButton(
            text = stringResource(android.R.string.cancel),
            onClick = onDismiss,
            backdrop = backdrop,
            modifier = Modifier.weight(1f)
        )
        GlassActionButton(
            text = stringResource(android.R.string.ok),
            onClick = { onConfirm(clientChecked, hostChecked) },
            primary = true,
            backdrop = backdrop,
            modifier = Modifier.weight(1f)
        )
    }
}
