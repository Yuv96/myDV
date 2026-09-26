#!/usr/bin/env python3
"""Check the shipping APK independently of the regression-only build."""
import os, pathlib, re, subprocess, zipfile
root=pathlib.Path(__file__).resolve().parent.parent
apk=root/'dist/Douyin-TV-0.1.1.apk'
tools=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
info=subprocess.check_output([tools/'aapt','dump','badging',apk],text=True)
assert "package: name='com.dycomment.tv.android5'" in info
assert "sdkVersion:'21'" in info
assert "versionCode='1006'" in info and "versionName='0.1.1'" in info
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
if os.environ.get('REQUIRE_RELEASE_SIGNER') == '1':
    certificates=subprocess.check_output([tools/'apksigner','verify','--print-certs',apk],text=True)
    digests=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$', certificates, re.M)
    assert digests == ['a9a53928e2d288aba0ed134d1c92bf1e8ba29886f22b23578f56cd34ef1d32e6'], 'Release signer differs from published a5.4; do not publish this APK'
    print('PASS release signer matches published a5.4')
print('PASS production: API21, version 0.1.1 / 1006, no test activities/videos or Qishui')
