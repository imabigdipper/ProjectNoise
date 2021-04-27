# ProjectNoise

This app was created for Erika Skoe of UCONN's Auditory Brain Research Lab. This app is meant to be used by researchers interested in correlating a test participant's daily activiies to the ambient dB exposed to while performing that activity. This app collects data on ambient dB levels, and the user's current activity, and stores it into a logfile. The app also has the ability to send notifications to remind the user to update their activity periodically.

# Requirements
This app is compatible with Android 8.1 and above, but has been mainly tested on Android 10.

# Permissions
Microphone: to record ambient dB levels
Storage: to store the logfile

# Dependencies
This app uses the JTransforms library for Fast Fourier Transforms. It also has it's core dB measurement system based upon that found in https://github.com/gworkman/SoundMap. This app also uses Takisoft's AndroidX Preference eXtended, https://github.com/takisoft/preferencex-android.
