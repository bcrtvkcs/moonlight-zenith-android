# Changelog

All notable changes to **Moonlight Zenith Android** are documented in this file.

---

## [v3.1.0-zenith] - 2026-11-26

### ⚡ Liquid Glass Performance Overhaul, Static Pre-Blurred Backgrounds & 1:1 Synchronized Tab Animations
- **Perf (Static Pre-Computed Background Blur — `0.00 ms` GPU Blur/Frame):**
  - Replaced live per-frame GPU `BlurEffect` / `ImageView.setRenderEffect` on custom background images with a one-time background-thread static blurred `Bitmap` cache (`UiBackgroundManager.kt`, 3-pass separable box blur saved to `ui_background_blurred.webp` with metadata validation).
  - Keeps the master image (`ui_background_custom.webp`) untouched so changing blur strength never accumulates quality loss, provides fast downscaled live preview while dragging the slider, and brings custom background blur support to **all Android versions (API 23+)**.
- **Perf (Eliminated Stacked Multi-Pass Blurs & Offscreen Shadow FBOs):**
  - Removed the Liquid Glass FPS limiter (`list_glass_fps`) and offscreen `framePacedGlassCache` / `FramePacedLayerBackdrop` wrappers, restoring native zero-overhead `rememberLayerBackdrop()` / `.layerBackdrop(...)` everywhere.
  - Added lightweight `nestedGlassSurface` for buttons and rows nested inside already-blurred Liquid Glass containers (`GlassTopBar` icon buttons, `PcView` landscape nav rail buttons, `GlassModalDialog`, `GlassBottomSheetOverlay`, `GlassMenuOverlay`, `GlassDialogPortal`, `GlassWallpaperNoticeCard`, `AddComputerGlassScreen`), cutting dialog open/close glass passes from **11 down to 1**.
  - Optimized `LiquidGlassSwitch` and `LiquidGlassSlider` to use zero `LayerBackdrop` recording, AGSL shaders, or shadow FBOs while idle, activating full optical lens refraction instantly on touch/drag/toggle.
  - Eliminated triple-stacked full-screen blur in `LiquidFloatingBottomBar` (inner sliding lens now applies single-pass `lens()` directly over `dockContentBackdrop`), disabled heavy per-card offscreen `ShadowNode` FBOs on scrolling `PcCard` / `AppCard` grids, reduced chrome/sheet shadow radii (`12–16 dp`), and removed redundant second Gaussian blur on the 28 `AttectGlassMenuHost` shortcut buttons while preserving crisp single-pass optical lens refraction.
- **Fix & Feat (Tab Drag Stall Fix & 1:1 Synchronized Tab ↔ Page Animations):**
  - Fixed active-tab dragging stalling mid-transition in `LiquidFloatingBottomBar` by replacing repeated `animateScrollToPage` cancellations with a single conflated `snapshotFlow` driving `pagerState.scrollToPage(page, offsetFraction)` **1:1 with the finger**, snapping smoothly on release via a single tracked `Job`.
  - Bound the sliding lens bubble, tab icon/label color interpolation, and bar scroll directly to `pagerState.currentPage + pagerState.currentPageOffsetFraction` (eliminating secondary `animateFloatAsState` lag).
  - Rebuilt `GlassPillTabBar` (Tabbed mode) as a single Liquid Glass capsule with a 1:1 width-morphing sliding active lens pill (replacing 7 independent `drawBackdrop` pills).
  - Upgraded Material You Expressive's Floating Bottom Bar (`activity_stream_settings.xml`, `StreamSettings.java`) with a sliding M3 pill indicator and `onPageScrolled` 1:1 color & scroll synchronization.
- **Polish (Background Controls & Turkish Localization):**
  - Removed the redundant **Background opacity** (`seekbar_ui_background_opacity`) setting and its extra background `alpha` compositing layer; custom images & wallpapers render cleanly at full visibility beneath the optional color tint overlay.
  - Refined Turkish localization (`values-tr/strings.xml`): **`Arka plan renk tonu`**, **`Renk tonu yoğunluğu`**, and **`Renk Tonu Yok (Orijinal Renkler)`**.
  - Added `WallpaperManager.getWallpaperId(FLAG_SYSTEM)` change detection for cached static wallpapers on Android ≤12 while keeping zero-permission `FLAG_SHOW_WALLPAPER` on Android 13+.

---

## [v3.0.2-zenith] - 2026-11-25

