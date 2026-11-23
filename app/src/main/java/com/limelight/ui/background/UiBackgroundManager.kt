package com.limelight.ui.background

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.exifinterface.media.ExifInterface
import com.limelight.LimeLog
import com.limelight.preferences.PreferenceConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Immutable snapshot of the active user-configured background & Liquid Glass preferences.
 * Observed reactively by both Compose Liquid Glass screens and traditional XML M3 screens.
 */
data class UiBackgroundState(
    val uiStyle: String = PreferenceConfiguration.DEFAULT_UI_STYLE,
    val liquidGlassStyle: String = PreferenceConfiguration.DEFAULT_LIQUID_GLASS_STYLE,
    val glassBlurDp: Int = PreferenceConfiguration.DEFAULT_GLASS_BLUR,
    val glassRefractionAmountDp: Int = PreferenceConfiguration.DEFAULT_GLASS_REFRACTION_AMOUNT,
    val glassRefractionHeightDp: Int = PreferenceConfiguration.DEFAULT_GLASS_REFRACTION_HEIGHT,
    val glassChromaticAberration: Boolean = PreferenceConfiguration.DEFAULT_GLASS_CHROMATIC_ABERRATION,
    val glassDepthEffect: Boolean = PreferenceConfiguration.DEFAULT_GLASS_DEPTH_EFFECT,
    val glassSurfaceOpacity: Int = PreferenceConfiguration.DEFAULT_GLASS_SURFACE_OPACITY,
    val glassHighlightIntensity: Int = PreferenceConfiguration.DEFAULT_GLASS_HIGHLIGHT_INTENSITY,
    val glassVibrancy: Boolean = PreferenceConfiguration.DEFAULT_GLASS_VIBRANCY,
    val themeMode: String = PreferenceConfiguration.DEFAULT_THEME_MODE,
    val pureBlackTheme: Boolean = false,
    val backgroundSource: String = PreferenceConfiguration.DEFAULT_UI_BACKGROUND_SOURCE,
    val backgroundOpacity: Int = PreferenceConfiguration.DEFAULT_UI_BACKGROUND_OPACITY,
    val tintMode: String = PreferenceConfiguration.DEFAULT_UI_BACKGROUND_TINT_MODE,
    val tintOpacity: Int = PreferenceConfiguration.DEFAULT_UI_BACKGROUND_TINT_OPACITY,
    val blurDp: Int = PreferenceConfiguration.DEFAULT_UI_BACKGROUND_BLUR,
    val hasCustomImage: Boolean = false,
    val imageVersion: Long = 0L,
    val previewOpacity: Int? = null,
    val previewTintOpacity: Int? = null,
    val previewBlurDp: Int? = null,
    val previewGlassBlurDp: Int? = null,
    val previewGlassRefractionAmountDp: Int? = null,
    val previewGlassRefractionHeightDp: Int? = null,
    val previewGlassSurfaceOpacity: Int? = null,
    val previewGlassHighlightIntensity: Int? = null
) {
    val effectiveOpacity: Int get() = (previewOpacity ?: backgroundOpacity).coerceIn(0, 100)
    val effectiveTintOpacity: Int get() = (previewTintOpacity ?: tintOpacity).coerceIn(0, 90)
    val effectiveBlurDp: Int get() = (previewBlurDp ?: blurDp).coerceIn(0, 24)
    val effectiveGlassBlurDp: Int get() = (previewGlassBlurDp ?: glassBlurDp).coerceIn(0, 32)
    val effectiveGlassRefractionAmountDp: Int get() = (previewGlassRefractionAmountDp ?: glassRefractionAmountDp).coerceIn(0, 48)
    val effectiveGlassRefractionHeightDp: Int get() = (previewGlassRefractionHeightDp ?: glassRefractionHeightDp).coerceIn(0, 28)
    val effectiveGlassSurfaceOpacity: Int get() = (previewGlassSurfaceOpacity ?: glassSurfaceOpacity).coerceIn(5, 75)
    val effectiveGlassHighlightIntensity: Int get() = (previewGlassHighlightIntensity ?: glassHighlightIntensity).coerceIn(0, 100)

    val isLiquidGlass: Boolean
        get() = uiStyle == PreferenceConfiguration.UI_STYLE_LIQUID_GLASS

    val isCustomImageActive: Boolean
        get() = backgroundSource == PreferenceConfiguration.UI_BACKGROUND_SOURCE_CUSTOM && hasCustomImage

    val isWallpaperActive: Boolean
        get() = backgroundSource == PreferenceConfiguration.UI_BACKGROUND_SOURCE_WALLPAPER
}

