package com.example.projectnoise.ui.preferences;

import android.os.Bundle;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import com.example.projectnoise.R;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
    }
}