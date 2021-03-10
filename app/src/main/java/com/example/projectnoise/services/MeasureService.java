package com.example.projectnoise.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MeasureService extends Service {
    public static final String PERSISTENT_CHANNEL_ID = "PersistentServiceChannel";
    public static final String ALERT_CHANNEL_ID = "AlertServiceChannel";

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
        // Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Creates notification channel & notification in preparation to launch service in foreground
        createPersistentNotificationChannel();
        createAlertNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        // Start foreground service with given foreground notification & initialize preference variables
        startForeground(1, createForegroundNotification(pendingIntent));
        initPrefs();
        setActivityNotifTime();



        // Helper function to set up thread for measuring sound data
        startRecorder();
        Log.d(TAG, "Starting measureService thread with \n" +
                "Calibration: " + toggleCalibration + "\n" +
                "Calibration Constant: " + calibration + " dB \n" +
                "Averaging Interval Length: " + averageIntervalLen + " seconds \n" +
                "Activity Notifications: " + toggleActivityNotifications + "\n" +
                "Activity Notification Interval: " + notificationIntervalLen + " hours\n" +
                "Threshold Notifications: " + toggleThresholdNotifications + "\n" +
                "Threshold Intervals: " + thresholdIntervalNum + "\n" +
                "Threshold: " + dbThreshold +  " dB \n"
                );

        return  START_STICKY;
    }


    private void setActivityNotifTime() {
        nextActivityNotifTime = sdf.format(System.currentTimeMillis() + (long)(60*60*1000*notificationIntervalLen));
        Log.d(TAG, "curtime: " + sdf.format(System.currentTimeMillis()));
        Log.d(TAG, "nexttime: " + nextActivityNotifTime);
    }



    /** Helper function to create foreground notification **/

    private Notification createForegroundNotification(PendingIntent pendingIntent) {
        return new NotificationCompat.Builder(this, PERSISTENT_CHANNEL_ID)
                .setOngoing(true)
                .setContentTitle("Measure Service")
                .setContentText("Measuring dB")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();
    }


    /** Helper function to create notification channel **/

    private void createPersistentNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    PERSISTENT_CHANNEL_ID,
                    "Persistent Service Notification Channel",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private void createAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Alert Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }


    /** Initialize variables from preferences to minimize getPreference calls **/

    private void initPrefs() {
        toggleCalibration = preferences.getBoolean("toggle_calibration", false);
        toggleThresholdNotifications = preferences.getBoolean("toggle_threshold_notifications", false);
        toggleActivityNotifications = preferences.getBoolean("toggle_activity_notifications", false);
        averageIntervalLen = Long.parseLong(preferences.getString("average_interval_len", "30"));

        if (toggleCalibration) {
            calibration = Double.parseDouble(preferences.getString("calibration", "0"));
        }

        if (toggleThresholdNotifications) {
            dbThreshold = Double.parseDouble(preferences.getString("db_threshold", "100"));
            thresholdIntervalNum = Integer.parseInt(preferences.getString("threshold_interval_num", "30"));
        }

        if (toggleActivityNotifications) {
            notificationIntervalLen = Double.parseDouble(preferences.getString("notification_interval_len", "2"));
        }
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


    /**
     * This chunk of the service is all about the dB measuring process
     **/

    private static final String TAG = "Measure Service";

    // Notification IDS
    int THRESH_ID = 2;
    int ACTIVITY_ID = 3;

    // Preference variables
    private long averageIntervalLen;
    private double calibration;
    private double dbThreshold;
    private int thresholdIntervalNum;
    private boolean toggleCalibration;
    private boolean toggleThresholdNotifications;
    private boolean toggleActivityNotifications;
    private double notificationIntervalLen;

    // Date and counter stuff for determining when to send out notifications
    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    private String nextActivityNotifTime;
    private int threshCounter = 0;

    // AudioRecord instance configuration
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;

    int bufferSize = 8192; // 2 ^ 13, necessary for the fft
    private final DoubleFFT_1D transform = new DoubleFFT_1D(8192);

    // AudioRecord and thread handler stuff
    private AudioRecord recorder;
    private HandlerThread handlerThread;
    private android.os.Handler handler;

    private boolean isRecording = false;


    /** Runnable executed inside the HandlerThread. Measures sound data over the given interval then calculates average dB. Re-invokes itself until service is killed. Heavily based on:
     * https://github.com/gworkman/SoundMap/blob/master/app/src/main/java/edu/osu/sphs/soundmap/util/MeasureTask.java
     */


    Runnable measureRunnable = new Runnable() {
        @Override
        public void run() {
            long startTime = SystemClock.uptimeMillis();
            long measureTime = SystemClock.uptimeMillis() + (1000 * averageIntervalLen);
            short[] buffer = new short[bufferSize];
            double dB;
            double dbSumTotal = 0;
            double instant;
            int count = 0;
            double average = 0;


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
                average = toggleCalibration ?
                        20 * Math.log10(dbSumTotal / count) + 8.25 + calibration
                        : 20 * Math.log10(dbSumTotal / count) + 8.25;

                // instant = 20 * Math.log10(dB) + 8.25 + calibration;
            }

            Log.i(TAG, "Average dB over " + averageIntervalLen + " seconds: " + average);
            writeToLog(formatLog(average));

            // Check preferences to see if notification types are enabled
            if (toggleThresholdNotifications)
                threshCheck(average);
            if (toggleActivityNotifications && !getCurActivity().equals("sleep"))
                 activityNotificationCheck();


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


    /** Functions that format and log date/time, average dB, and activity to log file */

    private String formatLog(double average) {
        String current_activity = getCurActivity();
        Date currentTime = Calendar.getInstance().getTime();
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat( "HH:mm" );
        @SuppressLint("SimpleDateFormat") SimpleDateFormat stf = new SimpleDateFormat( "dd/MM/yyyy" );
        String time = sdf.format(currentTime);
        String date = stf.format(currentTime);
        return date + "," + time + "," + average + "," + current_activity+ "\n";
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


    /** Returns current activity */

    private String getCurActivity() {
        String activity = preferences.getString("current_activity","None");
        if(activity.equals("custom1"))
            activity = preferences.getString("custom_activity_1", "N/A");
        if(activity.equals("custom2"))
            activity = preferences.getString("custom_activity_2", "N/A");
        if(activity.equals("custom3"))
            activity = preferences.getString("custom_activity_3", "N/A");

        return activity;
    }


    /** Helper function check threshold and display notification if necessary **/

    private void threshCheck(double average) {
        Log.d(TAG, "Threshold notifs enabled, checking thresh data...");
        if (average > dbThreshold)
            threshCounter++;
        else
            threshCounter = 0;
        if (threshCounter >= thresholdIntervalNum) {
            Log.d(TAG, "Thresholds met, sending thresh notification");
            createThresholdNotification();
            threshCounter = 0;
        }
    }


    /**
     * Function compares current time against next time at which an activity notification should be sent.
     * If the interval has passed, send the notification and set the time for the next notification
     */

    private void activityNotificationCheck() {
        Log.d(TAG, "Activity notifs enabled, checking interval data...");
        try {
            Date curTime = sdf.parse(sdf.format(System.currentTimeMillis()));
            Date notifTime = sdf.parse(nextActivityNotifTime);

            Log.d(TAG, "curTime: " + sdf.format(System.currentTimeMillis()));
            Log.d(TAG, "notifTime: " + nextActivityNotifTime);

            assert curTime != null;
            if (curTime.after(notifTime)) {
                Log.d(TAG, "Time to send an activity notification");
                createActivityNotification();
                setActivityNotifTime();
                return;
            }

            Log.d(TAG, "Not time to send activity notification yet");

        } catch (ParseException e) { e.printStackTrace(); }
    }


    /** Helper function to create threshold notification */

    private void createThresholdNotification() {
        int color = Color.argb(255, 228, 14, 18);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
        Notification threshNotification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle("Activity Tracker")
                .setContentText("You are experiencing prolonged exposure to a loud environment, please update current activity")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setColor(color)
                .setContentIntent(pendingIntent)
                .build();
        threshNotification.flags = Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(THRESH_ID, threshNotification);
    }


    /** Helper function to create activity notification **/

    private void createActivityNotification(){
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
        Notification activityNotification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle("Activity tracker")
                .setContentText("Please tap here to update your current activity")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();
        activityNotification.flags = Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(ACTIVITY_ID, activityNotification);
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