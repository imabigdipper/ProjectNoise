package com.example.projectnoise.services;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
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
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.projectnoise.MainActivity;
import com.example.projectnoise.R;
import com.example.projectnoise.util.MovingAverage;
import com.example.projectnoise.util.Values;

import org.jtransforms.fft.DoubleFFT_1D;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class MeasureService extends Service {
    public static final String PERSISTENT_CHANNEL_ID = "PersistentServiceChannel";
    public static final int PERSISTENT_ID = 1;
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

        // Start foreground service with given foreground notification & initialize preference variables
        startForeground(PERSISTENT_ID, createForegroundNotification());
        initPrefs();

        // Helper function to set up thread for measuring sound data
        startRecorder();

        // Log the parameters of the current recording session
        Log.d(TAG, "Starting measureService thread with \n" +
                "Calibration: " + toggleCalibration + "\n" +
                "Calibration Constant: " + calibration + " dB \n" +
                "Averaging Interval Length: " + averageIntervalLen + " seconds \n" +
                "Activity Notifications: " + toggleActivityNotifications + "\n" +
                "Activity Notification Interval: " + notificationIntervalLen + " hours\n" +
                "Threshold Notifications: " + toggleThresholdNotifications + "\n" +
                "Threshold Intervals: " + thresholdIntervalNum + "\n" +
                "Threshold: " + dbThreshold +  " dB \n" +
                "Wakeup Notifications: " + toggleWakeupNotifications + "\n" +
                "Wakeup Notification Time: " + wakeupNotificationTime +  " dB \n"
                );

        return  START_STICKY;
    }


    /**
     * Helper function to create persistent foreground notification
     */
    private Notification createForegroundNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        int color = Color.argb(255, 228, 14, 18);
        return new NotificationCompat.Builder(this, PERSISTENT_CHANNEL_ID)
                .setOngoing(true)
                .setContentTitle("Recording in Progress...")
                .setContentText("Current Activity: " + getCurActivity())
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setColor(color)
                .setColorized(true)
                .build();
    }


    /**
     * Helper function to create notification channel for the persistent notification
     */
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

    /**
     * Helper function to create notification channel for the alert (activity, threshold) notifications
     */
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


    /**
     * Initialize variables from root_preferences file. Preference keys are defined in /res/values/strings.xml
     */
    private void initPrefs() {
        toggleCalibration = preferences.getBoolean(getString(R.string.pref_toggle_calibration), false);
        toggleThresholdNotifications = preferences.getBoolean(getString(R.string.pref_toggle_thresh_notif), false);
        toggleActivityNotifications = preferences.getBoolean(getString(R.string.pref_toggle_activity_notifs), false);
        toggleWakeupNotifications = preferences.getBoolean(getString(R.string.pref_toggle_wakeup_notif), false);
        averageIntervalLen = Long.parseLong(preferences.getString(getString(R.string.pref_interval_len), "30"));

        if (toggleCalibration)
            calibration = Double.parseDouble(preferences.getString(getString(R.string.pref_calibration), "0"));

        if (toggleThresholdNotifications) {
            toggleNewThresholdAlgorithm = preferences.getBoolean(getString(R.string.pref_toggle_thresh_algo), true);
            dbThreshold = Double.parseDouble(preferences.getString(getString(R.string.pref_threshold), "100"));
            thresholdIntervalNum = Integer.parseInt(preferences.getString(getString(R.string.pref_thresh_interval_num), "30"));
            threshQueue = new MovingAverage(thresholdIntervalNum);
        }

        if (toggleActivityNotifications) {
            notificationIntervalLen = Double.parseDouble(preferences.getString(getString(R.string.pref_activity_notif_interval), "2"));
            setActivityNotifTime();
        }

        if (toggleWakeupNotifications) {
            wakeupNotificationTime = preferences.getString(getString(R.string.pref_wakeup_notif_time), "09:00");
            setWakeupDate();
        }
    }


    /**
     * Prepares AudioRecord, Handler, and HandlerThread instances then posts measureRunnable to the thread.
     */
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


    /**
     * Releases and cleans AudioRecord, Handler, and HandlerThread instances.
     */
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
     * Initialize variables related to the dB and activity recording process
     */
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
    private boolean toggleNewThresholdAlgorithm;
    private boolean toggleActivityNotifications;
    private boolean toggleWakeupNotifications;
    private double notificationIntervalLen;
    private String wakeupNotificationTime;
    private Date wakeupDate;

    // Date and counter stuff for determining when to send out notifications
    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    private SimpleDateFormat sdfWake = new SimpleDateFormat("HH:mm");
    private String nextActivityNotifTime;
    private MovingAverage threshQueue;
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


    /** Runnable executed inside the HandlerThread. Measures sound data over the given interval then calculates average dB.
     * Then does all processing related to notifications and logging.
     * Re-invokes itself until service is killed.
     * Heavily based on: https://github.com/gworkman/SoundMap/blob/master/app/src/main/java/edu/osu/sphs/soundmap/util/MeasureTask.java
     */
    Runnable measureRunnable = new Runnable() {
        @Override
        public void run() {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
            long startTime = SystemClock.uptimeMillis();
            long measureTime = SystemClock.uptimeMillis() + (1000 * averageIntervalLen);
            short[] buffer = new short[bufferSize];
            double dB;
            double dbSumTotal = 0;
            double instant;
            int count = 0;
            double average = 0;
            boolean beenOpened = false;


            // Continuously read audio into buffer for measureTime ms
            while (SystemClock.uptimeMillis() < measureTime && isRecording) {
                recorder.read(buffer, 0, bufferSize);

                // Perform Fast Fourier Transform to get dB
                dB = doFFT(buffer);
                if (dB != Double.NEGATIVE_INFINITY) {
                    dbSumTotal += dB;
                    count++;
                }

                // Check if calibration is enabled, if so, add the calibration constant
                average = toggleCalibration ?
                        20 * Math.log10(dbSumTotal / count) + 8.25 + calibration
                        : 20 * Math.log10(dbSumTotal / count) + 8.25;

                // Instant dB
                // instant = 20 * Math.log10(dB) + 8.25 + calibration;

                // If app hasn't been opened during the current recording interval, check if it is currently open
                beenOpened = beenOpened || ForegroundCheck();
            }

            // Check if recording service has ended or not
            if (isRecording) {
                // Check preferences to see if notification types are enabled, if so, check if they need to be sent
                if (toggleThresholdNotifications) {
                    // Check which threshold algorithm is being used
                    if (toggleNewThresholdAlgorithm)
                        threshCheckQueue(average);
                    else
                        threshCheckCounter(average);
                }

                if (toggleActivityNotifications &&
                        !getCurActivity().equals("sleep"))      activityNotificationCheck();
                if (toggleWakeupNotifications)                  wakeupNotificationCheck();

                // Log recorded info and update persistent notification with current activity
                Log.i(TAG, "Average dB over " + averageIntervalLen + " seconds: " + average);
                writeToLog(formatLog(average, beenOpened));
                notificationManager.notify(PERSISTENT_ID, createForegroundNotification());

                // Call the runnable again to measure average of next time block
                handler.post(this);
            } else {
                // Stop thread, remove persistent notification, release instances
                notificationManager.cancel(PERSISTENT_ID);
                // Thread is done recording, release AudioRecord instance
                Log.d(TAG, "Stopping measuring thread");
                recorder.release();
                recorder = null;
                Log.d(TAG, "Successfully released AudioRecord instance");
            }
        }
    };


    /**
     * Function that formats a single line of the logfile containing the date/time, average dB, activity, notification visibility, and foreground status
     * @param average   The calculated average dB of the last completed recording interval
     * @return          Comma separated and newline terminated line with date, time, avg dB, activity, notification visibility, and foreground status data.
     */
    private String formatLog(double average, boolean isForeground) {
        // Check which notification are present
        int threshPresent = 0;
        int activityPresent = 0;
        int notif = notificationCheck();
        if (notif == THRESH_ID)
            threshPresent = 1;
        else if (notif == ACTIVITY_ID)
            activityPresent = 1;
        else if (notif == THRESH_ID + ACTIVITY_ID) {
            threshPresent = 1;
            activityPresent = 1;
        }
        // Get required data and format single line in logfile
        String current_activity = getCurActivity();
        Date currentTime = Calendar.getInstance().getTime();
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat( "HH:mm:ss" );
        @SuppressLint("SimpleDateFormat") SimpleDateFormat stf = new SimpleDateFormat( "dd/MM/yyyy" );
        String time = sdf.format(currentTime);
        String date = stf.format(currentTime);

        Log.d(TAG, "LOGGING: " + date + "," + time + "," + average + "," + current_activity + "," + threshPresent + "," + activityPresent + "\n");
        return date + "," + time + "," + average + "," + current_activity + "," + threshPresent + "," + activityPresent + "," + isForeground + "\n";
    }


    /**
     * Checks for the presence of Activity and Threshold Notification in the Notification bar.
     * @return THRESH_ID, ACTIVITY_ID, or THRESH_ID + ACTIVITY_ID
     */
    private int notificationCheck() {
        int notif = 0;
        NotificationManager notificationManager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
        StatusBarNotification[] notifications =
                notificationManager.getActiveNotifications();
        for (StatusBarNotification notification : notifications) {
            if (notification.getId() == THRESH_ID)
                notif += THRESH_ID;
            if (notification.getId() == ACTIVITY_ID)
                notif += ACTIVITY_ID;
        }
        return notif;
    }


    /**
     * Checks if the app is in the foreground.
     * @return 1 if the app is currently in the foreground, 0 if it is not.
     */
    private boolean ForegroundCheck(){
        ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND || appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE);
    }


    /**
     * Writes given data to a logfile stored on the phone's disk
     * @param text Line to be written to the logfile
     */
    public void writeToLog(String text){
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state))
            Log.d(TAG, "External storage not mounted");

        /*
          Saves to Android/data/com.example.projectnoise/files when viewed over USB
          full path is /storage/emulated/0/Android/data/com.example.projectnoise/files/log.csv
         */
        File file = new File(getExternalFilesDir(null), "log.csv");

        try {
            boolean success = file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file, true);
            if(success)
            {
                fos.write("Date, Time, Average DB, Current Activity, Threshold Level Exceeded, Activity Notification Triggered, User Opened Notification \n".getBytes());
            }
            fos.write(text.getBytes());
            MediaScannerConnection.scanFile(this, new String[] {file.toString()}, null, null);
            fos.flush();
            fos.close();

        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "FILE SAVING FAILED");
        }
    }


    /**
     * Returns current activity from the Activities page
     */
    private String getCurActivity() {
        String activity = preferences.getString(getString(R.string.act_current_activity),"None");
        if(activity.equals("custom1"))
            activity = preferences.getString(getString(R.string.act_custom_1), "N/A");
        if(activity.equals("custom2"))
            activity = preferences.getString(getString(R.string.act_custom_2), "N/A");
        if(activity.equals("custom3"))
            activity = preferences.getString(getString(R.string.act_custom_3), "N/A");

        return activity;
    }


    /**
     * Helper function to check threshold and display notification if necessary. Uses a queue in MovingAverage class to calculate moving average.
     */
    private void threshCheckQueue(double current_average) {
        Log.d(TAG, "QUEUE adding : " + current_average);
        threshQueue.addData(current_average);
        Log.d(TAG, "QUEUE MEAN: " + threshQueue.getMean());
        if (threshQueue.getMean() >= dbThreshold)
            createThresholdNotification();
    }


    /**
     * Helper function check threshold and display notification if necessary. Uses a counter.
     */
    private void threshCheckCounter(double current_average) {
        Log.d(TAG, "Threshold notifs enabled, checking thresh data...");
        if (current_average > dbThreshold)
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
     * Sets new time for a wakeup notification to be sent based on the wakeup notification setting.
     */
    private void setWakeupDate() {
        sdfWake.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date wakeupTime = null;
        try {
            wakeupTime = sdfWake.parse(wakeupNotificationTime);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        // today
        Calendar date = new GregorianCalendar();
        // reset hour, minutes, seconds and millis
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        // go forward one day
        date.add(Calendar.DATE, 1);

        wakeupDate = new Date(date.getTimeInMillis() + wakeupTime.getTime());
        Log.d(TAG, "Wakeup notification time set to:  " + wakeupDate);
    }


    /**
     * Sets a new time for an activity notification to be sent.
     */
    private void setActivityNotifTime() {
        nextActivityNotifTime = sdf.format(System.currentTimeMillis() + (long)(60*60*1000*notificationIntervalLen));
        Log.d(TAG, "curtime: " + sdf.format(System.currentTimeMillis()));
        Log.d(TAG, "nexttime: " + nextActivityNotifTime);
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

            if (!getCurActivity().equals("sleep")) {
                assert curTime != null;
                if (curTime.after(notifTime)) {
                    Log.d(TAG, "Time to send an activity notification");
                    createActivityNotification();
                    setActivityNotifTime();
                    return;
                }
            }
            Log.d(TAG, "Not time to send activity notification yet");
        } catch (ParseException e) { e.printStackTrace(); }
    }


    /**
     * Check if it is time to send a wakeup activity notification.
     */
    private void wakeupNotificationCheck() {
        Date curDate = new Date();
        Log.d(TAG, "curDate: " + curDate);
        Log.d(TAG, "wakeupDate: " + wakeupDate);

        // Check if it is time to send wakeup notification
        if (curDate.after(wakeupDate)) {
            Log.d(TAG, "Time to send a wakeup notification");
            createActivityNotification();
            setWakeupDate();
            return;
        }
        Log.d(TAG, "Not time to send wakeup notification yet");
    }


    /**
     * Helper function to create a threshold notification
     */
    private void createThresholdNotification() {
        int color = Color.argb(255, 228, 14, 18);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
        Notification threshNotification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle("Activity Tracker")
                .setContentText("What are you up to? Tap to update your current activity.")
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setColor(color)
                .setColorized(true)
                .setContentIntent(pendingIntent)
                .build();
        threshNotification.flags = Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(THRESH_ID, threshNotification);
    }


    /**
     * Helper function to create an activity notification
     */
    private void createActivityNotification(){
        int color = Color.argb(255, 228, 14, 18);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
        Notification activityNotification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle("Activity tracker")
                .setContentText("What are you up to? Tap to update your current activity.")
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setColor(color)
                .setColorized(true)
                .setContentIntent(pendingIntent)
                .build();
        activityNotification.flags = Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(ACTIVITY_ID, activityNotification);
    }


    /**
     * Does a Fast Fourier Transform to get dB data from raw audio data
     * @param rawData A buffer containing raw audio data recorded form the microphone.
     * @return The average dB level of a snippet of raw audio data
     */
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