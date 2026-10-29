#!/usr/bin/env bash
# Download the official aFreeRDP 3.32.1 (arm64-v8a) APK from F-Droid and extract its
# native libraries into app/src/main/jniLibs/arm64-v8a. ClearRDP uses these prebuilt
# FreeRDP libraries as its RDP engine.
set -euo pipefail
cd "$(dirname "$0")/.."
URL="https://f-droid.org/repo/com.freerdp.afreerdp_3320103.apk"
SHA256="520403bf41dec2e4599ddd22ab2bd7597f64867ec422c5e2fa46da259cac668e"
mkdir -p dl
APK=dl/afreerdp_3320103.apk
if [ ! -f "$APK" ]; then
  curl -fL --retry 5 -o "$APK" "$URL"
fi
echo "$SHA256  $APK" | sha256sum -c -
rm -rf app/src/main/jniLibs/arm64-v8a
mkdir -p app/src/main/jniLibs/arm64-v8a
unzip -q -j -o "$APK" 'lib/arm64-v8a/*' -d app/src/main/jniLibs/arm64-v8a
ls app/src/main/jniLibs/arm64-v8a
