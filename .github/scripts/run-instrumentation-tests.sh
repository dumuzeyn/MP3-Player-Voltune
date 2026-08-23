#!/usr/bin/env bash

set -uo pipefail

REPORT_DIR="build/reports/emulator/android-${ANDROID_VERSION:-unknown}-api-${API_LEVEL:-unknown}"
PACKAGE_NAME="com.dumuzeyn.mp3player.debug"
mkdir -p "$REPORT_DIR"

collect_reports() {
  set +e
  adb logcat -b all -d -v threadtime > "$REPORT_DIR/logcat.txt" 2>&1
  adb shell dumpsys activity services "$PACKAGE_NAME" > "$REPORT_DIR/player-service.txt" 2>&1
  adb shell dumpsys media_session > "$REPORT_DIR/media-session.txt" 2>&1
  adb shell dumpsys dropbox --print data_app_crash > "$REPORT_DIR/android-crashes.txt" 2>&1
  adb shell run-as "$PACKAGE_NAME" sh -c \
    'for file in files/crash-reports/*.txt; do if [ -f "$file" ]; then echo "===== $file ====="; cat "$file"; fi; done' \
    > "$REPORT_DIR/local-crash-reports.txt" 2>&1
  adb shell getprop > "$REPORT_DIR/device-properties.txt" 2>&1
}

trap collect_reports EXIT

adb wait-for-device
adb logcat -c
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

GRADLE_ARGUMENTS=(:app:connectedDebugAndroidTest --stacktrace)
if [[ "${REQUIRE_TABLET:-false}" == "true" ]]; then
  GRADLE_ARGUMENTS+=("-Pandroid.testInstrumentationRunnerArguments.requireTablet=true")
  GRADLE_ARGUMENTS+=("-Pandroid.testInstrumentationRunnerArguments.class=com.dumuzeyn.mp3player.CompatibilityInstrumentedTest")
fi

./gradlew "${GRADLE_ARGUMENTS[@]}"
TEST_STATUS=$?

if [[ "${REQUIRE_TABLET:-false}" == "true" ]]; then
  ./gradlew :app:installDebug
  adb shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 2
  adb exec-out screencap -p > "$REPORT_DIR/tablet-layout.png"
  adb shell dumpsys activity top > "$REPORT_DIR/tablet-activity.txt" 2>&1
fi

exit "$TEST_STATUS"
