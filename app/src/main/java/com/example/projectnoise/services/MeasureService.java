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
import android.os.IBinder;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.projectnoise.MainActivity;
import com.example.projectnoise.R;
import com.example.projectnoise.util.Values;

import org.jtransforms.fft.DoubleFFT_1D;

public class MeasureService extends Service {
    public static final String CHANNEL_ID = "MeasureServiceChannel";


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

        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        // Start foreground service with given foreground notification
        startForeground(1, createForegroundNotification(pendingIntent));
        Log.i(TAG, "Started in Foreground");

        // Offload work to background thread
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
     * dB measuring code using AudioRecord. Borrowed from:
     *  https://github.com/gworkman/SoundMap/blob/master/app/src/main/java/edu/osu/sphs/soundmap/util/MeasureTask.java
     **/

    private static final String TAG = "Measure Service";

    // AudioRecord instance configuration
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    int bufferSize = 8192; // 2 ^ 13, necessary for the fft

    private DoubleFFT_1D transform = new DoubleFFT_1D(8192);
    private AudioRecord recorder;
    private Thread recorderThread;

    private double calibration = 0;
    private boolean isRecording = false;

    private void startRecorder() {
        // Record for 1m
         long endTime = System.currentTimeMillis()+ 100000;

        recorder = new AudioRecord(SOURCE, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize);
        recorder.startRecording();
        isRecording = true;

        short[] buffer = new short[bufferSize];

        // Define and run thread to do AudioRecord background work
        Thread recorderThread = new Thread((() -> {
            double dB;
            double average;
            double dbSumTotal = 0;
            double instant;
            int count = 0;

            // Continuously read audio into buffer for 1m
            while (System.currentTimeMillis() < endTime) {
                recorder.read(buffer, 0, bufferSize);
                //os.write(buffer, 0, buffer.length); for writing data to output file; buffer must be byt
                dB = doFFT(buffer); // Perform Fast Fourier Transform
                if (dB != Double.NEGATIVE_INFINITY) {
                    dbSumTotal += dB;
                    count++;
                }
                average = 20 * Math.log10(dbSumTotal / count) + 8.25 + calibration;
                instant = 20 * Math.log10(dB) + 8.25 + calibration;
                Log.i(TAG, "instant: " + instant);
                Log.i(TAG, "average: " + average);
            }
        }), "AudioRecorder Thread");

        Log.i(TAG, "Starting recorder thread");
        recorderThread.start();
    }

    public void stopRecorder() {
        Log.i(TAG, "Stopping the audio stream");
        if (recorder != null) {
            isRecording = false;
            try {
                recorderThread.join();
                //fos.close();
            } catch (Exception e) {
                Log.d(TAG,
                        "Primary thread cannot wait for secondary audio thread to close");
            }

            // Properly stop and release AudioRecord instance
            recorder.stop();
            recorder.release();
            recorder = null;
            recorderThread = null;
        }
    }


    /** Helper function to do Fast Fourier Transform. Uses JTransforms dependency **/

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
}