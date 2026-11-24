package com.limelight.ui.glass

import android.content.Context
import android.preference.CheckBoxPreference
import android.preference.ListPreference
import android.preference.Preference
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.SeekBarPreference
import com.limelight.preferences.StreamSettings
import com.limelight.ui.background.UiBackgroundManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    data class SliderDialog(val pref: SeekBarPreference) : SettingsDialogState()
    data class ConfirmOscReset(val pref: ConfirmDeleteOscPreference) : SettingsDialogState()
}

@Composable
fun StreamSettingsGlassScreen(
    controller: StreamSettingsGlassController,
    callbacks: StreamSettingsGlassCallbacks
) {
    val uiState by controller.uiState.collectAsState()
    val bgState by UiBackgroundManager.state.collectAsState()
    val categories = uiState.categories

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
                if (categories.isNotEmpty()) {
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
        },
        overlayContent = { combinedBackdrop ->
            when (val dlg = activeDialog) {
                is SettingsDialogState.SingleChoice -> {
                    GlassSingleChoicePrefDialog(
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
                is SettingsDialogState.SliderDialog -> {
                    GlassSeekBarPrefDialog(
                        pref = dlg.pref,
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
                    GlassModalDialog(
                        visible = true,
                        onDismiss = { activeDialog = null },
                        backdrop = combinedBackdrop
                    ) {
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
                null -> Unit
            }
        }
    ) { backgroundBackdrop ->
        if (categories.isNotEmpty()) {
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
                        top = topInsets.calculateTopPadding() + 132.dp,
                        bottom = navInsets.calculateBottomPadding() + 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = prefsList,
                        key = { idx, pref -> "${pref.key ?: pref.title?.toString() ?: "pref"}_$idx" }
                    ) { _, pref ->
                        if (pref.key == PreferenceConfiguration.UI_BACKGROUND_PICK_IMAGE_PREF_STRING) {
                            GlassCustomBackgroundPickerCard(
                                enabled = pref.isEnabled,
                                hasCustomImage = bgState.hasCustomImage,
                                imageVersion = bgState.imageVersion,
                                backdrop = backgroundBackdrop,
                                onPickImage = callbacks::onPickCustomBackgroundImage,
                                onClearImage = callbacks::onClearCustomBackgroundImage
                            )
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
    enabled: Boolean,
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
            .alpha(if (enabled) 1f else 0.42f)
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
                onClick = { if (enabled) onPickImage() },
                primary = true,
                backdrop = backdrop,
                modifier = Modifier.weight(1f)
            )
            if (hasCustomImage) {
                GlassActionButton(
                    text = stringResource(R.string.ui_background_clear_action),
                    onClick = { if (enabled) onClearImage() },
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
    // Reference revision to ensure Compose reads updated preference state cleanly
    val _rev = revision
    val enabled = preference.isEnabled
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val valueBadgeText = remember(preference, _rev) {
        when (preference) {
            is ListPreference -> preference.entry?.toString()
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
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f))
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
            GlassSwitch(
                checked = preference.isChecked,
                enabled = enabled,
                onCheckedChange = { onClick() }
            )
        }
    }
}

@Composable
private fun GlassSingleChoicePrefDialog(
    pref: ListPreference,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    val entries = pref.entries ?: emptyArray()
    val values = pref.entryValues ?: emptyArray()
    val currentVal = pref.value

    GlassModalDialog(
        visible = true,
        onDismiss = onDismiss,
        backdrop = backdrop
    ) {
        Text(
            text = (pref.dialogTitle ?: pref.title)?.toString().orEmpty(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(14.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                isFocused -> Color.White.copy(alpha = 0.16f)
                                else -> Color.White.copy(alpha = 0.06f)
                            }
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
}

@Composable
private fun GlassSeekBarPrefDialog(
    pref: SeekBarPreference,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val minVal = pref.minValue
    val maxVal = pref.maxValue.coerceAtLeast(minVal + 1)
    val step = pref.stepSize.coerceAtLeast(1)
    var currentVal by remember(pref) { mutableIntStateOf(pref.progress.coerceIn(minVal, maxVal)) }

    val isLivePreview = pref.key == PreferenceConfiguration.UI_BACKGROUND_OPACITY_PREF_STRING ||
            pref.key == PreferenceConfiguration.UI_BACKGROUND_TINT_OPACITY_PREF_STRING ||
            pref.key == PreferenceConfiguration.UI_BACKGROUND_BLUR_PREF_STRING ||
            pref.key == PreferenceConfiguration.GLASS_BLUR_PREF_STRING ||
            pref.key == PreferenceConfiguration.GLASS_REFRACTION_AMOUNT_PREF_STRING ||
            pref.key == PreferenceConfiguration.GLASS_REFRACTION_HEIGHT_PREF_STRING ||
            pref.key == PreferenceConfiguration.GLASS_SURFACE_OPACITY_PREF_STRING ||
            pref.key == PreferenceConfiguration.GLASS_HIGHLIGHT_INTENSITY_PREF_STRING

    GlassModalDialog(
        visible = true,
        onDismiss = onDismiss,
        backdrop = backdrop,
        // Keep scrim light during background & glass slider live-preview so the user sees live optical changes!
        scrimAlpha = if (isLivePreview) 0.14f else 0.45f
    ) {
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

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                text = pref.formatDisplayValue(currentVal).orEmpty(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Slider(
            value = currentVal.toFloat(),
            onValueChange = { raw ->
                val stepped = (((raw.roundToInt() - minVal + step / 2) / step) * step + minVal)
                    .coerceIn(minVal, maxVal)
                if (stepped != currentVal) {
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
            },
            valueRange = minVal.toFloat()..maxVal.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
            ),
            modifier = Modifier.fillMaxWidth()
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
}
