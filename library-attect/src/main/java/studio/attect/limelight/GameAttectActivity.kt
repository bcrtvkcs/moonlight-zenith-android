package studio.attect.limelight

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import studio.attect.limelight.ui.TouchDragListener

/**
 */
abstract class GameAttectActivity : Activity() {

    private val myCoroutineContext = newSingleThreadContext("GameAttectActivityCoroutineContext")
    private val scope = CoroutineScope(myCoroutineContext)

    private lateinit var translateKeyCode: (keyCode: Int, device: Int) -> Short
    private lateinit var sendKeyboardInput: (translatedKeyCode: Short, keyDirection: Byte, modifierState: Byte, flags: Byte) -> Unit
    private lateinit var mouseButtonUp: (button: Byte) -> Unit
    private lateinit var mouseButtonDown: (button: Byte) -> Unit

    private lateinit var floatButton: ImageView
    private lateinit var overflowLayout: FrameLayout

    private lateinit var virtualKeyEsc: RelativeLayout
    private lateinit var virtualKeyWin: RelativeLayout
    private lateinit var virtualKeySystemMenu: RelativeLayout
    private lateinit var virtualMouseMiddleButton: RelativeLayout
    private lateinit var onScreenGamePad: RelativeLayout
    private lateinit var touchScreenModeMenu: RelativeLayout
    private lateinit var touchPadModeMenu: RelativeLayout
    private lateinit var toggleSoftKeyboardMenu: RelativeLayout

    private lateinit var virtualKeyExitProgram: RelativeLayout
    private lateinit var virtualKeyScreenshot: RelativeLayout
    private lateinit var virtualKeyTaskManager: RelativeLayout
    private lateinit var virtualKeyExplorer: RelativeLayout
    private lateinit var virtualKeyCopy: RelativeLayout
    private lateinit var virtualKeyCut: RelativeLayout
    private lateinit var virtualKeyPaste: RelativeLayout
    private lateinit var virtualKeySelectAll: RelativeLayout

    private lateinit var virtualKeyShowDesktop: RelativeLayout
    private lateinit var virtualKeyTaskView: RelativeLayout
    private lateinit var virtualKeyNewDesktop: RelativeLayout
    private lateinit var virtualKeyDeleteDesktop: RelativeLayout
    private lateinit var virtualKeyLeftDesktop: RelativeLayout
    private lateinit var virtualKeyRightDesktop: RelativeLayout

    private lateinit var virtualKeyNvidiaOverflow: RelativeLayout
    private lateinit var virtualKeyNvidiaScreenshot: RelativeLayout
    private lateinit var virtualKeyNvidiaPhotoMode: RelativeLayout
    private lateinit var virtualKeyNvidiaGameFilter: RelativeLayout
    private lateinit var virtualKeyNvidiaInstantlyRelay: RelativeLayout
    private lateinit var virtualKeyNvidiaRecord: RelativeLayout

    private var isShowOverflowMenu = false
    private var isLiquidGlassStyle = false
    private var glassOverflowDialog: Dialog? = null

    var nativeTouchEventMode = false

