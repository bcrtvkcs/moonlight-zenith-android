package com.limelight.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.limelight.R;

import java.util.Locale;

public class SeekBarPreference extends DialogPreference
{
    private static final String ANDROID_SCHEMA_URL = "http://schemas.android.com/apk/res/android";
    private static final String SEEKBAR_SCHEMA_URL = "http://schemas.moonlight-stream.com/apk/res/seekbar";

    private SeekBar seekBar;
    private TextView valueText;
    private View cachedView;
    private final Context context;

    private final String dialogMessage;
    private final String suffix;
    private final int defaultValue;
    private final int maxValue;
    private final int minValue;
    private final int stepSize;
    private final int keyStepSize;
    private final int divisor;
    private int currentValue;

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        if (getLayoutResource() != R.layout.m3_preference_item) {
            setLayoutResource(R.layout.m3_preference_item);
        }

        // Read the message from XML
        int dialogMessageId = attrs.getAttributeResourceValue(ANDROID_SCHEMA_URL, "dialogMessage", 0);
        if (dialogMessageId == 0) {
            dialogMessage = attrs.getAttributeValue(ANDROID_SCHEMA_URL, "dialogMessage");
        }
        else {
            dialogMessage = context.getString(dialogMessageId);
        }

        // Get the suffix for the number displayed in the dialog
        int suffixId = attrs.getAttributeResourceValue(ANDROID_SCHEMA_URL, "text", 0);
        if (suffixId == 0) {
            suffix = attrs.getAttributeValue(ANDROID_SCHEMA_URL, "text");
        }
        else {
            suffix = context.getString(suffixId);
        }

        // Get default, min, and max seekbar values
        defaultValue = attrs.getAttributeIntValue(ANDROID_SCHEMA_URL, "defaultValue", PreferenceConfiguration.getDefaultBitrate(context));
        maxValue = attrs.getAttributeIntValue(ANDROID_SCHEMA_URL, "max", 100);
        minValue = attrs.getAttributeIntValue(SEEKBAR_SCHEMA_URL, "min", 1);
        stepSize = attrs.getAttributeIntValue(SEEKBAR_SCHEMA_URL, "step", 1);
        divisor = attrs.getAttributeIntValue(SEEKBAR_SCHEMA_URL, "divisor", 1);
        keyStepSize = attrs.getAttributeIntValue(SEEKBAR_SCHEMA_URL, "keyStep", 0);
    }

    private String formatValueText(int value) {
        int roundedValue = ((value + (stepSize - 1)) / stepSize) * stepSize;
        String t;
        if (divisor != 1) {
            float floatValue = roundedValue / (float) divisor;
            t = String.format((Locale) null, "%.1f", floatValue);
        } else {
            t = String.valueOf(roundedValue);
        }
        return suffix == null ? t : t.concat(suffix.length() > 1 ? " " + suffix : suffix);
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
        if (shouldPersist()) {
            currentValue = getPersistedInt(defaultValue);
        }
        TextView badge = view.findViewById(R.id.m3_pref_value_badge);
        if (badge != null) {
            badge.setText(formatValueText(currentValue));
            badge.setVisibility(View.VISIBLE);
        }
        view.jumpDrawablesToCurrentState();
    }

    @Override
    protected View onCreateDialogView() {
        View view = LayoutInflater.from(context).inflate(R.layout.m3_dialog_slider, null, false);

        TextView splashText = view.findViewById(R.id.m3_slider_message);
        if (dialogMessage != null && !dialogMessage.isEmpty()) {
            splashText.setText(dialogMessage);
            splashText.setVisibility(View.VISIBLE);
        } else {
            splashText.setVisibility(View.GONE);
        }

        valueText = view.findViewById(R.id.m3_slider_value_text);
        seekBar = view.findViewById(R.id.m3_slider_seekbar);

        if (shouldPersist()) {
            currentValue = getPersistedInt(defaultValue);
        }

        valueText.setText(formatValueText(currentValue));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                if (value < minValue) {
                    seekBar.setProgress(minValue);
                    return;
                }

                int roundedValue = ((value + (stepSize - 1))/stepSize)*stepSize;
                if (roundedValue != value) {
                    seekBar.setProgress(roundedValue);
                    return;
                }

                valueText.setText(formatValueText(roundedValue));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBar.setMax(maxValue);
        if (keyStepSize != 0) {
            seekBar.setKeyProgressIncrement(keyStepSize);
        }
        seekBar.setProgress(currentValue);

        return view;
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        seekBar.setMax(maxValue);
        if (keyStepSize != 0) {
            seekBar.setKeyProgressIncrement(keyStepSize);
        }
        seekBar.setProgress(currentValue);
    }

    @Override
    protected void onSetInitialValue(boolean restore, Object defaultValue)
    {
        super.onSetInitialValue(restore, defaultValue);
        if (restore) {
            currentValue = shouldPersist() ? getPersistedInt(this.defaultValue) : 0;
        }
        else {
            currentValue = (Integer) defaultValue;
        }
    }

    public void setProgress(int progress) {
        this.currentValue = progress;
        if (seekBar != null) {
            seekBar.setProgress(progress);
        }
        notifyChanged();
    }

    public int getProgress() {
        return currentValue;
    }

    @Override
    public void showDialog(Bundle state) {
        View contentView = onCreateDialogView();
        onBindDialogView(contentView);

        new MaterialAlertDialogBuilder(context)
                .setTitle(getTitle())
                .setView(contentView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (shouldPersist()) {
                            currentValue = seekBar.getProgress();
                            persistInt(seekBar.getProgress());
                            callChangeListener(seekBar.getProgress());
                            notifyChanged();
                        }
                        d.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