/**
 * Centralized manager for custom background image persistence, safe downsampling + EXIF rotation,
 * phone wallpaper handling (zero-permission FLAG_SHOW_WALLPAPER on Android 13+, bitmap fallback on <=12),
 * color tinting, opacity, blur, and live settings preview.
 */
object UiBackgroundManager {
    private const val CUSTOM_BG_FILENAME = "ui_background_custom.webp"
    private const val MAX_IMAGE_DIMENSION_PX = 2160
    private const val BG_HOST_TAG = "moonlight_zenith_bg_layer"

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(UiBackgroundState())
    val state: StateFlow<UiBackgroundState> = _state.asStateFlow()

    @Volatile
    private var cachedCustomBitmap: Bitmap? = null

    @Volatile
    private var cachedCustomBitmapVersion: Long = -1L

    @Volatile
    private var cachedWallpaperBitmap: Bitmap? = null

    @Volatile
    private var wallpaperBitmapAttempted: Boolean = false

    @Volatile
    private var initialized: Boolean = false

    @JvmStatic
    fun getCustomImageFile(context: Context): File =
        File(context.applicationContext.filesDir, CUSTOM_BG_FILENAME)

    @JvmStatic
    fun hasCustomImage(context: Context): Boolean {
        val f = getCustomImageFile(context)
        return f.exists() && f.length() > 0L
    }

    @JvmStatic
    fun refreshFromPreferences(context: Context) {
        val appCtx = context.applicationContext
        val prefs = PreferenceConfiguration.readPreferences(appCtx)
        val hasImg = hasCustomImage(appCtx)
        val prev = _state.value
        initialized = true
        _state.value = UiBackgroundState(
            uiStyle = prefs.uiStyle ?: PreferenceConfiguration.DEFAULT_UI_STYLE,
            liquidGlassStyle = prefs.liquidGlassStyle ?: PreferenceConfiguration.DEFAULT_LIQUID_GLASS_STYLE,
            glassBlurDp = prefs.glassBlurDp,
            glassRefractionAmountDp = prefs.glassRefractionAmountDp,
            glassRefractionHeightDp = prefs.glassRefractionHeightDp,
            glassChromaticAberration = prefs.glassChromaticAberration,
            glassDepthEffect = prefs.glassDepthEffect,
            glassSurfaceOpacity = prefs.glassSurfaceOpacity,
            glassHighlightIntensity = prefs.glassHighlightIntensity,
            glassVibrancy = prefs.glassVibrancy,
            themeMode = prefs.themeMode ?: PreferenceConfiguration.DEFAULT_THEME_MODE,
            pureBlackTheme = prefs.pureBlackTheme,
            backgroundSource = prefs.uiBackgroundSource ?: PreferenceConfiguration.DEFAULT_UI_BACKGROUND_SOURCE,
            backgroundOpacity = prefs.uiBackgroundOpacity,
            tintMode = prefs.uiBackgroundTintMode ?: PreferenceConfiguration.DEFAULT_UI_BACKGROUND_TINT_MODE,
            tintOpacity = prefs.uiBackgroundTintOpacity,
            blurDp = prefs.uiBackgroundBlur,
            hasCustomImage = hasImg,
            imageVersion = prev.imageVersion,
            previewOpacity = prev.previewOpacity,
            previewTintOpacity = prev.previewTintOpacity,
            previewBlurDp = prev.previewBlurDp,
            previewGlassBlurDp = prev.previewGlassBlurDp,
            previewGlassRefractionAmountDp = prev.previewGlassRefractionAmountDp,
            previewGlassRefractionHeightDp = prev.previewGlassRefractionHeightDp,
            previewGlassSurfaceOpacity = prev.previewGlassSurfaceOpacity,
            previewGlassHighlightIntensity = prev.previewGlassHighlightIntensity
        )
    }

