package com.example.projectnoise.services;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.projectnoise.R;

import java.io.IOException;

public class TestService extends Service {

    private HandlerThread handlerThread;
    private Handler backgroundHandler;
    private static String TAG = "Test Service";
    private Integer counter = 0;
    private MediaRecorder mRecorder;
    private static final int RECORD_REQUEST_CODE = 99;


    /** Runnable which is executed by the background handler thread **/

    final Runnable measureRunnable = new Runnable() {
        @Override
        public void run() {
            double amp = mRecorder.getMaxAmplitude();
            double dbReading = 20 * Math.log10(amp / 2700.0);
            Log.i(TAG, "Max Instant dB Reading " + dbReading);
            backgroundHandler.postDelayed(this, 1000); // Run the same code again after 1000ms

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

        // Call buildForegroundNotification to return a persistent notification to display while the service is running
        startForeground((int) System.currentTimeMillis(), buildForegroundNotification());

        // Start MediaRecorder to collect audio data
        startMediaRecorder().start();

        // Start thread
        handlerThread = new HandlerThread("Test Thread");
        handlerThread.setDaemon(true);
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());

        backgroundHandler.postDelayed(measureRunnable, 1000);

        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        stopMediaRecorder(mRecorder);
        mRecorder = null;
        handlerThread.quit();
        Toast.makeText(this, "Service Destroyed", Toast.LENGTH_SHORT).show();
        super.onDestroy();
    }


    /** Helper function to build the persistent notification **/

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


    /** Helper function to start & configure an instance of MediaRecorder **/

    private MediaRecorder startMediaRecorder() {
        // Start MediaRecorder
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mRecorder.setOutputFile("/dev/null");
        try {
            mRecorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (
                IOException e) {
            e.printStackTrace();
        }
        return mRecorder;
    }

    private void stopMediaRecorder(MediaRecorder mRecorder) {
        mRecorder.stop();
        mRecorder.reset();
        mRecorder.release();
    }
}