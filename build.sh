#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK with platform 36 and build-tools 36.0.0}"
# Local signing settings are intentionally excluded from Git.
if [ -f signing/signing.env ]; then source signing/signing.env; fi
SIGNING_KEYSTORE="${SIGNING_KEYSTORE:-signing/morning-drive.p12}"
SIGNING_ALIAS="${SIGNING_ALIAS:-morningdrive}"
: "${SIGNING_PASSWORD:?Set SIGNING_PASSWORD or configure ignored signing/signing.env}"
export SIGNING_PASSWORD
BT="$ANDROID_HOME/build-tools/36.0.0"
PLATFORM="$ANDROID_HOME/platforms/android-36/android.jar"
rm -rf build
mkdir -p build/classes build/gen build/dex
"$BT/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$BT/aapt2" link -I "$PLATFORM" --manifest app/src/main/AndroidManifest.xml --java build/gen -o build/base.apk build/resources.zip
find app/src/main/java build/gen -name '*.java' > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PLATFORM:$BT/core-lambda-stubs.jar" -d build/classes @build/sources.txt
jar cf build/classes.jar -C build/classes .
"$BT/d8" --lib "$PLATFORM" --min-api 26 --output build/dex build/classes.jar
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes*.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --ks "$SIGNING_KEYSTORE" --ks-key-alias "$SIGNING_ALIAS" --ks-pass env:SIGNING_PASSWORD --out build/Morning_Drive.apk build/aligned.apk
"$BT/apksigner" verify --verbose build/Morning_Drive.apk
echo "Built: $(pwd)/build/Morning_Drive.apk"
