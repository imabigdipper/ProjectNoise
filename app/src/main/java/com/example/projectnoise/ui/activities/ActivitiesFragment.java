package com.example.projectnoise.ui.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.example.projectnoise.R;
import com.example.projectnoise.util.ShowListPreference;

public class ActivitiesFragment extends PreferenceFragmentCompat {
    private SharedPreferences preferences;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.activity_preferences, rootKey);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        super.onViewCreated(view, savedInstanceState);
        if (preferences.getBoolean("auto_activity_list", true)) {
            PreferenceScreen preferenceScreen = findPreference("activity_screen");
            ShowListPreference preference = findPreference("current_activity");
            onDisplayPreferenceDialog(preference);
        }
    }
}