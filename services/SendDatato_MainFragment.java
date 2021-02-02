package com.example.projectnoise.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class SendDatato_MainFragment extends Service {





    private void sendDataToActivity()
    {
        Intent sendLevel = new Intent();
        sendLevel.setAction("Get Db Level");
        sendLevel.putExtra( "LEVEL_DATA","Strength_Value");
        sendBroadcast(sendLevel);
    }
    protected void onHandleIntent(@Nullable Intent intent) {

        sendDataToActivity();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
