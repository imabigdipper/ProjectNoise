package com.example.projectnoise.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.projectnoise.R;
import com.example.projectnoise.services.TestService;
import com.example.projectnoise.util.DisplayReading;

import java.util.Locale;

public class HomeFragment extends Fragment implements DisplayReading.OnUpdateCallback {

    private static String TAG = "Home Fragment";

    private HomeViewModel homeViewModel;
    private double dBvalue;
    private TextView dBreading;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        final TextView textView = root.findViewById(R.id.text_home);
        homeViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });
        return root;
    }



    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dBreading = view.findViewById(R.id.db_reading_text);

        // On click, generate and intent to start/stop the TestService

        view.findViewById(R.id.button_start).setOnClickListener(view12 -> {
            Intent intent = new Intent(getActivity(), TestService.class);
            Log.i(TAG, "Intent made");
            getActivity().startService(intent);
        });

        view.findViewById(R.id.button_stop).setOnClickListener(view1 -> {
            Intent intent = new Intent(getActivity(), TestService.class);
            Log.i(TAG, "Intent made");
            getActivity().stopService(intent);
        });
    }

    @Override
    public void onUpdate(double instantDB) {
        this.dBvalue = instantDB;
        String text = String.format(Locale.US, "%.02f", instantDB) + " dB";
        this.dBreading.setText(text);
    }
}