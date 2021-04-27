package com.example.projectnoise.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
        if (preferences.getBoolean(getString(R.string.pref_auto_activity_list), true)) {
            PreferenceScreen preferenceScreen = findPreference(getString(R.string.prefscreen_activity));
            ShowListPreference preference = findPreference(getString(R.string.act_current_activity));
            onDisplayPreferenceDialog(preference);
        }
    }


}