# Emulator smoke tests

These test files are development fixtures. **Never install the Maps probe on a real phone.** It intentionally owns the Maps package name on an isolated emulator so a navigation intent can be observed without external accounts, location, or networking. It is not Google Maps and does not navigate.

Build the main app first, then run `tests/android/build-tests.sh` using the same `ANDROID_HOME` and JDK environment. On a fresh Android 36 emulator without Google Maps installed:

```sh
adb install -r build/Morning_Drive.apk
adb install -r tests/android/build/probe/probe.apk
adb install -r tests/android/build/tests/tests.apk
adb shell appops set com.cameron.morningdrive SCHEDULE_EXACT_ALARM allow
adb shell appops set com.cameron.morningdrive SYSTEM_ALERT_WINDOW allow
adb shell pm grant com.cameron.morningdrive android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am instrument -w com.cameron.morningdrive.tests/com.cameron.morningdrive.SmokeInstrumentation
```

The suite resets the app's test configuration, launches its screen, checks saved settings and enable/disable, starts the foreground unlock monitor, makes a trip due while screen is off, wakes/unlocks, checks that the trip was handled, and verifies the Maps navigation URI in the probe log. The basic suite finishes with automation disabled. `run-tests.sh` then runs an additional phase: it arms a trip and exact alarm, exits instrumentation, verifies no handoff while the display is off, wakes/unlocks the emulator, and checks the probe log. This phase leaves automation enabled in the emulator. It exercises background launching after instrumentation has ended. No genuine Maps guidance or Samsung firmware is tested. Avoid interacting with the emulator while it runs.
