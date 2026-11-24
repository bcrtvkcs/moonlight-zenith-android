package com.limelight.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.limelight.R;

/**
 * Multi-select Audio Output preference allowing independent selection of Client (this device)
 * and/or Host (PC) playback: Client only, Host only, Client + Host, or None (Muted).
 * Persists directly to existing `checkbox_host_audio` and `checkbox_client_audio` keys so
 * upgrades from previous versions preserve user preferences cleanly.
 */
public class M3AudioOutputPreference extends Preference {
    private View cachedView;

    public M3AudioOutputPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initM3();
    }

    public M3AudioOutputPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initM3();
    }

    public M3AudioOutputPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initM3();
    }

    public M3AudioOutputPreference(Context context) {
        super(context);
        initM3();
    }

    private void initM3() {
        setPersistent(false);
        if (getLayoutResource() != R.layout.m3_preference_item) {
            setLayoutResource(R.layout.m3_preference_item);
        }
    }

    public boolean isClientEnabled() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        return prefs.getBoolean(
                PreferenceConfiguration.CLIENT_AUDIO_PREF_STRING,
                PreferenceConfiguration.DEFAULT_CLIENT_AUDIO
        );
    }

    public boolean isHostEnabled() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        return prefs.getBoolean(
                PreferenceConfiguration.HOST_AUDIO_PREF_STRING,
                PreferenceConfiguration.DEFAULT_HOST_AUDIO
        );
    }

    public void applySelection(boolean clientEnabled, boolean hostEnabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        prefs.edit()
                .putBoolean(PreferenceConfiguration.CLIENT_AUDIO_PREF_STRING, clientEnabled)
                .putBoolean(PreferenceConfiguration.HOST_AUDIO_PREF_STRING, hostEnabled)
                .apply();
        callChangeListener(getDisplayBadgeText());
        notifyChanged();
    }

    public String getDisplayBadgeText() {
        Context ctx = getContext();
        boolean client = isClientEnabled();
        boolean host = isHostEnabled();
        if (client && host) {
            return ctx.getString(R.string.audio_output_both);
        } else if (client) {
            return ctx.getString(R.string.audio_output_client);
        } else if (host) {
            return ctx.getString(R.string.audio_output_host);
        } else {
            return ctx.getString(R.string.audio_output_none);
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
        view.setAlpha(isEnabled() ? 1.0f : 0.42f);
        TextView badge = view.findViewById(R.id.m3_pref_value_badge);
        if (badge != null) {
            String badgeText = getDisplayBadgeText();
            if (badgeText != null && !badgeText.isEmpty()) {
                badge.setText(badgeText);
                badge.setVisibility(View.VISIBLE);
            } else {
                badge.setVisibility(View.GONE);
            }
        }
        view.jumpDrawablesToCurrentState();
    }

    @Override
    protected void onClick() {
        super.onClick();
        final Context ctx = getContext();
        final boolean[] checked = new boolean[]{
                isClientEnabled(),
                isHostEnabled()
        };
        final CharSequence[] labels = new CharSequence[]{
                ctx.getString(R.string.audio_output_client),
                ctx.getString(R.string.audio_output_host)
        };

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(getTitle())
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        if (which >= 0 && which < checked.length) {
                            checked[which] = isChecked;
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        applySelection(checked[0], checked[1]);
                    }
                })
                .show();
    }
}
