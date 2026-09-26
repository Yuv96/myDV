#!/usr/bin/env bash
# GitHub API21 emulator only: preserve a private-data marker across real upgrades.
set -euo pipefail
mkdir -p evidence
adb root
adb wait-for-device
adb uninstall com.dycomment.tv.android5 >/dev/null 2>&1 || true
adb install dist-reference/a5.4.apk > evidence/upgrade-a5.4.txt
tr -d '\r' < evidence/upgrade-a5.4.txt | grep -qx 'Success'
# API21's shell lacks printf, and legacy adb shell does not propagate its failure.
printf '%s' 'mydv-upgrade-preserved' > evidence/upgrade-marker.txt
adb push evidence/upgrade-marker.txt /data/data/com.dycomment.tv.android5/upgrade-probe
marker=$(adb shell cat /data/data/com.dycomment.tv.android5/upgrade-probe | tr -d '\r')
test "$marker" = 'mydv-upgrade-preserved'
adb install -r dist-reference/v0.1.1.apk > evidence/upgrade-v0.1.1.txt
tr -d '\r' < evidence/upgrade-v0.1.1.txt | grep -qx 'Success'
marker=$(adb shell cat /data/data/com.dycomment.tv.android5/upgrade-probe | tr -d '\r')
test "$marker" = 'mydv-upgrade-preserved'
adb install -r dist-reference/v0.1.2.apk > evidence/upgrade-v0.1.2.txt
tr -d '\r' < evidence/upgrade-v0.1.2.txt | grep -qx 'Success'
marker=$(adb shell cat /data/data/com.dycomment.tv.android5/upgrade-probe | tr -d '\r')
test "$marker" = 'mydv-upgrade-preserved'
adb install -r dist/Douyin-TV-0.1.3.apk > evidence/upgrade-v0.1.3.txt
tr -d '\r' < evidence/upgrade-v0.1.3.txt | grep -qx 'Success'
marker=$(adb shell cat /data/data/com.dycomment.tv.android5/upgrade-probe | tr -d '\r')
test "$marker" = 'mydv-upgrade-preserved'
adb shell rm /data/data/com.dycomment.tv.android5/upgrade-probe
printf '%s\n' 'PASS API21 a5.4 -> v0.1.1 -> v0.1.2 -> v0.1.3 install -r; application data preserved' > evidence/upgrade-result.txt
cat evidence/upgrade-result.txt
