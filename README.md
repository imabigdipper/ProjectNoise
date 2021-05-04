# ProjectNoise

This app was created for Erika Skoe of UCONN's Auditory Brain Research Lab. This app is meant to be used by researchers interested in correlating a test participant's daily activiies to the ambient dB exposed to while performing that activity. This app collects data on ambient dB levels, and the user's current activity, and stores it into a logfile. The app also has the ability to send notifications to remind the user to update their activity periodically.

# Requirements
This app is compatible with Android 8.1 and above, but has been mainly tested on Android 10.

# Permissions
Microphone: to record ambient dB levels
Storage: to store the logfile

# Dependencies
This app uses the JTransforms library for Fast Fourier Transforms. It also has it's core dB measurement system based upon that found in https://github.com/gworkman/SoundMap. This app also uses Takisoft's AndroidX Preference eXtended, https://github.com/takisoft/preferencex-android.


# Copyright
Copyright (C) 2021 Luca Alberti, Mihir Bhalodia, Roman Gusyev, Zejian Qiu, Da Shen, Mukul S. Bansal, and Erika Skoe (luca.alberti@uconn.edu, erika.skoe@uconn.edu).

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. 

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details. 

You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
