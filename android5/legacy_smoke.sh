#!/usr/bin/env bash
# Called only by GitHub Actions after the API-21 regression APK is installed.
set -euo pipefail
mkdir -p evidence
for page in ProfileActivity FeaturedActivity SearchActivity; do
  adb shell am force-stop com.dycomment.tv.android5
  adb logcat -c
  adb shell am start -W -n "com.dycomment.tv.android5/com.dycomment.tv.$page" --ez legacy_ui_fixture true
  sleep 3
  adb logcat -d -s Android5LegacyUiTest:I AndroidRuntime:E > "evidence/legacy-$page.txt"
  cat "evidence/legacy-$page.txt"
  grep -q "PASS $page populated fixture" "evidence/legacy-$page.txt"
  if grep -q 'FAIL\|FATAL EXCEPTION' "evidence/legacy-$page.txt"; then exit 1; fi
  adb exec-out screencap -p > "evidence/legacy-$page.png"
  adb shell input keyevent 22
  adb shell input keyevent 20
  sleep 1
  adb exec-out screencap -p > "evidence/legacy-$page-focus.png"
done
adb shell am force-stop com.dycomment.tv.android5
adb logcat -c
adb shell am start -W -n com.dycomment.tv.android5/com.dycomment.tv.ProfileActivity --ez legacy_ui_fixture true --ez is_self true
sleep 3
adb logcat -d -s Android5LegacyUiTest:I AndroidRuntime:E > evidence/legacy-self-profile.txt
grep -q 'PASS ProfileActivity populated fixture' evidence/legacy-self-profile.txt
if grep -q 'FAIL\|FATAL EXCEPTION' evidence/legacy-self-profile.txt; then cat evidence/legacy-self-profile.txt; exit 1; fi
adb exec-out screencap -p > evidence/legacy-self-profile.png
adb shell am force-stop com.dycomment.tv.android5
