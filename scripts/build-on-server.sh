#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/opt/gradle-cache}"
export PATH="$JAVA_HOME/bin:$PATH"

GRADLE_ZIP="${GRADLE_DIST_ZIP:-/opt/gradle-distributions/gradle-8.14-all.zip}"
GRADLE_HOME="$GRADLE_USER_HOME/local-gradle-8.14"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  [ -f "$GRADLE_ZIP" ] || { echo "ERROR: missing $GRADLE_ZIP"; exit 1; }
  mkdir -p "$GRADLE_USER_HOME"
  echo "Extracting local Gradle 8.14 distribution..."
  python3 - "$GRADLE_ZIP" "$GRADLE_USER_HOME" <<'PY'
import sys, zipfile, os
src, dest = sys.argv[1:]
with zipfile.ZipFile(src) as z:
  for item in z.infolist():
    target = os.path.realpath(os.path.join(dest,item.filename))
    if not target.startswith(os.path.realpath(dest) + os.sep):
      raise ValueError('Unsafe archive entry')
  z.extractall(dest)
PY
  mv "$GRADLE_USER_HOME/gradle-8.14" "$GRADLE_HOME"
  chmod +x "$GRADLE_HOME/bin/gradle"
fi

if [ ! -f "$ANDROID_HOME/platforms/android-36/android.jar" ]; then
  echo "ERROR: Missing Android SDK platform 36: $ANDROID_HOME/platforms/android-36"
  echo "Install via: \"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager\" 'platforms;android-36' 'build-tools;35.0.0'"
  exit 1
fi
if [ ! -d "$ANDROID_HOME/build-tools/35.0.0" ]; then
  echo "WARNING: build-tools 35.0.0 not present; Gradle may select another compatible installed version."
fi

"$JAVA_HOME/bin/java" -version
"$GRADLE_HOME/bin/gradle" --version | head -14
"$GRADLE_HOME/bin/gradle" --no-daemon --stacktrace :app:assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "ERROR: no output APK"; exit 1; }
sha256sum "$APK"
echo "SUCCESS: $(pwd)/$APK"
