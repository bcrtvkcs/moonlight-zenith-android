package com.limelight.utils;

import android.app.Activity;
import android.app.GameManager;
import android.app.GameState;
import android.app.LocaleManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.LocaleList;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.background.UiBackgroundManager;
import com.limelight.ui.glass.GlassDialogPortal;

import java.util.Locale;

public class UiHelper {

    private static final int TV_VERTICAL_PADDING_DP = 15;
    private static final int TV_HORIZONTAL_PADDING_DP = 15;

    public static boolean isLiquidGlassEnabled(Context context) {
        if (context == null) {
            return false;
        }
        PreferenceConfiguration prefs = PreferenceConfiguration.readPreferences(context);
        return PreferenceConfiguration.UI_STYLE_LIQUID_GLASS.equals(prefs.uiStyle);
    }

    public static int resolveThemeColor(Context context, int attrResId, int fallbackColor) {
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(attrResId, tv, true)) {
            return tv.data;
        }
        return fallbackColor;
    }

    public static void applyMaterial3Theme(Activity activity) {
        PreferenceConfiguration prefs = PreferenceConfiguration.readPreferences(activity);
        boolean isZenith = "zenith".equals(prefs.themeMode);
        boolean isPureBlack = prefs.pureBlackTheme;

        if (!isZenith && DynamicColors.isDynamicColorAvailable()) {
            DynamicColors.applyToActivityIfAvailable(activity);
            activity.getTheme().applyStyle(com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Dark, true);
            if (isPureBlack) {
                activity.getTheme().applyStyle(R.style.ThemeOverlay_Moonlight_DynamicAMOLED, true);
            }
        } else if (isPureBlack) {
            activity.getTheme().applyStyle(R.style.ThemeOverlay_Moonlight_AMOLED, true);
        }
        UiBackgroundManager.refreshFromPreferences(activity);
        applyWindowSurfaceColors(activity);
    }

    public static void applyWindowSurfaceColors(Activity activity) {
        if (activity == null || activity instanceof Game) {
            return;
        }
        int surfaceColor = resolveThemeColor(activity, com.google.android.material.R.attr.colorSurface, 0xFF11131A);
        int primaryColor = resolveThemeColor(activity, androidx.appcompat.R.attr.colorPrimary, 0xFFBAC3FF);

        if (activity.getWindow() != null) {
            UiBackgroundManager.applyWindowWallpaperFlags(activity, surfaceColor);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activity.getWindow().clearFlags(
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
                activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activity.getWindow().setStatusBarContrastEnforced(false);
                activity.getWindow().setNavigationBarContrastEnforced(false);
            }
        }
        if (!isLiquidGlassEnabled(activity)) {
            UiBackgroundManager.applyBackgroundToXmlActivity(activity, surfaceColor, primaryColor);
        } else {
            View contentView = activity.findViewById(android.R.id.content);
            if (contentView != null) {
                contentView.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    private static void setGameModeStatus(Context context, boolean streaming, boolean interruptible) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                GameManager gameManager = context.getSystemService(GameManager.class);
                if (gameManager == null) {
                    return; // Not supported on this device (e.g., Meta Quest)
                }

                if (streaming) {
                    gameManager.setGameState(new GameState(false, interruptible ? GameState.MODE_GAMEPLAY_INTERRUPTIBLE : GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE));
                } else {
                    gameManager.setGameState(new GameState(false, GameState.MODE_NONE));
                }
            } catch (Throwable t) {
                // Swallow any failure. Some OEM builds ship partial/incompatible GameManager impls.
            }
        }
    }

    public static void notifyStreamConnecting(Context context) {
        setGameModeStatus(context, true, true);
    }

    public static void notifyStreamConnected(Context context) {
        setGameModeStatus(context, true, false);
    }

    public static void notifyStreamEnteringPiP(Context context) {
        setGameModeStatus(context, true, true);
    }

    public static void notifyStreamExitingPiP(Context context) {
        setGameModeStatus(context, true, false);
    }

    public static void notifyStreamEnded(Context context) {
        setGameModeStatus(context, false, false);
    }

    public static void setLocale(Activity activity)
    {
        applyMaterial3Theme(activity);

        String locale = PreferenceConfiguration.readPreferences(activity).language;
        if (!locale.equals(PreferenceConfiguration.DEFAULT_LANGUAGE)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // On Android 13, migrate this non-default language setting into the OS native API
                LocaleManager localeManager = activity.getSystemService(LocaleManager.class);
                localeManager.setApplicationLocales(LocaleList.forLanguageTags(locale));
                PreferenceConfiguration.completeLanguagePreferenceMigration(activity);
            }
            else {
                Configuration config = new Configuration(activity.getResources().getConfiguration());

                // Some locales include both language and country which must be separated
                // before calling the Locale constructor.
                if (locale.contains("-"))
                {
                    config.locale = new Locale(locale.substring(0, locale.indexOf('-')),
                            locale.substring(locale.indexOf('-') + 1));
                }
                else
                {
                    config.locale = new Locale(locale);
                }

                activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
            }
        }
    }

    public static void applyStatusBarPadding(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // This applies the padding that we omitted in notifyNewRootView() on Q
            view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                    view.setPadding(view.getPaddingLeft(),
                            view.getPaddingTop(),
                            view.getPaddingRight(),
                            windowInsets.getTappableElementInsets().bottom);
                    return windowInsets;
                }
            });
            view.requestApplyInsets();
        }
    }

    public static boolean isTvMode(Context context) {
        UiModeManager modeMgr = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return modeMgr != null && modeMgr.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    public static void notifyNewRootView(final Activity activity)
    {
        applyWindowSurfaceColors(activity);
        View rootView = activity.findViewById(android.R.id.content);
        UiModeManager modeMgr = (UiModeManager) activity.getSystemService(Context.UI_MODE_SERVICE);

        // Set GameState.MODE_NONE initially for all activities
        setGameModeStatus(activity, false, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Allow this non-streaming activity to layout under notches.
            //
            // We should NOT do this for the Game activity unless
            // the user specifically opts in, because it can obscure
            // parts of the streaming surface.
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        if (isLiquidGlassEnabled(activity)) {
            // Compose Liquid Glass screens manage edge-to-edge insets directly so the custom
            // background image or phone wallpaper extends seamlessly behind status & nav bars.
            rootView.setPadding(0, 0, 0, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.getWindow().setDecorFitsSystemWindows(false);
            } else {
                activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
            return;
        }

        if (modeMgr.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            // Increase view padding on TVs
            float scale = activity.getResources().getDisplayMetrics().density;
            int verticalPaddingPixels = (int) (TV_VERTICAL_PADDING_DP*scale + 0.5f);
            int horizontalPaddingPixels = (int) (TV_HORIZONTAL_PADDING_DP*scale + 0.5f);

            rootView.setPadding(horizontalPaddingPixels, verticalPaddingPixels,
                    horizontalPaddingPixels, verticalPaddingPixels);
        }
        else {
            // True edge-to-edge in Material You Expressive as well: draw nothing over/behind
            // the status bar so custom images, wallpapers, or solid theme backgrounds extend
            // seamlessly behind both system bars while content children respect safe insets.
            rootView.setPadding(0, 0, 0, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.getWindow().setDecorFitsSystemWindows(false);
            } else {
                activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }

            rootView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                    view.setPadding(0, 0, 0, 0);

                    int leftInset;
                    int topInset;
                    int rightInset;
                    int navBottomInset;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Insets topBars = windowInsets.getInsets(
                                WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout());
                        Insets navBars = windowInsets.getInsets(WindowInsets.Type.navigationBars());
                        leftInset = Math.max(topBars.left, navBars.left);
                        topInset = topBars.top;
                        rightInset = Math.max(topBars.right, navBars.right);
                        navBottomInset = navBars.bottom;
                    } else {
                        leftInset = windowInsets.getSystemWindowInsetLeft();
                        topInset = windowInsets.getSystemWindowInsetTop();
                        rightInset = windowInsets.getSystemWindowInsetRight();
                        navBottomInset = windowInsets.getSystemWindowInsetBottom();
                    }

                    if (view instanceof ViewGroup) {
                        ViewGroup contentGroup = (ViewGroup) view;
                        for (int i = 0; i < contentGroup.getChildCount(); i++) {
                            View child = contentGroup.getChildAt(i);
                            if ("moonlight_zenith_bg_layer".equals(child.getTag())) {
                                child.setPadding(0, 0, 0, 0);
                            } else {
                                child.setPadding(leftInset, topInset, rightInset, 0);
                            }
                        }
                    }

                    float density = activity.getResources().getDisplayMetrics().density;
                    View floatingNav = activity.findViewById(R.id.m3FloatingBottomBarCard);
                    if (floatingNav != null && floatingNav.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams lp =
                                (ViewGroup.MarginLayoutParams) floatingNav.getLayoutParams();
                        int targetBottom = navBottomInset + (int) (14 * density + 0.5f);
                        if (lp.bottomMargin != targetBottom) {
                            lp.bottomMargin = targetBottom;
                            floatingNav.setLayoutParams(lp);
                        }
                    }
                    View addPcFab = activity.findViewById(R.id.manuallyAddPc);
                    if (addPcFab != null && addPcFab.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams lp =
                                (ViewGroup.MarginLayoutParams) addPcFab.getLayoutParams();
                        int targetBottom = navBottomInset + (int) (16 * density + 0.5f);
                        if (lp.bottomMargin != targetBottom) {
                            lp.bottomMargin = targetBottom;
                            addPcFab.setLayoutParams(lp);
                        }
                    }

                    return windowInsets;
                }
            });
            rootView.requestApplyInsets();
        }
    }

    public static void showDecoderCrashDialog(Activity activity) {
        final SharedPreferences prefs = activity.getSharedPreferences("DecoderTombstone", 0);
        final int crashCount = prefs.getInt("CrashCount", 0);
        int lastNotifiedCrashCount = prefs.getInt("LastNotifiedCrashCount", 0);

        // Remember the last crash count we notified at, so we don't
        // display the crash dialog every time the app is started until
        // they stream again
        if (crashCount != 0 && crashCount != lastNotifiedCrashCount) {
            if (crashCount % 3 == 0) {
                // At 3 consecutive crashes, we'll forcefully reset their settings
                PreferenceConfiguration.resetStreamingSettings(activity);
                Dialog.displayDialog(activity,
                        activity.getResources().getString(R.string.title_decoding_reset),
                        activity.getResources().getString(R.string.message_decoding_reset),
                        new Runnable() {
                            @Override
                            public void run() {
                                // Mark notification as acknowledged on dismissal
                                prefs.edit().putInt("LastNotifiedCrashCount", crashCount).apply();
                            }
                        });
            }
            else {
                Dialog.displayDialog(activity,
                        activity.getResources().getString(R.string.title_decoding_error),
                        activity.getResources().getString(R.string.message_decoding_error),
                        new Runnable() {
                            @Override
                            public void run() {
                                // Mark notification as acknowledged on dismissal
                                prefs.edit().putInt("LastNotifiedCrashCount", crashCount).apply();
                            }
                        });
            }
        }
    }

    public static void applyLiquidGlassDialogStyle(Context context, android.app.Dialog dialog) {
        if (context == null || dialog == null || !isLiquidGlassEnabled(context)) {
            return;
        }
        android.view.Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawableResource(R.drawable.m3_glass_dialog_bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                int blurPx = Math.min(150, (int) (24 * context.getResources().getDisplayMetrics().density));
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                WindowManager.LayoutParams lp = window.getAttributes();
                if (lp != null) {
                    lp.setBlurBehindRadius(blurPx);
                    window.setAttributes(lp);
                }
                window.setBackgroundBlurRadius(blurPx);
                window.setDimAmount(0.32f);
            } catch (Throwable ignored) {
                // Fallback gracefully on OEM builds that disable window blur
            }
        }
    }

    public static void displayQuitConfirmationDialog(Activity parent, final Runnable onYes, final Runnable onNo) {
        if (isLiquidGlassEnabled(parent)) {
            GlassDialogPortal.showConfirmation(
                    parent,
                    null,
                    parent.getResources().getString(R.string.applist_quit_confirmation),
                    parent.getResources().getString(R.string.yes),
                    parent.getResources().getString(R.string.no),
                    true,
                    onYes,
                    onNo
            );
            return;
        }
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        if (onYes != null) {
                            onYes.run();
                        }
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        if (onNo != null) {
                            onNo.run();
                        }
                        break;
                }
            }
        };

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(parent)
                .setMessage(parent.getResources().getString(R.string.applist_quit_confirmation))
                .setPositiveButton(parent.getResources().getString(R.string.yes), dialogClickListener)
                .setNegativeButton(parent.getResources().getString(R.string.no), dialogClickListener)
                .create();
        applyLiquidGlassDialogStyle(parent, dialog);
        dialog.show();
    }

    public static void displayDeletePcConfirmationDialog(Activity parent, ComputerDetails computer, final Runnable onYes, final Runnable onNo) {
        if (isLiquidGlassEnabled(parent)) {
            GlassDialogPortal.showConfirmation(
                    parent,
                    computer != null ? computer.name : null,
                    parent.getResources().getString(R.string.delete_pc_msg),
                    parent.getResources().getString(R.string.yes),
                    parent.getResources().getString(R.string.no),
                    true,
                    onYes,
                    onNo
            );
            return;
        }
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        if (onYes != null) {
                            onYes.run();
                        }
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        if (onNo != null) {
                            onNo.run();
                        }
                        break;
                }
            }
        };

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(parent)
                .setMessage(parent.getResources().getString(R.string.delete_pc_msg))
                .setTitle(computer.name)
                .setPositiveButton(parent.getResources().getString(R.string.yes), dialogClickListener)
                .setNegativeButton(parent.getResources().getString(R.string.no), dialogClickListener)
                .create();
        applyLiquidGlassDialogStyle(parent, dialog);
        dialog.show();
    }
}
