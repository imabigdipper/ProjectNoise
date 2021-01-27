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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.projectnoise.MainActivity;
import com.example.projectnoise.R;

import org.jtransforms.fft.DoubleFFT_1D;

public class MeasureService extends Service {
    public static final String CHANNEL_ID = "MeasureServiceChannel";


    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
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
        String input = intent.getStringExtra("inputExtra");
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification foregroundNotification = createForegroundNotification(pendingIntent);

        startForeground(1,foregroundNotification);

        // Offload work to background thread

        StartRecorder();

        return  START_STICKY;
    }


    private Notification createForegroundNotification(PendingIntent pendingIntent) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setOngoing(true)
                .setContentTitle("Measure Service")
                .setContentText("Measuring dB")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();

        return notification;
    }

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


    // Measuring code

    private static String TAG = "Measure Service";

    private static final int RECORDER_SAMPLE_RATE = 44100;
    private static final int RECORDER_CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BYTES_PER_ELEMENT = 2
    private static final int RECORDER_BLOCK_SIZE = AudioRecord.getMinBufferSize(RECORDER_SAMPLE_RATE, RECORDER_CHANNEL, RECORDER_ENCODING)
            / BYTES_PER_ELEMENT;

    private final static int BLOCK_SIZE_FFT = 1764;
    private final static int FFT_PER_SECOND = RECORDER_SAMPLE_RATE
            / BLOCK_SIZE_FFT;
    private final static double FREQ_RESOLUTION = ((double) RECORDER_SAMPLE_RATE)
            / BLOCK_SIZE_FFT;
    private double filter = 0;

    private AudioRecord recorder;
    private static int BUFFER_SIZE = AudioRecord.getMinBufferSize(RECORDER_SAMPLE_RATE, RECORDER_CHANNEL, RECORDER_ENCODING);
    private boolean isRecording = false;

    private void StartRecorder() {
        Log.i(TAG, "Starting Audio Collection");
        recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                RECORDER_SAMPLE_RATE, RECORDER_CHANNEL, RECORDER_ENCODING,
                RECORDER_BLOCK_SIZE * BYTES_PER_ELEMENT);

        if (recorder.getState() == 1)
            Log.i(TAG, "Recorder is ready");

        else
            Log.i(TAG, "Recorder is not ready");

        recorder.startRecording();
        isRecording = true;

        DoubleFFT_1D fft = new DoubleFFT_1D(BLOCK_SIZE_FFT);

        Thread recorderThread = new Thread((new Runnable() {
            @Override
            public void run() {

                // Raw data array
                short rawData[] = new short[BLOCK_SIZE_FFT];
                // Unweighted amplitude array
                final float dbFft[] = new float[BLOCK_SIZE_FFT / 2];
                // Weighted amplitude array
                final float dbFftA[] = new float[BLOCK_SIZE_FFT / 2];
                float normalizedRawData;
                double[] fftAudioData = new double[BLOCK_SIZE_FFT * 2];
                float ampThreshold = 0.00002f;

                while (isRecording) {
                    // Read in one block of data
                    recorder.read(rawData,0, BLOCK_SIZE_FFT);
                    for (int i = 0, j = 0; i < BLOCK_SIZE_FFT; i++, j+=2) {
                        normalizedRawData = (float) rawData[i] / (float) Short.MAX_VALUE;

                        // Use Hanning window function
                        filter = normalizedRawData;
                        double x = (2 * Math.PI * i) / (BLOCK_SIZE_FFT - 1);
                        double winValue = (1 - Math.cos(x)) * 0.5d;


                    }
                }




            }
        }));




    }


    public void StopRecorder() {
        Log.i(TAG, "Stopping the audio stream");
        isRecording = false;
        recorder.release();
    }
}