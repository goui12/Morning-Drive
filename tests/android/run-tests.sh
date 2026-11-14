#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${ANDROID_HOME:?Set ANDROID_HOME}"
ADB="$ANDROID_HOME/platform-tools/adb"
if [ "$("$ADB" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]; then
  echo "Refusing: these fixtures are only for an isolated emulator." >&2
  exit 1
fi
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" install -r build/Morning_Drive.apk
"$ADB" install -r tests/android/build/probe/probe.apk
"$ADB" install -r tests/android/build/tests/tests.apk
"$ADB" shell appops set com.cameron.morningdrive SCHEDULE_EXACT_ALARM allow
"$ADB" shell appops set com.cameron.morningdrive SYSTEM_ALERT_WINDOW allow
"$ADB" shell pm grant com.cameron.morningdrive android.permission.POST_NOTIFICATIONS
"$ADB" logcat -c
"$ADB" shell am instrument -w com.cameron.morningdrive.tests/com.cameron.morningdrive.SmokeInstrumentation | tee tests/android/build/results.txt
if ! grep -q 'SUCCESS:' tests/android/build/results.txt; then
  "$ADB" logcat -d -s MorningDriveSmoke:E AndroidRuntime:E ActivityTaskManager:W '*:S' > tests/android/build/failure-logcat.txt
  echo "Instrumentation failed; see tests/android/build/failure-logcat.txt" >&2
  exit 1
fi

"$ADB" logcat -c
"$ADB" shell am instrument -w -e mode arm_pending com.cameron.morningdrive.tests/com.cameron.morningdrive.SmokeInstrumentation | tee tests/android/build/uninstrumented-arm.txt
grep -q 'ARMED:' tests/android/build/uninstrumented-arm.txt
echo "Waiting for exact alarm with instrumentation stopped and screen off..."
sleep 12
"$ADB" logcat -d -s MorningDriveMapsProbe:I '*:S' > tests/android/build/before-unlock-log.txt
if grep -q 'google.navigation:q=' tests/android/build/before-unlock-log.txt; then
  echo "FAIL: Maps probe started before unlock" >&2
  exit 1
fi
"$ADB" shell input keyevent KEYCODE_WAKEUP
"$ADB" shell wm dismiss-keyguard
for attempt in $(seq 1 20); do
  "$ADB" logcat -d -s MorningDriveMapsProbe:I '*:S' > tests/android/build/after-unlock-log.txt
  if grep -q 'google.navigation:q=Uninstrumented%20handoff%2C%20Portland%20OR&mode=d' tests/android/build/after-unlock-log.txt; then
    echo "SUCCESS: uninstrumented exact-alarm, background unlock and Maps intent handoff" | tee tests/android/build/uninstrumented-result.txt
    exit 0
  fi
  sleep 1
done
"$ADB" logcat -d -s AndroidRuntime:E ActivityTaskManager:W ActivityManager:I '*:S' > tests/android/build/failure-logcat.txt
echo "FAIL: no uninstrumented handoff; see failure-logcat.txt" >&2
exit 1
