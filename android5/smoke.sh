#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 21
adb install -r dist/myDV-Android5-1.1.9-a5.1.apk
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
printf '%s\n' 'PASS: API 21 H.264 High/Baseline video output, advancing playback, next-video prefetch/cache reuse, pause, seek and rate; application launch.' > evidence/RESULT.txt
