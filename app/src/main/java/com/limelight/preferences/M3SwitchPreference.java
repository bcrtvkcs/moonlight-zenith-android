package com.limelight.preferences;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.limelight.R;

public class M3SwitchPreference extends CheckBoxPreference {
    private View cachedView;

    public M3SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initM3();
    }

    public M3SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initM3();
    }

    public M3SwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initM3();
    }

    public M3SwitchPreference(Context context) {
        super(context);
        initM3();
    }

    private void initM3() {
        if (getLayoutResource() != R.layout.m3_preference_item) {
            setLayoutResource(R.layout.m3_preference_item);
        }
        if (getWidgetLayoutResource() != R.layout.m3_preference_widget_switch) {
            setWidgetLayoutResource(R.layout.m3_preference_widget_switch);
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
        boolean isSamePreference = (view.getTag() == this);
        view.setTag(this);

        super.onBindView(view);
        view.setAlpha(isEnabled() ? 1.0f : 0.42f);

        View badge = view.findViewById(R.id.m3_pref_value_badge);
        if (badge != null) {
            badge.setVisibility(View.GONE);
        }

        if (!isSamePreference) {
            View checkboxView = view.findViewById(android.R.id.checkbox);
            if (checkboxView != null) {
                checkboxView.jumpDrawablesToCurrentState();
            }
            view.jumpDrawablesToCurrentState();
        }
    }
}
