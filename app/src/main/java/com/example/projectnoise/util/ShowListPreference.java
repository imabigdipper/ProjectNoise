package com.example.projectnoise.util;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

public class ShowListPreference extends ListPreference {


    public ShowListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ShowListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ShowListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ShowListPreference(Context context) {
        super(context);
    }

    public void show() {
        super.onClick();
    }
}
