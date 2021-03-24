package com.example.projectnoise.ui.preferences;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.example.projectnoise.services.MeasureService;
import com.example.projectnoise.util.ShowListPreference;
import com.takisoft.preferencex.PreferenceFragmentCompat;

import com.example.projectnoise.R;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
    }

    @Override
    public void onCreate(Bundle savedInstance){

        super.onCreate(savedInstance);
        PreferenceManager.setDefaultValues(this.getContext(), R.xml.root_preferences, false);
        Preference start_button = findPreference("service_start");
        start_button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getActivity(), MeasureService.class);
                getActivity().startService(intent);
                return true;
            }
        });

        Preference stop_button = findPreference("service_stop");
        stop_button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getActivity(), MeasureService.class);
                getActivity().stopService(intent);
                return true;
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        PreferenceScreen preferenceScreen = (PreferenceScreen)findPreference("activity_screen");
        ShowListPreference preference = findPreference("current_activity");
        onDisplayPreferenceDialog(preference);
    }
}