package com.example.projectnoise.ui.activities;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.example.projectnoise.R;

public class ActivitiesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.activity_preferences, rootKey);
        ListPreference currentActivity = findPreference("current_activity");
        Preference customActivity = findPreference("custom_activity");

        customActivity.setEnabled(currentActivity.getValue().equals("custom"));
        currentActivity.setOnPreferenceChangeListener((preference, newValue) -> {
            final String val = newValue.toString();
            int index = currentActivity.findIndexOfValue(val);
            customActivity.setEnabled(val.equals("custom"));
            return true;
        });

    }

//    @Override
//    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
//
//    }
}