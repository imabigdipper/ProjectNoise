package com.example.projectnoise.services;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.projectnoise.MainActivity;
import com.example.projectnoise.R;
import com.example.projectnoise.util.Values;

import org.jtransforms.fft.DoubleFFT_1D;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;

public class MeasureService<var> extends Service {
    public static final String CHANNEL_ID = "MeasureServiceChannel";
    private static final String FILE_NAME = "example.csv";
    private SharedPreferences preferences;



    @Override
    public void onCreate() {
        super.onCreate();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
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

        // Start foreground service with given foreground notification & initialize preference variables
        startForeground(1, createForegroundNotification(pendingIntent));
        initPrefs();

        // Helper function to set up thread for measuring sound data
        startRecorder();
        Log.d(TAG, "Starting measureService thread with calibration: " + Integer.valueOf(preferences.getString("calibration", "0")));

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
                    NotificationManager.IMPORTANCE_HIGH
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }


    /** Initialize variables from preferences to minimize getPreference calls **/

    private void initPrefs() {
        interval = Long.parseLong(preferences.getString("average_interval", "60"));
        calibration = Double.parseDouble(preferences.getString("calibration", "0"));
        toggle_calibration = preferences.getBoolean("toggle_calibration", false);
        toggle_threshold_notifications = preferences.getBoolean("toggle_threshold_notifications", false);
        toggle_activity_notifications = preferences.getBoolean("toggle_activity_notifications", false);
    }


    /**
     * This chunk of the service is all about the dB measuring process
     **/

    private static final String TAG = "Measure Service";

    // Preference variables
    private long interval;
    private double calibration;
    private boolean toggle_calibration;
    private boolean toggle_threshold_notifications;
    private boolean toggle_activity_notifications;

    private AlarmManager alarmMgr;
    private PendingIntent alarmIntent;

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

    private boolean isRecording = false;

    /** import current time and time+2h **/
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH.mm");
    LocalTime s = LocalTime.now();
    LocalTime ns = s.plusHours(2);
    public String time_notification = ns.format(formatter);



    /** Runnable executed inside the HandlerThread. Measures sound data over the given interval then calculates average dB. Re-invokes itself until service is killed. Heavily based on:
     * https://github.com/gworkman/SoundMap/blob/master/app/src/main/java/edu/osu/sphs/soundmap/util/MeasureTask.java
     */


    Runnable measureRunnable = new Runnable() {
        @Override
        public void run() {
            long startTime = SystemClock.uptimeMillis();
            long measureTime = SystemClock.uptimeMillis() + (1000 * interval);
            short[] buffer = new short[bufferSize];
            double dB;
            double dbSumTotal = 0;
            double instant;
            int count = 0;
            double average = 0;

            Log.d(TAG, "Measuring for " + interval + " seconds");

            // Continuously read audio into buffer for measureTime ms
            while (SystemClock.uptimeMillis() < measureTime && isRecording) {
                recorder.read(buffer, 0, bufferSize);
                //os.write(buffer, 0, buffer.length); for writing data to output file; buffer must be byte
                dB = doFFT(buffer); // Perform Fast Fourier Transform
                if (dB != Double.NEGATIVE_INFINITY) {
                    dbSumTotal += dB;
                    count++;
                }

                // Check if calibration is enabled
                average = toggle_calibration ?
                        20 * Math.log10(dbSumTotal / count) + 8.25 + calibration
                        : 20 * Math.log10(dbSumTotal / count) + 8.25;

                // instant = 20 * Math.log10(dB) + 8.25 + calibration;
            }

            Log.i(TAG, "Average dB over " + interval + " seconds: " + average);
            writeToLog(formatLog(average));
            activityNotificationCheck();

            // Check preferences to see if notification types are enabled
            if (toggle_threshold_notifications)
                threshCheck(average);
                //activityNotificationCheck();
//            if (toggle_activity_notifications)
//                // TODO Mihir: Activity Notification Check
//                 activityNotificationCheck();


            // Check if recording service has ended or not
            if (isRecording) {
                // Call the runnable again to measure average of next time block
                handler.post(this);
            } else {
                // Thread is done recording, release AudioRecord instance
                Log.d(TAG, "Stopping measuring thread");
                recorder.release();
                recorder = null;
                Log.d(TAG, "Successfully released AudioRecord instance");
            }
        }
    };


