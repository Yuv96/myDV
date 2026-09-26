#!/usr/bin/env python3
"""Check the shipping APK independently of the regression-only build."""
import os, pathlib, re, subprocess, sys, zipfile
from signing import require_release_signer, REFERENCES, certificate
root=pathlib.Path(__file__).resolve().parent.parent
apk=root/'dist/Douyin-TV-0.1.2.apk'
tools=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
info=subprocess.check_output([tools/'aapt','dump','badging',apk],text=True)
assert "package: name='com.dycomment.tv.android5'" in info
assert "sdkVersion:'21'" in info
assert "versionCode='1007'" in info and "versionName='0.1.2'" in info
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
subprocess.run([sys.executable,root/'android5/verify_branding.py',apk],check=True)
digest=require_release_signer(apk,tools/'apksigner')
for tag in REFERENCES:
    reference=root/'dist-reference'/(tag+'.apk')
    assert reference.is_file(), 'Published reference APK missing: '+tag
    assert certificate(reference,tools/'apksigner') == digest, 'Upgrade signature mismatch: '+tag
print('PASS production: API21, version 0.1.2 / 1007, no test activities/videos, signer matches a5.4 and v0.1.1')
