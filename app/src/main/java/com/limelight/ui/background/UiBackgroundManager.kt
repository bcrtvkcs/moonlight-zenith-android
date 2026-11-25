package com.limelight.ui.background

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.PixelFormat
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
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Immutable snapshot of the active user-configured background & Liquid Glass preferences.
 * Observed reactively by both Compose Liquid Glass screens and traditional XML M3 screens.
 */
data class UiBackgroundState(
    val uiStyle: String = PreferenceConfiguration.DEFAULT_UI_STYLE,
    val settingsNavStyle: String = PreferenceConfiguration.DEFAULT_SETTINGS_NAV_STYLE,
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
    val tintMode: String = PreferenceConfiguration.DEFAULT_UI_BACKGROUND_TINT_MODE,
    val tintOpacity: Int = PreferenceConfiguration.DEFAULT_UI_BACKGROUND_TINT_OPACITY,
    val blurDp: Int = PreferenceConfiguration.DEFAULT_UI_BACKGROUND_BLUR,
    val hasCustomImage: Boolean = false,
    val imageVersion: Long = 0L,
    val blurredVersion: Long = 0L,
    val previewTintOpacity: Int? = null,
    val previewBlurDp: Int? = null,
    val previewGlassBlurDp: Int? = null,
    val previewGlassRefractionAmountDp: Int? = null,
    val previewGlassRefractionHeightDp: Int? = null,
    val previewGlassSurfaceOpacity: Int? = null,
    val previewGlassHighlightIntensity: Int? = null
) {
    val effectiveTintOpacity: Int get() = (previewTintOpacity ?: tintOpacity).coerceIn(0, 90)
    val effectiveBlurDp: Int get() = (previewBlurDp ?: blurDp).coerceIn(0, 24)
    val effectiveGlassBlurDp: Int get() = (previewGlassBlurDp ?: glassBlurDp).coerceIn(0, 32)
    val effectiveGlassRefractionAmountDp: Int get() = (previewGlassRefractionAmountDp ?: glassRefractionAmountDp).coerceIn(0, 72)
    val effectiveGlassRefractionHeightDp: Int get() = (previewGlassRefractionHeightDp ?: glassRefractionHeightDp).coerceIn(0, 40)
    val effectiveGlassSurfaceOpacity: Int get() = (previewGlassSurfaceOpacity ?: glassSurfaceOpacity).coerceIn(5, 75)
    val effectiveGlassHighlightIntensity: Int get() = (previewGlassHighlightIntensity ?: glassHighlightIntensity).coerceIn(0, 100)

    val isLiquidGlass: Boolean
        get() = uiStyle == PreferenceConfiguration.UI_STYLE_LIQUID_GLASS

    val isFloatingBottomBarNav: Boolean
        get() = settingsNavStyle == PreferenceConfiguration.SETTINGS_NAV_STYLE_FLOATING_BOTTOM_BAR

    val isCustomImageActive: Boolean
        get() = backgroundSource == PreferenceConfiguration.UI_BACKGROUND_SOURCE_CUSTOM && hasCustomImage

    val isWallpaperActive: Boolean
        get() = backgroundSource == PreferenceConfiguration.UI_BACKGROUND_SOURCE_WALLPAPER
}

/**
 * Centralized manager for custom background image persistence (supporting local gallery + Google Photos
 * cloud media picker via single-stream copy + API 28+ ImageDecoder), pre-computed static background
 * blur bitmaps (0.00ms GPU blur cost per frame on API 23+), phone wallpaper handling
 * (zero-permission FLAG_SHOW_WALLPAPER on Android 13+, cached static wallpaper via getWallpaperId
 * on Android <=12), color tinting, and live settings preview.
 */
object UiBackgroundManager {
    private const val CUSTOM_BG_FILENAME = "ui_background_custom.webp"
    private const val BLURRED_BG_FILENAME = "ui_background_blurred.webp"
    private const val BLURRED_META_FILENAME = "ui_background_blurred.meta"
    private const val MAX_IMAGE_DIMENSION_PX = 2160
    private const val COMMITTED_BLUR_MAX_DIM_PX = 720
    private const val PREVIEW_BLUR_MAX_DIM_PX = 360
    private const val BG_HOST_TAG = "moonlight_zenith_bg_layer"

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val previewBlurGen = AtomicInteger(0)
    private val committedBlurGen = AtomicInteger(0)