### ⚡ True GPU-Saving Liquid Glass FPS Pacing & Floating Bottom Bar Polish
- **Fix & Perf (Genuine GPU Shader Frame Pacing for `list_glass_fps`):**
  - Replaced backdrop-only rate limiting with an offscreen GPU texture cache (`Modifier.framePacedGlassCache` using `GraphicsLayer` + `CompositingStrategy.Offscreen`) across `liquidGlassSurface`, `LiquidGlassSlider`, `LiquidGlassSwitch`, `LiquidFloatingBottomBar`, `AttectGlassMenuHost`, and `GlassDialogPortal`.
  - When a refresh rate cap (`15`, `30`, `60`, `90`, `120`, or `144` FPS) is selected, `DrawBackdropNode` and its GPU AGSL blur, lens refraction, vibrancy, and highlight shaders now execute **strictly at most N times per second**, skipping `drawContent()` completely on intermediate frames and blitting the cached offscreen texture instead (saving ~75% GPU glass shader work at 30 FPS and ~87.5% at 15 FPS on 120Hz displays).
  - Separated glass background surfaces from foreground interactive content (`GlassPreferenceItemRow`, `LiquidFloatingBottomBar`, `GlassModalDialog`, `GlassBottomSheetOverlay`, `AttectGlassMenuHost`) so scrolling, touch, text, switches, and sliders stay at full native display refresh rate while the Liquid Glass shaders pace cleanly to the chosen FPS.
  - Selecting `144+ (Unlimited)` bypasses the offscreen cache completely with zero extra VRAM or blit overhead.
  - Added defensive default value initialization (`"0"`) for `list_glass_fps` in `StreamSettings` on upgrades from earlier builds.
- **Polish (Floating Bottom Bar Active-Tab Swell):**
  - Refined the Liquid Glass floating bottom bar active-tab hold scale from `+7%` (`1.07x`) to a more balanced **`+5%` (`1.05x`)**.

---

## [v3.0.1-zenith] - 2026-11-25

### 🫧 Floating Bottom Navigation Bar Proportions & Elastic Liquid Glass Physics
- **Polish (20% Larger Default Floating Bottom Navigation Bar):**
  - Scaled up default floating bottom navigation bar dimensions by ~20% across both **Liquid Glass** (`LiquidFloatingBottomBar`: `80dp` capsule height, `90dp` tab width, `25dp` icons, `12.5sp` labels) and **Material You Expressive** (`activity_stream_settings.xml`, `StreamSettings.java`: `80dp` card height, `88×66dp` items, `25dp` icons, `12.5sp` labels), with matching `114dp` bottom list clearance.
- **Feat (Active-Tab Hold +7% Swell & Dynamic Horizontal Screen-Position Shift in Liquid Glass):**
  - Pressing and holding the active tab in the Liquid Glass floating bottom bar smoothly scales the entire capsule dock up by **+7%** (`1.07x`) with spring physics and returns it to normal size (`1.0x`) on release.
  - While holding and dragging the active tab, the entire bottom navigation bar dynamically shifts slightly right or left based on the finger's horizontal screen position relative to the horizontal screen center (zero shift at screen center, smooth proportional shift up to `±16dp` toward the screen edges, with automatic edge scrolling across all 7 categories), springing cleanly back to center when released.

---

## [v3.0.0-zenith] - 2026-11-25

### 🫧 Liquid Glass UI (`Kyant0/AndroidLiquidGlass`), Floating Bottom Bar, Custom Tinted Backgrounds & Flexible Audio Output
- **Feat (Dual-Engine Liquid Glass Interface via `io.github.kyant0:backdrop:2.0.1`):**
  - Integrated **Jetpack Compose (BOM `2026.09.00`)**, **Kotlin `2.4.10`** (`org.jetbrains.kotlin.plugin.compose`), and **`io.github.kyant0:backdrop:2.0.1`** (`Kyant0/AndroidLiquidGlass`) while keeping the existing **Material You Expressive** XML UI as the default with zero regressions (minimum SDK raised from `21` to `23` to align with Compose `1.12.1`, preserving `root` flavor `maxSdk 25`).
  - Added an **Interface style** selector (`list_ui_style`: **Material You Expressive** vs. **Liquid Glass**) under **UI Settings**.
  - Implemented a dual-layer `FramePacedLayerBackdrop` + `rememberCombinedBackdrop` rendering architecture (`LiquidGlassTheme.kt`, `LiquidGlassComponents.kt`, `LiquidGlassControls.kt`) with real-time AGSL optical lens refraction (`lens`), chromatic aberration, 3D depth effect, Gaussian backdrop blur, vibrancy, specular rim highlights, and depth shadows on Android 13+, hardware `RenderEffect` blur on Android 12, and a clean translucent fallback on older versions.
  - Built full Liquid Glass Compose screens with D-pad / Android TV focus navigation and edge-to-edge system bar insets across **`PcView`** (`PcViewGlassScreen.kt`), **`AppView`** (`AppViewGlassScreen.kt`), **`StreamSettings`** (`StreamSettingsGlassScreen.kt`), **`AddComputerManually`** (`AddComputerGlassScreen.kt`), animated **`GlassMenuOverlay`** bottom sheets / TV dialogs, and unified animated **`GlassDialogPortal`** (`Dialog`, `SpinnerDialog`, Quit & Delete PC confirmations).
  - Implemented authentic refractive **`LiquidGlassSwitch`** (`LiquidToggle` capsule thumb), **`LiquidGlassSlider`** (`LiquidSlider` magnifying drag capsule that stays active strictly until finger lift), and a zero-latency **`AttectGlassMenuHost`** rendering the in-stream Attect overflow panel and all **28** shortcut buttons in live Liquid Glass without blurring the game stream behind it.
