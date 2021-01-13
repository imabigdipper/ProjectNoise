package com.example.projectnoise.services;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.projectnoise.R;

public class TestService extends Service {

    private HandlerThread handlerThread;
    private Handler backgroundHandler;
    private static String TAG = "Test Service";
    private Integer counter = 0;

    final Runnable r = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "Runnable Thread active for: " + counter + " seconds");
            counter++;
            backgroundHandler.postDelayed(this, 1000);
        }
    };


    public TestService() {
    }


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Creating TestService");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground((int) System.currentTimeMillis(), buildForegroundNotification());

        handlerThread = new HandlerThread("Test Thread");
        handlerThread.setDaemon(true);
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());

        backgroundHandler.postDelayed(r, 1000);

        return  START_STICKY;
    }


    @Override
    public void onDestroy() {
        handlerThread.quit();
        Toast.makeText(this, "Service Destroyed", Toast.LENGTH_SHORT).show();
        super.onDestroy();
    }


    private Notification buildForegroundNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.channel_id))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setContentTitle("PN Notification")
                .setContentText("Service Running")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        return builder.build();
    }
}