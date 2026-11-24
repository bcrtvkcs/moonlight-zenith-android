package com.limelight.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.limelight.R
import com.limelight.utils.M3MenuHelper

data class GlassMenuData(
    val title: String,
    val subtitle: String?,
    val headerIconRes: Int,
    val options: List<M3MenuHelper.MenuOption>,
    val onOptionSelected: (M3MenuHelper.MenuOption) -> Unit,
    val onDismiss: () -> Unit
) {
    companion object {
        @JvmStatic
        fun create(
            title: String?,
            subtitle: String?,
            headerIconRes: Int,
            options: List<M3MenuHelper.MenuOption>?,
            listener: M3MenuHelper.OnMenuOptionSelectedListener?,
            onDismiss: Runnable?
        ): GlassMenuData {
            return GlassMenuData(
                title = title.orEmpty(),
                subtitle = subtitle,
                headerIconRes = headerIconRes,
                options = options.orEmpty().filterNotNull(),
                onOptionSelected = { option -> listener?.onOptionSelected(option) },
                onDismiss = { onDismiss?.run() }
            )
        }
    }
}

@Composable
fun GlassMenuOverlay(
    menuData: GlassMenuData?,
    isTv: Boolean,
    backdrop: Backdrop = LocalContentBackdrop.current
) {
    val visible = menuData != null
    val dismiss = {
        menuData?.onDismiss?.invoke()
        Unit
    }

    if (isTv) {
        GlassModalDialog(
            visible = visible,
            onDismiss = dismiss,
            backdrop = backdrop
        ) {
            if (menuData != null) {
                GlassMenuContent(menuData = menuData)
            }
        }
    } else {
        GlassBottomSheetOverlay(
            visible = visible,
            onDismiss = dismiss,
            backdrop = backdrop
        ) {
            if (menuData != null) {
                GlassMenuContent(menuData = menuData)
            }
        }
    }
}

@Composable
private fun GlassMenuContent(menuData: GlassMenuData) {
    // Header
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
        ) {
            Icon(
                painter = painterResource(
                    id = if (menuData.headerIconRes != 0) menuData.headerIconRes else R.drawable.ic_computer
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = menuData.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!menuData.subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = menuData.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .verticalScroll(rememberScrollState())
    ) {
        menuData.options.forEach { option ->
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            val rowBg = when {
                isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                option.isDestructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
                option.isPrimary -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                else -> Color.White.copy(alpha = 0.08f)
            }
            val contentColor = when {
                option.isDestructive -> MaterialTheme.colorScheme.error
                option.isPrimary -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(rowBg)
                    .focusable(interactionSource = interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null) {
                        menuData.onOptionSelected(option)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (option.iconResId != 0) {
                    Icon(
                        painter = painterResource(id = option.iconResId),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }
                Text(
                    text = option.title.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (option.isPrimary || option.isDestructive) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (option.isCheckable) {
                    Spacer(modifier = Modifier.width(10.dp))
                    GlassSwitch(
                        checked = option.isChecked,
                        onCheckedChange = { menuData.onOptionSelected(option) }
                    )
                }
            }
        }
    }
}