    @JvmStatic
    fun setLivePreview(opacity: Int? = null, tintOpacity: Int? = null, blurDp: Int? = null) {
        val cur = _state.value
        _state.value = cur.copy(
            previewOpacity = opacity ?: cur.previewOpacity,
            previewTintOpacity = tintOpacity ?: cur.previewTintOpacity,
            previewBlurDp = blurDp ?: cur.previewBlurDp
        )
    }

    @JvmStatic
    fun setGlassLivePreview(
        glassBlurDp: Int? = null,
        refractionAmountDp: Int? = null,
        refractionHeightDp: Int? = null,
        surfaceOpacity: Int? = null,
        highlightIntensity: Int? = null
    ) {
        val cur = _state.value
        _state.value = cur.copy(
            previewGlassBlurDp = glassBlurDp ?: cur.previewGlassBlurDp,
            previewGlassRefractionAmountDp = refractionAmountDp ?: cur.previewGlassRefractionAmountDp,
            previewGlassRefractionHeightDp = refractionHeightDp ?: cur.previewGlassRefractionHeightDp,
            previewGlassSurfaceOpacity = surfaceOpacity ?: cur.previewGlassSurfaceOpacity,
            previewGlassHighlightIntensity = highlightIntensity ?: cur.previewGlassHighlightIntensity
        )
    }

    @JvmStatic
    fun clearLivePreview() {
        val cur = _state.value
        if (cur.previewOpacity != null ||
            cur.previewTintOpacity != null ||
            cur.previewBlurDp != null ||
            cur.previewGlassBlurDp != null ||
            cur.previewGlassRefractionAmountDp != null ||
            cur.previewGlassRefractionHeightDp != null ||
            cur.previewGlassSurfaceOpacity != null ||
            cur.previewGlassHighlightIntensity != null
        ) {
            _state.value = cur.copy(
                previewOpacity = null,
                previewTintOpacity = null,
                previewBlurDp = null,
                previewGlassBlurDp = null,
                previewGlassRefractionAmountDp = null,
                previewGlassRefractionHeightDp = null,
                previewGlassSurfaceOpacity = null,
                previewGlassHighlightIntensity = null
            )
        }
    }

    /**
     * Applies one of the built-in Liquid Glass presets ("frosted", "clear", "tinted")
     * to SharedPreferences so all 8 optical parameter controls stay synchronized.
     */
    @JvmStatic
    fun applyGlassPresetToPreferences(context: Context, preset: String) {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val editor = prefs.edit()
        when (preset) {
            "clear" -> {
                editor.putInt(PreferenceConfiguration.GLASS_BLUR_PREF_STRING, 8)
                editor.putInt(PreferenceConfiguration.GLASS_REFRACTION_AMOUNT_PREF_STRING, 28)
                editor.putInt(PreferenceConfiguration.GLASS_REFRACTION_HEIGHT_PREF_STRING, 14)
                editor.putBoolean(PreferenceConfiguration.GLASS_CHROMATIC_ABERRATION_PREF_STRING, true)
                editor.putBoolean(PreferenceConfiguration.GLASS_DEPTH_EFFECT_PREF_STRING, true)
                editor.putInt(PreferenceConfiguration.GLASS_SURFACE_OPACITY_PREF_STRING, 22)
                editor.putInt(PreferenceConfiguration.GLASS_HIGHLIGHT_INTENSITY_PREF_STRING, 85)
                editor.putBoolean(PreferenceConfiguration.GLASS_VIBRANCY_PREF_STRING, true)
            }
            "tinted" -> {
                editor.putInt(PreferenceConfiguration.GLASS_BLUR_PREF_STRING, 16)
                editor.putInt(PreferenceConfiguration.GLASS_REFRACTION_AMOUNT_PREF_STRING, 20)
                editor.putInt(PreferenceConfiguration.GLASS_REFRACTION_HEIGHT_PREF_STRING, 12)
                editor.putBoolean(PreferenceConfiguration.GLASS_CHROMATIC_ABERRATION_PREF_STRING, true)
                editor.putBoolean(PreferenceConfiguration.GLASS_DEPTH_EFFECT_PREF_STRING, false)
                editor.putInt(PreferenceConfiguration.GLASS_SURFACE_OPACITY_PREF_STRING, 34)
                editor.putInt(PreferenceConfiguration.GLASS_HIGHLIGHT_INTENSITY_PREF_STRING, 72)
                editor.putBoolean(PreferenceConfiguration.GLASS_VIBRANCY_PREF_STRING, true)
            }
            "frosted" -> {
                editor.putInt(PreferenceConfiguration.GLASS_BLUR_PREF_STRING, 16)
                editor.putInt(PreferenceConfiguration.GLASS_REFRACTION_AMOUNT_PREF_STRING, 18)
                editor.putInt(PreferenceConfiguration.GLASS_REFRACTION_HEIGHT_PREF_STRING, 11)
                editor.putBoolean(PreferenceConfiguration.GLASS_CHROMATIC_ABERRATION_PREF_STRING, true)
                editor.putBoolean(PreferenceConfiguration.GLASS_DEPTH_EFFECT_PREF_STRING, false)
                editor.putInt(PreferenceConfiguration.GLASS_SURFACE_OPACITY_PREF_STRING, 38)
                editor.putInt(PreferenceConfiguration.GLASS_HIGHLIGHT_INTENSITY_PREF_STRING, 75)
                editor.putBoolean(PreferenceConfiguration.GLASS_VIBRANCY_PREF_STRING, true)
            }
            else -> return
        }
        editor.apply()
        refreshFromPreferences(context)
    }

