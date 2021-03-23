package com.example.projectnoise.ui.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceFragmentCompat;

import com.example.projectnoise.MainActivity;
import com.example.projectnoise.R;

public class PasswordFragment extends Fragment {
    private Button btnSubmit;
    private EditText password;

    public PasswordFragment(){
        super(R.layout.password);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.password, container, false);

        password = (EditText) view.findViewById(R.id.editPass);
        btnSubmit = (Button) view.findViewById(R.id.btnSubmit);

        btnSubmit.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                //get input from textbox
                String text = password.getText().toString();
                //see if it matches password (password is defined in res/values/strings.xml
                if(text.equals(getString(R.string.password))){
                    //navigate to settings page
                    Navigation.findNavController(view).navigate(R.id.navigation_preferences);
                }


            }

        });


        return view;
    }

}
