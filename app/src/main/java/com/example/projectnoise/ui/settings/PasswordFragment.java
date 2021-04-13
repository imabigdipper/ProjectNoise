package com.example.projectnoise.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.projectnoise.R;

public class PasswordFragment extends Fragment {
    private Button btnSubmit;
    private EditText password;
    private SharedPreferences preferences;

    public PasswordFragment(){
        super(R.layout.password);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view,savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        // If password setting is disabled, bypass screen
        if (!preferences.getBoolean(getString(R.string.pref_toggle_password), true))
            Navigation.findNavController(view).navigate(R.id.navigation_preferences);

        // Find password field and button
        password = view.findViewById(R.id.editPass);
        btnSubmit = view.findViewById(R.id.btnSubmit);

        btnSubmit.setOnClickListener(v -> {
            // Get input from textbox
            String text = password.getText().toString();
            // See if it matches password, defined in res/xml/root_preferences.xml
            if(text.equals(preferences.getString(getString(R.string.pref_settings_password), "pnoise"))){
                InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                // Navigate to settings page
                Navigation.findNavController(view).navigate(R.id.navigation_preferences);
            }
        });
    }
}