    private val _state = MutableStateFlow(UiBackgroundState())
    val state: StateFlow<UiBackgroundState> = _state.asStateFlow()

    @Volatile
    private var cachedCustomBitmap: Bitmap? = null

    @Volatile
    private var cachedCustomBitmapVersion: Long = -1L

    @Volatile
    private var cachedBlurredBitmap: Bitmap? = null

    @Volatile
    private var cachedBlurredMetaKey: String? = null

    @Volatile
    private var cachedPreviewBlurredBitmap: Bitmap? = null

    @Volatile
    private var cachedPreviewBlurDp: Int = -1

    @Volatile
    private var cachedWallpaperBitmap: Bitmap? = null

    @Volatile
    private var cachedWallpaperId: Int = Int.MIN_VALUE

    @Volatile
    private var wallpaperBitmapAttempted: Boolean = false

    @Volatile
    private var cachedWallpaperBlurredBitmap: Bitmap? = null

    @Volatile
    private var cachedWallpaperBlurKey: String? = null

    @Volatile
    private var initialized: Boolean = false

    @Volatile
    private var xmlBlurRefreshCallback: Runnable? = null

    private fun notifyBlurVersionUpdated() {
        val cur = _state.value
        _state.value = cur.copy(blurredVersion = cur.blurredVersion + 1L)
        xmlBlurRefreshCallback?.run()
    }

    @JvmStatic
    fun getCustomImageFile(context: Context): File =
        File(context.applicationContext.filesDir, CUSTOM_BG_FILENAME)

    private fun getBlurredImageFile(context: Context): File =
        File(context.applicationContext.filesDir, BLURRED_BG_FILENAME)

    private fun getBlurredMetaFile(context: Context): File =
        File(context.applicationContext.filesDir, BLURRED_META_FILENAME)

    private fun computeCustomBlurMetaKey(context: Context, blurDp: Int): String? {
        val srcFile = getCustomImageFile(context)
        if (!srcFile.exists() || srcFile.length() <= 0L) return null
        return "${srcFile.lastModified()}_${srcFile.length()}_$blurDp"
    }

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
        val newBlurDp = prefs.uiBackgroundBlur.coerceIn(0, 24)
        val nextState = UiBackgroundState(
            uiStyle = prefs.uiStyle ?: PreferenceConfiguration.DEFAULT_UI_STYLE,
            settingsNavStyle = prefs.settingsNavStyle ?: PreferenceConfiguration.DEFAULT_SETTINGS_NAV_STYLE,
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
            tintMode = prefs.uiBackgroundTintMode ?: PreferenceConfiguration.DEFAULT_UI_BACKGROUND_TINT_MODE,
            tintOpacity = prefs.uiBackgroundTintOpacity,
            blurDp = newBlurDp,
            hasCustomImage = hasImg,
            imageVersion = prev.imageVersion,
            blurredVersion = prev.blurredVersion,
            previewTintOpacity = prev.previewTintOpacity,
            previewBlurDp = prev.previewBlurDp,
            previewGlassBlurDp = prev.previewGlassBlurDp,
            previewGlassRefractionAmountDp = prev.previewGlassRefractionAmountDp,
            previewGlassRefractionHeightDp = prev.previewGlassRefractionHeightDp,
            previewGlassSurfaceOpacity = prev.previewGlassSurfaceOpacity,
            previewGlassHighlightIntensity = prev.previewGlassHighlightIntensity
        )
        _state.value = nextState

