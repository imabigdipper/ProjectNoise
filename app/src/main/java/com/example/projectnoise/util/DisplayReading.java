package com.example.projectnoise.util;

import android.os.AsyncTask;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/** No idea how this works lol im still working on it **/

public class DisplayReading extends AppCompatActivity {

    private OnUpdateCallback callback;

    public void setCallback(OnUpdateCallback callback) {
        this.callback = callback;
    }



    public interface OnUpdateCallback {
        void onUpdate(double instantDB);
    }
}