    void activityNotificationCheck() {
        // call createActivityNotification();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH.mm");
        LocalTime s = LocalTime.now();
        String timeS1 = s.format(formatter);
        Log.d(TAG," ");
        Log.d(TAG, "current time in hh.mm: " + timeS1);
        Log.d(TAG, "time to notify hh.mm: " + time_notification);
        int current_time = getsecond(timeS1);
        int time_2h = getsecond(time_notification);

        //Log.d(TAG, "current time: " + current_time);
        //Log.d(TAG, "time + 2h: " + time_2h);
        //if ((time_2h-7200) == current_time)
        if (current_time > time_2h)
        {
            Log.d(TAG, "successfully");
            // push a activity notification
            createActivityNotification();
        }
        else{
            Log.d(TAG, "unsuccessfully");
        }

        }



    /** Helper function to get the seconds out of hr and min **/
    private int getsecond(String cur) {

          String strNew = cur.replace(".", ""); // 16.05 will be converted to 1605
          //Log.d(TAG, "new string will be: " + String.valueOf(strNew));

          String s1 = strNew.substring(0, strNew.length()/2); // 16 (hr)
          //Log.d(TAG, "new s1: "+ s1);
          String s2 = strNew.substring(2, strNew.length());  // 05 (min)
          //Log.d(TAG, "new s2: "+ s2);
          Integer x1 = Integer.valueOf(s1);
          //Log.d(TAG, "new x1: "+ x1);
          Integer x2 = Integer.valueOf(s2);
          //Log.d(TAG, "new x2: "+ x2);

          int sum = 0;
          sum = (x1*3600) + (x2*60);

        return sum;

    }

    /** Prepares AudioRecord, Handler, and HandlerThread instances then posts measureRunnable to the thread. **/

    private void startRecorder() {
        try {
            recorder = new AudioRecord(SOURCE, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize);
            recorder.startRecording();
            isRecording = true;
        } catch (Exception e) {
            Log.e(TAG, "AudioRecord not initialized");
            return;
        }
        // Handler and HandlerThread setup
        handlerThread = new HandlerThread("measureThread");
        handlerThread.start();
        handler = new android.os.Handler(handlerThread.getLooper());
        handler.post(measureRunnable);
    }


    /** Releases and cleans AudioRecord, Handler, and HandlerThread instances. **/

    private void stopRecorder() {
        Log.i(TAG, "Stopping the audio stream");
        if (recorder != null) {
            isRecording = false;
            try {
                recorder.stop();
                handler.removeCallbacksAndMessages(null);
                handlerThread.quit();
            } catch (Exception e) {
                Log.d(TAG, "handlerThread failed to quit");
            }
        }
    }


    /** Function that writes average dB to log file **/

    private String formatLog(double average) {
        String current_activity = activity_check();
        Date currentTime = Calendar.getInstance().getTime();
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat( "HH:mm" );
        @SuppressLint("SimpleDateFormat") SimpleDateFormat stf = new SimpleDateFormat( "dd/MM/yyyy" );
        String time = sdf.format(currentTime);
        String date = stf.format(currentTime);
        return date + "," + time + "," + average + "," + activity_check()+ "\n";
    }


    public void writeToLog(String text){
        FileOutputStream fos = null;
        try {
            fos = openFileOutput(FILE_NAME, MODE_APPEND);
            fos.write(text.getBytes());
            Toast.makeText(this,"Saved to " + getFilesDir() + "/" + FILE_NAME, Toast.LENGTH_LONG).show();

        } catch (IOException e) { e.printStackTrace(); }

        finally {
            if(fos!=null){
                try {
                    fos.close();
                } catch (IOException e) { e.printStackTrace(); }
            }
        }
    }

    private String activity_check() {
        String activity = preferences.getString("current_activity","None");
        if(activity.equals("custom")) {
            activity = preferences.getString("custom_activity", "");
        }
        return activity;
    }


    /** Helper function check threshold and display notification if necessary **/

    private int threshCounter = 0;

    private void threshCheck(double average) {
        if (average > Integer.parseInt(preferences.getString("db_threshold", "150")))
            threshCounter++;

        else
            threshCounter = 0;

        if (threshCounter >= Integer.parseInt(preferences.getString("threshold_intervals","30"))) {
            createThresholdNotification();
            threshCounter = 0;
        }
    }


    /** Helper function to create threshold notification **/

    private void createThresholdNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
        Notification threshNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Measure Service")
                .setContentText("dB has exceeded threshold, please update current activity")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();
        threshNotification.flags = Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(0, threshNotification);
    }


    /** Helper function to create activity notification **/
    private void createActivityNotification(){
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
        Notification activityNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Activity tracker")
                .setContentText("Please tap here to update your current activity")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();
        activityNotification.flags = Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(0, activityNotification);
    }


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