        if (nextState.isCustomImageActive) {
            if (newBlurDp <= 0) {
                clearCommittedBlurFilesAsync(appCtx)
            } else {
                ensureCommittedBlurBitmapAsync(appCtx, newBlurDp)
            }
        }
    }

    @JvmOverloads
    @JvmStatic
    fun setLivePreview(
        context: Context,
        tintOpacity: Int? = null,
        blurDp: Int? = null,
        onBlurReady: Runnable? = null
    ) {
        if (onBlurReady != null) {
            xmlBlurRefreshCallback = onBlurReady
        }
        val cur = _state.value
        _state.value = cur.copy(
            previewTintOpacity = tintOpacity ?: cur.previewTintOpacity,
            previewBlurDp = blurDp ?: cur.previewBlurDp
        )
        if (blurDp != null) {
            schedulePreviewBlurAsync(context.applicationContext, blurDp.coerceIn(0, 24))
        }
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
        previewBlurGen.incrementAndGet()
        val hadPreviewBlur = cachedPreviewBlurredBitmap != null || cachedPreviewBlurDp != -1
        cachedPreviewBlurredBitmap = null
        cachedPreviewBlurDp = -1

        val cur = _state.value
        if (hadPreviewBlur ||
            cur.previewTintOpacity != null ||
            cur.previewBlurDp != null ||
            cur.previewGlassBlurDp != null ||
            cur.previewGlassRefractionAmountDp != null ||
            cur.previewGlassRefractionHeightDp != null ||
            cur.previewGlassSurfaceOpacity != null ||
            cur.previewGlassHighlightIntensity != null
        ) {
            _state.value = cur.copy(
                previewTintOpacity = null,
                previewBlurDp = null,
                previewGlassBlurDp = null,
                previewGlassRefractionAmountDp = null,
                previewGlassRefractionHeightDp = null,
                previewGlassSurfaceOpacity = null,
                previewGlassHighlightIntensity = null,
                blurredVersion = if (hadPreviewBlur) cur.blurredVersion + 1L else cur.blurredVersion
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
                editor.putInt(PreferenceConfiguration.GLASS_BLUR_PREF_STRING, 3)
                editor.putInt(PreferenceConfiguration.GLASS_REFRACTION_AMOUNT_PREF_STRING, 36)
                editor.putInt(PreferenceConfiguration.GLASS_REFRACTION_HEIGHT_PREF_STRING, 20)
                editor.putBoolean(PreferenceConfiguration.GLASS_CHROMATIC_ABERRATION_PREF_STRING, true)
                editor.putBoolean(PreferenceConfiguration.GLASS_DEPTH_EFFECT_PREF_STRING, true)
                editor.putInt(PreferenceConfiguration.GLASS_SURFACE_OPACITY_PREF_STRING, 10)
                editor.putInt(PreferenceConfiguration.GLASS_HIGHLIGHT_INTENSITY_PREF_STRING, 88)
                editor.putBoolean(PreferenceConfiguration.GLASS_VIBRANCY_PREF_STRING, true)
            }
            "tinted" -> {
                editor.putInt(PreferenceConfiguration.GLASS_BLUR_PREF_STRING, 10)
                editor.putInt(PreferenceConfiguration.GLASS_REFRACTION_AMOUNT_PREF_STRING, 28)
                editor.putInt(PreferenceConfiguration.GLASS_REFRACTION_HEIGHT_PREF_STRING, 18)
                editor.putBoolean(PreferenceConfiguration.GLASS_CHROMATIC_ABERRATION_PREF_STRING, true)
                editor.putBoolean(PreferenceConfiguration.GLASS_DEPTH_EFFECT_PREF_STRING, false)
                editor.putInt(PreferenceConfiguration.GLASS_SURFACE_OPACITY_PREF_STRING, 22)
                editor.putInt(PreferenceConfiguration.GLASS_HIGHLIGHT_INTENSITY_PREF_STRING, 78)
                editor.putBoolean(PreferenceConfiguration.GLASS_VIBRANCY_PREF_STRING, true)
            }
            "frosted" -> {
                editor.putInt(PreferenceConfiguration.GLASS_BLUR_PREF_STRING, 10)
                editor.putInt(PreferenceConfiguration.GLASS_REFRACTION_AMOUNT_PREF_STRING, 26)
                editor.putInt(PreferenceConfiguration.GLASS_REFRACTION_HEIGHT_PREF_STRING, 16)
                editor.putBoolean(PreferenceConfiguration.GLASS_CHROMATIC_ABERRATION_PREF_STRING, true)
                editor.putBoolean(PreferenceConfiguration.GLASS_DEPTH_EFFECT_PREF_STRING, false)
                editor.putInt(PreferenceConfiguration.GLASS_SURFACE_OPACITY_PREF_STRING, 24)
                editor.putInt(PreferenceConfiguration.GLASS_HIGHLIGHT_INTENSITY_PREF_STRING, 78)
                editor.putBoolean(PreferenceConfiguration.GLASS_VIBRANCY_PREF_STRING, true)
            }
            else -> return
        }
        editor.apply()
        refreshFromPreferences(context)
    }

    /**
     * Imports a user-picked image URI (local gallery OR Google Photos cloud media) on a background
     * thread using a single sequential stream copy to cacheDir followed by API 28+ ImageDecoder
     * (handling JPEG, PNG, WebP, HEIC, AVIF, UltraHDR & EXIF rotation cleanly) or BitmapFactory
     * fallback on older versions. Also immediately pre-computes the static blurred cache if blur > 0.
     */
    @JvmStatic
    fun importCustomImage(context: Context, uri: Uri, onComplete: (Boolean) -> Unit) {
        val appCtx = context.applicationContext
        ioExecutor.execute {
            val stagedFile = File(appCtx.cacheDir, "ui_bg_import_${System.nanoTime()}.tmp")
            val success = try {
                val copied = copyUriToTempFile(appCtx, uri, stagedFile)
                if (!copied || !stagedFile.exists() || stagedFile.length() <= 0L) {
                    LimeLog.warning("Failed to copy selected image stream from URI: $uri")
                    false
                } else {
                    val decoded = decodeSampledAndRotatedFile(stagedFile)
                    if (decoded != null) {
                        val outFile = getCustomImageFile(appCtx)
                        val tmpOut = File(appCtx.filesDir, "$CUSTOM_BG_FILENAME.tmp")
                        writeBitmapAsWebp(decoded, tmpOut, 90)
                        if (outFile.exists()) {
                            outFile.delete()
                        }
                        if (!tmpOut.renameTo(outFile)) {
                            tmpOut.copyTo(outFile, overwrite = true)
                            tmpOut.delete()
                        }

                        val newVersion = System.currentTimeMillis()
                        cachedCustomBitmap = decoded
                        cachedCustomBitmapVersion = newVersion
                        cachedBlurredBitmap = null
                        cachedBlurredMetaKey = null
                        cachedPreviewBlurredBitmap = null
                        cachedPreviewBlurDp = -1

                        val prefs = PreferenceConfiguration.readPreferences(appCtx)
                        val blurDp = prefs.uiBackgroundBlur.coerceIn(0, 24)
                        if (blurDp > 0) {
                            generateAndSaveCommittedBlurSync(appCtx, decoded, blurDp)
                        } else {
                            deleteBlurredFilesSync(appCtx)
                        }

                        val prev = _state.value
                        _state.value = prev.copy(
                            backgroundSource = PreferenceConfiguration.UI_BACKGROUND_SOURCE_CUSTOM,
                            hasCustomImage = true,
                            imageVersion = newVersion,
                            blurredVersion = prev.blurredVersion + 1L,
                            tintMode = prefs.uiBackgroundTintMode ?: PreferenceConfiguration.DEFAULT_UI_BACKGROUND_TINT_MODE,
                            tintOpacity = prefs.uiBackgroundTintOpacity,
                            blurDp = blurDp
                        )
                        true
                    } else {
                        LimeLog.warning("Decoded bitmap was null for custom background URI: $uri")
                        false
                    }
                }
            } catch (t: Throwable) {
                LimeLog.warning("Failed to import custom UI background image ($uri): ${t.message}")
                false
            } finally {
                try {
                    if (stagedFile.exists()) {
                        stagedFile.delete()
                    }
                } catch (_: Throwable) {
                }
            }
            mainHandler.post { onComplete(success) }
        }
    }

    private fun writeBitmapAsWebp(bitmap: Bitmap, target: File, quality: Int) {
        FileOutputStream(target).use { fos ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, quality, fos)
            fos.flush()
        }
    }

    private fun deleteBlurredFilesSync(context: Context) {
        try {
            val bf = getBlurredImageFile(context)
            if (bf.exists()) bf.delete()
            val mf = getBlurredMetaFile(context)
            if (mf.exists()) mf.delete()
        } catch (_: Throwable) {
        }
        cachedBlurredBitmap = null
        cachedBlurredMetaKey = null
    }

    private fun clearCommittedBlurFilesAsync(context: Context) {
        committedBlurGen.incrementAndGet()
        if (cachedBlurredBitmap == null && cachedBlurredMetaKey == null && !getBlurredImageFile(context).exists()) {
            return
        }
        ioExecutor.execute {
            deleteBlurredFilesSync(context)
            mainHandler.post {
                val cur = _state.value
                _state.value = cur.copy(blurredVersion = cur.blurredVersion + 1L)
            }
        }
    }

    private fun generateAndSaveCommittedBlurSync(context: Context, source: Bitmap, blurDp: Int): Bitmap? {
        val metaKey = computeCustomBlurMetaKey(context, blurDp) ?: return null
        val blurred = createPreBlurredBitmap(source, blurDp, COMMITTED_BLUR_MAX_DIM_PX)
        try {
            val blurFile = getBlurredImageFile(context)
            val tmpBlur = File(context.filesDir, "$BLURRED_BG_FILENAME.tmp")
            writeBitmapAsWebp(blurred, tmpBlur, 88)
            if (blurFile.exists()) blurFile.delete()
            if (!tmpBlur.renameTo(blurFile)) {
                tmpBlur.copyTo(blurFile, overwrite = true)
                tmpBlur.delete()
            }
            getBlurredMetaFile(context).writeText(metaKey)
        } catch (t: Throwable) {
            LimeLog.warning("Failed to persist pre-blurred background cache: ${t.message}")
        }
        cachedBlurredBitmap = blurred
        cachedBlurredMetaKey = metaKey
        return blurred
    }

    private fun ensureCommittedBlurBitmapAsync(context: Context, blurDp: Int) {
        if (blurDp <= 0) return
        val expectedKey = computeCustomBlurMetaKey(context, blurDp) ?: return
        val existing = cachedBlurredBitmap
        if (existing != null && !existing.isRecycled && cachedBlurredMetaKey == expectedKey) {
            return
        }
        val token = committedBlurGen.incrementAndGet()
        ioExecutor.execute {
            if (committedBlurGen.get() != token) return@execute
            val metaFile = getBlurredMetaFile(context)
            val blurFile = getBlurredImageFile(context)
            val diskMatch = try {
                metaFile.exists() && blurFile.exists() && blurFile.length() > 0L &&
                    metaFile.readText().trim() == expectedKey
            } catch (_: Throwable) {
                false
            }

            val result = if (diskMatch) {
                try {
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeFile(blurFile.absolutePath, opts)?.also { bmp ->
                        cachedBlurredBitmap = bmp
                        cachedBlurredMetaKey = expectedKey
                    }
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            } ?: run {
                val src = loadCustomBitmapSync(context)
                if (src != null && !src.isRecycled) {
                    generateAndSaveCommittedBlurSync(context, src, blurDp)
                } else {
                    null
                }
            }

        if (result != null && committedBlurGen.get() == token) {
                mainHandler.post {
                    notifyBlurVersionUpdated()
                }
            }
        }
    }

    private fun schedulePreviewBlurAsync(context: Context, blurDp: Int) {
        val token = previewBlurGen.incrementAndGet()
        if (blurDp <= 0) {
            cachedPreviewBlurredBitmap = null
            cachedPreviewBlurDp = 0
            val cur = _state.value
            _state.value = cur.copy(blurredVersion = cur.blurredVersion + 1L)
            return
        }
        ioExecutor.execute {
            if (previewBlurGen.get() != token) return@execute
            val curState = _state.value
            val src = when {
                curState.isCustomImageActive -> loadCustomBitmapSync(context)
                curState.isWallpaperActive -> tryLoadWallpaperBitmap(context)
                else -> null
            } ?: return@execute
            if (src.isRecycled || previewBlurGen.get() != token) return@execute

            val previewBmp = createPreBlurredBitmap(src, blurDp, PREVIEW_BLUR_MAX_DIM_PX)
            if (previewBlurGen.get() == token) {
                cachedPreviewBlurredBitmap = previewBmp
                cachedPreviewBlurDp = blurDp
                mainHandler.post {
                    if (previewBlurGen.get() == token) {
                        notifyBlurVersionUpdated()
                    }
                }
            }
        }
    }

    /**
     * Ultra-fast O(W*H) 3-pass separable box blur (Gaussian-equivalent) on a downscaled copy of [src].
     * Produces a smooth static pre-blurred Bitmap in ~2–6 ms on a background thread so rendering the
     * background costs 0.00 ms of GPU blur per frame on all Android versions (API 23+).
     */
    private fun createPreBlurredBitmap(src: Bitmap, blurDp: Int, maxDimPx: Int): Bitmap {
        val safeBlur = blurDp.coerceIn(1, 24)
        val srcW = src.width.coerceAtLeast(1)
        val srcH = src.height.coerceAtLeast(1)
        val maxSide = max(srcW, srcH)
        val scale = if (maxSide > maxDimPx) maxDimPx.toFloat() / maxSide.toFloat() else 1f
        val dstW = (srcW * scale).roundToInt().coerceAtLeast(2)
        val dstH = (srcH * scale).roundToInt().coerceAtLeast(2)

        val scaled = if (dstW != srcW || dstH != srcH) {
            Bitmap.createScaledBitmap(src, dstW, dstH, true)
        } else {
            src.copy(Bitmap.Config.ARGB_8888, true)
        }
        val argb = if (scaled.config != Bitmap.Config.ARGB_8888) {
            scaled.copy(Bitmap.Config.ARGB_8888, true).also {
                if (scaled !== src) scaled.recycle()
            }
        } else {
            scaled
        }

        val minSide = min(dstW, dstH).toFloat()
        val radius = (safeBlur * (minSide / 360f) * 1.15f).roundToInt().coerceIn(1, 48)
        val pixels = IntArray(dstW * dstH)
        val temp = IntArray(dstW * dstH)
        argb.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH)

        repeat(3) {
            boxBlurHorizontal(pixels, temp, dstW, dstH, radius)
            boxBlurVertical(temp, pixels, dstW, dstH, radius)
        }

        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
        if (argb !== src) {
            argb.recycle()
        }
        return out
    }

    private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val div = radius * 2 + 1
        val lastX = w - 1
        for (y in 0 until h) {
            val rowOff = y * w
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (i in -radius..radius) {
                val px = src[rowOff + i.coerceIn(0, lastX)]
                rSum += (px shr 16) and 0xFF
                gSum += (px shr 8) and 0xFF
                bSum += px and 0xFF
            }
            for (x in 0 until w) {
                dst[rowOff + x] = (0xFF shl 24) or
                    ((rSum / div) shl 16) or
                    ((gSum / div) shl 8) or
                    (bSum / div)
                val addIdx = rowOff + (x + radius + 1).coerceAtMost(lastX)
                val remIdx = rowOff + (x - radius).coerceAtLeast(0)
                val addPx = src[addIdx]
                val remPx = src[remIdx]
                rSum += ((addPx shr 16) and 0xFF) - ((remPx shr 16) and 0xFF)
                gSum += ((addPx shr 8) and 0xFF) - ((remPx shr 8) and 0xFF)
                bSum += (addPx and 0xFF) - (remPx and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val div = radius * 2 + 1
        val lastY = h - 1
        for (x in 0 until w) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (i in -radius..radius) {
                val px = src[i.coerceIn(0, lastY) * w + x]
                rSum += (px shr 16) and 0xFF
                gSum += (px shr 8) and 0xFF
                bSum += px and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or
                    ((rSum / div) shl 16) or
                    ((gSum / div) shl 8) or
                    (bSum / div)
                val addRow = (y + radius + 1).coerceAtMost(lastY) * w + x
                val remRow = (y - radius).coerceAtLeast(0) * w + x
                val addPx = src[addRow]
                val remPx = src[remRow]
                rSum += ((addPx shr 16) and 0xFF) - ((remPx shr 16) and 0xFF)
                gSum += ((addPx shr 8) and 0xFF) - ((remPx shr 8) and 0xFF)
                bSum += (addPx and 0xFF) - (remPx and 0xFF)
            }
        }
    }

    private fun copyUriToTempFile(context: Context, uri: Uri, target: File): Boolean {
        val resolver = context.contentResolver
        val stream = try {
            resolver.openInputStream(uri)
        } catch (_: Throwable) {
            null
        } ?: try {
            resolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
        } catch (_: Throwable) {
            null
        } ?: return false

        stream.use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
        return target.exists() && target.length() > 0L
    }

    @JvmStatic
    fun clearCustomImage(context: Context) {
        val appCtx = context.applicationContext
        val file = getCustomImageFile(appCtx)
        if (file.exists()) {
            file.delete()
        }
        deleteBlurredFilesSync(appCtx)
        cachedCustomBitmap = null
        cachedCustomBitmapVersion = -1L
        cachedPreviewBlurredBitmap = null
        cachedPreviewBlurDp = -1
        val cur = _state.value
        _state.value = cur.copy(
            hasCustomImage = false,
            imageVersion = System.currentTimeMillis(),
            blurredVersion = cur.blurredVersion + 1L
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
     * Returns the ready-to-draw static background Bitmap for the active [bgState] — either the crisp
     * original custom/wallpaper image (`blurDp == 0`) or the pre-computed static blurred Bitmap
     * (`blurDp > 0`) so rendering the background incurs ZERO per-frame GPU blur cost.
     */
    @JvmStatic
    fun loadDisplayBitmapSync(context: Context, bgState: UiBackgroundState = _state.value): Bitmap? {
        val targetBlur = bgState.effectiveBlurDp
        return when {
            bgState.isCustomImageActive -> {
                if (targetBlur <= 0) {
                    loadCustomBitmapSync(context)
                } else if (bgState.previewBlurDp != null) {
                    val previewBmp = cachedPreviewBlurredBitmap
                    if (previewBmp != null && !previewBmp.isRecycled && cachedPreviewBlurDp == targetBlur) {
                        previewBmp
                    } else {
                        schedulePreviewBlurAsync(context.applicationContext, targetBlur)
                        previewBmp?.takeIf { !it.isRecycled }
                            ?: cachedBlurredBitmap?.takeIf { !it.isRecycled }
                            ?: loadCustomBitmapSync(context)
                    }
                } else {
                    val expectedKey = computeCustomBlurMetaKey(context, targetBlur)
                    val mem = cachedBlurredBitmap
                    if (mem != null && !mem.isRecycled && expectedKey != null && cachedBlurredMetaKey == expectedKey) {
                        mem
                    } else {
                        val metaFile = getBlurredMetaFile(context)
                        val blurFile = getBlurredImageFile(context)
                        val diskBmp = if (expectedKey != null && metaFile.exists() && blurFile.exists() && blurFile.length() > 0L) {
                            try {
                                if (metaFile.readText().trim() == expectedKey) {
                                    val opts = BitmapFactory.Options().apply {
                                        inPreferredConfig = Bitmap.Config.ARGB_8888
                                    }
                                    BitmapFactory.decodeFile(blurFile.absolutePath, opts)?.also { bmp ->
                                        cachedBlurredBitmap = bmp
                                        cachedBlurredMetaKey = expectedKey
                                    }
                                } else null
                            } catch (_: Throwable) {
                                null
                            }
                        } else null

                        if (diskBmp != null) {
                            diskBmp
                        } else {
                            ensureCommittedBlurBitmapAsync(context.applicationContext, targetBlur)
                            mem?.takeIf { !it.isRecycled } ?: loadCustomBitmapSync(context)
                        }
                    }
                }
            }
            bgState.isWallpaperActive -> {
                val wp = tryLoadWallpaperBitmap(context) ?: return null
                if (targetBlur <= 0) {
                    wp
                } else if (bgState.previewBlurDp != null) {
                    val previewBmp = cachedPreviewBlurredBitmap
                    if (previewBmp != null && !previewBmp.isRecycled && cachedPreviewBlurDp == targetBlur) {
                        previewBmp
                    } else {
                        schedulePreviewBlurAsync(context.applicationContext, targetBlur)
                        previewBmp?.takeIf { !it.isRecycled } ?: wp
                    }
                } else {
                    val wpKey = "${cachedWallpaperId}_$targetBlur"
                    val cachedWpBlur = cachedWallpaperBlurredBitmap
                    if (cachedWpBlur != null && !cachedWpBlur.isRecycled && cachedWallpaperBlurKey == wpKey) {
                        cachedWpBlur
                    } else {
                        ioExecutor.execute {
                            val blurred = createPreBlurredBitmap(wp, targetBlur, COMMITTED_BLUR_MAX_DIM_PX)
                            cachedWallpaperBlurredBitmap = blurred
                            cachedWallpaperBlurKey = wpKey
                            mainHandler.post {
                                val cur = _state.value
                                _state.value = cur.copy(blurredVersion = cur.blurredVersion + 1L)
                            }
                        }
                        cachedWpBlur?.takeIf { !it.isRecycled } ?: wp
                    }
                }
            }
            else -> null
        }
    }

    /**
     * On Android 12 and below (API <= 32), if the app has wallpaper read access, obtains and caches
     * the static wallpaper bitmap keyed by WallpaperManager.getWallpaperId(FLAG_SYSTEM) so changes
     * are detected automatically. On Android 13+ (API 33+), returns null and uses zero-permission
     * FLAG_SHOW_WALLPAPER cleanly without requesting sensitive storage permissions.
     */
    @JvmStatic
    fun tryLoadWallpaperBitmap(context: Context): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return null
        }
        return try {
            val wm = WallpaperManager.getInstance(context.applicationContext)
            if (wm.wallpaperInfo != null) {
                cachedWallpaperBitmap = null
                return null
            }
            val currentId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            } else {
                0
            }
            if (wallpaperBitmapAttempted && currentId == cachedWallpaperId) {
                val cached = cachedWallpaperBitmap
                return if (cached != null && !cached.isRecycled) cached else null
            }
            wallpaperBitmapAttempted = true
            cachedWallpaperId = currentId
            cachedWallpaperBlurredBitmap = null
            cachedWallpaperBlurKey = null

            val drawable = wm.drawable
            val bmp = (drawable as? BitmapDrawable)?.bitmap
            if (bmp != null && !bmp.isRecycled) {
                cachedWallpaperBitmap = bmp
                bmp
            } else {
                cachedWallpaperBitmap = null
                null
            }
        } catch (_: SecurityException) {
            cachedWallpaperBitmap = null
            null
        } catch (_: Throwable) {
            cachedWallpaperBitmap = null
            null
        }
    }

    @JvmStatic
    fun shouldUseWindowWallpaperFlag(context: Context, bgState: UiBackgroundState = _state.value): Boolean {
        if (!bgState.isWallpaperActive) return false
        return tryLoadWallpaperBitmap(context) == null
    }

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
     * Applies or clears FLAG_SHOW_WALLPAPER and optional Android 12+ window blur-behind
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
            window.setFormat(PixelFormat.TRANSLUCENT)
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.decorView.setBackgroundColor(Color.TRANSPARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val blurPx = (bgState.effectiveBlurDp * activity.resources.displayMetrics.density)
                        .roundToInt()
                        .coerceIn(0, 150)
                    val lp = window.attributes
                    if (blurPx > 0) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        lp.blurBehindRadius = blurPx
                        window.attributes = lp
                        window.setBackgroundBlurRadius(0)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        if (lp.blurBehindRadius != 0) {
                            lp.blurBehindRadius = 0
                            window.attributes = lp
                        }
                        window.setBackgroundBlurRadius(0)
                    }
                } catch (_: Throwable) {
                    // Some OEMs disable window blur behind
                }
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setFormat(PixelFormat.OPAQUE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val lp = window.attributes
                    if (lp.blurBehindRadius != 0) {
                        lp.blurBehindRadius = 0
                        window.attributes = lp
                    }
                    window.setBackgroundBlurRadius(0)
                } catch (_: Throwable) {
                }
            }
            window.setBackgroundDrawable(ColorDrawable(surfaceColor))
            window.decorView.setBackgroundColor(surfaceColor)
        }
    }

    /**
     * Attaches or updates a static pre-blurred custom image / tinted wallpaper background layer
     * behind standard Material 3 XML Activity layouts (0.00ms per-frame GPU blur cost).
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

        val useWindowWallpaper = shouldUseWindowWallpaperFlag(activity, bgState)

        if (!useWindowWallpaper) {
            val actRef = WeakReference(activity)
            xmlBlurRefreshCallback = Runnable {
                val act = actRef.get()
                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    applyBackgroundToXmlActivity(act, themeSurfaceColor, themePrimaryColor)
                }
            }
            bgContainer.setBackgroundColor(themeSurfaceColor)
            val bitmap = loadDisplayBitmapSync(activity, bgState)
            if (bitmap != null && !bitmap.isRecycled) {
                val imgView = ImageView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(bitmap)
                }
                bgContainer.addView(imgView)
            }
        } else {
            bgContainer.setBackgroundColor(Color.TRANSPARENT)
        }

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

    private fun decodeSampledAndRotatedFile(file: File): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(file)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    val srcW = info.size.width
                    val srcH = info.size.height
                    val maxDim = max(srcW, srcH)
                    if (maxDim > MAX_IMAGE_DIMENSION_PX && srcW > 0 && srcH > 0) {
                        val scale = MAX_IMAGE_DIMENSION_PX.toFloat() / maxDim.toFloat()
                        val targetW = (srcW * scale).roundToInt().coerceAtLeast(1)
                        val targetH = (srcH * scale).roundToInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetW, targetH)
                    }
                }
            } catch (t: Throwable) {
                LimeLog.warning("ImageDecoder failed, falling back to BitmapFactory: ${t.message}")
            }
        }

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        var inSampleSize = 1
        val maxDim = max(srcW, srcH)
        while ((maxDim / inSampleSize) > MAX_IMAGE_DIMENSION_PX * 2) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
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