- **Feat (Fine-Grained Liquid Glass Controls, Live Preview & Refresh Rate Limit):**
  - Added 3 synchronized presets (**Frosted Glass**, **Clear Glass**, **Tinted Glass**) plus automatic **Custom Parameters** mode (`list_liquid_glass_style`), an interactive draggable **Live Liquid Glass Preview** card, and **8** real-time optical parameter controls: **Glass blur radius** (`0–32 dp`), **Lens refraction amount** (`0–72 dp`), **Lens refraction height** (`0–40 dp`), **Chromatic aberration**, **3D lens depth effect**, **Glass surface opacity** (`5–75%`), **Specular edge highlight** (`0–100%`), and **Backdrop vibrancy**.
  - Added **Liquid Glass refresh rate (FPS)** (`list_glass_fps`: **`15`**, **`30`**, **`60`**, **`90`**, **`120`**, **`144`**, **`144+ (Unlimited)`**) powered by `FramePacedLayerBackdrop` to pace dynamic backdrop sampling while keeping scrolling and touch at full native display refresh rate.
- **Feat (Optional Settings Navigation Style — Tabbed vs. Floating Bottom Bar):**
  - Added **`list_settings_nav_style`** (*Settings navigation style*: **Tabbed** default vs. **Floating Bottom Bar**) under **UI Settings**, switching live without restarting Settings.
  - Renders a full-capsule sliding-lens Liquid Glass floating dock (`LiquidFloatingBottomBar`) in **Liquid Glass** mode and a pure Material 3 Expressive floating dock (`activity_stream_settings.xml`, `StreamSettings.java`) in **Material You Expressive** mode across all 7 categories with custom vector icons (`ic_nav_*.xml`).
- **Feat (Customizable Tinted Background Image & Phone Wallpaper Support):**
  - Added **`UiBackgroundManager.kt`** supporting **Theme Default** (adaptive ambient aurora mesh), **Custom Image** (modern `ActivityResultContracts.PickVisualMedia` supporting local gallery & Google Photos cloud media + `OpenDocument` fallback, single-stream staging, API 28+ `ImageDecoder` for JPEG/PNG/WebP/HEIC/AVIF/EXIF, and persistent WebP storage), and **Phone Wallpaper** (zero-permission `FLAG_SHOW_WALLPAPER` + Android 12+ `blurBehindRadius`).
  - Added **Background opacity** (`0–100%`), **Background color tint** (9 modes), **Tint intensity** (`0–90%`), and **Background blur** (`0–24 dp`, Android 12+) with live preview across both **Liquid Glass** and **Material You Expressive** modes.
- **Feat (Flexible Multi-Select Audio Output — Client, Host, Both, or Muted):**
  - Replaced the single *Play audio on PC* toggle with a 2-checkbox multi-select **Audio output** preference (`M3AudioOutputPreference`, key `pref_audio_output`) supporting **This device (Client)** and/or **PC (Host)** independently (`Client`, `Host`, `Client + Host`, or `None / Muted`), backed by **`SilentAudioRenderer`** when Client audio is disabled so no `AudioTrack` or system audio focus is taken.
  - Localized all new audio output strings across all **29** language locales (`values*/strings.xml`), and full EN/TR localizations for all UI & Liquid Glass settings.

---

## [v2.2.1-zenith] - 2026-11-23

### 🛠️ Build Toolchain & Native NDK Modernization
- **Chore (Latest Build Toolchain & NDK r30 LTS Upgrade):**
  - Upgraded **Gradle Wrapper** from `9.7.1` to **`9.9.2`** for security fixes and faster incremental Kotlin DSL compilation.
  - Upgraded **Android Gradle Plugin (AGP)** from `9.4.0` to **`9.5.1`**.
  - Upgraded **Android NDK** from `r29` (`29.0.14206865`) to **`r30` LTS (`30.0.16248370`)** across `app/build.gradle` and GitHub Actions CI/release workflows, verified cleanly on device across native `moonlight-core` and `evdev_reader` builds.
  - Upgraded GitHub Actions workflow steps to Node 24-native major releases (`actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v6`, `actions/upload-artifact@v7`, `softprops/action-gh-release@v3`) and switched to the runner's preinstalled Android SDK `cmdline-tools 20.0`.
  - Extended release tag matching (`v*-zenith*`) so pre-release tags (`-alpha`, `-beta`, `-rc`) are automatically published as GitHub Pre-releases.

---

## [v2.2.0-zenith] - 2026-11-23