    /**
     * Imports a user-picked image URI on a background thread, normalizes EXIF rotation and size
     * (max 2160px long edge), compresses to WebP inside app internal storage, and updates state.
     */
    @JvmStatic
    fun importCustomImage(context: Context, uri: Uri, onComplete: (Boolean) -> Unit) {
        val appCtx = context.applicationContext
        ioExecutor.execute {
            val success = try {
                val decoded = decodeSampledAndRotatedBitmap(appCtx, uri)
                if (decoded != null) {
                    val outFile = getCustomImageFile(appCtx)
                    val tmpFile = File(appCtx.filesDir, "$CUSTOM_BG_FILENAME.tmp")
                    FileOutputStream(tmpFile).use { fos ->
                        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP
                        }
                        decoded.compress(format, 88, fos)
                        fos.flush()
                    }
                    if (outFile.exists()) {
                        outFile.delete()
                    }
                    tmpFile.renameTo(outFile)

                    val newVersion = System.currentTimeMillis()
                    cachedCustomBitmap = decoded
                    cachedCustomBitmapVersion = newVersion

                    val prefs = PreferenceConfiguration.readPreferences(appCtx)
                    _state.value = _state.value.copy(
                        backgroundSource = prefs.uiBackgroundSource ?: PreferenceConfiguration.UI_BACKGROUND_SOURCE_CUSTOM,
                        hasCustomImage = true,
                        imageVersion = newVersion
                    )
                    true
                } else {
                    false
                }
            } catch (t: Throwable) {
                LimeLog.warning("Failed to import custom UI background image: ${t.message}")
                false
            }
            mainHandler.post { onComplete(success) }
        }
    }

    @JvmStatic
    fun clearCustomImage(context: Context) {
        val appCtx = context.applicationContext
        val file = getCustomImageFile(appCtx)
        if (file.exists()) {
            file.delete()
        }
        cachedCustomBitmap = null
        cachedCustomBitmapVersion = -1L
        _state.value = _state.value.copy(
            hasCustomImage = false,
            imageVersion = System.currentTimeMillis()
        )
    }

    @JvmStatic
    fun loadCustomBitmapSync(context: Context): Bitmap? {
        val curVersion = _state.value.imageVersion
        val cached = cachedCustomBitmap
        if (cached != null && !cached.isRecycled && cachedCustomBitmapVersion == curVersion) {
            return cached
        }
        val file = getCustomImageFile(context)
        if (!file.exists() || file.length() <= 0L) {
            return null
        }
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
            if (bmp != null) {
                cachedCustomBitmap = bmp
                cachedCustomBitmapVersion = curVersion
            }
            bmp
        } catch (t: Throwable) {
            LimeLog.warning("Failed to decode cached custom background: ${t.message}")
            null
        }
    }

    /**
     * On Android 12 and below, if the app happens to have wallpaper read access, attempts to obtain
     * the static wallpaper bitmap so even wallpaper mode can participate in full in-app lens refraction.
     * On Android 13+ (API 33+), Android blocks WallpaperManager.getDrawable() with SecurityException,
     * so we never crash and instead use zero-permission FLAG_SHOW_WALLPAPER!
     */
    @JvmStatic
    fun tryLoadWallpaperBitmap(context: Context): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return null
        }
        if (wallpaperBitmapAttempted) {
            val cached = cachedWallpaperBitmap
            return if (cached != null && !cached.isRecycled) cached else null
        }
        wallpaperBitmapAttempted = true
        return try {
            val wm = WallpaperManager.getInstance(context.applicationContext)
            if (wm.wallpaperInfo != null) {
                // Live wallpaper active — use FLAG_SHOW_WALLPAPER so live animations keep playing!
                return null
            }
            val drawable = wm.drawable
            val bmp = (drawable as? BitmapDrawable)?.bitmap
            if (bmp != null && !bmp.isRecycled) {
                cachedWallpaperBitmap = bmp
                bmp
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Returns true when we should use Android's native FLAG_SHOW_WALLPAPER window mode
     * (always on Android 13+ when wallpaper background is chosen, or whenever a live wallpaper is set).
     */
    @JvmStatic
    fun shouldUseWindowWallpaperFlag(context: Context, bgState: UiBackgroundState = _state.value): Boolean {
        if (!bgState.isWallpaperActive) return false
        return tryLoadWallpaperBitmap(context) == null
    }

    /**
     * Resolves the ARGB tint color (with full alpha; caller multiplies by effectiveTintOpacity)
     * or null when tintMode is "none".
     */
    @JvmStatic
    @ColorInt
    fun resolveTintColorInt(
        tintMode: String,
        @ColorInt themeSurfaceColor: Int,
        @ColorInt themePrimaryColor: Int
    ): Int? {
        return when (tintMode) {
            "none" -> null
            "theme_surface" -> Color.rgb(
                Color.red(themeSurfaceColor),
                Color.green(themeSurfaceColor),
                Color.blue(themeSurfaceColor)
            )
            "theme_primary" -> Color.rgb(
                Color.red(themePrimaryColor),
                Color.green(themePrimaryColor),
                Color.blue(themePrimaryColor)
            )
            "pure_black" -> Color.BLACK
            "indigo" -> 0xFF1A1F4B.toInt()
            "ocean" -> 0xFF0A2438.toInt()
            "emerald" -> 0xFF0A2E22.toInt()
            "amber" -> 0xFF3A2308.toInt()
            "crimson" -> 0xFF3B0D18.toInt()
            else -> Color.rgb(
                Color.red(themeSurfaceColor),
                Color.green(themeSurfaceColor),
                Color.blue(themeSurfaceColor)
            )
        }
    }

    /**
     * Applies or clears FLAG_SHOW_WALLPAPER and optional Android 12+ window background blur
     * for non-streaming UI activities.
     */
    @JvmStatic
    fun applyWindowWallpaperFlags(activity: Activity, @ColorInt surfaceColor: Int) {
        val window = activity.window ?: return
        if (!initialized) {
            refreshFromPreferences(activity)
        }
        val bgState = _state.value
        val useWallpaperFlag = shouldUseWindowWallpaperFlag(activity, bgState)

        if (useWallpaperFlag) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.decorView.setBackgroundColor(Color.TRANSPARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val blurPx = (bgState.effectiveBlurDp * activity.resources.displayMetrics.density).roundToInt()
                    if (blurPx > 0) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.setBackgroundBlurRadius(blurPx)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.setBackgroundBlurRadius(0)
                    }
                } catch (_: Throwable) {
                    // Some OEMs disable window blur behind
                }
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.setBackgroundBlurRadius(0)
                } catch (_: Throwable) {
                }
            }
            window.setBackgroundDrawable(ColorDrawable(surfaceColor))
            window.decorView.setBackgroundColor(surfaceColor)
        }
    }

    /**
     * Attaches or updates a custom image / tinted wallpaper background layer behind standard
     * Material 3 XML Activity layouts (when UI style is Material You Expressive and user picked
     * a custom image or phone wallpaper).
     */
    @JvmStatic
    fun applyBackgroundToXmlActivity(
        activity: Activity,
        @ColorInt themeSurfaceColor: Int,
        @ColorInt themePrimaryColor: Int
    ) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val bgState = _state.value
        val existing = content.findViewWithTag<View>(BG_HOST_TAG)

        val wantsCustomOrWallpaper = bgState.isCustomImageActive || bgState.isWallpaperActive
        if (!wantsCustomOrWallpaper) {
            if (existing != null) {
                content.removeView(existing)
            }
            content.setBackgroundColor(themeSurfaceColor)
            val firstChild = if (content.childCount > 0) content.getChildAt(0) else null
            firstChild?.setBackgroundColor(themeSurfaceColor)
            return
        }

        // Make content & root layout transparent so the background layer shines through cleanly
        content.setBackgroundColor(Color.TRANSPARENT)
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child.tag != BG_HOST_TAG) {
                child.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        val bgContainer = (existing as? FrameLayout) ?: FrameLayout(activity).apply {
            tag = BG_HOST_TAG
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
            content.addView(this, 0)
        }
        bgContainer.removeAllViews()

        val opacityFraction = bgState.effectiveOpacity / 100f
        val useWindowWallpaper = shouldUseWindowWallpaperFlag(activity, bgState)

        // Base dark surface behind custom bitmap (so lowering opacity blends smoothly into theme surface)
        if (!useWindowWallpaper) {
            bgContainer.setBackgroundColor(themeSurfaceColor)
            val bitmap = if (bgState.isCustomImageActive) {
                loadCustomBitmapSync(activity)
            } else {
                tryLoadWallpaperBitmap(activity)
            }
            if (bitmap != null && !bitmap.isRecycled) {
                val imgView = ImageView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(bitmap)
                    alpha = opacityFraction
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && bgState.effectiveBlurDp > 0) {
                        val radiusPx = bgState.effectiveBlurDp * activity.resources.displayMetrics.density
                        setRenderEffect(
                            RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                        )
                    }
                }
                bgContainer.addView(imgView)
            }
        } else {
            // Live system wallpaper via FLAG_SHOW_WALLPAPER: inverse opacity dims the live wallpaper cleanly
            val dimAlpha = ((1f - opacityFraction) * 255f).roundToInt().coerceIn(0, 255)
            val dimColor = ColorUtils.setAlphaComponent(themeSurfaceColor, dimAlpha)
            bgContainer.setBackgroundColor(dimColor)
        }

        // Optional color tint scrim overlay
        val tintBase = resolveTintColorInt(bgState.tintMode, themeSurfaceColor, themePrimaryColor)
        if (tintBase != null && bgState.effectiveTintOpacity > 0) {
            val tintAlpha = ((bgState.effectiveTintOpacity / 100f) * 255f).roundToInt().coerceIn(0, 255)
            val scrim = View(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(ColorUtils.setAlphaComponent(tintBase, tintAlpha))
            }
            bgContainer.addView(scrim)
        }
    }

    private fun decodeSampledAndRotatedBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver

        // Pass 1: Read bounds only
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: return null

        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        var inSampleSize = 1
        val maxDim = max(srcW, srcH)
        while ((maxDim / inSampleSize) > MAX_IMAGE_DIMENSION_PX * 2) {
            inSampleSize *= 2
        }

        // Pass 2: Decode downsampled bitmap
        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        // Pass 3: Read EXIF orientation
        val orientation = try {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }

        val currentMax = max(rawBitmap.width, rawBitmap.height)
        if (currentMax > MAX_IMAGE_DIMENSION_PX) {
            val scale = MAX_IMAGE_DIMENSION_PX.toFloat() / currentMax.toFloat()
            matrix.postScale(scale, scale)
        }

        return if (matrix.isIdentity) {
            rawBitmap
        } else {
            val transformed = Bitmap.createBitmap(
                rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
            )
            if (transformed !== rawBitmap) {
                rawBitmap.recycle()
            }
            transformed
        }
    }
}
