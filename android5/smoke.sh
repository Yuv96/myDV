#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 21
adb shell wm size 1280x720
adb shell wm density 160
adb install -r dist-test/Douyin-TV-0.1.0.apk
adb logcat -c
adb shell am start -W -n com.dycomment.tv.android5/com.dycomment.tv.InteractionSelfTestActivity
sleep 3
adb logcat -d -s Android5InteractionTest:I AndroidRuntime:E > evidence/interaction-test.txt
cat evidence/interaction-test.txt
grep -q 'PASS API21_MENU_KEYS_SOCIAL_PARSERS' evidence/interaction-test.txt
if grep -q 'FAIL\|FATAL EXCEPTION' evidence/interaction-test.txt; then exit 1; fi
adb logcat -c
adb shell am start -W -n com.dycomment.tv.android5/com.dycomment.tv.PlaybackSelfTestActivity
passed=false
for attempt in $(seq 1 30); do
  sleep 2
  adb logcat -d -s Android5SelfTest:I Android5Player:I NextVideoCache:I AndroidRuntime:E > evidence/selftest.txt
  if grep -q 'FAIL\|FATAL EXCEPTION' evidence/selftest.txt; then cat evidence/selftest.txt; exit 1; fi
  if grep -q 'PASS API21_H264' evidence/selftest.txt; then passed=true; break; fi
done
cat evidence/selftest.txt
$passed
adb exec-out screencap -p > evidence/android5-decoded.png
adb shell am force-stop com.dycomment.tv.android5
adb logcat -c
adb shell am start -W -n com.dycomment.tv.android5/com.dycomment.tv.SwitchingSelfTestActivity
passed=false
for attempt in $(seq 1 65); do
  sleep 2
  adb logcat -d -s Android5SwitchTest:I Android5Player:I NextVideoCache:I AndroidRuntime:E > evidence/switching-test.txt
  if grep -q 'FAIL\|FATAL EXCEPTION' evidence/switching-test.txt; then cat evidence/switching-test.txt; exit 1; fi
  if grep -q 'PASS API21_REAL_FEED' evidence/switching-test.txt; then passed=true; break; fi
done
cat evidence/switching-test.txt
$passed
adb exec-out screencap -p > evidence/switching-recovered.png
adb shell input keyevent 82
sleep 2
adb shell uiautomator dump /data/local/tmp/quick.xml
adb pull /data/local/tmp/quick.xml evidence/quick.xml
adb exec-out screencap -p > evidence/quick-menu.png
grep -q 'id/android5_interaction_panel' evidence/quick.xml
grep -q 'id/android5_menu_row_3' evidence/quick.xml
grep -q 'id/android5_menu_row_4' evidence/quick.xml
adb shell input keyevent 82
sleep 2
adb shell uiautomator dump /data/local/tmp/comments.xml
adb pull /data/local/tmp/comments.xml evidence/comments.xml
adb exec-out screencap -p > evidence/comments-menu.png
grep -q 'id/android5_comments_panel' evidence/comments.xml
adb shell input keyevent 4
sleep 1
adb shell input keyevent 4
sleep 2
adb shell uiautomator dump /data/local/tmp/settings.xml
adb pull /data/local/tmp/settings.xml evidence/settings.xml
adb exec-out screencap -p > evidence/settings-menu.png
grep -q 'id/android5_menu_panel' evidence/settings.xml
grep -q 'id/android5_menu_row_0' evidence/settings.xml
adb logcat -d -s AndroidRuntime:E > evidence/ui-runtime.txt
if grep -q 'FATAL EXCEPTION' evidence/ui-runtime.txt; then exit 1; fi
printf '%s\n' 'PASS: API 21 H.264 High/Baseline video output, advancing playback, next-video prefetch/cache reuse, pause, seek and rate; application launch.' > evidence/RESULT.txt
