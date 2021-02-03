package com.example.projectnoise.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.projectnoise.MainActivity;
import com.example.projectnoise.R;
import com.example.projectnoise.util.Values;

import org.jtransforms.fft.DoubleFFT_1D;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MeasureService extends Service {
    public static final String CHANNEL_ID = "MeasureServiceChannel";
    private static final String FILE_NAME = "example.txt";


    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        stopRecorder();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Creates notification channel & notification in preparation to launch service in foreground
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        // Start foreground service with given foreground notification
        startForeground(1, createForegroundNotification(pendingIntent));
        Log.i(TAG, "Started in Foreground");

        // TODO Find a way to set up the calibration variable before starting the measuring thread
        // Helper function to set up thread for measuring sound data
        startRecorder();

        return  START_STICKY;
    }


    /** Helper function to create foreground notification **/

    private Notification createForegroundNotification(PendingIntent pendingIntent) {

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setOngoing(true)
                .setContentTitle("Measure Service")
                .setContentText("Measuring dB")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();
    }


    /** Helper function to create notification channel **/

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Measure Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }


    /**
     * This chunk of the service is all about the dB measuring process
     **/

    private static final String TAG = "Measure Service";

    long interval = 5000;

    // AudioRecord instance configuration
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;

    int bufferSize = 8192; // 2 ^ 13, necessary for the fft
    private final DoubleFFT_1D transform = new DoubleFFT_1D(8192);


    private AudioRecord recorder;
    private HandlerThread handlerThread;
    private android.os.Handler handler;

    private double calibration = 0;
    private boolean isRecording = false;


    /** Runnable executed inside the HandlerThread. Measures sound data over the given interval then calculates average dB. Re-invokes itself until service is killed. Heavily based on:
     * https://github.com/gworkman/SoundMap/blob/master/app/src/main/java/edu/osu/sphs/soundmap/util/MeasureTask.java
     */

    Runnable measureRunnable = new Runnable() {
        @Override
        public void run() {
            long startTime = SystemClock.uptimeMillis();
            long measureTime = SystemClock.uptimeMillis() + interval;
            Log.d(TAG, "Measuring for " + (interval / 1000) + " seconds");
            try {
                recorder.startRecording();
                isRecording = true;
            } catch (Exception e) {
                Log.e(TAG, "AudioRecord not initialized");
                return;
            }

            short[] buffer = new short[bufferSize];
            double dB;
            double dbSumTotal = 0;
            double instant;
            int count = 0;
            double average = 0;

            // Continuously read audio into buffer for measureTime ms
            while (SystemClock.uptimeMillis() < measureTime) {
                recorder.read(buffer, 0, bufferSize);

                //os.write(buffer, 0, buffer.length); for writing data to output file; buffer must be byte

                dB = doFFT(buffer); // Perform Fast Fourier Transform
                if (dB != Double.NEGATIVE_INFINITY) {
                    dbSumTotal += dB;
                    count++;
                }
                average = 20 * Math.log10(dbSumTotal / count) + 8.25 + calibration;
                instant = 20 * Math.log10(dB) + 8.25 + calibration;
//                Log.i(TAG, "instant: " + instant);
//                Log.i(TAG, "average: " + average);
            }
            recorder.stop();
            Log.i(TAG, "Average dB over " + interval + " seconds: " + average);
            // TODO export average and time to file
            String log = "Average dB over " + interval + " seconds: " + average;
            write(log);

            long endTime = SystemClock.uptimeMillis();
            long wait = 10000 - (endTime - startTime);
            Log.d(TAG, "Waiting for " + wait/(long) 1000 + " seconds");

            // Check if recording service has ended or not
            if (isRecording) {
                // Call the runnable again to measure average of next time block
                handler.postDelayed(this, wait);
            } else {
                // Thread is done recording, release AudioRecord instance
                Log.d(TAG, "Stopping measuring thread");
                recorder.release();
                recorder = null;
                Log.d(TAG, "Successfully released AudioRecord instance");
            }
        }
    };


    /** Prepares AudioRecord, Handler, and HandlerThread instances then posts measureRunnable to the thread. **/

    private void startRecorder() {
        recorder = new AudioRecord(SOURCE, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize);
        // Handler and HandlerThread setup
        handlerThread = new HandlerThread("measureThread");
        handlerThread.start();
        handler = new android.os.Handler(handlerThread.getLooper());
        handler.post(measureRunnable);
    }

    public void stopRecorder() {
        Log.i(TAG, "Stopping the audio stream");
        if (recorder != null) {
            isRecording = false;
            try {
                handler.removeCallbacksAndMessages(null);
                handlerThread.quit();
            } catch (Exception e) {
                Log.d(TAG,
                        "handlerThread failed to quit");
            }
        }
    }


    /** Helper function to do Fast Fourier Transform using JTransforms**/

    private double doFFT(short[] rawData) {
        double[] fft = new double[2 * rawData.length];
        double avg = 0.0, amplitude = 0.0;

        // Get a half-filled array of double values for FFT calculation
        for (int i = 0; i < rawData.length; i++) {
            fft[i] = rawData[i] / ((double) Short.MAX_VALUE);
        }

        // FFT
        transform.realForwardFull(fft);

        // Calculate the sum of amplitudes
        for (int i = 0; i < fft.length; i += 2) {
            //                              reals                 imaginary
            amplitude += Math.sqrt(Math.pow(fft[i], 2) + Math.pow(fft[i + 1], 2));
            avg += amplitude * Values.A_WEIGHT_COEFFICIENTS[i / 2];
        }
        return avg / rawData.length;
    }

    public void write(String text){

        FileOutputStream fos = null;

        try {
            fos = openFileOutput(FILE_NAME, MODE_PRIVATE);
            fos.write(text.getBytes());


            Toast.makeText(this,"Saved to " + getFilesDir() + "/" + FILE_NAME, Toast.LENGTH_LONG).show();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        finally {
            if(fos!=null){
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }


}