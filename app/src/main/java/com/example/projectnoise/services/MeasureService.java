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
        String input = intent.getStringExtra("inputExtra");
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification foregroundNotification = createForegroundNotification(pendingIntent);

        startForeground(1,foregroundNotification);

        Log.i(TAG, "Started in Foreground");

        // Offload work to background thread
        startRecorder();

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
    private static final int BYTES_PER_ELEMENT = 2;
    private static final int RECORDER_BLOCK_SIZE = AudioRecord.getMinBufferSize(RECORDER_SAMPLE_RATE, RECORDER_CHANNEL, RECORDER_ENCODING)
            / BYTES_PER_ELEMENT;

    private final static int BLOCK_SIZE_FFT = 44100;
    private final static int FFT_PER_SECOND = RECORDER_SAMPLE_RATE
            / BLOCK_SIZE_FFT;
    private final static double FREQ_RESOLUTION = ((double) RECORDER_SAMPLE_RATE)
            / BLOCK_SIZE_FFT;
    private double filter = 0;
    private double[] weightedA = new double[BLOCK_SIZE_FFT];
    private float gain;

    private float [] THIRD_OCTAVE = {16, 20, 25, 31.5f, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500,
            630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000};
    String [] THIRD_OCTAVE_LABEL = {"16", "20", "25", "31.5", "40", "50", "63", "80", "100", "125", "160", "200", "250", "315", "400", "500",
            "630", "800", "1000", "1250", "1600", "2000", "2500", "3150", "4000", "5000", "6300", "8000", "10000", "12500", "16000", "20000"};


    private Thread recorderThread = null;

    double linearFftAGlobalRunning = 0;
    private long fftCount = 0;
    private double dbFftAGlobalRunning = 0;

    // SLM mix and max
    double dbFftAGlobalMinTemp = 0;
    double dbFftAGlobalMaxTemp = 0;
    int dbFftAGlobalMinFirst = 0;
    int dbFftAGlobalMaxFirst = 0;

    // Final vars
    private double dbFftAGlobalMax = 0;
    private double dbFftAGlobalMin = 0;
    private double dbATimeDisplay = 0;

    private AudioRecord recorder;
    private static int BUFFER_SIZE = AudioRecord.getMinBufferSize(RECORDER_SAMPLE_RATE, RECORDER_CHANNEL, RECORDER_ENCODING);
    private boolean isRecording = false;

    private void precalculateWeightedA() {
        for (int i = 0; i < BLOCK_SIZE_FFT; i++) {
            double actualFreq = FREQ_RESOLUTION * i;
            double actualFreqSQ = actualFreq * actualFreq;
            double actualFreqFour = actualFreqSQ * actualFreqSQ;
            double actualFreqEight = actualFreqFour * actualFreqFour;

            double t1 = 20.598997 * 20.598997 + actualFreqSQ;
            t1 = t1 * t1;
            double t2 = 107.65265 * 107.65265 + actualFreqSQ;
            double t3 = 737.86223 * 737.86223 + actualFreqSQ;
            double t4 = 12194.217 * 12194.217 + actualFreqSQ;
            t4 = t4 * t4;

            double weightFormula = (3.5041384e16 * actualFreqEight)
                    / (t1 * t2 * t3 * t4);

            weightedA[i] = weightFormula;
        }
    }

    private void startRecorder() {
        Log.i(TAG, "Starting Audio Collection");
        recorder = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED,
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

                final float[] dbBand = new float[THIRD_OCTAVE.length];
                final float[] linearBand = new float[THIRD_OCTAVE.length];
                final float[] linearBandCount = new float[THIRD_OCTAVE.length];
                int n = 3;

                // Variabili per calcolo medie Time Log
                int indexTimeLog = 0;
                double linearTimeLog = 0;
                double linearATimeLog = 0;
                final float[] linearBandTimeLog = new float[THIRD_OCTAVE.length];

                while (isRecording) {
                    // Read in one block of data
                    recorder.read(rawData,0, BLOCK_SIZE_FFT);
                    Log.i(TAG, "Raw Data: " + rawData);
                    for (int i = 0, j = 0; i < BLOCK_SIZE_FFT; i++, j+=2) {
                        normalizedRawData = (float) rawData[i] / (float) Short.MAX_VALUE;

                        // Use Hanning window function
                        filter = normalizedRawData;
                        double x = (2 * Math.PI * i) / (BLOCK_SIZE_FFT - 1);
                        double winValue = (1 - Math.cos(x)) * 0.5d;

                        // Real and imaginary parts for Fourier transform
                        fftAudioData[j] = filter * winValue;
                        fftAudioData[j+1] = 0.0;
                    }

                    fft.complexForward(fftAudioData);

                    // Unweighted amps
                    double linearFftGlobal = 0;
                    // Weighted apms
                    double linearFftAGlobal = 0;

                    int k = 0;

                    for (int ki = 0; ki < THIRD_OCTAVE.length; ki++) {
                        linearBandCount[ki] = 0;
                        linearBand[ki] = 0;
                        dbBand[ki] = 0;
                    }

                    for (int i = 0, j = 0; i < BLOCK_SIZE_FFT / 2; i++, j += 2) {

                        double re = fftAudioData[j];
                        double im = fftAudioData[j + 1];

                        // Magnitudo
                        double mag = Math.sqrt((re * re) + (im * im));

                        // Ponderata A
                        double weightFormula = weightedA[i];

                        dbFft[i] = (float) (10 * Math.log10(mag * mag
                                / ampThreshold))
                                + (float) gain;
                        dbFftA[i] = (float) (10 * Math.log10(mag * mag
                                * weightFormula
                                / ampThreshold))
                                + (float) gain;

                        linearFftGlobal += Math.pow(10, (float) dbFft[i] / 10f);
                        linearFftAGlobal += Math.pow(10, (float) dbFftA[i] / 10f);

                        float linearFft = (float) Math.pow(10, (float) dbFft[i] / 10f);

                        if ((0 <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 17.8f)) {
                            linearBandCount[0] += 1;
                            linearBand[0] += linearFft;
                            dbBand[0] =  (float) (10 * Math.log10(linearBand[0]));
                        }
                        if ((17.8f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 22.4f)) {
                            linearBandCount[1] += 1;
                            linearBand[1] += linearFft;
                            dbBand[1] =  (float) (10 * Math.log10(linearBand[1]));
                        }
                        if ((22.4f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 28.2f)) {
                            linearBandCount[2] += 1;
                            linearBand[2] += linearFft;
                            dbBand[2] =  (float) (10 * Math.log10(linearBand[2]));
                        }
                        if ((28.2f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 35.5f)) {
                            linearBandCount[3] += 1;
                            linearBand[3] += linearFft;
                            dbBand[3] =  (float) (10 * Math.log10(linearBand[3]));
                        }
                        if ((35.5f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 44.7f)) {
                            linearBandCount[4] += 1;
                            linearBand[4] += linearFft;
                            dbBand[4] =  (float) (10 * Math.log10(linearBand[4]));
                        }
                        if ((44.7f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 56.2f)) {
                            linearBandCount[5] += 1;
                            linearBand[5] += linearFft;
                            dbBand[5] =  (float) (10 * Math.log10(linearBand[5]));
                        }
                        if ((56.2f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 70.8f)) {
                            linearBandCount[6] += 1;
                            linearBand[6] += linearFft;
                            dbBand[6] =  (float) (10 * Math.log10(linearBand[6]));
                        }
                        if ((70.8f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 89.1f)) {
                            linearBandCount[7] += 1;
                            linearBand[7] += linearFft;
                            dbBand[7] =  (float) (10 * Math.log10(linearBand[7]));
                        }
                        if ((89.1f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 112f)) {
                            linearBandCount[8] += 1;
                            linearBand[8] += linearFft;
                            dbBand[8] =  (float) (10 * Math.log10(linearBand[8]));
                        }
                        if ((112f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 141f)) {
                            linearBandCount[9] += 1;
                            linearBand[9] += linearFft;
                            dbBand[9] =  (float) (10 * Math.log10(linearBand[9]));
                        }
                        if ((141f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 178f)) {
                            linearBandCount[10] += 1;
                            linearBand[10] += linearFft;
                            dbBand[10] =  (float) (10 * Math.log10(linearBand[10]));
                        }
                        if ((178f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 224f)) {
                            linearBandCount[11] += 1;
                            linearBand[11] += linearFft;
                            dbBand[11] =  (float) (10 * Math.log10(linearBand[11]));
                        }
                        if ((224f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 282f)) {
                            linearBandCount[12] += 1;
                            linearBand[12] += linearFft;
                            dbBand[12] =  (float) (10 * Math.log10(linearBand[12]));
                        }
                        if ((282f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 355f)) {
                            linearBandCount[13] += 1;
                            linearBand[13] += linearFft;
                            dbBand[13] =  (float) (10 * Math.log10(linearBand[13]));
                        }
                        if ((355f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 447f)) {
                            linearBandCount[14] += 1;
                            linearBand[14] += linearFft;
                            dbBand[14] =  (float) (10 * Math.log10(linearBand[14]));
                        }
                        if ((447f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 562f)) {
                            linearBandCount[15] += 1;
                            linearBand[15] += linearFft;
                            dbBand[15] =  (float) (10 * Math.log10(linearBand[15]));
                        }
                        if ((562f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 708f)) {
                            linearBandCount[16] += 1;
                            linearBand[16] += linearFft;
                            dbBand[16] =  (float) (10 * Math.log10(linearBand[16]));
                        }
                        if ((708f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 891f)) {
                            linearBandCount[17] += 1;
                            linearBand[17] += linearFft;
                            dbBand[17] =  (float) (10 * Math.log10(linearBand[17]));
                        }
                        if ((891f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 1122f)) {
                            linearBandCount[18] += 1;
                            linearBand[18] += linearFft;
                            dbBand[18] =  (float) (10 * Math.log10(linearBand[18]));
                        }
                        if ((1122f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 1413f)) {
                            linearBandCount[19] += 1;
                            linearBand[19] += linearFft;
                            dbBand[19] =  (float) (10 * Math.log10(linearBand[19]));
                        }
                        if ((1413f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 1778f)) {
                            linearBandCount[20] += 1;
                            linearBand[20] += linearFft;
                            dbBand[20] =  (float) (10 * Math.log10(linearBand[20]));
                        }
                        if ((1778f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 2239f)) {
                            linearBandCount[21] += 1;
                            linearBand[21] += linearFft;
                            dbBand[21] =  (float) (10 * Math.log10(linearBand[21]));
                        }
                        if ((2239f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 2818f)) {
                            linearBandCount[22] += 1;
                            linearBand[22] += linearFft;
                            dbBand[22] =  (float) (10 * Math.log10(linearBand[22]));
                        }
                        if ((2818f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 3548f)) {
                            linearBandCount[23] += 1;
                            linearBand[23] += linearFft;
                            dbBand[23] =  (float) (10 * Math.log10(linearBand[23]));
                        }
                        if ((3548f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 4467f)) {
                            linearBandCount[24] += 1;
                            linearBand[24] += linearFft;
                            dbBand[24] =  (float) (10 * Math.log10(linearBand[24]));
                        }
                        if ((4467f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 5623f)) {
                            linearBandCount[25] += 1;
                            linearBand[25] += linearFft;
                            dbBand[25] =  (float) (10 * Math.log10(linearBand[25]));
                        }
                        if ((5623f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 7079f)) {
                            linearBandCount[26] += 1;
                            linearBand[26] += linearFft;
                            dbBand[26] =  (float) (10 * Math.log10(linearBand[26]));
                        }
                        if ((7079f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 8913f)) {
                            linearBandCount[27] += 1;
                            linearBand[27] += linearFft;
                            dbBand[27] =  (float) (10 * Math.log10(linearBand[27]));
                        }
                        if ((8913f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 11220f)) {
                            linearBandCount[28] += 1;
                            linearBand[28] += linearFft;
                            dbBand[28] =  (float) (10 * Math.log10(linearBand[28]));
                        }
                        if ((11220f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 14130f)) {
                            linearBandCount[29] += 1;
                            linearBand[29] += linearFft;
                            dbBand[29] =  (float) (10 * Math.log10(linearBand[29]));
                        }
                        if ((14130f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 17780f)) {
                            linearBandCount[30] += 1;
                            linearBand[30] += linearFft;
                            dbBand[30] =  (float) (10 * Math.log10(linearBand[30]));
                        }
                        if ((17780f <= i * FREQ_RESOLUTION) && (i * FREQ_RESOLUTION < 22390f)) {
                            linearBandCount[31] += 1;
                            linearBand[31] += linearFft;
                            dbBand[31] =  (float) (10 * Math.log10(linearBand[31]));
                        }
                    }

                    final double dbFftAGlobal = 10 * Math.log10(linearFftAGlobal);

                    // calcolo min e max valore globale FFT pesato A
                    if (dbFftAGlobal > 0) {
                        if (dbFftAGlobalMinFirst == 0) {
                            dbFftAGlobalMinTemp = dbFftAGlobal;
                            dbFftAGlobalMinFirst = 1;
                        } else {
                            if (dbFftAGlobalMinTemp > dbFftAGlobal) {
                                dbFftAGlobalMinTemp = dbFftAGlobal;
                            }
                        }
                        if (dbFftAGlobalMaxFirst == 0){
                            dbFftAGlobalMaxTemp = dbFftAGlobal;
                            dbFftAGlobalMaxFirst = 1;
                        } else {
                            if (dbFftAGlobalMaxTemp < dbFftAGlobal){
                                dbFftAGlobalMaxTemp = dbFftAGlobal;
                            }
                        }
                    }
                    dbFftAGlobalMin = dbFftAGlobalMinTemp;
                    dbFftAGlobalMax = dbFftAGlobalMaxTemp;


                    // Running Leq
                    fftCount++;
                    linearFftAGlobalRunning += linearFftAGlobal;
                    dbFftAGlobalRunning = 10 * Math.log10(linearFftAGlobalRunning/fftCount);
                    final int TimeRunning = (int) fftCount / FFT_PER_SECOND;

                    Log.i(TAG, "dbFftAGlobal: " + Double.toString(dbFftAGlobal));
                    Log.i(TAG, "dbFftAGlobalMin: " + Double.toString(dbFftAGlobalMin));
                    Log.i(TAG, "dbFftAGlobalMax: " + Double.toString(dbFftAGlobalMax));
                } // while
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
            recorder.stop();
            recorder.release();
            recorder = null;
            recorderThread = null;
        }
    }
}