package com.limelight.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.limelight.R;

public class M3ListPreference extends ListPreference {
    private View cachedView;

    public M3ListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initM3();
    }

    public M3ListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initM3();
    }

    public M3ListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initM3();
    }

    public M3ListPreference(Context context) {
        super(context);
        initM3();
    }

    private void initM3() {
        if (getLayoutResource() != R.layout.m3_preference_item) {
            setLayoutResource(R.layout.m3_preference_item);
        }
    }

    private static int resolveThemeColor(Context context, int attrResId, int fallbackColor) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attrResId, typedValue, true)) {
            return typedValue.data;
        }
        return fallbackColor;
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        notifyChanged();
    }

    @Override
    public View getView(View convertView, ViewGroup parent) {
        if (convertView == null && cachedView != null && cachedView.getParent() == null) {
            convertView = cachedView;
        }
        View v = super.getView(convertView, parent);
        cachedView = v;
        return v;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        view.setAlpha(isEnabled() ? 1.0f : 0.42f);
        TextView badge = view.findViewById(R.id.m3_pref_value_badge);
        if (badge != null) {
            CharSequence entry = getEntry();
            if (entry != null && entry.length() > 0) {
                badge.setText(entry);
                badge.setVisibility(View.VISIBLE);
            } else {
                badge.setVisibility(View.GONE);
            }
        }
        view.jumpDrawablesToCurrentState();
    }

    @Override
    protected void showDialog(Bundle state) {
        final CharSequence[] entries = getEntries();
        final CharSequence[] entryValues = getEntryValues();
        if (entries == null || entryValues == null) {
            return;
        }

        final Context ctx = getContext();
        final int checkedItem = findIndexOfValue(getValue());
        final LayoutInflater inflater = LayoutInflater.from(ctx);

        final int colorPrimary = resolveThemeColor(ctx, androidx.appcompat.R.attr.colorPrimary, 0xFFBAC3FF);
        final int colorOnSurface = resolveThemeColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE3E2E9);
        final int colorOnSurfaceVariant = resolveThemeColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFC5C6D0);
        final int colorOnSecondaryContainer = resolveThemeColor(ctx, com.google.android.material.R.attr.colorOnSecondaryContainer, 0xFFDAE2FF);

        final ColorStateList radioTintList = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked}
                },
                new int[]{
                        colorPrimary,
                        colorOnSurfaceVariant
                }
        );

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return entries.length;
            }

            @Override
            public Object getItem(int position) {
                return entries[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.m3_dialog_single_choice_item, parent, false);
                }

                View container = convertView.findViewById(R.id.m3_choice_container);
                MaterialRadioButton radio = convertView.findViewById(R.id.m3_choice_radio);
                TextView title = convertView.findViewById(R.id.m3_choice_title);
                ImageView checkIcon = convertView.findViewById(R.id.m3_choice_check);

                boolean isSelected = (position == checkedItem);

                title.setText(entries[position]);
                radio.setButtonTintList(radioTintList);
                radio.setChecked(isSelected);
                radio.jumpDrawablesToCurrentState();

                container.setSelected(isSelected);
                if (isSelected) {
                    title.setTextColor(colorOnSecondaryContainer);
                    title.setTypeface(null, Typeface.BOLD);
                    checkIcon.setVisibility(View.VISIBLE);
                } else {
                    title.setTextColor(colorOnSurface);
                    title.setTypeface(null, Typeface.NORMAL);
                    checkIcon.setVisibility(View.GONE);
                }

                return convertView;
            }
        };

        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(getTitle())
                .setSingleChoiceItems(adapter, checkedItem, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which >= 0 && which < entryValues.length) {
                            String value = entryValues[which].toString();
                            if (callChangeListener(value)) {
                                setValue(value);
                            }
                        }
                        d.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.show();

        ListView listView = dialog.getListView();
        if (listView != null) {
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setSelector(android.R.color.transparent);
            listView.setScrollingCacheEnabled(false);
            listView.setAnimationCacheEnabled(false);
            if (checkedItem >= 0) {
                listView.setSelection(checkedItem);
            }
        }
    }
}
