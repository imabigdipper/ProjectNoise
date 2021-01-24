package com.example.projectnoise.util;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import org.jtransforms.fft.DoubleFFT_1D;
import org.jtransforms.fft.RealFFTUtils_2D;

public class MeasureTask {

    public static final int RESULT_OK = 0;
    public static final int RESULT_CANCELLED = -1;


    private static final String TAG = "MeasureTask";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;

    private AudioRecord recorder;
    private DoubleFFT_1D transform = new DoubleFFT_1D(8192);
    private long endTime;
    private double calibration = 0;

    protected Double doInBackground(Void... voids) {
        double dB, average = 0, dbSumTotal = 0, instant = 0;
        int count = 0;

        //FileOutputStream os;
        // File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + "/temp";
        //os = new FileOutputStream(file);

        int bufferSize = 8192; // 2 ^ 13, necessary for the fft
        recorder = new AudioRecord(SOURCE, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize);

        short[] buffer = new short[bufferSize];

        long endTime = System.currentTimeMillis() + 30000; // 30 seconds
        recorder.startRecording();
        while (System.currentTimeMillis() < endTime) {
            recorder.read(buffer, 0, bufferSize);
            //os.write(buffer, 0, buffer.length); for writing data to output file; buffer must be byte
            dB = doFFT(buffer);
            if (dB != Double.NEGATIVE_INFINITY) {
                dbSumTotal += dB;
                count++;
            }
            average = 20 * Math.log10(dbSumTotal / count) + 8.25 + calibration;
            instant = 20 * Math.log10(dB) + 8.25 + calibration;
        }

        //os.close();

        recorder.stop();
        recorder.release();
        recorder = null;

        return average;
    }

    private double doFFT(short[] rawData) {
        double[] fft = new double[2 * rawData.length];
        double avg = 0.0, amplitude = 0.0;

        // get a half-filled array of double values for the fft calculation
        for (int i = 0; i < rawData.length; i++) {
            fft[i] = rawData[i] / ((double) Short.MAX_VALUE);
        }

        // fft
        transform.realForwardFull(fft);


        // calculate the sum of amplitudes
        for (int i = 0; i < fft.length; i += 2) {
            //                              reals                 imaginary
            amplitude += Math.sqrt(Math.pow(fft[i], 2) + Math.pow(fft[i + 1], 2));
            avg += amplitude * Values.A_WEIGHT_COEFFICIENTS[i / 2];
        }

        return avg / rawData.length;
    }

}
