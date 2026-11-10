package com.limelight.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.limelight.R;

import java.util.List;

public class M3MenuHelper {

    public static class MenuOption {
        public final int id;
        public final String title;
        public final int iconResId;
        public final boolean isCheckable;
        public final boolean isChecked;
        public final boolean isDestructive;
        public final boolean isPrimary;

        public MenuOption(int id, String title, int iconResId, boolean isCheckable, boolean isChecked, boolean isDestructive, boolean isPrimary) {
            this.id = id;
            this.title = title;
            this.iconResId = iconResId;
            this.isCheckable = isCheckable;
            this.isChecked = isChecked;
            this.isDestructive = isDestructive;
            this.isPrimary = isPrimary;
        }

        public static MenuOption action(int id, String title, int iconResId) {
            return new MenuOption(id, title, iconResId, false, false, false, false);
        }

        public static MenuOption primary(int id, String title, int iconResId) {
            return new MenuOption(id, title, iconResId, false, false, false, true);
        }

        public static MenuOption destructive(int id, String title, int iconResId) {
            return new MenuOption(id, title, iconResId, false, false, true, false);
        }

        public static MenuOption checkable(int id, String title, int iconResId, boolean isChecked) {
            return new MenuOption(id, title, iconResId, true, isChecked, false, false);
        }
    }

    public interface OnMenuOptionSelectedListener {
        void onOptionSelected(MenuOption option);
    }

    private static int resolveThemeColor(Activity activity, int attrResId, int fallbackColor) {
        TypedValue typedValue = new TypedValue();
        if (activity.getTheme().resolveAttribute(attrResId, typedValue, true)) {
            return typedValue.data;
        }
        return fallbackColor;
    }

    /**
     * Shows a Material Design 3 menu:
     * - On touch devices (Phones, Tablets): M3 Modal BottomSheetDialog
     * - On Android TV / Leanback: Centered M3 MaterialAlertDialog
     */
    public static void showMenu(
            final Activity activity,
            String headerTitle,
            String headerSubtitle,
            int headerIconResId,
            List<MenuOption> options,
            final OnMenuOptionSelectedListener listener,
            final Runnable onDismiss) {

        if (activity.isFinishing()) {
            return;
        }

        final boolean isTv = UiHelper.isTvMode(activity);
        LayoutInflater inflater = LayoutInflater.from(activity);
        View sheetView = inflater.inflate(R.layout.m3_menu_sheet, null, false);

        View dragHandle = sheetView.findViewById(R.id.m3_sheet_drag_handle);
        if (isTv && dragHandle != null) {
            dragHandle.setVisibility(View.GONE);
        }

        ImageView headerIcon = sheetView.findViewById(R.id.m3_menu_header_icon);
        TextView titleView = sheetView.findViewById(R.id.m3_menu_header_title);
        TextView subtitleView = sheetView.findViewById(R.id.m3_menu_header_subtitle);
        LinearLayout container = sheetView.findViewById(R.id.m3_menu_items_container);

        if (headerIconResId != 0) {
            headerIcon.setImageResource(headerIconResId);
        }
        titleView.setText(headerTitle);

        if (headerSubtitle != null && !headerSubtitle.isEmpty()) {
            subtitleView.setText(headerSubtitle);
            subtitleView.setVisibility(View.VISIBLE);
        } else {
            subtitleView.setVisibility(View.GONE);
        }

        final Dialog dialog;
        if (isTv) {
            dialog = new MaterialAlertDialogBuilder(activity)
                    .setView(sheetView)
                    .create();
        } else {
            BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(activity);
            bottomSheetDialog.setContentView(sheetView);
            dialog = bottomSheetDialog;
        }

        int colorPrimary = resolveThemeColor(activity, androidx.appcompat.R.attr.colorPrimary, 0xFFBAC3FF);
        int colorError = resolveThemeColor(activity, androidx.appcompat.R.attr.colorError, 0xFFFFB4AB);

        View firstRow = null;
        for (final MenuOption option : options) {
            View row = inflater.inflate(R.layout.m3_menu_row, container, false);
            ImageView icon = row.findViewById(R.id.m3_menu_item_icon);
            TextView title = row.findViewById(R.id.m3_menu_item_title);
            final MaterialSwitch toggleSwitch = row.findViewById(R.id.m3_menu_item_switch);

            title.setText(option.title);

            if (option.iconResId != 0) {
                icon.setImageResource(option.iconResId);
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.GONE);
            }

            if (option.isDestructive) {
                title.setTextColor(colorError);
                icon.setImageTintList(ColorStateList.valueOf(colorError));
            } else if (option.isPrimary) {
                title.setTextColor(colorPrimary);
                icon.setImageTintList(ColorStateList.valueOf(colorPrimary));
            }

            if (option.isCheckable) {
                toggleSwitch.setVisibility(View.VISIBLE);
                toggleSwitch.setChecked(option.isChecked);
                toggleSwitch.jumpDrawablesToCurrentState();
            } else {
                toggleSwitch.setVisibility(View.GONE);
            }

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) {
                        listener.onOptionSelected(option);
                    }
                }
            });

            if (firstRow == null) {
                firstRow = row;
            }
            container.addView(row);
        }

        if (onDismiss != null) {
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface d) {
                    onDismiss.run();
                }
            });
        }

        final View focusTarget = firstRow;
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                if (isTv && focusTarget != null) {
                    focusTarget.requestFocus();
                }
            }
        });

        dialog.show();
    }
}
