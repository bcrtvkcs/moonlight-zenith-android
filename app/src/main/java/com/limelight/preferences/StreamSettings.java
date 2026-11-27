package com.limelight.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.provider.MediaStore;
import android.widget.Toast;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.DisplayMetrics;
import android.util.Range;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.limelight.LimeLog;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.ui.background.UiBackgroundManager;
import com.limelight.ui.glass.StreamSettingsGlassCallbacks;
import com.limelight.ui.glass.StreamSettingsGlassController;
import com.limelight.ui.glass.StreamSettingsGlassHost;
import com.limelight.utils.Dialog;
import com.limelight.utils.UiHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StreamSettings extends AppCompatActivity {
    public static final String EXTRA_CATEGORY_KEY = "com.limelight.preferences.EXTRA_CATEGORY_KEY";

    private PreferenceConfiguration previousPrefs;
    private int previousDisplayPixelCount;
    private String currentCategoryKey = "category_basic_settings";
    private TabLayout settingsTabLayout;
    private TextView settingsTitleText;
    private MaterialCardView m3FloatingBottomBarCard;
    private HorizontalScrollView m3FloatingBottomBarScroll;
    private LinearLayout m3FloatingBottomBarContainer;
    private View m3FloatingBottomBarIndicator;
    private List<CategoryTab> loadedCategories;
    private ViewPager2 activeViewPager;
    private SettingsFragment activeFragment;
    private TabLayoutMediator tabLayoutMediator;
    private boolean isGlassUi;
    private StreamSettingsGlassController glassController;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;
    private ActivityResultLauncher<String[]> documentImagePickerLauncher;

    // HACK for Android 9
    static DisplayCutout displayCutoutP;

    public static class CategoryTab {
        public final String key;
        public final CharSequence title;
        public final PreferenceScreen screen;

        public CategoryTab(String key, CharSequence title, PreferenceScreen screen) {
            this.key = key;
            this.title = title;
            this.screen = screen;
        }
    }

    String getCurrentCategoryKey() {
        return currentCategoryKey;
    }

    boolean isFloatingBottomBarNav() {
        PreferenceConfiguration prefs = PreferenceConfiguration.readPreferences(this);
        return PreferenceConfiguration.SETTINGS_NAV_STYLE_FLOATING_BOTTOM_BAR.equals(prefs.settingsNavStyle);
    }

    private int resolveCategoryIconRes(String key) {
        if ("category_basic_settings".equals(key)) return R.drawable.ic_nav_basic;
        if ("category_audio_settings".equals(key)) return R.drawable.ic_nav_audio;
        if ("category_gamepad_settings".equals(key)) return R.drawable.ic_nav_gamepad;
        if ("category_input_settings".equals(key)) return R.drawable.ic_nav_input;
        if ("category_onscreen_controls".equals(key)) return R.drawable.ic_nav_osc;
        if ("category_ui_settings".equals(key)) return R.drawable.ic_nav_ui;
        if ("category_advanced_settings".equals(key)) return R.drawable.ic_nav_advanced;
        return R.drawable.ic_settings;
    }

    private String resolveCategoryShortLabel(String key, CharSequence fallbackTitle) {
        if ("category_basic_settings".equals(key)) return getString(R.string.nav_short_basic);
        if ("category_audio_settings".equals(key)) return getString(R.string.nav_short_audio);
        if ("category_gamepad_settings".equals(key)) return getString(R.string.nav_short_gamepad);
        if ("category_input_settings".equals(key)) return getString(R.string.nav_short_input);
        if ("category_onscreen_controls".equals(key)) return getString(R.string.nav_short_osc);
        if ("category_ui_settings".equals(key)) return getString(R.string.nav_short_ui);
        if ("category_advanced_settings".equals(key)) return getString(R.string.nav_short_advanced);
        return fallbackTitle != null ? fallbackTitle.toString() : "";
    }

    private void buildM3FloatingBottomBarItems(final List<CategoryTab> categories, final ViewPager2 viewPager) {
        if (m3FloatingBottomBarContainer == null || categories == null) {
            return;
        }
        m3FloatingBottomBarContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int itemWidth = (int) (88 * density + 0.5f);
        int itemHeight = (int) (66 * density + 0.5f);
        int iconSize = (int) (25 * density + 0.5f);
        int marginH = (int) (2 * density + 0.5f);

        for (int i = 0; i < categories.size(); i++) {
            final int position = i;
            CategoryTab cat = categories.get(i);

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(itemWidth, itemHeight);
            lp.setMargins(marginH, 0, marginH, 0);
            item.setLayoutParams(lp);
            item.setClickable(true);
            item.setFocusable(true);
            item.setTag(position);

            ImageView icon = new ImageView(this);
            icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
            icon.setImageResource(resolveCategoryIconRes(cat.key));
            icon.setTag("nav_icon");
            item.addView(icon);

            TextView label = new TextView(this);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            labelLp.topMargin = (int) (3 * density + 0.5f);
            label.setLayoutParams(labelLp);
            label.setText(resolveCategoryShortLabel(cat.key, cat.title));
            label.setTextSize(12.5f);
            label.setMaxLines(1);
            label.setTag("nav_label");
            item.addView(label);

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (viewPager != null) {
                        viewPager.setCurrentItem(position, true);
                    }
                }
            });
            item.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus && viewPager != null && viewPager.getCurrentItem() != position) {
                        viewPager.setCurrentItem(position, true);
                    }
                }
            });

            m3FloatingBottomBarContainer.addView(item);
        }
    }

    private void updateM3FloatingBottomBarSelection(int selectedIndex) {
        updateM3FloatingBottomBarPosition((float) selectedIndex, true);
    }

    private void updateM3FloatingBottomBarPosition(float pagePosition, boolean postScroll) {
        if (m3FloatingBottomBarContainer == null) {
            return;
        }
        int count = m3FloatingBottomBarContainer.getChildCount();
        if (count == 0) {
            return;
        }
        float clampedPos = Math.max(0f, Math.min(pagePosition, count - 1));
        float density = getResources().getDisplayMetrics().density;
        int pillRadius = (int) (33 * density + 0.5f);
        int selectedBg = UiHelper.resolveThemeColor(this, com.google.android.material.R.attr.colorSecondaryContainer, 0xFF434659);
        int selectedFg = UiHelper.resolveThemeColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer, 0xFFE0E1F9);
        int unselectedFg = UiHelper.resolveThemeColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFC6C5D0);

        float itemWidthPx = (int) (88 * density + 0.5f);
        float marginHPx = (int) (2 * density + 0.5f);
        float itemStepPx = itemWidthPx + (marginHPx * 2f);

        if (m3FloatingBottomBarIndicator != null) {
            if (!(m3FloatingBottomBarIndicator.getBackground() instanceof GradientDrawable)) {
                GradientDrawable pill = new GradientDrawable();
                pill.setColor(selectedBg);
                pill.setCornerRadius(pillRadius);
                m3FloatingBottomBarIndicator.setBackground(pill);
            }
            m3FloatingBottomBarIndicator.setTranslationX(clampedPos * itemStepPx);
        }

        for (int i = 0; i < count; i++) {
            View child = m3FloatingBottomBarContainer.getChildAt(i);
            child.setBackgroundColor(Color.TRANSPARENT);
            float activeWeight = Math.max(0f, 1f - Math.abs(clampedPos - i));
            int blendedFg = androidx.core.graphics.ColorUtils.blendARGB(unselectedFg, selectedFg, activeWeight);
            ImageView icon = child.findViewWithTag("nav_icon");
            if (icon != null) {
                icon.setColorFilter(blendedFg);
            }
            TextView label = child.findViewWithTag("nav_label");
            if (label != null) {
                label.setTextColor(blendedFg);
                label.setTypeface(null, activeWeight >= 0.5f ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            }
        }

        if (m3FloatingBottomBarScroll != null) {
            final float centerPx = marginHPx + (clampedPos * itemStepPx) + (itemWidthPx / 2f);
            if (postScroll) {
                m3FloatingBottomBarScroll.post(new Runnable() {
                    @Override
                    public void run() {
                        if (m3FloatingBottomBarScroll != null && m3FloatingBottomBarScroll.getWidth() > 0) {
                            int targetScroll = (int) (centerPx - m3FloatingBottomBarScroll.getWidth() / 2f);
                            m3FloatingBottomBarScroll.scrollTo(Math.max(0, targetScroll), 0);
                        }
                    }
                });
            } else if (m3FloatingBottomBarScroll.getWidth() > 0) {
                int targetScroll = (int) (centerPx - m3FloatingBottomBarScroll.getWidth() / 2f);
                m3FloatingBottomBarScroll.scrollTo(Math.max(0, targetScroll), 0);
            }
        }
    }

    void applyM3NavigationStyle() {
        if (isGlassUi) {
            return;
        }
        boolean floating = isFloatingBottomBarNav();
        if (settingsTabLayout != null) {
            settingsTabLayout.setVisibility(floating ? View.GONE : View.VISIBLE);
        }
        if (m3FloatingBottomBarCard != null) {
            m3FloatingBottomBarCard.setVisibility(floating ? View.VISIBLE : View.GONE);
            if (floating && loadedCategories != null && activeViewPager != null) {
                if (m3FloatingBottomBarContainer != null && m3FloatingBottomBarContainer.getChildCount() != loadedCategories.size()) {
                    buildM3FloatingBottomBarItems(loadedCategories, activeViewPager);
                }
                updateM3FloatingBottomBarSelection(activeViewPager.getCurrentItem());
            }
        }
        if (activeFragment != null) {
            activeFragment.refreshVisibleListViewPadding();
        }
    }

    private void handlePickedBackgroundImageUri(Uri uri) {
        if (uri == null) {
            return;
        }
        UiBackgroundManager.importCustomImage(StreamSettings.this, uri, new kotlin.jvm.functions.Function1<Boolean, kotlin.Unit>() {
            @Override
            public kotlin.Unit invoke(Boolean success) {
                if (Boolean.TRUE.equals(success)) {
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(StreamSettings.this);
                    sp.edit().putString(
                            PreferenceConfiguration.UI_BACKGROUND_SOURCE_PREF_STRING,
                            PreferenceConfiguration.UI_BACKGROUND_SOURCE_CUSTOM
                    ).apply();
                    UiBackgroundManager.refreshFromPreferences(StreamSettings.this);
                    UiHelper.applyWindowSurfaceColors(StreamSettings.this);
                    if (activeFragment != null) {
                        activeFragment.syncBackgroundSourceListValue(PreferenceConfiguration.UI_BACKGROUND_SOURCE_CUSTOM);
                        activeFragment.refreshUiBackgroundPreferenceStates();
                    }
                    if (glassController != null) {
                        glassController.bumpRevision();
                    }
                    Toast.makeText(StreamSettings.this, R.string.ui_background_image_saved, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(StreamSettings.this, R.string.ui_background_image_failed, Toast.LENGTH_SHORT).show();
                }
                return kotlin.Unit.INSTANCE;
            }
        });
    }

    public void launchBackgroundImagePicker() {
        if (photoPickerLauncher != null) {
            try {
                PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build();
                photoPickerLauncher.launch(request);
                return;
            } catch (Exception ignored) {
                // Fall back to OpenDocument on devices without system Photo Picker
            }
        }
        if (documentImagePickerLauncher != null) {
            try {
                documentImagePickerLauncher.launch(new String[]{"image/*"});
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(this, R.string.ui_background_image_failed, Toast.LENGTH_SHORT).show();
    }

    public void clearCustomBackgroundImage() {
        UiBackgroundManager.clearCustomImage(this);
        UiBackgroundManager.refreshFromPreferences(this);
        UiHelper.applyWindowSurfaceColors(this);
        if (activeFragment != null) {
            activeFragment.refreshUiBackgroundPreferenceStates();
        }
        if (glassController != null) {
            glassController.bumpRevision();
        }
        Toast.makeText(this, R.string.ui_background_image_cleared, Toast.LENGTH_SHORT).show();
    }

    public void showCustomImageOptionsDialog() {
        if (!UiBackgroundManager.hasCustomImage(this)) {
            launchBackgroundImagePicker();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_ui_background_pick_image)
                .setItems(new CharSequence[]{
                        getString(R.string.ui_background_pick_action),
                        getString(R.string.ui_background_clear_action)
                }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            launchBackgroundImagePicker();
                        } else if (which == 1) {
                            clearCustomBackgroundImage();
                        }
                    }
                })
                .show();
    }

    void onCategoriesLoaded(final List<CategoryTab> categories, SettingsFragment fragment, final ViewPager2 viewPager) {
        this.activeFragment = fragment;
        this.loadedCategories = categories;
        this.activeViewPager = viewPager;
        if (categories == null || categories.isEmpty()) {
            return;
        }
        if (isGlassUi && glassController != null) {
            glassController.setCategories(categories, currentCategoryKey);
            return;
        }
        if (settingsTabLayout == null || viewPager == null) {
            return;
        }

        int selectedIndex = 0;
        boolean found = false;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).key.equals(currentCategoryKey)) {
                selectedIndex = i;
                found = true;
                break;
            }
        }
        if (!found) {
            currentCategoryKey = categories.get(0).key;
        }

        if (tabLayoutMediator != null) {
            tabLayoutMediator.detach();
            tabLayoutMediator = null;
        }

        viewPager.setCurrentItem(selectedIndex, false);

        tabLayoutMediator = new TabLayoutMediator(settingsTabLayout, viewPager, true, true,
                new TabLayoutMediator.TabConfigurationStrategy() {
                    @Override
                    public void onConfigureTab(@NonNull final TabLayout.Tab tab, int position) {
                        CategoryTab cat = categories.get(position);
                        tab.setText(cat.title);
                        tab.setTag(cat);
                        if (tab.view != null) {
                            tab.view.setFocusable(true);
                            tab.view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                                @Override
                                public void onFocusChange(View v, boolean hasFocus) {
                                    if (hasFocus && !tab.isSelected()) {
                                        tab.select();
                                    }
                                }
                            });
                        }
                    }
                });
        tabLayoutMediator.attach();

        buildM3FloatingBottomBarItems(categories, viewPager);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                updateM3FloatingBottomBarPosition(position + positionOffset, false);
            }

            @Override
            public void onPageSelected(int position) {
                if (position >= 0 && position < categories.size()) {
                    CategoryTab cat = categories.get(position);
                    currentCategoryKey = cat.key;
                    if (settingsTitleText != null) {
                        settingsTitleText.setText(cat.title);
                    }
                }
            }
        });

        if (settingsTitleText != null) {
            settingsTitleText.setText(categories.get(selectedIndex).title);
        }

        final int scrollTargetIndex = selectedIndex;
        settingsTabLayout.post(new Runnable() {
            @Override
            public void run() {
                if (settingsTabLayout != null && scrollTargetIndex < settingsTabLayout.getTabCount()) {
                    settingsTabLayout.setScrollPosition(scrollTargetIndex, 0f, true);
                }
            }
        });

        applyM3NavigationStyle();
    }

    void reloadSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();
            previousDisplayPixelCount = mode.getPhysicalWidth() * mode.getPhysicalHeight();
        }
        SettingsFragment fragment = new SettingsFragment();
        activeFragment = fragment;
        getFragmentManager().beginTransaction().replace(
                R.id.stream_settings, fragment
        ).commitAllowingStateLoss();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            currentCategoryKey = savedInstanceState.getString(EXTRA_CATEGORY_KEY, "category_basic_settings");
        } else if (getIntent() != null && getIntent().hasExtra(EXTRA_CATEGORY_KEY)) {
            String extraKey = getIntent().getStringExtra(EXTRA_CATEGORY_KEY);
            if (extraKey != null && !extraKey.isEmpty()) {
                currentCategoryKey = extraKey;
            }
        }

        previousPrefs = PreferenceConfiguration.readPreferences(this);

        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        handlePickedBackgroundImageUri(uri);
                    }
                }
        );

        documentImagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        handlePickedBackgroundImageUri(uri);
                    }
                }
        );

        UiHelper.setLocale(this);
        isGlassUi = UiHelper.isLiquidGlassEnabled(this);

        if (isGlassUi) {
            glassController = new StreamSettingsGlassController();
            FrameLayout root = new FrameLayout(this);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            FrameLayout hiddenFragmentHost = new FrameLayout(this);
            hiddenFragmentHost.setId(R.id.stream_settings);
            hiddenFragmentHost.setVisibility(View.GONE);
            root.addView(hiddenFragmentHost, new FrameLayout.LayoutParams(0, 0));

            root.addView(StreamSettingsGlassHost.createView(this, glassController, new StreamSettingsGlassCallbacks() {
                @Override
                public void onBackClicked() {
                    onBackPressed();
                }

                @Override
                public void onCategorySelected(String categoryKey) {
                    if (categoryKey != null && !categoryKey.isEmpty()) {
                        currentCategoryKey = categoryKey;
                    }
                }

                @Override
                public void onPickCustomBackgroundImage() {
                    launchBackgroundImagePicker();
                }

                @Override
                public void onClearCustomBackgroundImage() {
                    clearCustomBackgroundImage();
                }

                @Override
                public void onPreferenceMutated(String key) {
                    if (activeFragment != null) {
                        activeFragment.refreshUiBackgroundPreferenceStates();
                    }
                    UiBackgroundManager.refreshFromPreferences(StreamSettings.this);
                    UiHelper.applyWindowSurfaceColors(StreamSettings.this);
                    if (glassController != null) {
                        glassController.bumpRevision();
                    }
                }
            }), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            setContentView(root);
        } else {
            setContentView(R.layout.activity_stream_settings);
            settingsTitleText = findViewById(R.id.settingsTitleText);
            settingsTabLayout = findViewById(R.id.settingsTabLayout);
            m3FloatingBottomBarCard = findViewById(R.id.m3FloatingBottomBarCard);
            m3FloatingBottomBarScroll = findViewById(R.id.m3FloatingBottomBarScroll);
            m3FloatingBottomBarContainer = findViewById(R.id.m3FloatingBottomBarContainer);
            m3FloatingBottomBarIndicator = findViewById(R.id.m3FloatingBottomBarIndicator);

            View backButton = findViewById(R.id.settingsBackButton);
            if (backButton != null) {
                backButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onBackPressed();
                    }
                });
            }
        }

        UiHelper.notifyNewRootView(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_CATEGORY_KEY, currentCategoryKey);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // We have to use this hack on Android 9 because we don't have Display.getCutout()
        // which was added in Android 10.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            // Insets can be null when the activity is recreated on screen rotation
            // https://stackoverflow.com/questions/61241255/windowinsets-getdisplaycutout-is-null-everywhere-except-within-onattachedtowindo
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                displayCutoutP = insets.getDisplayCutout();
            }
        }

        reloadSettings();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();

            // If the display's physical pixel count has changed, we consider that it's a new display
            // and we should reload our settings (which include display-dependent values).
            //
            // NB: We aren't using displayId here because that stays the same (DEFAULT_DISPLAY) when
            // switching between screens on a foldable device.
            if (mode.getPhysicalWidth() * mode.getPhysicalHeight() != previousDisplayPixelCount) {
                reloadSettings();
            }
        }
    }

    @Override
    // NOTE: This will NOT be called on Android 13+ with android:enableOnBackInvokedCallback="true"
    public void onBackPressed() {
        finish();

        // Language changes are handled via configuration changes in Android 13+,
        // so manual activity relaunching is no longer required.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PreferenceConfiguration newPrefs = PreferenceConfiguration.readPreferences(this);
            if (!newPrefs.language.equals(previousPrefs.language)) {
                // Restart the PC view to apply UI changes
                Intent intent = new Intent(this, PcView.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent, null);
            }
        }
    }

    public static class SettingsFragment extends PreferenceFragment {
        private int nativeResolutionStartIndex = Integer.MAX_VALUE;
        private boolean nativeFramerateShown = false;
        private final List<CategoryTab> categoryList = new ArrayList<>();
        private SeekBarPreference bitratePref;
        private ViewPager2 settingsViewPager;

        private static class CategoryPageViewHolder extends RecyclerView.ViewHolder {
            final ListView listView;

            CategoryPageViewHolder(@NonNull ListView itemView) {
                super(itemView);
                this.listView = itemView;
            }
        }

        private class CategoryPagerAdapter extends RecyclerView.Adapter<CategoryPageViewHolder> {
            @NonNull
            @Override
            public CategoryPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ListView pageList = new ListView(parent.getContext());
                pageList.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                configureListView(pageList);
                return new CategoryPageViewHolder(pageList);
            }

            @Override
            public void onBindViewHolder(@NonNull CategoryPageViewHolder holder, int position) {
                configureListView(holder.listView);
                PreferenceScreen catScreen = categoryList.get(position).screen;
                holder.listView.setAdapter(catScreen.getRootAdapter());
                holder.listView.setOnItemClickListener(catScreen);
            }

            @Override
            public int getItemCount() {
                return categoryList.size();
            }
        }

        void refreshVisibleListViewPadding() {
            if (settingsViewPager == null || !isAdded()) {
                return;
            }
            if (settingsViewPager.getAdapter() != null) {
                settingsViewPager.getAdapter().notifyDataSetChanged();
            }
        }

        private void configureListView(ListView list) {
            if (list == null || !isAdded()) {
                return;
            }
            float density = getResources().getDisplayMetrics().density;
            boolean floatingBottomBar = (getActivity() instanceof StreamSettings)
                    && ((StreamSettings) getActivity()).isFloatingBottomBarNav();
            int hPad = (int) (16 * density + 0.5f);
            int tPad = (int) (4 * density + 0.5f);
            int bPad = (int) ((floatingBottomBar ? 126 : 24) * density + 0.5f);
            list.setDivider(null);
            list.setDividerHeight(0);
            list.setSelector(android.R.color.transparent);
            list.setCacheColorHint(android.graphics.Color.TRANSPARENT);
            list.setScrollingCacheEnabled(false);
            list.setAnimationCacheEnabled(false);
            list.setPadding(hPad, tPad, hPad, bPad);
            list.setClipToPadding(false);
        }

        void syncBackgroundSourceListValue(String value) {
            Preference pref = findPreference(PreferenceConfiguration.UI_BACKGROUND_SOURCE_PREF_STRING);
            if (pref instanceof ListPreference) {
                ((ListPreference) pref).setValue(value);
            }
        }

        private void markGlassStyleAsCustom(Activity act) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(act);
            if (!PreferenceConfiguration.LIQUID_GLASS_STYLE_CUSTOM.equals(
                    sp.getString(PreferenceConfiguration.LIQUID_GLASS_STYLE_PREF_STRING, PreferenceConfiguration.DEFAULT_LIQUID_GLASS_STYLE))) {
                sp.edit().putString(
                        PreferenceConfiguration.LIQUID_GLASS_STYLE_PREF_STRING,
                        PreferenceConfiguration.LIQUID_GLASS_STYLE_CUSTOM
                ).apply();
                Preference stylePref = findPreference(PreferenceConfiguration.LIQUID_GLASS_STYLE_PREF_STRING);
                if (stylePref instanceof ListPreference) {
                    ((ListPreference) stylePref).setValue(PreferenceConfiguration.LIQUID_GLASS_STYLE_CUSTOM);
                }
            }
        }

        private void syncGlassParameterPreferencesFromConfig(Activity act) {
            PreferenceConfiguration cfg = PreferenceConfiguration.readPreferences(act);
            Preference blurP = findPreference(PreferenceConfiguration.GLASS_BLUR_PREF_STRING);
            if (blurP instanceof SeekBarPreference) {
                ((SeekBarPreference) blurP).setProgress(cfg.glassBlurDp);
            }
            Preference refAmtP = findPreference(PreferenceConfiguration.GLASS_REFRACTION_AMOUNT_PREF_STRING);
            if (refAmtP instanceof SeekBarPreference) {
                ((SeekBarPreference) refAmtP).setProgress(cfg.glassRefractionAmountDp);
            }
            Preference refHeightP = findPreference(PreferenceConfiguration.GLASS_REFRACTION_HEIGHT_PREF_STRING);
            if (refHeightP instanceof SeekBarPreference) {
                ((SeekBarPreference) refHeightP).setProgress(cfg.glassRefractionHeightDp);
            }
            Preference chromaP = findPreference(PreferenceConfiguration.GLASS_CHROMATIC_ABERRATION_PREF_STRING);
            if (chromaP instanceof CheckBoxPreference) {
                ((CheckBoxPreference) chromaP).setChecked(cfg.glassChromaticAberration);
            }
            Preference depthP = findPreference(PreferenceConfiguration.GLASS_DEPTH_EFFECT_PREF_STRING);
            if (depthP instanceof CheckBoxPreference) {
                ((CheckBoxPreference) depthP).setChecked(cfg.glassDepthEffect);
            }
            Preference surfaceOpacityP = findPreference(PreferenceConfiguration.GLASS_SURFACE_OPACITY_PREF_STRING);
            if (surfaceOpacityP instanceof SeekBarPreference) {
                ((SeekBarPreference) surfaceOpacityP).setProgress(cfg.glassSurfaceOpacity);
            }
            Preference highlightP = findPreference(PreferenceConfiguration.GLASS_HIGHLIGHT_INTENSITY_PREF_STRING);
            if (highlightP instanceof SeekBarPreference) {
                ((SeekBarPreference) highlightP).setProgress(cfg.glassHighlightIntensity);
            }
            Preference vibrancyP = findPreference(PreferenceConfiguration.GLASS_VIBRANCY_PREF_STRING);
            if (vibrancyP instanceof CheckBoxPreference) {
                ((CheckBoxPreference) vibrancyP).setChecked(cfg.glassVibrancy);
            }
        }

        void refreshUiBackgroundPreferenceStates() {
            Activity act = getActivity();
            if (act == null) {
                return;
            }
            PreferenceConfiguration prefs = PreferenceConfiguration.readPreferences(act);
            boolean isGlass = PreferenceConfiguration.UI_STYLE_LIQUID_GLASS.equals(prefs.uiStyle);
            boolean hasBackground = !PreferenceConfiguration.DEFAULT_UI_BACKGROUND_SOURCE.equals(prefs.uiBackgroundSource);
            boolean isCustomImage = PreferenceConfiguration.UI_BACKGROUND_SOURCE_CUSTOM.equals(prefs.uiBackgroundSource);
            boolean hasTint = hasBackground && !"none".equals(prefs.uiBackgroundTintMode);

            String[] glassKeys = new String[]{
                    PreferenceConfiguration.LIQUID_GLASS_STYLE_PREF_STRING,
                    PreferenceConfiguration.GLASS_BLUR_PREF_STRING,
                    PreferenceConfiguration.GLASS_REFRACTION_AMOUNT_PREF_STRING,
                    PreferenceConfiguration.GLASS_REFRACTION_HEIGHT_PREF_STRING,
                    PreferenceConfiguration.GLASS_CHROMATIC_ABERRATION_PREF_STRING,
                    PreferenceConfiguration.GLASS_DEPTH_EFFECT_PREF_STRING,
                    PreferenceConfiguration.GLASS_SURFACE_OPACITY_PREF_STRING,
                    PreferenceConfiguration.GLASS_HIGHLIGHT_INTENSITY_PREF_STRING,
                    PreferenceConfiguration.GLASS_VIBRANCY_PREF_STRING
            };
            for (String key : glassKeys) {
                Preference p = findPreference(key);
                if (p != null) {
                    p.setEnabled(isGlass);
                }
            }
            Preference pickPref = findPreference(PreferenceConfiguration.UI_BACKGROUND_PICK_IMAGE_PREF_STRING);
            if (pickPref != null) {
                pickPref.setEnabled(true);
                pickPref.setSummary(UiBackgroundManager.hasCustomImage(act)
                        ? R.string.summary_ui_background_pick_image_set
                        : R.string.summary_ui_background_pick_image_empty);
            }
            Preference tintModePref = findPreference(PreferenceConfiguration.UI_BACKGROUND_TINT_MODE_PREF_STRING);
            if (tintModePref != null) {
                tintModePref.setEnabled(hasBackground);
            }
            Preference tintOpacityPref = findPreference(PreferenceConfiguration.UI_BACKGROUND_TINT_OPACITY_PREF_STRING);
            if (tintOpacityPref != null) {
                tintOpacityPref.setEnabled(hasTint);
            }
            Preference blurPref = findPreference(PreferenceConfiguration.UI_BACKGROUND_BLUR_PREF_STRING);
            if (blurPref != null) {
                boolean blurSupported = isCustomImage || (hasBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);
                blurPref.setEnabled(blurSupported);
            }
        }

        private void setValue(String preferenceKey, String value) {
            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            pref.setValue(value);
        }

        private void appendPreferenceEntry(ListPreference pref, String newEntryName, String newEntryValue) {
            CharSequence[] newEntries = Arrays.copyOf(pref.getEntries(), pref.getEntries().length + 1);
            CharSequence[] newValues = Arrays.copyOf(pref.getEntryValues(), pref.getEntryValues().length + 1);

            // Add the new option
            newEntries[newEntries.length - 1] = newEntryName;
            newValues[newValues.length - 1] = newEntryValue;

            pref.setEntries(newEntries);
            pref.setEntryValues(newValues);
        }

        private void addNativeResolutionEntry(int nativeWidth, int nativeHeight, boolean insetsRemoved, boolean portrait) {
            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING);

            String newName;

            if (insetsRemoved) {
                newName = getResources().getString(R.string.resolution_prefix_native_fullscreen);
            }
            else {
                newName = getResources().getString(R.string.resolution_prefix_native);
            }

            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                if (portrait) {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_portrait);
                }
                else {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_landscape);
                }
            }

            newName += " ("+nativeWidth+"x"+nativeHeight+")";

            String newValue = nativeWidth+"x"+nativeHeight;

            // Check if the native resolution is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (newValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    return;
                }
            }

            if (pref.getEntryValues().length < nativeResolutionStartIndex) {
                nativeResolutionStartIndex = pref.getEntryValues().length;
            }
            appendPreferenceEntry(pref, newName, newValue);
        }

        private void addNativeResolutionEntries(int nativeWidth, int nativeHeight, boolean insetsRemoved) {
            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                addNativeResolutionEntry(nativeHeight, nativeWidth, insetsRemoved, true);
            }
            addNativeResolutionEntry(nativeWidth, nativeHeight, insetsRemoved, false);
        }

        private void addNativeFrameRateEntry(float framerate) {
            int frameRateRounded = Math.round(framerate);
            if (frameRateRounded == 0) {
                return;
            }

            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.FPS_PREF_STRING);
            String fpsValue = Integer.toString(frameRateRounded);
            String fpsName = getResources().getString(R.string.resolution_prefix_native) +
                    " (" + fpsValue + " " + getResources().getString(R.string.fps_suffix_fps) + ")";

            // Check if the native frame rate is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (fpsValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    nativeFramerateShown = false;
                    return;
                }
            }

            appendPreferenceEntry(pref, fpsName, fpsValue);
            nativeFramerateShown = true;
        }

        private void removeValue(String preferenceKey, String value, Runnable onMatched) {
            int matchingCount = 0;

            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            // Count the number of matching entries we'll be removing
            for (CharSequence seq : pref.getEntryValues()) {
                if (seq.toString().equalsIgnoreCase(value)) {
                    matchingCount++;
                }
            }

            // Create the new arrays
            CharSequence[] entries = new CharSequence[pref.getEntries().length-matchingCount];
            CharSequence[] entryValues = new CharSequence[pref.getEntryValues().length-matchingCount];
            int outIndex = 0;
            for (int i = 0; i < pref.getEntryValues().length; i++) {
                if (pref.getEntryValues()[i].toString().equalsIgnoreCase(value)) {
                    // Skip matching values
                    continue;
                }

                entries[outIndex] = pref.getEntries()[i];
                entryValues[outIndex] = pref.getEntryValues()[i];
                outIndex++;
            }

            if (pref.getValue().equalsIgnoreCase(value)) {
                onMatched.run();
            }

            // Update the preference with the new list
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

        private void resetBitrateToDefault(SharedPreferences prefs, String res, String fps) {
            if (res == null) {
                res = prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION);
            }
            if (fps == null) {
                fps = prefs.getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS);
            }

            int newBitrate = PreferenceConfiguration.getDefaultBitrate(res, fps);
            prefs.edit()
                    .putInt(PreferenceConfiguration.BITRATE_PREF_STRING, newBitrate)
                    .apply();
            if (bitratePref != null) {
                bitratePref.setProgress(newBitrate);
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            Context context = getActivity();
            FrameLayout root = new FrameLayout(context);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // Hidden ListView required by PreferenceFragment.ensureList() to bind root PreferenceScreen
            ListView hiddenList = new ListView(context);
            hiddenList.setId(android.R.id.list);
            hiddenList.setVisibility(View.GONE);
            root.addView(hiddenList, new FrameLayout.LayoutParams(0, 0));

            // Swipeable ViewPager2 hosting one ListView per settings category tab
            settingsViewPager = new ViewPager2(context);
            settingsViewPager.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            settingsViewPager.setAdapter(new CategoryPagerAdapter());
            root.addView(settingsViewPager);

            UiHelper.applyStatusBarPadding(root);

            Activity act = getActivity();
            if (act instanceof StreamSettings) {
                ((StreamSettings) act).onCategoriesLoaded(categoryList, this, settingsViewPager);
            }

            return root;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);
            Preference.OnPreferenceChangeListener themeReloadListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Activity act = SettingsFragment.this.getActivity();
                            if (act != null && !act.isFinishing()) {
                                String currentCat = (act instanceof StreamSettings)
                                        ? ((StreamSettings) act).getCurrentCategoryKey()
                                        : "category_ui_settings";
                                Intent intent = new Intent(act, PcView.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                act.startActivity(intent);
                                Intent settingsIntent = new Intent(act, StreamSettings.class);
                                settingsIntent.putExtra(EXTRA_CATEGORY_KEY, currentCat);
                                act.startActivity(settingsIntent);
                            }
                        }
                    }, 150);
                    return true;
                }
            };
            Preference themePref = findPreference(PreferenceConfiguration.THEME_MODE_PREF_STRING);
            if (themePref != null) {
                themePref.setOnPreferenceChangeListener(themeReloadListener);
            }
            Preference pureBlackPref = findPreference(PreferenceConfiguration.PURE_BLACK_THEME_PREF_STRING);
            if (pureBlackPref != null) {
                pureBlackPref.setOnPreferenceChangeListener(themeReloadListener);
            }
            Preference uiStylePref = findPreference(PreferenceConfiguration.UI_STYLE_PREF_STRING);
            if (uiStylePref != null) {
                uiStylePref.setOnPreferenceChangeListener(themeReloadListener);
            }
            Preference settingsNavStylePref = findPreference(PreferenceConfiguration.SETTINGS_NAV_STYLE_PREF_STRING);
            if (settingsNavStylePref != null) {
                settingsNavStylePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        new Handler().post(new Runnable() {
                            @Override
                            public void run() {
                                Activity act = SettingsFragment.this.getActivity();
                                if (act != null && !act.isFinishing()) {
                                    UiBackgroundManager.refreshFromPreferences(act);
                                    if (act instanceof StreamSettings) {
                                        ((StreamSettings) act).applyM3NavigationStyle();
                                        if (((StreamSettings) act).glassController != null) {
                                            ((StreamSettings) act).glassController.bumpRevision();
                                        }
                                    }
                                }
                            }
                        });
                        return true;
                    }
                });
            }

            // Instant reactive listeners for Liquid Glass style & background customization
            Preference.OnPreferenceChangeListener liveUiAppearanceListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                    new Handler().post(new Runnable() {
                        @Override
                        public void run() {
                            Activity act = SettingsFragment.this.getActivity();
                            if (act != null && !act.isFinishing()) {
                                UiBackgroundManager.clearLivePreview();
                                UiBackgroundManager.refreshFromPreferences(act);
                                UiHelper.applyWindowSurfaceColors(act);
                                refreshUiBackgroundPreferenceStates();
                                if (PreferenceConfiguration.UI_BACKGROUND_SOURCE_PREF_STRING.equals(preference.getKey())
                                        && PreferenceConfiguration.UI_BACKGROUND_SOURCE_CUSTOM.equals(newValue)
                                        && !UiBackgroundManager.hasCustomImage(act)
                                        && act instanceof StreamSettings) {
                                    ((StreamSettings) act).launchBackgroundImagePicker();
                                }
                                if (act instanceof StreamSettings && ((StreamSettings) act).glassController != null) {
                                    ((StreamSettings) act).glassController.bumpRevision();
                                }
                            }
                        }
                    });
                    return true;
                }
            };

            Preference glassStylePref = findPreference(PreferenceConfiguration.LIQUID_GLASS_STYLE_PREF_STRING);
            if (glassStylePref != null) {
                glassStylePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, final Object newValue) {
                        new Handler().post(new Runnable() {
                            @Override
                            public void run() {
                                Activity act = SettingsFragment.this.getActivity();
                                if (act != null && !act.isFinishing()) {
                                    String preset = newValue != null ? newValue.toString() : PreferenceConfiguration.DEFAULT_LIQUID_GLASS_STYLE;
                                    if (!PreferenceConfiguration.LIQUID_GLASS_STYLE_CUSTOM.equals(preset)) {
                                        UiBackgroundManager.applyGlassPresetToPreferences(act, preset);
                                        syncGlassParameterPreferencesFromConfig(act);
                                    } else {
                                        UiBackgroundManager.refreshFromPreferences(act);
                                    }
                                    refreshUiBackgroundPreferenceStates();
                                    if (act instanceof StreamSettings && ((StreamSettings) act).glassController != null) {
                                        ((StreamSettings) act).glassController.bumpRevision();
                                    }
                                }
                            }
                        });
                        return true;
                    }
                });
            }

            // Fine-grained Liquid Glass optical parameters (auto-switches preset to "custom" on edit)
            Preference.OnPreferenceChangeListener glassParamChangeListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    new Handler().post(new Runnable() {
                        @Override
                        public void run() {
                            Activity act = SettingsFragment.this.getActivity();
                            if (act != null && !act.isFinishing()) {
                                UiBackgroundManager.clearLivePreview();
                                markGlassStyleAsCustom(act);
                                UiBackgroundManager.refreshFromPreferences(act);
                                refreshUiBackgroundPreferenceStates();
                                if (act instanceof StreamSettings && ((StreamSettings) act).glassController != null) {
                                    ((StreamSettings) act).glassController.bumpRevision();
                                }
                            }
                        }
                    });
                    return true;
                }
            };

            PreferenceCategory uiCatForGlass = (PreferenceCategory) findPreference("category_ui_settings");
            String[] api31GlassKeys = new String[]{
                    PreferenceConfiguration.GLASS_BLUR_PREF_STRING,
                    PreferenceConfiguration.GLASS_VIBRANCY_PREF_STRING
            };
            String[] api33GlassKeys = new String[]{
                    PreferenceConfiguration.GLASS_REFRACTION_AMOUNT_PREF_STRING,
                    PreferenceConfiguration.GLASS_REFRACTION_HEIGHT_PREF_STRING,
                    PreferenceConfiguration.GLASS_CHROMATIC_ABERRATION_PREF_STRING,
                    PreferenceConfiguration.GLASS_DEPTH_EFFECT_PREF_STRING
            };
            String[] allGlassParamKeys = new String[]{
                    PreferenceConfiguration.GLASS_BLUR_PREF_STRING,
                    PreferenceConfiguration.GLASS_REFRACTION_AMOUNT_PREF_STRING,
                    PreferenceConfiguration.GLASS_REFRACTION_HEIGHT_PREF_STRING,
                    PreferenceConfiguration.GLASS_CHROMATIC_ABERRATION_PREF_STRING,
                    PreferenceConfiguration.GLASS_DEPTH_EFFECT_PREF_STRING,
                    PreferenceConfiguration.GLASS_SURFACE_OPACITY_PREF_STRING,
                    PreferenceConfiguration.GLASS_HIGHLIGHT_INTENSITY_PREF_STRING,
                    PreferenceConfiguration.GLASS_VIBRANCY_PREF_STRING
            };

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && uiCatForGlass != null) {
                for (String key : api31GlassKeys) {
                    Preference p = findPreference(key);
                    if (p != null) {
                        uiCatForGlass.removePreference(p);
                    }
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && uiCatForGlass != null) {
                for (String key : api33GlassKeys) {
                    Preference p = findPreference(key);
                    if (p != null) {
                        uiCatForGlass.removePreference(p);
                    }
                }
            }
            for (String key : allGlassParamKeys) {
                Preference p = findPreference(key);
                if (p != null) {
                    p.setOnPreferenceChangeListener(glassParamChangeListener);
                }
            }
            Preference bgSourcePref = findPreference(PreferenceConfiguration.UI_BACKGROUND_SOURCE_PREF_STRING);
            if (bgSourcePref != null) {
                bgSourcePref.setOnPreferenceChangeListener(liveUiAppearanceListener);
            }
            Preference bgTintModePref = findPreference(PreferenceConfiguration.UI_BACKGROUND_TINT_MODE_PREF_STRING);
            if (bgTintModePref != null) {
                bgTintModePref.setOnPreferenceChangeListener(liveUiAppearanceListener);
            }
            Preference bgPickImagePref = findPreference(PreferenceConfiguration.UI_BACKGROUND_PICK_IMAGE_PREF_STRING);
            if (bgPickImagePref != null) {
                bgPickImagePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Activity act = SettingsFragment.this.getActivity();
                        if (act instanceof StreamSettings) {
                            ((StreamSettings) act).showCustomImageOptionsDialog();
                        }
                        return true;
                    }
                });
            }

            final SeekBarPreference bgTintOpacityPref = (SeekBarPreference) findPreference(PreferenceConfiguration.UI_BACKGROUND_TINT_OPACITY_PREF_STRING);
            if (bgTintOpacityPref != null) {
                bgTintOpacityPref.setOnPreferenceChangeListener(liveUiAppearanceListener);
                bgTintOpacityPref.setOnLiveValueChangedListener(new SeekBarPreference.OnLiveValueChangedListener() {
                    @Override
                    public void onLiveValueChanged(int value) {
                        Activity act = SettingsFragment.this.getActivity();
                        if (act != null) {
                            UiBackgroundManager.setLivePreview(act, value, null, null);
                            if (!UiHelper.isLiquidGlassEnabled(act)) {
                                UiHelper.applyWindowSurfaceColors(act);
                            }
                        }
                    }

                    @Override
                    public void onLivePreviewCancelled() {
                        Activity act = SettingsFragment.this.getActivity();
                        UiBackgroundManager.clearLivePreview();
                        if (act != null && !UiHelper.isLiquidGlassEnabled(act)) {
                            UiHelper.applyWindowSurfaceColors(act);
                        }
                    }
                });
            }

            final SeekBarPreference bgBlurPref = (SeekBarPreference) findPreference(PreferenceConfiguration.UI_BACKGROUND_BLUR_PREF_STRING);
            if (bgBlurPref != null) {
                bgBlurPref.setOnPreferenceChangeListener(liveUiAppearanceListener);
                bgBlurPref.setOnLiveValueChangedListener(new SeekBarPreference.OnLiveValueChangedListener() {
                    @Override
                    public void onLiveValueChanged(int value) {
                        final Activity act = SettingsFragment.this.getActivity();
                        if (act != null) {
                            Runnable xmlRefresh = !UiHelper.isLiquidGlassEnabled(act) ? new Runnable() {
                                @Override
                                public void run() {
                                    if (!act.isFinishing() && !act.isDestroyed()) {
                                        UiHelper.applyWindowSurfaceColors(act);
                                    }
                                }
                            } : null;
                            UiBackgroundManager.setLivePreview(act, null, value, xmlRefresh);
                            if (xmlRefresh != null) {
                                UiHelper.applyWindowSurfaceColors(act);
                            }
                        }
                    }

                    @Override
                    public void onLivePreviewCancelled() {
                        Activity act = SettingsFragment.this.getActivity();
                        UiBackgroundManager.clearLivePreview();
                        if (act != null && !UiHelper.isLiquidGlassEnabled(act)) {
                            UiHelper.applyWindowSurfaceColors(act);
                        }
                    }
                });
            }

            refreshUiBackgroundPreferenceStates();
            PreferenceScreen screen = getPreferenceScreen();

            // hide on-screen controls category on non touch screen devices
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_onscreen_controls");
                screen.removePreference(category);
            }

            // Hide remote desktop mouse mode on pre-Oreo (which doesn't have pointer capture)
            // and NVIDIA SHIELD devices (which support raw mouse input in pointer capture mode)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    getActivity().getPackageManager().hasSystemFeature("com.nvidia.feature.shield")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_input_settings");
                category.removePreference(findPreference("checkbox_absolute_mouse_mode"));
            }

            // Hide gamepad motion sensor option when running on OSes before Android 12.
            // Support for motion, LED, battery, and other extensions were introduced in S.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_gamepad_motion_sensors"));
            }

            // Hide gamepad motion sensor fallback option if the device has no gyro or accelerometer
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER) &&
                    !getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_gamepad_motion_fallback"));
            }

            // Hide USB driver options on devices without USB host support
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_usb_bind_all"));
                category.removePreference(findPreference("checkbox_usb_driver"));
            }

            // Remove PiP mode on devices pre-Oreo, where the feature is not available (some low RAM devices),
            // and on Fire OS where it violates the Amazon App Store guidelines for some reason.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !getActivity().getPackageManager().hasSystemFeature("android.software.picture_in_picture") ||
                    getActivity().getPackageManager().hasSystemFeature("com.amazon.software.fireos")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_ui_settings");
                category.removePreference(findPreference("checkbox_enable_pip"));
            }

            // Fire TV apps are not allowed to use WebViews or browsers, so hide the Help category
            /*if (getActivity().getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_help");
                screen.removePreference(category);
            }*/
            PreferenceCategory category_gamepad_settings =
                    (PreferenceCategory) findPreference("category_gamepad_settings");
            // Remove the vibration options if the device can't vibrate
            if (!((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator()) {
                category_gamepad_settings.removePreference(findPreference("checkbox_vibrate_fallback"));
                category_gamepad_settings.removePreference(findPreference("seekbar_vibrate_fallback_strength"));
                // The entire OSC category may have already been removed by the touchscreen check above
                PreferenceCategory category = (PreferenceCategory) findPreference("category_onscreen_controls");
                if (category != null) {
                    category.removePreference(findPreference("checkbox_vibrate_osc"));
                }
            }
            else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasAmplitudeControl() ) {
                // Remove the vibration strength selector of the device doesn't have amplitude control
                category_gamepad_settings.removePreference(findPreference("seekbar_vibrate_fallback_strength"));
            }

            Display display = getActivity().getWindowManager().getDefaultDisplay();
            float maxSupportedFps = display.getRefreshRate();

            // Hide non-supported resolution/FPS combinations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int maxSupportedResW = 0;

                // Add a native resolution with any insets included for users that don't want content
                // behind the notch of their display
                boolean hasInsets = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use the much nicer Display.getCutout() API on Android 10+
                        cutout = display.getCutout();
                    }
                    else {
                        // Android 9 only
                        cutout = displayCutoutP;
                    }

                    if (cutout != null) {
                        int widthInsets = cutout.getSafeInsetLeft() + cutout.getSafeInsetRight();
                        int heightInsets = cutout.getSafeInsetBottom() + cutout.getSafeInsetTop();

                        if (widthInsets != 0 || heightInsets != 0) {
                            DisplayMetrics metrics = new DisplayMetrics();
                            display.getRealMetrics(metrics);

                            int width = Math.max(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);
                            int height = Math.min(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);

                            addNativeResolutionEntries(width, height, false);
                            hasInsets = true;
                        }
                    }
                }

                // Always allow resolutions that are smaller or equal to the active
                // display resolution because decoders can report total non-sense to us.
                // For example, a p201 device reports:
                // AVC Decoder: OMX.amlogic.avc.decoder.awesome
                // HEVC Decoder: OMX.amlogic.hevc.decoder.awesome
                // AVC supported width range: 64 - 384
                // HEVC supported width range: 64 - 544
                for (Display.Mode candidate : display.getSupportedModes()) {
                    // Some devices report their dimensions in the portrait orientation
                    // where height > width. Normalize these to the conventional width > height
                    // arrangement before we process them.

                    int width = Math.max(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());
                    int height = Math.min(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());

                    // Some TVs report strange values here, so let's avoid native resolutions on a TV
                    // unless they report greater than 4K resolutions.
                    if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                            (width > 3840 || height > 2160)) {
                        addNativeResolutionEntries(width, height, hasInsets);
                    }

                    if ((width >= 3840 || height >= 2160) && maxSupportedResW < 3840) {
                        maxSupportedResW = 3840;
                    }
                    else if ((width >= 2560 || height >= 1440) && maxSupportedResW < 2560) {
                        maxSupportedResW = 2560;
                    }
                    else if ((width >= 1920 || height >= 1080) && maxSupportedResW < 1920) {
                        maxSupportedResW = 1920;
                    }

                    if (candidate.getRefreshRate() > maxSupportedFps) {
                        maxSupportedFps = candidate.getRefreshRate();
                    }
                }

                // This must be called to do runtime initialization before calling functions that evaluate
                // decoder lists.
                MediaCodecHelper.initialize(getContext(), GlPreferences.readPreferences(getContext()).glRenderer);

                MediaCodecInfo avcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/avc", -1);
                MediaCodecInfo hevcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1);

                if (avcDecoder != null) {
                    Range<Integer> avcWidthRange = avcDecoder.getCapabilitiesForType("video/avc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("AVC supported width range: "+avcWidthRange.getLower()+" - "+avcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (avcWidthRange.contains(1280)) {
                        if (avcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (avcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                if (hevcDecoder != null) {
                    Range<Integer> hevcWidthRange = hevcDecoder.getCapabilitiesForType("video/hevc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("HEVC supported width range: "+hevcWidthRange.getLower()+" - "+hevcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (hevcWidthRange.contains(1280)) {
                        if (hevcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (hevcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                LimeLog.info("Maximum resolution slot: "+maxSupportedResW);

                if (maxSupportedResW != 0) {
                    if (maxSupportedResW < 3840) {
                        // 4K is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_4K, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    if (maxSupportedResW < 2560) {
                        // 1440p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    if (maxSupportedResW < 1920) {
                        // 1080p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P, new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_720P);
                                resetBitrateToDefault(prefs, null, null);
                            }
                        });
                    }
                    // Never remove 720p
                }
            }
            else {
                // We can get the true metrics via the getRealMetrics() function (unlike the lies
                // that getWidth() and getHeight() tell to us).
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                int width = Math.max(metrics.widthPixels, metrics.heightPixels);
                int height = Math.min(metrics.widthPixels, metrics.heightPixels);
                addNativeResolutionEntries(width, height, false);
            }

            if (!PreferenceConfiguration.readPreferences(this.getActivity()).unlockFps) {
                // We give some extra room in case the FPS is rounded down
                if (maxSupportedFps < 118) {
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "120", new Runnable() {
                        @Override
                        public void run() {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "90");
                            resetBitrateToDefault(prefs, null, null);
                        }
                    });
                }
                if (maxSupportedFps < 88) {
                    // 1080p is unsupported
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "90", new Runnable() {
                        @Override
                        public void run() {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "60");
                            resetBitrateToDefault(prefs, null, null);
                        }
                    });
                }
                // Never remove 30 FPS or 60 FPS
            }
            addNativeFrameRateEntry(maxSupportedFps);

            // Android L introduces the drop duplicate behavior of releaseOutputBuffer()
            // that the unlock FPS option relies on to not massively increase latency.
            findPreference(PreferenceConfiguration.UNLOCK_FPS_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // HACK: We need to let the preference change succeed before reinitializing to ensure
                    // it's reflected in the new layout.
                    final Handler h = new Handler();
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Ensure the activity is still open when this timeout expires
                            StreamSettings settingsActivity = (StreamSettings) SettingsFragment.this.getActivity();
                            if (settingsActivity != null) {
                                settingsActivity.reloadSettings();
                            }
                        }
                    }, 500);

                    // Allow the original preference change to take place
                    return true;
                }
            });

            // Remove HDR preference for devices below Nougat
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                LimeLog.info("Excluding HDR toggle based on OS");
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_basic_settings");
                category.removePreference(findPreference("checkbox_enable_hdr"));
            }
            else {
                Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();

                // We must now ensure our display is compatible with HDR10
                boolean foundHdr10 = false;
                if (hdrCaps != null) {
                    // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
                    for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                        if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                            foundHdr10 = true;
                            break;
                        }
                    }
                }

                if (!foundHdr10) {
                    LimeLog.info("Excluding HDR toggle based on display capabilities");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_basic_settings");
                    category.removePreference(findPreference("checkbox_enable_hdr"));
                }
                else if (PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()) {
                    LimeLog.info("Disabling HDR toggle on old broken SHIELD TV firmware");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_basic_settings");
                    CheckBoxPreference hdrPref = (CheckBoxPreference) category.findPreference("checkbox_enable_hdr");
                    hdrPref.setEnabled(false);
                    hdrPref.setChecked(false);
                    hdrPref.setSummary("Update the firmware on your NVIDIA SHIELD Android TV to enable HDR");
                }
            }

            // Add a listener to the FPS and resolution preference
            // so the bitrate can be auto-adjusted
            findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                    String valueStr = (String) newValue;

                    // Detect if this value is the native resolution option
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    boolean isNativeRes = true;
                    for (int i = 0; i < values.length; i++) {
                        // Look for a match prior to the start of the native resolution entries
                        if (valueStr.equals(values[i].toString()) && i < nativeResolutionStartIndex) {
                            isNativeRes = false;
                            break;
                        }
                    }

                    // If this is native resolution, show the warning dialog
                    if (isNativeRes) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_res_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, valueStr, null);

                    // Allow the original preference change to take place
                    return true;
                }
            });
            findPreference(PreferenceConfiguration.FPS_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsFragment.this.getActivity());
                    String valueStr = (String) newValue;

                    // If this is native frame rate, show the warning dialog
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    if (nativeFramerateShown && values[values.length - 1].toString().equals(newValue.toString())) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_fps_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, null, valueStr);

                    // Allow the original preference change to take place
                    return true;
                }
            });

            bitratePref = (SeekBarPreference) findPreference(PreferenceConfiguration.BITRATE_PREF_STRING);

            categoryList.clear();
            for (int i = 0; i < screen.getPreferenceCount(); i++) {
                Preference p = screen.getPreference(i);
                if (p instanceof PreferenceCategory) {
                    PreferenceCategory cat = (PreferenceCategory) p;
                    String key = cat.getKey();
                    if (key == null || key.isEmpty()) {
                        key = "category_" + i;
                    }
                    List<Preference> children = new ArrayList<>();
                    for (int j = 0; j < cat.getPreferenceCount(); j++) {
                        Preference child = cat.getPreference(j);
                        child.setOrder(j);
                        children.add(child);
                    }
                    for (int j = cat.getPreferenceCount() - 1; j >= 0; j--) {
                        cat.removePreference(cat.getPreference(j));
                    }
                    if (!children.isEmpty()) {
                        PreferenceScreen catScreen = getPreferenceManager().createPreferenceScreen(getActivity());
                        catScreen.setKey(key);
                        catScreen.setTitle(cat.getTitle());
                        for (Preference child : children) {
                            catScreen.addPreference(child);
                        }
                        categoryList.add(new CategoryTab(key, cat.getTitle(), catScreen));
                    }
                }
            }

            for (int i = screen.getPreferenceCount() - 1; i >= 0; i--) {
                screen.removePreference(screen.getPreference(i));
            }
            for (CategoryTab tab : categoryList) {
                screen.addPreference(tab.screen);
            }
        }
    }
}
