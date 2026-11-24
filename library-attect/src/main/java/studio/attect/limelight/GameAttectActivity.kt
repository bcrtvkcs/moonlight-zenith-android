package studio.attect.limelight

import android.app.Activity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
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
    private var toggleSoftKeyboardRunnerRef: (() -> Unit)? = null
    private var updateTouchscreenTrackpadRef: ((Boolean) -> Unit)? = null
    private var virtualControllerShowRef: (() -> Unit)? = null

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

    /**
     * Optional external overlay host (used by Liquid Glass mode in `app` module to render
     * all 28 Attect menu buttons with genuine Kyant0/AndroidLiquidGlass shaders & Frame-1 blur).
     * Material You Expressive leaves this null and uses the native XML overflow card untouched.
     */
    var customOverflowMenuHandler: ((show: Boolean) -> Boolean)? = null

    var nativeTouchEventMode = false

    fun applyAttectLiquidGlassStyle(surfaceOpacityPercent: Int = 24, highlightIntensityPercent: Int = 78) {
        if (::floatButton.isInitialized) {
            // Zero-latency static glass badge reflecting user's surface opacity & specular highlight
            // without running live AGSL shaders over the gameplay SurfaceView.
            val bgAlpha = ((surfaceOpacityPercent.coerceIn(5, 75) / 100f) * 255f).toInt().coerceIn(28, 196)
            val strokeAlpha = ((highlightIntensityPercent.coerceIn(0, 100) / 100f) * 210f).toInt().coerceIn(24, 220)
            val fillColor = (bgAlpha shl 24) or 0x1E2332
            val strokeColor = (strokeAlpha shl 24) or 0xFFFFFF
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(fillColor)
                setStroke((1.2f * resources.displayMetrics.density).toInt().coerceAtLeast(1), strokeColor)
            }
            floatButton.background = drawable
            floatButton.alpha = 0.84f
        }
    }

    fun onExternalOverflowMenuDismissed() {
        isShowOverflowMenu = false
        if (::floatButton.isInitialized) {
            floatButton.visibility = View.VISIBLE
        }
    }

    fun performAttectAction(actionId: Int) {
        when (actionId) {
            ACTION_ESC -> pressKey(KeyEvent.KEYCODE_ESCAPE)
            ACTION_WIN -> pressKey(KeyEvent.KEYCODE_META_LEFT)
            ACTION_SYSTEM_MENU -> pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_X)
            ACTION_MIDDLE_MOUSE -> pressMouseKey(MOUSE_BUTTON_MIDDLE)
            ACTION_ONSCREEN_GAMEPAD -> virtualControllerShowRef?.invoke()
            ACTION_TOUCHSCREEN_MODE -> {
                nativeTouchEventMode = true
                updateTouchscreenTrackpadRef?.invoke(false)
            }
            ACTION_TOUCHPAD_MODE -> {
                nativeTouchEventMode = false
                updateTouchscreenTrackpadRef?.invoke(true)
            }
            ACTION_TOGGLE_KEYBOARD -> toggleSoftKeyboardRunnerRef?.invoke()
            ACTION_EXIT_PROGRAM -> pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_F4)
            ACTION_SCREENSHOT -> pressKey(KeyEvent.KEYCODE_SYSRQ)
            ACTION_SCREENSHOT_WIN -> pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_SYSRQ)
            ACTION_TASK_MANAGER -> pressKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_ESCAPE)
            ACTION_EXPLORER -> pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_E)
            ACTION_COPY -> pressKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_C)
            ACTION_CUT -> pressKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_X)
            ACTION_PASTE -> pressKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_V)
            ACTION_SELECT_ALL -> pressKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_A)
            ACTION_SHOW_DESKTOP -> pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_D)
            ACTION_TASK_VIEW -> pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_TAB)
            ACTION_NEW_DESKTOP -> pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_D)
            ACTION_DELETE_DESKTOP -> pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_F4)
            ACTION_LEFT_DESKTOP -> pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_DPAD_LEFT)
            ACTION_RIGHT_DESKTOP -> pressKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
            ACTION_NVIDIA_OVERFLOW -> pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_Z)
            ACTION_NVIDIA_SCREENSHOT -> pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_F1)
            ACTION_NVIDIA_PHOTO_MODE -> pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_F2)
            ACTION_NVIDIA_GAME_FILTER -> pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_F3)
            ACTION_NVIDIA_INSTANT_REPLAY -> pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_F10)
            ACTION_NVIDIA_RECORD -> pressKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_F10)
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
        this.toggleSoftKeyboardRunnerRef = toggleSoftKeyboardRunner
        this.updateTouchscreenTrackpadRef = updateTouchscreenTrackpad
        this.virtualControllerShowRef = virtualControllerShow

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
            performAttectAction(ACTION_ESC)
            toggleOverflowMenu()
        }
        virtualKeyWin.setOnClickListener {
            performAttectAction(ACTION_WIN)
            toggleOverflowMenu()
        }
        virtualKeySystemMenu.setOnClickListener {
            performAttectAction(ACTION_SYSTEM_MENU)
            toggleOverflowMenu()
        }
        virtualMouseMiddleButton.setOnClickListener {
            performAttectAction(ACTION_MIDDLE_MOUSE)
            toggleOverflowMenu()
        }
        onScreenGamePad.setOnClickListener {
            performAttectAction(ACTION_ONSCREEN_GAMEPAD)
            toggleOverflowMenu()
        }
        touchScreenModeMenu.setOnClickListener {
            performAttectAction(ACTION_TOUCHSCREEN_MODE)
            toggleOverflowMenu()
        }
        touchPadModeMenu.setOnClickListener {
            performAttectAction(ACTION_TOUCHPAD_MODE)
            toggleOverflowMenu()
        }
        toggleSoftKeyboardMenu.setOnClickListener {
            toggleOverflowMenu()
            performAttectAction(ACTION_TOGGLE_KEYBOARD)
        }

        virtualKeyExitProgram.setOnClickListener {
            performAttectAction(ACTION_EXIT_PROGRAM)
            toggleOverflowMenu()
        }
        virtualKeyScreenshot.setOnClickListener {
            performAttectAction(ACTION_SCREENSHOT)
            toggleOverflowMenu()
        }
        virtualKeyScreenshot.setOnLongClickListener {
            performAttectAction(ACTION_SCREENSHOT_WIN)
            toggleOverflowMenu()
            return@setOnLongClickListener true
        }
        virtualKeyTaskManager.setOnClickListener {
            performAttectAction(ACTION_TASK_MANAGER)
            toggleOverflowMenu()
        }
        virtualKeyExplorer.setOnClickListener {
            performAttectAction(ACTION_EXPLORER)
            toggleOverflowMenu()
        }
        virtualKeyCopy.setOnClickListener {
            performAttectAction(ACTION_COPY)
            toggleOverflowMenu()
        }
        virtualKeyCut.setOnClickListener {
            performAttectAction(ACTION_CUT)
            toggleOverflowMenu()
        }
        virtualKeyPaste.setOnClickListener {
            performAttectAction(ACTION_PASTE)
            toggleOverflowMenu()
        }
        virtualKeySelectAll.setOnClickListener {
            performAttectAction(ACTION_SELECT_ALL)
            toggleOverflowMenu()
        }

        virtualKeyShowDesktop.setOnClickListener {
            performAttectAction(ACTION_SHOW_DESKTOP)
            toggleOverflowMenu()
        }
        virtualKeyTaskView.setOnClickListener {
            performAttectAction(ACTION_TASK_VIEW)
            toggleOverflowMenu()
        }
        virtualKeyNewDesktop.setOnClickListener {
            performAttectAction(ACTION_NEW_DESKTOP)
            toggleOverflowMenu()
        }
        virtualKeyDeleteDesktop.setOnClickListener {
            performAttectAction(ACTION_DELETE_DESKTOP)
            toggleOverflowMenu()
        }
        virtualKeyLeftDesktop.setOnClickListener {
            performAttectAction(ACTION_LEFT_DESKTOP)
            toggleOverflowMenu()
        }
        virtualKeyRightDesktop.setOnClickListener {
            performAttectAction(ACTION_RIGHT_DESKTOP)
            toggleOverflowMenu()
        }

        virtualKeyNvidiaOverflow.setOnClickListener {
            performAttectAction(ACTION_NVIDIA_OVERFLOW)
            toggleOverflowMenu()
        }
        virtualKeyNvidiaScreenshot.setOnClickListener {
            performAttectAction(ACTION_NVIDIA_SCREENSHOT)
            toggleOverflowMenu()
        }
        virtualKeyNvidiaPhotoMode.setOnClickListener {
            performAttectAction(ACTION_NVIDIA_PHOTO_MODE)
            toggleOverflowMenu()
        }
        virtualKeyNvidiaGameFilter.setOnClickListener {
            performAttectAction(ACTION_NVIDIA_GAME_FILTER)
            toggleOverflowMenu()
        }
        virtualKeyNvidiaInstantlyRelay.setOnClickListener {
            performAttectAction(ACTION_NVIDIA_INSTANT_REPLAY)
            toggleOverflowMenu()
        }
        virtualKeyNvidiaRecord.setOnClickListener {
            performAttectAction(ACTION_NVIDIA_RECORD)
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
        val handler = customOverflowMenuHandler
        if (handler != null && handler.invoke(isShowOverflowMenu)) {
            overflowLayout.visibility = View.GONE
            floatButton.visibility = if (isShowOverflowMenu) View.GONE else View.VISIBLE
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

        const val ACTION_ESC = 1
        const val ACTION_WIN = 2
        const val ACTION_SYSTEM_MENU = 3
        const val ACTION_MIDDLE_MOUSE = 4
        const val ACTION_ONSCREEN_GAMEPAD = 5
        const val ACTION_TOUCHSCREEN_MODE = 6
        const val ACTION_TOUCHPAD_MODE = 7
        const val ACTION_TOGGLE_KEYBOARD = 8
        const val ACTION_EXIT_PROGRAM = 9
        const val ACTION_SCREENSHOT = 10
        const val ACTION_SCREENSHOT_WIN = 11
        const val ACTION_TASK_MANAGER = 12
        const val ACTION_EXPLORER = 13
        const val ACTION_COPY = 14
        const val ACTION_CUT = 15
        const val ACTION_PASTE = 16
        const val ACTION_SELECT_ALL = 17
        const val ACTION_SHOW_DESKTOP = 18
        const val ACTION_TASK_VIEW = 19
        const val ACTION_NEW_DESKTOP = 20
        const val ACTION_DELETE_DESKTOP = 21
        const val ACTION_LEFT_DESKTOP = 22
        const val ACTION_RIGHT_DESKTOP = 23
        const val ACTION_NVIDIA_OVERFLOW = 24
        const val ACTION_NVIDIA_SCREENSHOT = 25
        const val ACTION_NVIDIA_PHOTO_MODE = 26
        const val ACTION_NVIDIA_GAME_FILTER = 27
        const val ACTION_NVIDIA_INSTANT_REPLAY = 28
        const val ACTION_NVIDIA_RECORD = 29
    }
}
