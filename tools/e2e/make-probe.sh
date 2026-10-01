#!/usr/bin/env bash
# make-probe.sh <package> <versionCode> <targetSdk> <out.apk>
# A code-free APK signed with the debug key, for the automatic-update emulator test.
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
"$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
  --key-pass pass:android --ks-key-alias androiddebugkey --out "$OUT" "$W/a.apk"
rm -rf "$W"
