#!/usr/bin/env bash
# make-probe.sh <package> <versionCode> <targetSdk> <out.apk>
# A code-free APK signed with a throwaway key, for the automatic-update emulator test.
set -euo pipefail
PKG=$1; VC=$2; TARGET=$3; OUT=$4
BT="$ANDROID_HOME/build-tools/35.0.0"
JAR="$ANDROID_HOME/platforms/android-35/android.jar"
W=$(mktemp -d)
cat > "$W/AndroidManifest.xml" <<M
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$PKG" android:versionCode="$VC" android:versionName="$VC.0">
    <uses-sdk android:minSdkVersion="30" android:targetSdkVersion="$TARGET" />
    <application android:hasCode="false" android:label="$PKG" />
</manifest>
M
"$BT/aapt2" link -o "$W/u.apk" -I "$JAR" --manifest "$W/AndroidManifest.xml"
"$BT/zipalign" -p -f 4 "$W/u.apk" "$W/a.apk"
# One throwaway key for every probe, so version 2 can replace version 1.
KS="${PROBE_KEYSTORE:-/tmp/e2e-probe.jks}"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
  -alias probe -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=e2e probe" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android \
  --key-pass pass:android --ks-key-alias probe --out "$OUT" "$W/a.apk"
rm -rf "$W"
