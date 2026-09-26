"""GitHub-only regression for refusing incompatible production signing."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import signing


class ReleaseGuardTests(unittest.TestCase):
    def rejected_build(self, configure, expected):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            # A leftover regression key must never make production silently succeed.
            (work / 'test-only.p12').write_bytes(b'synthetic-unused-key')
            env = dict(os.environ, BUILD_WORK=str(work), SELF_TEST='0')
            for name in ['ANDROID5_KEYSTORE', 'ANDROID5_KEYSTORE_PASSWORD', 'ANDROID_HOME']:
                env.pop(name, None)
            configure(env, work)
            result = subprocess.run([sys.executable, '-B', str(ROOT / 'build.py')],
                                    env=env, text=True, capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(expected, result.stderr)
            self.assertEqual((work / 'test-only.p12').read_bytes(), b'synthetic-unused-key')
            self.assertFalse((work / 'original.apk').exists())

    def test_production_does_not_reuse_development_key(self):
        self.rejected_build(lambda env, work: None, 'Production build requires')

    def test_missing_configured_key_is_not_regenerated(self):
        self.rejected_build(lambda env, work: env.update(ANDROID5_KEYSTORE=str(work / 'missing.p12')),
                            'Configured signing keystore does not exist')

    def test_production_cannot_guess_password(self):
        self.rejected_build(lambda env, work: env.update(ANDROID5_KEYSTORE=str(work / 'test-only.p12')),
                            'requires ANDROID5_KEYSTORE_PASSWORD')

    def test_valid_signature_from_another_key_is_rejected(self):
        with patch.object(signing, 'certificate', return_value='0' * 64):
            with self.assertRaisesRegex(RuntimeError, 'differs from the installed release'):
                signing.require_release_signer('unused.apk', 'unused-signer')


if __name__ == '__main__':
    unittest.main()
