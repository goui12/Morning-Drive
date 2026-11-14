#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${ANDROID_HOME:?Set ANDROID_HOME}"
# Local signing settings are intentionally excluded from Git.
if [ -f signing/signing.env ]; then source signing/signing.env; fi
SIGNING_KEYSTORE="${SIGNING_KEYSTORE:-signing/morning-drive.p12}"
SIGNING_ALIAS="${SIGNING_ALIAS:-morningdrive}"
: "${SIGNING_PASSWORD:?Set SIGNING_PASSWORD or configure ignored signing/signing.env}"
export SIGNING_PASSWORD
BT="$ANDROID_HOME/build-tools/36.0.0"
PLATFORM="$ANDROID_HOME/platforms/android-36/android.jar"
test -f build/classes.jar || { echo "Build application first"; exit 1; }
ROOT="$PWD"
for KIND in probe tests; do
  OUT="tests/android/build/$KIND"
  rm -rf "$OUT"
  mkdir -p "$OUT/classes" "$OUT/dex"
  if [ "$KIND" = probe ]; then SOURCE=tests/android/probe; else SOURCE=tests/android; fi
  "$BT/aapt2" link -I "$PLATFORM" --manifest "$SOURCE/AndroidManifest.xml" -o "$OUT/base.apk"
  find "$SOURCE/src" -name '*.java' > "$OUT/sources.txt"
  javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PLATFORM:$BT/core-lambda-stubs.jar" -classpath build/classes.jar -d "$OUT/classes" @"$OUT/sources.txt"
  jar cf "$OUT/classes.jar" -C "$OUT/classes" .
  "$BT/d8" --lib "$PLATFORM" --classpath build/classes.jar --min-api 26 --output "$OUT/dex" "$OUT/classes.jar"
  cp "$OUT/base.apk" "$OUT/unsigned.apk"
  (cd "$OUT/dex" && zip -q ../unsigned.apk classes*.dex)
  "$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
  "$BT/apksigner" sign --ks "$SIGNING_KEYSTORE" --ks-key-alias "$SIGNING_ALIAS" --ks-pass env:SIGNING_PASSWORD --out "$OUT/$KIND.apk" "$OUT/aligned.apk"
done
echo "Emulator only: install build/Morning_Drive.apk, tests/android/build/probe/probe.apk, and tests/android/build/tests/tests.apk."
