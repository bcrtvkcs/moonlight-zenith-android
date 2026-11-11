# Changelog

All notable changes to **Moonlight Zenith Android** are documented in this file.

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
