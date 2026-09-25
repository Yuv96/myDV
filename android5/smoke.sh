#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 21
adb install -r dist/myDV-Android5-1.1.9-a5.2.apk
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
adb shell am start -W -n com.dycomment.tv.android5/com.dycomment.tv.MainActivity
sleep 25
adb shell input keyevent 20
sleep 15
adb exec-out screencap -p > evidence/android5-live.png
adb logcat -d -s Android5Player:I NextVideoCache:I AndroidRuntime:E > evidence/live.txt
cat evidence/live.txt
if grep -q 'FATAL EXCEPTION' evidence/live.txt; then exit 1; fi
adb shell dumpsys activity activities | grep mResumedActivity | grep com.dycomment.tv.android5
adb shell input keyevent 82
sleep 2
adb shell uiautomator dump /data/local/tmp/quick.xml
adb pull /data/local/tmp/quick.xml evidence/quick.xml
grep -q 'text="主页"' evidence/quick.xml
grep -q 'text="分享"' evidence/quick.xml
adb exec-out screencap -p > evidence/quick-menu.png
adb shell input keyevent 82
sleep 2
adb shell uiautomator dump /data/local/tmp/comments.xml
adb pull /data/local/tmp/comments.xml evidence/comments.xml
grep -q '评论' evidence/comments.xml
adb exec-out screencap -p > evidence/comments-menu.png
adb shell input keyevent 4
sleep 1
adb shell input keyevent 4
sleep 2
adb shell uiautomator dump /data/local/tmp/settings.xml
adb pull /data/local/tmp/settings.xml evidence/settings.xml
grep -q '播放与设置' evidence/settings.xml
grep -q '关注的直播' evidence/settings.xml
adb exec-out screencap -p > evidence/settings-menu.png
adb logcat -d -s AndroidRuntime:E > evidence/ui-runtime.txt
if grep -q 'FATAL EXCEPTION' evidence/ui-runtime.txt; then exit 1; fi
printf '%s\n' 'PASS: API 21 H.264 High/Baseline video output, advancing playback, next-video prefetch/cache reuse, pause, seek and rate; application launch.' > evidence/RESULT.txt
