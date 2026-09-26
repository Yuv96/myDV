"""Release certificate continuity checks. Execute only on GitHub runners."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.request

EXPECTED_SHA256 = 'a9a53928e2d288aba0ed134d1c92bf1e8ba29886f22b23578f56cd34ef1d32e6'
ROOT = Path(__file__).resolve().parent.parent
REFERENCES = {
    'v0.1.2': ('v0.1.2/Douyin-TV-0.1.2.apk',
               '0d0af45d84958437fbf4eba236f90797dec5f54409ea3adfb41a83f77493762e'),
    'a5.4': ('android5-ui-return-v4/myDV-Android5-1.1.9-a5.4.apk',
             '09e69ff762d05a456861ccd08223bcd31dc32790c1fef3d574465841aacfc9b7'),
    'v0.1.1': ('v0.1.1/Douyin-TV-0.1.1.apk',
               '00c36f95f541131b28e6b3cd54d62c1b7bd7adbcbc04032407d240a630af97fd'),
}


def certificate(apk, signer):
    result = subprocess.check_output([
        str(signer), 'verify', '--verbose', '--print-certs', '--min-sdk-version', '21',
        str(apk)], text=True)
    digests = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$', result, re.M)
    if len(digests) != 1:
        raise RuntimeError('Expected exactly one verified APK signing certificate')
    return digests[0]


def require_release_signer(apk, signer):
    actual = certificate(apk, signer)
    print('APK certificate SHA-256:', actual)
    if actual != EXPECTED_SHA256:
        raise RuntimeError('Signing certificate differs from the installed release; do not publish')
    return actual


def references():
    output = ROOT / 'dist-reference'
    output.mkdir(exist_ok=True)
    signer = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0/apksigner'
    report = {}
    for tag, (asset, digest) in REFERENCES.items():
        apk = output / (tag + '.apk')
        if not apk.exists():
            urllib.request.urlretrieve(
                'https://github.com/Yuv96/myDV/releases/download/' + asset, apk)
        actual_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
        if actual_hash != digest:
            raise RuntimeError('Published reference APK checksum mismatch: ' + tag)
        report[tag] = {'apk_sha256': actual_hash, 'certificate_sha256': certificate(apk, signer)}
    evidence = ROOT / 'evidence'
    evidence.mkdir(exist_ok=True)
    (evidence / 'published-signatures.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
    if any(item['certificate_sha256'] != EXPECTED_SHA256 for item in report.values()):
        raise RuntimeError('Published release certificates differ from the pinned upgrade signer')
    print('PASS all published releases use the pinned upgrade certificate')


if __name__ == '__main__':
    references()
