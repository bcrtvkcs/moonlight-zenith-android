package com.limelight.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.limelight.R;

import static com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader.OSC_PREFERENCE;

public class ConfirmDeleteOscPreference extends DialogPreference {
    private View cachedView;

    public ConfirmDeleteOscPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initM3();
    }

    public ConfirmDeleteOscPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initM3();
    }

    public ConfirmDeleteOscPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initM3();
    }

    public ConfirmDeleteOscPreference(Context context) {
        super(context);
        initM3();
    }

    private void initM3() {
        if (getLayoutResource() != R.layout.m3_preference_item) {
            setLayoutResource(R.layout.m3_preference_item);
        }
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
        View badge = view.findViewById(R.id.m3_pref_value_badge);
        if (badge != null) {
            badge.setVisibility(View.GONE);
        }
        view.jumpDrawablesToCurrentState();
    }

    @Override
    protected void showDialog(Bundle state) {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(getDialogTitle() != null ? getDialogTitle() : getTitle())
                .setMessage(getDialogMessage())
                .setPositiveButton(getPositiveButtonText() != null ? getPositiveButtonText() : getContext().getString(R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        getContext().getSharedPreferences(OSC_PREFERENCE, Context.MODE_PRIVATE).edit().clear().apply();
                        Toast.makeText(getContext(), R.string.toast_reset_osc_success, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getNegativeButtonText() != null ? getNegativeButtonText() : getContext().getString(R.string.no), null)
                .show();
    }
}
