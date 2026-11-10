package com.limelight.preferences;

import android.content.Context;
import android.preference.PreferenceCategory;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.limelight.R;

public class M3PreferenceCategory extends PreferenceCategory {
    private View cachedView;

    public M3PreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initM3();
    }

    public M3PreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initM3();
    }

    public M3PreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        initM3();
    }

    public M3PreferenceCategory(Context context) {
        super(context);
        initM3();
    }

    private void initM3() {
        if (getLayoutResource() != R.layout.m3_preference_category) {
            setLayoutResource(R.layout.m3_preference_category);
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
}