    /**
     * Upgrades the Attect floating trigger button, overflow card, and all 28 shortcut chips to
     * translucent specular Liquid Glass styling with smooth open/close animations and (on Android 12+)
     * a non-focusable SurfaceFlinger blur-behind overlay that blurs the live game stream with zero
     * added streaming latency. Material You Expressive mode never calls this method.
     */
    fun applyAttectLiquidGlassStyle() {
        isLiquidGlassStyle = true
        if (::floatButton.isInitialized) {
            floatButton.setBackgroundResource(R.drawable.m3_glass_float_button_bg)
            floatButton.alpha = 0.78f
        }
        if (::overflowLayout.isInitialized) {
            overflowLayout.setBackgroundColor(0x38000000)
            if (overflowLayout.childCount > 0) {
                val card = overflowLayout.getChildAt(0) as? com.google.android.material.card.MaterialCardView
                if (card != null) {
                    card.setCardBackgroundColor(Color.TRANSPARENT)
                    card.strokeWidth = 0
                    card.cardElevation = 0f
                    card.setBackgroundResource(R.drawable.m3_glass_overflow_card_bg)
                }
            }

            // Style all 28 inner shortcut chips as translucent specular Liquid Glass pills
            val primaryChips = listOf(
                virtualKeyEsc,
                virtualKeyWin,
                onScreenGamePad,
                touchScreenModeMenu,
                touchPadModeMenu,
                toggleSoftKeyboardMenu
            )
            primaryChips.forEach { it.setBackgroundResource(R.drawable.m3_glass_overflow_primary_chip_bg) }

            virtualKeyExitProgram.setBackgroundResource(R.drawable.m3_glass_overflow_destructive_chip_bg)

            val standardChips = listOf(
                virtualKeySystemMenu,
                virtualMouseMiddleButton,
                virtualKeyScreenshot,
                virtualKeyTaskManager,
                virtualKeyExplorer,
                virtualKeyCopy,
                virtualKeyCut,
                virtualKeyPaste,
                virtualKeySelectAll,
                virtualKeyShowDesktop,
                virtualKeyTaskView,
                virtualKeyNewDesktop,
                virtualKeyDeleteDesktop,
                virtualKeyLeftDesktop,
                virtualKeyRightDesktop,
                virtualKeyNvidiaOverflow,
                virtualKeyNvidiaScreenshot,
                virtualKeyNvidiaPhotoMode,
                virtualKeyNvidiaGameFilter,
                virtualKeyNvidiaInstantlyRelay,
                virtualKeyNvidiaRecord
            )
            standardChips.forEach { it.setBackgroundResource(R.drawable.m3_glass_overflow_chip_bg) }

            // On Android 12+, move overflowLayout into a non-focusable translucent Dialog so
            // SurfaceFlinger natively blurs the live SurfaceView stream behind the Attect glass card
            // without stealing pointer capture or window focus from Game!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && glassOverflowDialog == null) {
                try {
                    (overflowLayout.parent as? ViewGroup)?.removeView(overflowLayout)
                    val dlg = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
                    dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
                    dlg.setCancelable(false)
                    dlg.setCanceledOnTouchOutside(false)
                    overflowLayout.visibility = View.VISIBLE
                    dlg.setContentView(
                        overflowLayout,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    dlg.window?.let { win ->
                        win.setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        win.setFormat(PixelFormat.TRANSLUCENT)
                        win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                        win.decorView.setBackgroundColor(Color.TRANSPARENT)
                        // FLAG_NOT_FOCUSABLE keeps Game's pointer capture & immersive bars untouched
                        win.addFlags(
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                    or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                        )
                        val lp = win.attributes
                        if (lp != null) {
                            lp.blurBehindRadius = (22 * resources.displayMetrics.density)
                                .toInt()
                                .coerceIn(1, 110)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                lp.layoutInDisplayCutoutMode =
                                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                            }
                            win.attributes = lp
                        }
                    }
                    glassOverflowDialog = dlg
                } catch (_: Throwable) {
                    // Fallback seamlessly to in-decorView animated glass card
                    glassOverflowDialog = null
                    if (overflowLayout.parent == null) {
                        overflowLayout.visibility = View.GONE
                        (window.decorView as? FrameLayout)?.addView(overflowLayout)
                    }
                }
            }
        }
    }

    fun afterOnCreate(
        appIconResId: Int,
        toggleSoftKeyboardRunner: () -> Unit,
        updateTouchscreenTrackpad: (Boolean) -> Unit,
        translateKeyCode: (keyCode: Int, device: Int) -> Short,
        sendKeyboardInput: (translatedKeyCode: Short, keyDirection: Byte, modifierState: Byte, flags: Byte) -> Unit,
        mouseButtonDown: (button: Byte) -> Unit,
        mouseButtonUp: (button: Byte) -> Unit,
        virtualControllerShow: () -> Unit
    ) {
        this.translateKeyCode = translateKeyCode
        this.sendKeyboardInput = sendKeyboardInput
        this.mouseButtonUp = mouseButtonUp
        this.mouseButtonDown = mouseButtonDown
        val view = this.window.decorView as FrameLayout
        val appendView = LayoutInflater.from(this).inflate(R.layout.game_overflow_menu, view, true)

        floatButton = appendView.findViewById(R.id.floatButton)
        overflowLayout = appendView.findViewById(R.id.toolbar_overflow)

        virtualKeyEsc = appendView.findViewById(R.id.virtualKeyEsc)
        virtualKeyWin = appendView.findViewById(R.id.virtualKeyWin)
        virtualKeySystemMenu = appendView.findViewById(R.id.virtualKeySystemMenu)
        virtualMouseMiddleButton = appendView.findViewById(R.id.virtualMouseMiddleButton)
        onScreenGamePad = appendView.findViewById(R.id.onScreenGamePad)
        touchScreenModeMenu = appendView.findViewById(R.id.touchScreenModeMenu)
        touchPadModeMenu = appendView.findViewById(R.id.touchPadModeMenu)
        toggleSoftKeyboardMenu = appendView.findViewById(R.id.toggleSoftKeyboardMenu)

        virtualKeyExitProgram = appendView.findViewById(R.id.virtualKeyExitProgram)
        virtualKeyScreenshot = appendView.findViewById(R.id.virtualKeyScreenshot)
        virtualKeyTaskManager = appendView.findViewById(R.id.virtualKeyTaskManager)
        virtualKeyExplorer = appendView.findViewById(R.id.virtualKeyExplorer)
        virtualKeyCopy = appendView.findViewById(R.id.virtualKeyCopy)
        virtualKeyCut = appendView.findViewById(R.id.virtualKeyCut)
        virtualKeyPaste = appendView.findViewById(R.id.virtualKeyPaste)
        virtualKeySelectAll = appendView.findViewById(R.id.virtualKeySelectAll)

        virtualKeyShowDesktop = appendView.findViewById(R.id.virtualKeyShowDesktop)
        virtualKeyTaskView = appendView.findViewById(R.id.virtualKeyTaskView)
        virtualKeyNewDesktop = appendView.findViewById(R.id.virtualKeyNewDesktop)
        virtualKeyDeleteDesktop = appendView.findViewById(R.id.virtualKeyDeleteDesktop)
        virtualKeyLeftDesktop = appendView.findViewById(R.id.virtualKeyLeftDesktop)
        virtualKeyRightDesktop = appendView.findViewById(R.id.virtualKeyRightDesktop)

        virtualKeyNvidiaOverflow = appendView.findViewById(R.id.virtualKeyNvidiaOverflow)
        virtualKeyNvidiaScreenshot = appendView.findViewById(R.id.virtualKeyNvidiaScreenshot)
        virtualKeyNvidiaPhotoMode = appendView.findViewById(R.id.virtualKeyNvidiaPhotoMode)
        virtualKeyNvidiaGameFilter = appendView.findViewById(R.id.virtualKeyNvidiaGameFilter)
        virtualKeyNvidiaInstantlyRelay = appendView.findViewById(R.id.virtualKeyNvidiaInstantlyRelay)
        virtualKeyNvidiaRecord = appendView.findViewById(R.id.virtualKeyNvidiaRecord)

        overflowLayout.setOnClickListener { toggleOverflowMenu() }

        floatButton.setImageResource(appIconResId)
        floatButton.apply {
            alpha = 0.5f
            setOnTouchListener(TouchDragListener())
            setOnClickListener {
                toggleOverflowMenu()
            }
            setOnLongClickListener {
                return@setOnLongClickListener true
            }
        }

        virtualKeyEsc.setOnClickListener {
            pressKey(KeyEvent.KEYCODE_ESCAPE)
            toggleOverflowMenu()
        }
        virtualKeyWin.setOnClickListener {
            pressKey(KeyEvent.KEYCODE_META_LEFT)
            toggleOverflowMenu()
        }
        virtualKeySystemMenu.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_X)
            toggleOverflowMenu()
        }
        virtualMouseMiddleButton.setOnClickListener {
            pressMouseKey(MOUSE_BUTTON_MIDDLE)
            toggleOverflowMenu()
        }
        onScreenGamePad.setOnClickListener {
            virtualControllerShow()
            toggleOverflowMenu()
        }
        touchScreenModeMenu.setOnClickListener {
            nativeTouchEventMode = true
            updateTouchscreenTrackpad(false)
            toggleOverflowMenu()
        }
        touchPadModeMenu.setOnClickListener {
            nativeTouchEventMode = false
            updateTouchscreenTrackpad(true)
            toggleOverflowMenu()
        }
        toggleSoftKeyboardMenu.setOnClickListener {
            toggleOverflowMenu()
            toggleSoftKeyboardRunner()
        }

        virtualKeyExitProgram.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_F4)
            toggleOverflowMenu()
        }
        virtualKeyScreenshot.setOnClickListener {
            pressKey(KeyEvent.KEYCODE_SYSRQ)
            toggleOverflowMenu()
        }
        virtualKeyScreenshot.setOnLongClickListener {
            pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_SYSRQ)
            toggleOverflowMenu()
            return@setOnLongClickListener true
        }
        virtualKeyTaskManager.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_ESCAPE)
            toggleOverflowMenu()
        }
        virtualKeyExplorer.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_E)
            toggleOverflowMenu()
        }
        virtualKeyCopy.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_C)
            toggleOverflowMenu()
        }
        virtualKeyCut.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_X)
            toggleOverflowMenu()
        }
        virtualKeyPaste.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_V)
            toggleOverflowMenu()
        }
        virtualKeySelectAll.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_A)
            toggleOverflowMenu()
        }

        virtualKeyShowDesktop.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_D)
            toggleOverflowMenu()
        }
        virtualKeyTaskView.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_TAB)
            toggleOverflowMenu()
        }
        virtualKeyNewDesktop.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_D)
            toggleOverflowMenu()
        }
        virtualKeyDeleteDesktop.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_F4)
            toggleOverflowMenu()
        }
        virtualKeyLeftDesktop.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_DPAD_LEFT)
            toggleOverflowMenu()
        }
        virtualKeyRightDesktop.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
            toggleOverflowMenu()
        }

        virtualKeyNvidiaOverflow.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_Z)
            toggleOverflowMenu()
        }
        virtualKeyNvidiaScreenshot.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_F1)
            toggleOverflowMenu()
        }
        virtualKeyNvidiaPhotoMode.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_F2)
            toggleOverflowMenu()
        }
        virtualKeyNvidiaGameFilter.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_F3)
            toggleOverflowMenu()
        }
        virtualKeyNvidiaInstantlyRelay.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_F10)
            toggleOverflowMenu()
        }
        virtualKeyNvidiaRecord.setOnClickListener {
            pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_F10)
            toggleOverflowMenu()
        }
    }

    private fun pressKey(androidKeyCode: Int, modifierState: Byte = 0.toByte()) {
        val keyCode = translateKeyCode(androidKeyCode, 0)
        if (keyCode == 0.toShort()) {
            return
        }
        scope.launch {
            withContext(Dispatchers.Main) {
                sendKeyboardInput(keyCode, KEYBOARD_KEY_DOWN, modifierState, 0.toByte())
            }
            delay(16)
            withContext(Dispatchers.Main) {
                sendKeyboardInput(keyCode, KEYBOARD_KEY_UP, 0.toByte(), 0.toByte())
            }
        }
    }

    private fun pressKeys(vararg androidKeyCodes: Int) {
        val keycodes = androidKeyCodes.map { translateKeyCode(it, 0) }
        if (keycodes.find { it == 0.toShort() } != null) {
            return
        }
        scope.launch {
            keycodes.forEach { keyCode ->
                withContext(Dispatchers.Main) {
                    sendKeyboardInput(keyCode, KEYBOARD_KEY_DOWN, 0.toByte(), 0.toByte())
                }
                delay(5)
            }
            delay(16)
            keycodes.reversed().forEach { keyCode ->
                withContext(Dispatchers.Main) {
                    sendKeyboardInput(keyCode, KEYBOARD_KEY_UP, 0.toByte(), 0.toByte())
                }
                delay(5)
            }
        }
    }

    private fun pressMouseKey(button: Byte) {
        scope.launch {
            withContext(Dispatchers.Main) {
                mouseButtonDown(button)
            }
            delay(16)
            withContext(Dispatchers.Main) {
                mouseButtonUp(button)
            }
        }
    }

    private fun toggleOverflowMenu() {
        isShowOverflowMenu = !isShowOverflowMenu
        val dlg = glassOverflowDialog
        val card = if (overflowLayout.childCount > 0) overflowLayout.getChildAt(0) else null

        if (isLiquidGlassStyle && dlg != null) {
            if (isShowOverflowMenu) {
                floatButton.visibility = View.GONE
                overflowLayout.alpha = 0f
                card?.apply {
                    scaleX = 0.92f
                    scaleY = 0.92f
                }
                if (!dlg.isShowing && !isFinishing) {
                    dlg.show()
                }
                overflowLayout.animate().cancel()
                overflowLayout.animate()
                    .alpha(1f)
                    .setDuration(180L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                card?.animate()?.cancel()
                card?.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setDuration(200L)
                    ?.setInterpolator(DecelerateInterpolator())
                    ?.start()
            } else {
                overflowLayout.animate().cancel()
                card?.animate()?.cancel()
                card?.animate()
                    ?.scaleX(0.94f)
                    ?.scaleY(0.94f)
                    ?.setDuration(140L)
                    ?.start()
                overflowLayout.animate()
                    .alpha(0f)
                    .setDuration(140L)
                    .withEndAction {
                        if (!isShowOverflowMenu) {
                            try {
                                if (dlg.isShowing) dlg.dismiss()
                            } catch (_: Throwable) {
                            }
                            floatButton.visibility = View.VISIBLE
                        }
                    }
                    .start()
            }
            return
        }

        if (isLiquidGlassStyle) {
            if (isShowOverflowMenu) {
                overflowLayout.visibility = View.VISIBLE
                overflowLayout.alpha = 0f
                card?.apply {
                    scaleX = 0.92f
                    scaleY = 0.92f
                }
                overflowLayout.animate().cancel()
                overflowLayout.animate().alpha(1f).setDuration(180L).start()
                card?.animate()?.cancel()
                card?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(200L)?.start()
                floatButton.visibility = View.GONE
            } else {
                overflowLayout.animate().cancel()
                card?.animate()?.cancel()
                card?.animate()?.scaleX(0.94f)?.scaleY(0.94f)?.setDuration(140L)?.start()
                overflowLayout.animate()
                    .alpha(0f)
                    .setDuration(140L)
                    .withEndAction {
                        if (!isShowOverflowMenu) {
                            overflowLayout.visibility = View.GONE
                            floatButton.visibility = View.VISIBLE
                        }
                    }
                    .start()
            }
            return
        }

        overflowLayout.visibility = if (isShowOverflowMenu) View.VISIBLE else View.GONE
        floatButton.visibility = if (isShowOverflowMenu) View.GONE else View.VISIBLE
    }

    fun setFloatButtonVisibility(visibility: Int) {
        floatButton.visibility = visibility
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (glassOverflowDialog?.isShowing == true) {
                glassOverflowDialog?.dismiss()
            }
        } catch (_: Throwable) {
        }
        glassOverflowDialog = null
        kotlin.runCatching { myCoroutineContext.close() }
    }

    companion object {
        private const val TAG = "GameAttectActivity"

        const val KEYBOARD_KEY_DOWN: Byte = 0x03
        const val KEYBOARD_KEY_UP: Byte = 0x04

        const val KEYBOARD_MODIFIER_SHIFT: Byte = 0x01
        const val KEYBOARD_MODIFIER_CTRL: Byte = 0x02
        const val KEYBOARD_MODIFIER_ALT: Byte = 0x04
        const val KEYBOARD_MODIFIER_META: Byte = 0x08

        const val MOUSE_PRESS_EVENT: Byte = 0x07
        const val MOUSE_RELEASE_EVENT: Byte = 0x08

        const val MOUSE_BUTTON_LEFT: Byte = 0x01
        const val MOUSE_BUTTON_MIDDLE: Byte = 0x02
        const val MOUSE_BUTTON_RIGHT: Byte = 0x03
        const val MOUSE_BUTTON_X1: Byte = 0x04
        const val MOUSE_BUTTON_X2: Byte = 0x05
    }
}