### ⌨️ Smart Keyboard Mode (Gboard Glide Typing, Suggestions & Autocorrect)
- **Feat (Opt-In Smart Keyboard Mode for On-Screen IMEs):**
  - Added **`checkbox_smart_keyboard`** (*Smart keyboard mode* / *Akıllı klavye modu*) under **Input Settings** (`preferences.xml`, `PreferenceConfiguration.java`, `values/strings.xml`, `values-tr/strings.xml`), disabled by default so raw key-event behavior for games remains untouched unless enabled.
  - Implemented **`StreamInputConnection`** (`app/src/main/java/com/limelight/binding/input/StreamInputConnection.java`), a shadow-buffer `BaseInputConnection` with a real-time diff engine that translates IME text edits (`setComposingText`, `commitText`, `deleteSurroundingText`, `replaceText`, and spacebar cursor swiping via `setSelection`) directly into host cursor moves, backspaces, forward deletes, and keystrokes.
  - Updated **`StreamView`** (`onCheckIsTextEditor()`, `onCreateInputConnection()`) to expose a proper text editor configuration (`TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_AUTO_CORRECT | TYPE_TEXT_FLAG_CAP_SENTENCES | TYPE_TEXT_FLAG_MULTI_LINE`) with `IME_FLAG_NO_EXTRACT_UI | IME_FLAG_NO_FULLSCREEN` so Gboard enables glide typing, predictive suggestions, and autocorrect without covering the landscape stream in a fullscreen extract editor.
  - Refactored text delivery in **`Game.java`** (`sendTextToHost`) to route ASCII letters, digits, spaces, and `HostKeyboardLayoutTranslator` regional characters (TR-Q, DE, FR, ES, IT) directly through the low-latency keyboard VK channel (strictly ordered with backspaces and avoiding `LiSendUtf8TextEvent`'s 50ms flush delay), while batching remaining Unicode/emoji characters into unified UTF-8 sends.
  - Automatically resets the shadow text buffer on stream touch/mouse clicks, physical keyboard input, Enter/Tab/Esc, and IME dismissal (`WindowInsets.Type.ime()`) to prevent host cursor desynchronization.
  - Improved soft keyboard focus and toggle sequencing in **`Game.toggleKeyboard()`** and **`GameAttectActivity.kt`** (`toggleOverflowMenu()` before `toggleSoftKeyboardRunner()`, `restartInput`, and `showSoftInput` / `hideSoftInputFromWindow` on Android 11+).

### 🚀 Automated GitHub Actions CI & Release Pipeline
- **Feat (Cloud APK Builds & Signed Direct Releases):**
  - Added **`.github/workflows/build.yml`** for automated `master`/PR builds and manual `workflow_dispatch` APK builds (`nonRootDebug`, `debug`, `release`, `all`) uploaded directly to Actions Artifacts.
  - Added **`.github/workflows/release.yml`** triggered on `v*-zenith` tag pushes (or manual dispatch) to build both signed **`nonRootRelease`** and **`rootRelease`** APKs, verify signatures via `apksigner`, generate `SHA256SUMS.txt`, extract matching release notes directly from `CHANGELOG.md`, and publish the GitHub Release automatically.
  - Updated **`app/build.gradle`** (`signingConfigs.release`) to seamlessly support both local `local.properties` and CI environment variables (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), with safe default debug signing fallback when secrets are unavailable.
  - Added `*.jks` / `*.keystore` to **`.gitignore`** and enforced LF line endings for `gradlew` via **`.gitattributes`**.

---

## [v2.1.2-zenith] - 2026-11-11

### 🎨 Theme Engine Refinements, Material You System Surfaces & Soft Indigo App Icon
- **Feat (Independent Pure Black AMOLED Setting & 2-Theme Selector):**
  - Separated Pure Black (AMOLED) mode from the theme selector in `StreamSettings` (`preferences.xml`, `arrays.xml`, `PreferenceConfiguration.java`, `UiHelper.java`):
    - **`list_theme_mode`** now presents two core themes: **Material You (`dynamic`)** and **Zenith Indigo (`zenith`)**.
    - Added a dedicated **`checkbox_pure_black_theme`** (*Pure black background (AMOLED)*) switch directly below the theme selector that applies a true `#000000` OLED black surface overlay (`ThemeOverlay.Moonlight.DynamicAMOLED` / `ThemeOverlay.Moonlight.AMOLED`) on top of whichever theme is selected, with automatic migration for existing `dynamic_amoled` and `amoled` preferences.
- **Fix (Material You System Status Bar, Window Background & In-Stream Attect Menu Coloring):**
  - Added `UiHelper.applyWindowSurfaceColors(Activity)` to dynamically resolve `android.R.attr.colorBackground` and `colorSurfaceContainerLowest` after `DynamicColors` and AMOLED overlays are applied, fixing an issue where the system status bar, navigation bar, and window background remained on the static Zenith Indigo color (`#121318`) when **Material You** or **Pure Black (AMOLED)** was selected.
  - Replaced hardcoded Indigo colors in the in-stream Attect floating button (`m3_float_button_bg.xml`), overflow panel (`game_overflow_menu.xml`), and quick-action chips (`m3_overflow_chip_bg.xml`, `m3_overflow_primary_chip_bg.xml`, `m3_overflow_danger_chip_bg.xml`, `m3_overflow_nvidia_chip_bg.xml`) with dynamic Material 3 theme attributes (`?attr/colorSurfaceContainerHigh`, `?attr/colorPrimaryContainer`, `?attr/colorOnPrimaryContainer`, `?attr/colorOutlineVariant`) and applied `UiHelper.applyMaterial3Theme(this)` in `Game.onCreate`.
  - Added complete Turkish localization (`library-attect/src/main/res/values-tr/strings.xml`) for all in-stream floating menu actions.
- **Feat (Soft Indigo-Tinted Moonlight App Icon Everywhere):**
  - Softly tinted the standard Moonlight icon across all 23 PNG assets (`ic_launcher`, `ic_launcher_foreground`, `ic_pc_scut`, `ic_pc_scut_foreground` across `mdpi`–`xxxhdpi`, plus `app_icon`, `atv_banner`, and `ouya_icon`) and vector/color resources (`ic_launcher_background.xml`, `ic_pc_scut_background.xml`, `ic_lime_layer.xml`) with a refined slate-indigo (`#52597A`) background and soft ice-indigo (`#EEF0FF` / `#F0F2FF`) foreground, and removed `<monochrome>` from `ic_launcher.xml` and `ic_pc_scut.xml` so Android 13+ launchers always preserve the soft indigo icon design.

### ⌨️ Keyboard Quote (`'` / `"`) & Punctuation / Shift Synchronization Fixes
- **Fix (Single Quote `'` Typing `i` & Double Quote `"` Typing `é` on Turkish & European Keyboards):**
  - Added missing single-quote (`'`) mapping (`VK_2` `0x32` with `requiresShift = true` on Turkish Q) to `HostKeyboardLayoutTranslator`, fixing the bug where pressing `'` fell through to Android's US `KEYCODE_APOSTROPHE` (`VK_OEM_7` `0xDE`), which types **`i`** on a Windows Turkish Q keyboard.
  - Synchronized host `VK_LSHIFT` (`0x80A0`) and `VK_RSHIFT` (`0x80A1`) state in `Game.handleKeyDown`, `Game.handleKeyUp`, and `Game.handleKeyMultiple`:
    - When `override.requiresShift` is `false` (such as `"` on Turkish Q, which is unshifted `0xC0` on Windows Turkish Q, whereas Android's `Virtual.kcm` sends `VK_LSHIFT` `DOWN` prior to `KEYCODE_APOSTROPHE`), `VK_LSHIFT` (`0x80A0`) is explicitly released before sending the key press so Windows types **`"`** instead of `Shift + 0xC0` (**`é`**).
    - When `override.requiresShift` is `true` and Shift is not already held on the event (such as `'` on Turkish Q), `VK_LSHIFT` (`0x80A0`) is sent before the key down event and released on key up so Windows receives `Shift + 2` (**`'`**).
  - Added `HostKeyboardLayoutTranslator.resolveLayout` so the default `"auto"` keyboard setting automatically resolves to `tr_TR`, `de_DE`, `fr_FR`, `es_ES`, or `it_IT` from `Locale.getDefault().getLanguage()`.
  - Added complete number-row shifted punctuation (`!`, `^`, `+`, `%`, `&`, `/`, `(`, `)`, `=`, `?`, `_`, etc.) and direct UTF-8 injection (`TranslationResult.utf8`) for AltGr symbols (`@`, `#`, `$`, `[`, `]`, `{`, `}`, `\`, `|`, `` ` ``, `~`) across `tr_TR`, `de_DE`, `fr_FR`, `es_ES`, and `it_IT`.

### 🖥️ PC & App Grid Fixes, Reorganized Settings Categories & 29-Language Localization
- **Fix (PC Cards Stuck on `"Refreshing"` / `"Yenileniyor"` State):**
  - Updated `PcView.java` and `PcGridAdapter.java` so `inProgress` is initialized only when `details.state == ComputerDetails.State.UNKNOWN` and is immediately cleared whenever a PC transitions to `ONLINE` or `OFFLINE`, ensuring the loading spinner and `"Refreshing"` label disappear as soon as the host responds.
- **Fix (Desktop & Steam Cover Art Vertical Centering in `CenterContentImageView`):**
  - Enhanced `CenterContentImageView.findContentBounds()` with a dual-pass foreground bounding-box detector that identifies dual-tone silhouette posters (such as Sunshine's default `600x800` **Desktop** and **Steam** covers whose visual content occupies `y=152..534`) even when the background gradient reaches the outer border, mathematically centering the icon and title vertically and horizontally inside the card rectangle while keeping full-bleed multi-color game box art edge-to-edge.
- **Feat (Logical 8-Tab Settings Reorganization & Complete 29-Language Localization):**
  - Reorganized `preferences.xml` and `StreamSettings.java` into 8 logical categories: **Video Settings** (consolidated with HDR, Full Range, and Codec settings), **Audio Settings** (moved PC host audio toggles to Host Settings), **Gamepad Settings**, **Input & Keyboard Settings** (dedicated tab for keyboard layout, touchscreen trackpad, mouse navigation buttons, remote desktop mode, and stylus), **On-Screen Controls Settings**, **Host Settings**, **Interface & Theme Settings** (consolidated theme, Pure Black AMOLED toggle, sensor-driven landscape rotation, small icon mode, and language), and **Advanced Settings**.
  - Completed all missing translations across Turkish (`values-tr`) and all 27 other locale directories (**29 languages total**).

---

## [v2.0.0-zenith] - 2026-11-11

### 🎨 Material Design 3 (Material You) Complete UI Overhaul (`df94739d`)
- **Feat (Material Design 3 Theme Architecture & 3-Mode Theme Engine):**
  - Migrated the entire application from legacy Android 4.x Holo/DeviceDefault themes to **Material Design 3 (`com.google.android.material:material:1.12.0` & `androidx.appcompat:appcompat:1.7.0`)** across all screens, menus, dialogs, cards, switches, and overlays (`Theme.Moonlight.M3`, `Theme.Moonlight.M3.Stream`).
  - Added a configurable **Material 3 Theme Selector** (`list_theme_mode`) in `StreamSettings` (`UiHelper.applyTheme`) with three theme modes:
    - **Material You (`dynamic`):** Android 12+ (`API 31+`) dynamic wallpaper color extraction via `DynamicColors.applyToActivityIfAvailable`.
    - **Zenith Indigo (`zenith`):** Custom Material 3 Dark palette featuring Indigo/Violet primary accents (`#BAC3FF`) and layered tonal surface containers (`#121318`, `#1E1F26`, `#292A32`).
    - **Zenith AMOLED (`amoled`):** Pure black (`#000000`) background theme tailored for OLED screens with deep tonal cards.
- **Feat (Redesigned PC Grid & App Grid Screens with Symmetric Cards & Centered Visuals):**
  - Redesigned `PcView` (`activity_pc_view.xml`, portrait & landscape) and `AppView` (`activity_app_view.xml`) with Material 3 top app bars, tonal icon buttons (`materialIconButtonFilledTonalStyle`) for Back, Help, and Settings actions, a left-aligned `ExtendedFloatingActionButton` (`manuallyAddPc`) for manually adding a PC, and an elevated M3 empty-state discovery card with `CircularProgressIndicator`.
  - Unified the outer `MaterialCardView` dimensions (`174dp` height, `22dp` corner radius), grid column width (`160dp`), spacing (`14dp`), and symmetric horizontal padding (`16dp` start/end) across `pc_grid_item.xml`, `app_grid_item.xml`, `pc_grid_view.xml`, and `app_grid_view.xml` so PC and App cards share identical outer geometry and balanced screen margins.
  - Added `CenterContentImageView` (`com.limelight.ui.CenterContentImageView`) for `AppView` cards (`app_grid_item.xml`, `app_grid_item_small.xml`) to automatically detect foreground visual bounds (such as Sunshine's default **Desktop** and **Steam** box art icons and labels) and center them mathematically both vertically and horizontally inside the card rectangle while preserving full-bleed cover art cropping.
  - Added a `ViewHolder` pattern to `GenericGridAdapter` to eliminate repeated `findViewById` lookups during grid scrolling.
- **Feat (Swipeable Tabbed Settings Screen & Borderless M3 Preference Components):**
  - Redesigned `StreamSettings` (`activity_stream_settings.xml`, `StreamSettings.java`) from a single long scrolling page into a **Material 3 Scrollable Pill TabBar (`TabLayout` + `m3_tab_pill_indicator.xml`) and Swipeable `ViewPager2`** (`TabLayoutMediator`), allowing users to switch between settings categories either by swiping horizontally anywhere on the page surface or by tapping/focusing the pill tabs.
  - Built custom borderless Material 3 preference components (`m3_preference_item.xml`, `m3_preference_row_bg.xml`) with zero list dividers (`android:listDivider="@null"`) and zero card stroke lines:
    - **`M3SwitchPreference`:** Uses `MaterialSwitch` (`m3_preference_widget_switch.xml`) with a fixed `24dp` checkmark/cross thumb icon (`m3_switch_thumb_icon.xml`) and `cachedView` reuse to eliminate layout inflation and animation stutter during scrolling.
    - **`M3ListPreference`:** Displays the active value in a tonal pill badge (`m3_value_badge_bg.xml`) and opens a custom Material 3 single-choice dialog (`m3_dialog_single_choice_item.xml`) that highlights the currently selected option with a `MaterialRadioButton`, tonal container fill (`colorSecondaryContainer`), and trailing check icon (`ic_m3_check.xml`).
    - **`SeekBarPreference` & `ConfirmDeleteOscPreference`:** Migrated to `MaterialAlertDialogBuilder` with a custom M3 slider dialog (`m3_dialog_slider.xml`), live value badge updates, and `cachedView` reuse.
  - Removed the `android:dependency="checkbox_show_onscreen_controls"` lock from On-Screen Controls sub-settings (`checkbox_vibrate_osc`, `checkbox_only_show_L3R3`, `checkbox_show_guide_button`, `seekbar_osc_opacity`) so they remain freely configurable even when always-on OSC is disabled (such as when toggling OSC mid-stream via the floating menu), added uniform disabled-row opacity (`0.42f`) for dependent preferences, and added missing Turkish translations (`title_show_guide_button`, `summary_show_guide_button`).
- **Feat (Adaptive Context Menus, Add Computer Screen & M3 Dialogs):**
  - Created `M3MenuHelper` (`m3_menu_sheet.xml`, `m3_menu_row.xml`) to replace legacy context menus in `PcView` and `AppView` with an **M3 Modal Bottom Sheet** (`BottomSheetDialog`) on touch devices and a **Centered Material 3 Dialog** on TV / D-pad setups, featuring header status badges, semantic icons (`ic_m3_*`), primary action pills, and destructive action styling (`colorErrorContainer`).
  - Redesigned `AddComputerManually` (`activity_add_computer_manually.xml`) with a Material 3 top bar, outlined `TextInputLayout` / `TextInputEditText` address field, and full-width filled `MaterialButton`.
  - Migrated `Dialog` and `SpinnerDialog` (`m3_dialog_spinner.xml`) to `MaterialAlertDialogBuilder` and Material 3 `CircularProgressIndicator`.
- **Feat (In-Stream Floating Menu, HUD Overlays & Sensor-Driven Landscape Orientation):**
  - Redesigned the in-game Attect floating ball and overflow menu (`game_overflow_menu.xml`, `m3_float_button_bg.xml`, `m3_overflow_chip_bg.xml`, `m3_overflow_primary_chip_bg.xml`) and streaming performance/notification HUD overlays (`activity_game.xml`, `m3_perf_overlay_bg.xml`) with Material 3 dark glassmorphic pill surfaces.
  - **Sensor-Driven Landscape / Reverse-Landscape Switching (`checkbox_sensor_landscape`):** Updated `Game.setPreferredOrientationForCurrentDisplay()` to use `ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE` (enabled by default and configurable in `StreamSettings` under Basic Settings), enabling automatic real-time switching between normal landscape and reverse landscape via the device's physical orientation sensor while connecting and streaming—even when Android's system auto-rotate setting is turned off.
- **Chore (`.gitignore`)** (`9ce823fc`):
  - Added Android `.nomedia` tag to `.gitignore`.

---

## [v1.1.0-zenith] - 2026-10-20

### 🆕 Moonlight Zenith Improvements & Fixes
- **Fix (Turkish `ç` / `Ç` & Punctuation Mapping on Windows Hosts)** (`fc413b0e`, `38725e9b`):
  - Fixed a long-standing issue where `ç` and `Ç` would not type on Windows hosts (Sunshine) when the Turkish (`tr_TR`) keyboard layout was selected.
  - Corrected the `HostKeyboardLayoutTranslator` virtual key mapping for `ç`/`Ç` from `VK_OEM_5` (`0xDC`) to `VK_OEM_PERIOD` (`0xBE`, matching US English scancode `0x34` -> Turkish Q `ç`), and aligned `,`/`;` (`0xDC`) and `"`/`é` (`0xC0`).
  - Swallowed synthetic `KEYCODE_ALT_LEFT` / `KEYCODE_ALT_RIGHT` events generated by Android's `Virtual.kcm` (`Alt+C` / `Shift+Alt+C`) on software keyboards (such as Gboard and Hacker's Keyboard) before they reach the host PC, preventing Windows from interpreting them as `Alt` shortcuts or activating the window menu bar (`SC_KEYMENU`).

### 🔄 Upstream Synchronization up to **v12.2** (`97042bc7`, `b48494cb`) while preserving all Zenith features:
- **Android 15, 16 & 16.1 Keyboard Input:**
  - Added support for Android 16.1 native keyboard capture (`ddb674a9`).
  - Added support for newer keycodes introduced in Android 15 and Android 16 (`8974dcda`).
  - Fixed missing `Shift` modifier for the semantic `KEYCODE_PLUS` (`+`) key (`b9c5eddd`).
- **Video Decoding & Latency Optimizations:**
  - Disabled producer throttling on the video surface to reduce frame pacing latency (`6d4c64a5`).
  - Disabled H.264 bitstream constraint and `level_idc` modifications on Android 8.0 (Oreo) and later (`68adf9ec`).
- **Controller & Haptics Improvements:**
  - Fixed controller rumble not working due to a deprecated vibrator API call (`3c6a0d12`).
  - Fixed crashes and ANRs in controller LED handling (`abde6021`).
  - Added Xbox Series S/X controllers (`280454fd`) and 8BitDo controllers (`583f662a`) to the built-in Xbox One USB driver.
  - Synced SDL joystick controller database with upstream (`c2e224eb`).
  - Updated `UiHelper` to avoid failing on Meta Quest devices (`0711e236`).
- **Core Native Libraries & Toolchain Upgrades:**
  - Updated `moonlight-common-c` (`0dc4c4fe`), `libopus` to `1.6.1`, and `OpenSSL` to `4.0.2` (`31b70030`).
  - Updated `compileSdk` to `37`, `targetSdk` to `36` (`4b2221d3`, `9d8b073c`), NDK to `r29` (`1fa0e2a0`), AGP to `9.4.0` (`801dba1b`), Java compatibility to `JDK 17` (`9221a0ca`), and OkHttp to `5.5` (`98c12beb`).
  - Updated jMDNS and BouncyCastle dependencies (`b3a7e32a`) and enabled core library desugaring to fix mDNS host discovery on older Android versions (`4eb24a8d`).
  - Removed obsolete code paths for `< API 21` (`3df0103a`) and resolved Lint warnings (`ad861490`, `8d720748`, `5c0c2390`, `578f38f6`, `3f114ac7`).
- **Translations (Weblate)** (`4dc95b56`, `b55b4b6d`, `cc788e44`..`03bdf30d`):
  - Added new language entries and updated localization strings across Turkish, German, French, Spanish, Italian, Japanese, Korean, Russian, Ukrainian, Dutch, Portuguese (Standard & Brazil), Bulgarian, Hungarian, Indonesian, Latvian, Norwegian Bokmål, Tamil, and Esperanto.

---

## [v1.0.14-zenith] - 2026-06-15
- **Fix (On-Screen Controller Hotplug):** Fixed a bug where toggling the On-Screen Gamepad from the Attect menu would not work if the user had the Gamepad disabled in the Moonlight settings. The application now properly sends a Controller Arrival (Hotplug) event to the host PC when the gamepad is toggled dynamically mid-stream.

## [v1.0.13-zenith] - 2026-06-14
- **Fix (TouchPad Context Switch):** Fixed a critical bug where toggling "TouchPad Mode" from the floating menu mid-stream would not correctly switch the touch tracking mode, resolving the offset bug for users who did not have Absolute Mouse Mode enabled.

## [v1.0.12-zenith] - 2026-06-14
- **Fix (TouchPad Offset with Absolute Mouse Mode):** Refactored touch input coordinates to enforce offset adjustments correctly in `AbsoluteTouchContext` to fix the issue where TouchPad mode would jump to the right side of the screen when `absoluteMouseMode` was enabled.

## [v1.0.11-zenith] - 2026-06-14
- **Fix (Coordinate Precision):** Updated the touch handler to accumulate unreported sub-pixel fractional values for movement so that slow, precise cursor movements aren't lost in translation when rounding.

## [v1.0.10-zenith] - 2026-06-14
- **Fix (TouchPad Mode Precision):** Addressed a bug where TouchPad mouse coordinates were jumping erratically because relative movement was calculated using raw coordinates instead of scaled floating-point values.

## [v1.0.9-zenith] - 2026-06-14
- **Fix (Missing Material Library):** Re-added the missing `com.google.android.material:material:1.10.0` dependency that caused the app to crash on launch due to missing themes.
- **Native Touch Mode (Long-Press to Right-Click):** Added configurable long-press right-click support (300ms, 500ms, 800ms, 1000ms) in Native Touch mode.

## [v1.0.8-zenith] - 2026-06-14
- **Fixed `zenith.zenith` Package Name Bug:** Restored the base `applicationId` to `com.limelight.zenith` (and `com.limelight.root.zenith` for rooted devices).

## [v1.0.7-zenith] - 2026-06-13
- **Corrected Application IDs:** Updated internal `applicationId` to match Moonlight Zenith's identity.

## [v1.0.6-zenith] - 2026-06-13
- **Perfected Horizontal Symmetry & Fixed L3/D-PAD Overlap:** Shifted left and right on-screen control clusters inward and gave `L3` dedicated space in the bottom-left outer corner.

## [v1.0.5-zenith] - 2026-06-13
- **OSC Grid Coordinate Hotfix:** Rebuilt On-Screen Gamepad positioning logic to target the `0-72` vertical scale grid accurately.

## [v1.0.4-zenith] - 2026-06-13
- **OSC Layout Overhaul:** Refined analog stick, D-PAD, face button, and utility button sizes and margins.

## [v1.0.3-zenith] - 2026-06-13
- **Fixed On-Screen Gamepad Layout (L3/R3) & Production Keystore Migration:** Placed L3/R3 back into safe zones on wide-aspect ratio devices and migrated to a production keystore via `local.properties`.

## [v1.0.2-zenith] - 2026-06-13
- **Removed Enhanced Multi-Touch Subsystem:** Reverted experimental multi-touch subsystem in favor of stable touch handling.

## [v1.0.1-zenith] - 2026-06-13
- **OSC Layout Orientation Bug Fix:** Fixed oversized and shifted On-Screen Controller buttons when layout dimensions were requested before landscape rotation completed.

## [v1.0.0-zenith] - 2026-06-13
- **Initial Moonlight Zenith Release:** Added Host PC Keyboard Layout Selector (`tr_TR`, `de_DE`, `fr_FR`, `es_ES`, `it_IT`, `auto`), hardware-level Virtual-Key (`VK_`) translation, L3/R3 on-screen controls, Attect floating menu integration, dynamic Git versioning, and Zenith rebranding.
