package com.example.projectnoise.ui.activities;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import com.example.projectnoise.R;

public class ActivitiesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.activity_preferences, rootKey);
    }
}