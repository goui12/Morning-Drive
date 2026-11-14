# Morning Drive 1.0

A personal Android app that opens Google Maps in driving-navigation mode at a saved time on selected weekdays. Built for Cameron's Samsung Galaxy S25; Android 8.0 or later is required.

## Install and set up

1. Copy `Morning_Drive.apk` to the phone, open it, and allow that browser or file manager to install unknown apps if Android asks. Install Morning Drive.
2. Open Morning Drive. Enter a complete destination address, select the start time, and select the weekdays. Monday through Friday are selected initially.
3. In **One-time setup**, allow **Alarms & reminders**, **Appear on top**, and **Notifications**. Android may require selecting Morning Drive from a list before granting a permission.
4. Open **Samsung battery settings**. In the app's Battery settings, select **Unrestricted**. Remove Morning Drive from Sleeping apps or Deep sleeping apps if listed; add it to Never auto sleeping apps when that option is available. Samsung's labels and menu locations can vary by One UI version.
5. Tap **Test navigation**. This opens Google Maps immediately using the address currently entered. Complete Google Maps' initial setup and allow its location access. Confirm that Maps recognizes the intended address and can start guidance.
6. Return to Morning Drive and tap **Save**. The status displays the next departure. Save enables automation and keeps the configuration on the phone.

For an initial end-to-end test, save a departure a few minutes in the future on today's weekday. Lock the phone before that time, wait until after the scheduled minute, and unlock it. Google Maps should open for the pending departure. Then restore the intended commute schedule and save again.

## Behavior

- At the selected time, the app requests driving navigation from the phone's current location to the saved destination.
- If the phone is locked, the departure remains pending. Unlocking can trigger it without opening Morning Drive.
- Pending departures expire at local midnight. Yesterday's missed trip does not launch the following day.
- **Save** and **Enable** apply only to future departures. Saving or enabling after today's selected time does not immediately launch a missed trip.
- There is at most one automatic handoff to Google Maps per local calendar day. Changing the time or re-enabling after that day's handoff does not cause another automatic launch that day. **Test navigation** remains available and does not count as the scheduled handoff.
- **Disable** cancels future scheduling and pending navigation while retaining the address, time, and weekday selection. The button changes to **Enable**.
- Configuration survives restarts. Recovery occurs after the phone's first unlock following a restart. If today's departure became due while the phone was restarting or awaiting its first unlock, it can still run that day.
- Times use the phone's current local time zone. Clock and time-zone changes cause the schedule to be recalculated.
- An ongoing notification supports the small background monitor that listens for unlock events. The app does not need to stay visibly open.

**Force stop** in Android's app settings prevents automation until Morning Drive is opened again. Android's separate **Stop** action for an ongoing foreground service stops the monitor, so unlock detection can be unavailable until the next scheduled alarm or app launch. Scheduled alarms can still run after that service Stop action. Use the app's **Disable** button when you want automation paused.

The app cannot bypass a secure lock screen or run while the phone is powered off. Revoked permissions, Samsung battery restrictions, and Android service controls can interrupt automation. If automatic launch is blocked, inspect the app's status and notification; these cases still need validation on the physical S25.

Google Maps handles address resolution, GPS, routing, traffic, and guidance. Sending the navigation request successfully does not prove that Maps started guidance: Maps may require first-run setup, a location fix, connectivity, or user action. Morning Drive records the handoff rather than inspecting Maps' private navigation state.

## Permissions and privacy

| Permission or capability | Purpose |
| --- | --- |
| Alarms & reminders | Schedule a selected departure accurately, including while the phone is idle. |
| Appear on top | Allows the app to request a navigation launch from the background under Android's background-activity rules. |
| Notifications | Shows the ongoing monitor status and a way to open the app or retry a navigation request. |
| Foreground service, special-use type | Keeps an unlock listener available while automation is enabled. |
| Receive boot completed | Rebuilds scheduling after restart and the first unlock. |

Morning Drive requests no internet or location permission itself. It has no account, analytics, or remote server. Destination and schedule settings are stored locally; the destination is passed to the installed Google Maps app when requesting navigation. Google Maps uses its own permissions and services.

## Build from source

Requirements: Linux or another environment with Bash, Java JDK 17 or newer, Android SDK platform 36, Android build-tools 36.0.0, `zip`, and standard command-line tools. There are no third-party app dependencies or Gradle downloads in this build.

The GitHub repository contains source and build scripts, but no signing key or password. The local working copy on Goggins keeps the existing signing identity in the ignored `signing/` directory so updates can retain the installed app's identity.

When building from that local working copy:

```bash
ANDROID_HOME=/absolute/path/to/android-sdk ./build.sh
```

For another checkout, restore the existing private key securely and set `SIGNING_KEYSTORE`, `SIGNING_ALIAS`, and `SIGNING_PASSWORD` in your environment or in an ignored `signing/signing.env` file. The default key path is `signing/morning-drive.p12` and default alias is `morningdrive`. Environment values can override the supplied local signing settings. The build passes the password to the signing tool through the environment, not a command-line argument.

On Windows, these scripts require a suitable Bash build environment with the tools listed above; the repository can be edited normally on Goggins.

The signed APK is written to `build/Morning_Drive.apk`. The script compiles the native Java app, packages resources, creates DEX bytecode, aligns the APK, signs it, and verifies its signature.

Keep the private signing directory and any earlier source archive containing it private. They are excluded from version control. Future updates installed over this app must retain the same application ID and signing key and use an appropriate version code; generating a new key will not preserve update compatibility.

## Validation

- 26 scheduling checks passed, covering the schedule's boundary and recovery cases.
- APK compilation, alignment, signing, and signature verification passed.
- Emulator runtime validation: not completed. The Android 16 software emulator did not reach a usable boot/ADB state in this build environment. Runtime smoke tests are included under `tests/android` for a suitable emulator; no runtime pass is claimed.
- Physical Samsung Galaxy S25 validation: not performed. Treat this as a first personal build and complete the on-phone tests before relying on it for a commute.
