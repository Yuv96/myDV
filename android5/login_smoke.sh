#!/usr/bin/env bash
# Run only inside the GitHub API21 emulator; fixtures contain no real credentials.
set -euo pipefail
mkdir -p evidence
adb logcat -c
adb shell am start -W -n com.dycomment.tv.android5/com.dycomment.tv.OfficialLoginSelfTestActivity
passed=false
for attempt in $(seq 1 20); do
  sleep 2
  adb logcat -d -s Android5OfficialLoginTest:I AndroidRuntime:E > evidence/official-login-fixtures.txt
  if grep -q 'FAIL\|FATAL EXCEPTION' evidence/official-login-fixtures.txt; then cat evidence/official-login-fixtures.txt; exit 1; fi
  if grep -q 'PASS API21_OFFICIAL_LOGIN_ISOLATION_HTTPONLY_REMOTE_LIFECYCLE' evidence/official-login-fixtures.txt; then passed=true; break; fi
done
cat evidence/official-login-fixtures.txt
$passed
adb logcat -c
adb shell am start -W -n com.dycomment.tv.android5/com.dycomment.tv.OfficialLoginSelfTestActivity --ez official_probe true
for attempt in $(seq 1 25); do
  sleep 2
  adb logcat -d -s Android5OfficialLoginTest:I AndroidRuntime:E > evidence/official-login-live.txt
  if grep -q 'FATAL EXCEPTION' evidence/official-login-live.txt; then cat evidence/official-login-live.txt; exit 1; fi
  if grep -q 'LIVE_PROBE_DONE\|LIVE_PROBE_SCREENSHOT_UNAVAILABLE' evidence/official-login-live.txt; then break; fi
done
cat evidence/official-login-live.txt
screenshot=$(sed -n 's/.*LIVE_SCREENSHOT_PATH=//p' evidence/official-login-live.txt | tr -d '\r' | tail -n 1)
if [ -n "$screenshot" ]; then adb pull "$screenshot" evidence/official-login-redacted.png; fi
# Live network/old-engine limitations are recorded, never relabelled as successful login.
adb shell input keyevent 4
sleep 1
adb shell input keyevent 4
sleep 1
