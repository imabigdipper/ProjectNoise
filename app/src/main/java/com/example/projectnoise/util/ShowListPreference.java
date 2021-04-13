package com.example.projectnoise.util;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

/**
 * This class is an exact copy of the ListPreference. Having a custom copy of it allows us to access core methods that allow the Auto-Show Activity list function to work.
 */

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
