#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence
collect_diagnostics() {
  adb logcat -d -s AndroidRuntime:E ActivityManager:I > evidence/final-runtime.txt || true
  adb shell cat /data/anr/traces.txt > evidence/anr.txt 2>/dev/null || true
  adb shell dumpsys meminfo com.dycomment.tv.android5 > evidence/memory.txt || true
}
trap collect_diagnostics EXIT
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 21
adb shell wm size 1280x720
adb shell wm density 160
adb install -r dist-test/Douyin-TV-0.1.3.apk
adb logcat -c
adb shell am start -W -n com.dycomment.tv.android5/com.dycomment.tv.InteractionSelfTestActivity
sleep 3
adb logcat -d -s Android5InteractionTest:I AndroidRuntime:E > evidence/interaction-test.txt
cat evidence/interaction-test.txt
grep -q 'PASS API21_MENU_KEYS_SOCIAL_PARSERS' evidence/interaction-test.txt
if grep -q 'FAIL\|FATAL EXCEPTION' evidence/interaction-test.txt; then exit 1; fi
adb logcat -c
adb shell am start -W -n com.dycomment.tv.android5/com.dycomment.tv.CommentsSelfTestActivity
passed=false
for attempt in $(seq 1 35); do
  sleep 2
  adb logcat -d -s Android5CommentsTest:I AndroidRuntime:E > evidence/comments-test.txt
  if grep -q 'FAIL\|FATAL EXCEPTION' evidence/comments-test.txt; then cat evidence/comments-test.txt; exit 1; fi
  if grep -q 'PASS API21_COMMENTS_READ_ONLY_SCROLL_PAGING_LIFECYCLE' evidence/comments-test.txt; then passed=true; break; fi
done
cat evidence/comments-test.txt
$passed
comments_screenshot=$(sed -n 's/.*SCREENSHOT_PATH=//p' evidence/comments-test.txt | tr -d '\r' | tail -n 1)
test -n "$comments_screenshot"
adb pull "$comments_screenshot" evidence/comments-populated.png
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
# Exercise the real author Activity and return through the remote, without an account.
adb shell input keyevent 4
adb shell input keyevent 82
adb shell input keyevent 20
adb shell input keyevent 20
adb shell input keyevent 20
adb shell input keyevent 23
sleep 3
adb shell dumpsys activity activities | grep mResumedActivity | grep ProfileActivity
adb shell uiautomator dump /data/local/tmp/profile.xml
adb pull /data/local/tmp/profile.xml evidence/profile.xml
adb exec-out screencap -p > evidence/profile.png
adb shell input keyevent 4
sleep 3
adb shell dumpsys activity activities | grep mResumedActivity | grep MainActivity
adb exec-out screencap -p > evidence/profile-return.png
# Account menu must expose only QR login, never raw credential editors.
adb shell input keyevent 4
adb shell input keyevent 20
adb shell input keyevent 20
adb shell input keyevent 20
adb shell input keyevent 23
sleep 1
adb shell uiautomator dump /data/local/tmp/account.xml
adb pull /data/local/tmp/account.xml evidence/account.xml
# API21's uiautomator dump loses Chinese text; verify the single QR row by stable IDs.
grep -q 'id/android5_menu_row_0' evidence/account.xml
if grep -q 'id/android5_menu_row_1' evidence/account.xml; then exit 1; fi
adb shell input keyevent 4
adb shell input keyevent 4
adb logcat -d -s AndroidRuntime:E > evidence/ui-runtime.txt
if grep -q 'FATAL EXCEPTION' evidence/ui-runtime.txt; then exit 1; fi
printf '%s\n' 'PASS: API 21 H.264 High/Baseline video output, advancing playback, next-video prefetch/cache reuse, pause, seek and rate; application launch.' > evidence/RESULT.txt

bash android5/legacy_smoke.sh
