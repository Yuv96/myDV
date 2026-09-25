#!/usr/bin/env python3
"""Check the shipping APK independently of the regression-only build."""
import os, pathlib, subprocess, zipfile
root=pathlib.Path(__file__).resolve().parent.parent
apk=root/'dist/Douyin-TV-0.1.0.apk'
tools=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
info=subprocess.check_output([tools/'aapt','dump','badging',apk],text=True)
assert "sdkVersion:'21'" in info
assert "versionCode='1005'" in info and "versionName='0.1.0'" in info
assert '抖音抬头版' in info
manifest=subprocess.check_output([tools/'aapt','dump','xmltree',apk,'AndroidManifest.xml'],text=True)
assert 'SelfTestActivity' not in manifest and 'SurfaceCoverTestActivity' not in manifest
assert 'QishuiActivity' not in manifest
with zipfile.ZipFile(apk) as z:
    assert not any('selftest-' in n for n in z.namelist())
    dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
    assert b'SwitchingSelfTestActivity' not in dex
    assert b'DEFAULT_MS_TOKEN' not in dex
subprocess.run([tools/'apksigner','verify','--verbose','--min-sdk-version','21',apk],check=True)
print('PASS production: API21, version 0.1.0 / 1005, no test activities/videos or Qishui')
