#!/usr/bin/env bash
# The automatic-update test, on an Android 14 emulator with no Google services (like the
# Light Phone III). Drives the real AutoUpdateWorker through the debug-only DebugHooks.
# No pipefail: `grep -q` closes the pipe on the first match, and pipefail turns the
# writer's SIGPIPE into a failure (exit 141) whenever the input is long.
set -eu
OUT=e2e-out
BM=com.gios.brightmarket
HOOK="$BM/.DebugHooks"
PASS=0

vc() { adb shell dumpsys package "$1" | grep -m1 -o 'versionCode=[0-9]*' | cut -d= -f2; }
logs() { adb logcat -d -s BMAuto:* ; }
fail() {
  echo "FAIL: $*"
  echo "--- BMAuto"; logs | tail -60
  echo "--- PackageInstaller"; adb logcat -d | grep -i -E 'PackageInstaller|InstallerSession' | tail -40
  exit 1
}
ok() { PASS=$((PASS+1)); echo "ok $PASS - $*"; }
expect_vc() { local got; got=$(vc "$1"); [ "$got" = "$2" ] || fail "$1 versionCode $got, expected $2"; ok "$1 is versionCode $2"; }
expect_log() { logs | grep -q -- "$1" || fail "no log line: $1"; ok "log: $1"; }
hook() { adb shell am broadcast -f 0x20 -n "$HOOK" -a "com.gios.brightmarket.debug.$1" "${@:2}" >/dev/null; }
no_dialog() {
  if adb shell dumpsys activity activities | grep -q -i 'packageinstaller'; then
    fail "an install dialog is on screen"
  fi
  ok "no install dialog on screen"
}
jobs() { adb shell dumpsys jobscheduler "$BM" 2>/dev/null; }

adb shell settings put global verifier_verify_adb_installs 0 || true
adb shell settings put global package_verifier_enable 0 || true

python3 -m http.server 8000 --directory "$OUT" >/dev/null 2>&1 </dev/null &
SERVER=$!
trap 'kill $SERVER 2>/dev/null || true' EXIT
sleep 1
adb reverse tcp:8000 tcp:8000

adb install -r "$OUT/bm-v1.apk"
for p in owned held fresh oldtarget; do adb install -i "$BM" "$OUT/$p-v1.apk"; done
adb install "$OUT/foreign-v1.apk"
adb shell dumpsys package com.gios.e2e.owned | grep -q "installerPackageName=$BM" || fail "owned probe not installed as BrightMarket's"
ok "probes installed (owned: installer of record is BrightMarket)"

adb shell dumpsys battery unplug
adb logcat -c
hook CONFIGURE --es index_url http://127.0.0.1:8000/index.json --ez auto true
sleep 4
expect_log "configured auto=true index=http://127.0.0.1:8000/index.json"
J=$(jobs)
for c in CHARGING NOT_METERED BATTERY_NOT_LOW; do
  echo "$J" | grep -q "$c" || { echo "$J" | head -60; fail "periodic job lacks $c"; }
done
ok "periodic job scheduled: charging, unmetered network, battery not low"

# Turning it on may start the periodic run at once; let that finish first.
for i in $(seq 1 15); do logs | grep -q -E "done:|not allowed" && break; sleep 2; done

# A fresh phone: BrightMarket has never been allowed to install apps. Nothing may
# install, nothing may pop up, and the reason must be recorded for Settings.
adb logcat -c
hook RUN
for i in $(seq 1 20); do logs | grep -q "not allowed to install apps" && break; sleep 2; done
expect_log "not allowed to install apps"
expect_vc com.gios.e2e.owned 1
no_dialog
hook STATE; sleep 2
expect_log "isn't allowed to install apps yet"

# What a real phone has after its first install through BrightMarket.
adb shell appops set "$BM" REQUEST_INSTALL_PACKAGES allow
adb logcat -c
hook RUN
# BrightMarket goes last and its install ends the process, so wait for its versionCode.
for i in $(seq 1 90); do [ "$(vc $BM)" = "2" ] && break; sleep 2; done
sleep 8

expect_vc com.gios.e2e.owned 2
expect_log "installed com.gios.e2e.owned 2.0"
expect_vc com.gios.e2e.foreign 1
expect_log "skip com.gios.e2e.foreign: needs a tap"
expect_vc com.gios.e2e.held 1
expect_log "skip com.gios.e2e.held: held in the catalogue"
expect_vc com.gios.e2e.fresh 1
expect_log "skip com.gios.e2e.fresh: released under a day ago"
expect_vc com.gios.e2e.oldtarget 1
expect_log "skip com.gios.e2e.oldtarget: needs a tap (system)"
adb shell dumpsys package com.gios.e2e.oldtarget | grep -q "versionCode=1" && ok "abandoned session left oldtarget alone"
no_dialog
expect_log "self: committing 1.33.0-e2e"
expect_vc $BM 2
expect_log "self: now 1.33.0-e2e"
jobs | grep -q CHARGING || fail "schedule gone after BrightMarket replaced itself"
ok "schedule survives the self-update"

adb logcat -c
hook STATE
sleep 3
expect_log "recorded=1.33.0-e2e"
expect_log "updated Owned, BrightMarket"

# A second run: nothing left to install, and BrightMarket must not reinstall itself.
adb logcat -c
hook RUN
for i in $(seq 1 30); do logs | grep -q "done:" && break; sleep 2; done
expect_log "done: Last check: nothing installed"
logs | grep -q "self: committing" && fail "BrightMarket reinstalled itself on the second run"
ok "second run installs nothing"
no_dialog

hook CONFIGURE --ez auto false
sleep 4
jobs | grep -q CHARGING && fail "turning it off left the periodic job scheduled"
ok "turning it off cancels the schedule"

adb shell dumpsys battery reset
echo "ALL $PASS CHECKS PASSED"
touch "$OUT/PASSED"
