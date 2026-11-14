# Morning Drive 2.0

A simple repeating weekly commute planner for Android. Save named locations, choose where to go each day, and let Google Maps handle navigation. Built for a Samsung Galaxy S25; Android 8.0 or later is supported.

**[Download the latest APK](https://github.com/goui12/Morning-Drive/releases/latest/download/Morning_Drive.apk)** · [Release notes](https://github.com/goui12/Morning-Drive/releases)

## Set up your week

1. Add your locations with a name and complete address, such as Airport, Bank, and Arena.
2. Choose a default departure time.
3. Tap each weekday to choose a location. Give a day its own time if needed, or leave it using the default.
4. Use the location's apply action to assign several days together. **All enabled days** keeps days off untouched.
5. Complete the app's setup buttons, then **Save schedule**. The week repeats until you change it.

The main screen shows the next departure, whether automation is paused, and whether edits are unsaved. Locations can be saved ahead of time without enabling any days. You can copy one day's plan to other selected days, test a location, skip the next departure, undo a skip, and pause automation without losing the plan. There is no five-location limit.

**Check for updates** opens the same download link in your browser. Installation of an update still requires your confirmation. Version 2 keeps the original application ID and signing identity, so it can update version 1 in place. The old single destination becomes **Saved location**, with its existing weekdays and departure time.

## Install on Samsung

Open the download on your phone and tap **Install**. If prompted, open **Settings** and enable **Allow from this source** for the browser or file manager that opened the APK. If Samsung's Auto Blocker prevents installation, temporarily turn it off in **Settings → Security and privacy → Auto Blocker**, install the APK, then turn it back on. Do not disable Play Protect.

Inside Morning Drive, the setup buttons guide you through **Alarms & reminders**, **Appear on top**, and **Notifications**. In Samsung's app Battery settings, select **Unrestricted**. Remove Morning Drive from Sleeping or Deep sleeping apps if listed; add it to Never auto sleeping apps if available. Exact menu wording can vary by One UI version.

Open Google Maps once, finish its setup, and allow Maps to use your location. Use **Test navigation** for each saved location to verify that Google Maps recognizes it correctly.

Samsung reference: [Installing apps from a website](https://www.samsung.com/us/support/troubleshoot/TSG10001914/).

## Scheduling behavior

- Each enabled weekday has one location and one departure time. The saved plan repeats every week in the phone's local time zone.
- At departure, an unlocked phone requests Google Maps driving navigation. If locked, that day's trip stays pending until an unlock. Pending trips expire at local midnight.
- **Skip next departure** skips the pending trip first, otherwise the next scheduled departure. It affects only that date; the weekly plan stays intact. Undo restores the skipped date if it has not passed.
- **Save schedule** and **Enable** apply to future departures. They do not unexpectedly launch an earlier trip from today.
- At most one automatic navigation handoff is made per local day. **Test navigation** does not consume that day's scheduled handoff.
- **Disable** cancels future scheduling and pending navigation without erasing locations or day assignments.
- After a restart, scheduling is recovered after the first unlock. Today's missed departure can still run if it remains eligible.
- Time and time-zone changes reschedule the next departure.

A quiet ongoing notification keeps the unlock listener available while automation is enabled. **Force stop** in Android settings prevents automation until Morning Drive is reopened. Android's foreground-service **Stop** control stops the listener; a later scheduled alarm or app launch can restart it. Use Morning Drive's Disable button to pause deliberately.

The app cannot bypass the lock screen or navigate while the phone is powered off. Samsung battery restrictions or revoked permissions can interrupt automation. Google Maps controls GPS, address resolution, routing, traffic, and guidance. Morning Drive can confirm that it sent the navigation request; it cannot inspect Maps' private navigation state or prove guidance started. Test on your actual phone before relying on a commute.

## Privacy

Saved names, addresses, and schedules stay on the phone. There is no account, analytics, or server. Morning Drive requests no internet or location permission; Google Maps uses its own permissions. Checking for updates opens GitHub in the browser. Signing keys and local settings are excluded from Git.

## Build

Requirements: Bash, JDK 17+, Android SDK platform 36 and build-tools 36.0.0, `zip`, and standard command-line tools. No third-party app libraries are used.

```bash
ANDROID_HOME=/absolute/path/to/android-sdk ./build.sh
```

The signed output is `build/Morning_Drive.apk`. The GitHub release asset and Goggins copy are verified with SHA-256 after publication. On Goggins, the project is `C:\Users\Cameron\Documents\app_development\Morning Drive`.

The private `signing/` directory on Goggins retains the original key. Another checkout must restore that key securely and supply `SIGNING_KEYSTORE`, `SIGNING_ALIAS`, and `SIGNING_PASSWORD` in the environment or ignored `signing/signing.env`. Never commit the key or password. Windows builds require a suitable Bash environment, such as WSL, with the listed tools.

## Validation

The weekly scheduler passes 39 regression checks. The actual configuration code passes 41 migration/persistence checks with an in-memory Android preferences facade and a test-only JSON implementation. App compilation and signature verification pass. Run the configuration tests with `bash tests/config-jvm/run-tests.sh`; the test-only dependency is downloaded into ignored build output and checked by SHA-256.

Android instrumentation fixtures compile, but runtime execution could not complete because the software emulator did not finish booting. Physical Samsung Galaxy S25 testing has not been performed. See `tests/results/`, `tests/config-jvm/results.txt`, and the release notes for the exact scope.
