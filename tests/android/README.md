# Emulator smoke tests

These are development fixtures. **Never install the Maps probe on a real phone.** It intentionally owns the Maps package name on an isolated emulator so a navigation intent can be observed without external accounts, location, or networking. It is not Google Maps and does not navigate.

Build the main app, then run `tests/android/build-tests.sh` using the same `ANDROID_HOME` and JDK environment. On a fresh Android API 30 or newer emulator without Google Maps installed, run:

```sh
ANDROID_HOME=/absolute/path/to/android-sdk tests/android/run-tests.sh
```

`ADB_BIN` can override the adb executable. The runner refuses non-emulators, installs the app and fixtures, and grants only permissions applicable to the emulator's Android version.

The suite verifies version 1 settings migration, named-location persistence, weekday assignment and time overrides, preemptively saved locations, rename/removal, skip/undo, enable/disable, foreground monitoring, exact-alarm delivery with the display off, unlock handling, encoded destination URI, and duplicate prevention beyond the service's retry cooldown. The first suite finishes paused.

The runner then arms a pending trip and exact alarm, exits instrumentation, verifies that no handoff happened with the display off, wakes/unlocks the emulator, and verifies the Maps probe log. This phase leaves automation enabled in the emulator. It exercises launching after instrumentation has ended. No genuine Maps guidance, secure PIN entry, or Samsung firmware behavior is tested. Avoid interacting with the emulator during the run.

Results and logs are written under the ignored `tests/android/build` directory. Fixture compilation alone is not a runtime test pass.
