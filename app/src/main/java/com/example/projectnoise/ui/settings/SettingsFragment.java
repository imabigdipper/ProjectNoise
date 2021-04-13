package com.example.projectnoise.ui.settings;

import android.content.Intent;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.example.projectnoise.services.MeasureService;
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
        Preference start_button = findPreference(getString(R.string.pref_service_start));
        start_button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getActivity(), MeasureService.class);
                getActivity().startService(intent);
                return true;
            }
        });

        Preference stop_button = findPreference(getString(R.string.pref_service_stop));
        stop_button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getActivity(), MeasureService.class);
                getActivity().stopService(intent);
                return true;
            }
        });
    }
}