#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
TEST_DIR=tests/config-jvm
JAR="$TEST_DIR/build/json-20240303.jar"
mkdir -p "$TEST_DIR/build/classes"
if [ ! -f "$JAR" ]; then
  curl --fail --location --retry 2 --max-time 60 \
    https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar -o "$JAR"
fi
printf '%s  %s\n' 3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed "$JAR" | sha256sum -c -
find "$TEST_DIR/src" -name '*.java' > "$TEST_DIR/build/sources.txt"
printf '%s\n' app/src/main/java/com/cameron/morningdrive/Config.java app/src/main/java/com/cameron/morningdrive/Schedule.java >> "$TEST_DIR/build/sources.txt"
javac -encoding UTF-8 -cp "$JAR" -d "$TEST_DIR/build/classes" @"$TEST_DIR/build/sources.txt"
java -cp "$TEST_DIR/build/classes:$JAR" com.cameron.morningdrive.ConfigTest
