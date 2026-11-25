package com.limelight.ui.glass

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.limelight.R

interface AddComputerGlassCallbacks {
    fun onBackClicked()
    fun onSubmitHost(host: String)
}

object AddComputerGlassHost {
    @JvmStatic
    fun createView(
        context: Context,
        callbacks: AddComputerGlassCallbacks
    ): ComposeView {
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AddComputerGlassScreen(callbacks = callbacks)
            }
        }
    }
}

@Composable
fun AddComputerGlassScreen(callbacks: AddComputerGlassCallbacks) {
    var hostInput by remember { mutableStateOf("") }
    val topInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues()

    LiquidGlassScaffold(
        floatingChrome = { combinedBackdrop ->
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                GlassTopBar(
                    title = stringResource(R.string.title_add_pc),
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
        }
    ) { backgroundBackdrop ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(
                    top = topInsets.calculateTopPadding() + 92.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 24.dp
                )
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .liquidGlassSurface(
                        backdrop = backgroundBackdrop,
                        shape = RoundedCornerShape(28.dp),
                        level = GlassElevationLevel.CARD
                    )
                    .padding(24.dp)
            ) {
                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.ip_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_computer),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
                        focusedContainerColor = Color.Black.copy(alpha = 0.16f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.10f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { callbacks.onSubmitHost(hostInput.trim()) }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                GlassActionButton(
                    text = stringResource(android.R.string.ok),
                    onClick = { callbacks.onSubmitHost(hostInput.trim()) },
                    primary = true,
                    backdrop = backgroundBackdrop,
                    nestedInGlass = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )
            }
        }
    }
}
