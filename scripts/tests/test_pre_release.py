import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('verify_pre_release', Path(__file__).parents[1] / 'verify-pre-release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseEvidenceTest(unittest.TestCase):
    def test_invalid_version_codes_cannot_be_candidates(self):
        for value in ('version: 1.0.0+0', 'version: 1.0.0+2100000001', 'version: 1.0', 'version: 1.0.0-beta+1'):
            with self.assertRaises(ValueError):
                release.app_version(value)
        self.assertEqual(release.app_version('version: 0.1.0+1\n'), ('0.1.0', 1))

    def test_missing_skipped_and_failed_junit_are_not_green(self):
        with self.assertRaises(ValueError):
            release.junit_totals([])
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'result.xml'
            for attribute in ('skipped', 'errors', 'failures'):
                path.write_text(f'<testsuite tests="2" {attribute}="1"/>')
                with self.assertRaises(ValueError):
                    release.junit_totals([path])
            path.write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"/>')
            self.assertEqual(release.junit_totals([path])['tests'], 2)

    def test_incomplete_flutter_evidence_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'flutter.log'
            path.write_text('[]\nnull\n42\n{"type":"testDone","testID":1,"result":"success","hidden":false}\n')
            with self.assertRaises(ValueError):
                release.flutter_totals(path)
            with path.open('a') as stream:
                stream.write('{"type":"done","success":true}\n')
            self.assertEqual(release.flutter_totals(path)['passed'], 1)
            with path.open('a') as stream:
                stream.write('{"type":"testDone","testID":2,"skipped":true}\n')
            with self.assertRaises(ValueError):
                release.flutter_totals(path)

    def test_unsigned_or_partially_signed_bundle_cannot_pass(self):
        self.assertFalse(release.signed_jar('jar is unsigned.'))
        self.assertFalse(release.signed_jar('jar verified.\nThis jar contains unsigned entries.'))
        self.assertTrue(release.signed_jar('jar verified.\nThe certificate is self-signed.'))

    def test_signing_file_must_be_excluded_or_outside_checkout(self):
        self.assertFalse(release.signing_is_excluded(release.ROOT / 'frontend/pubspec.yaml'))
        with tempfile.NamedTemporaryFile(suffix='.jks', dir=release.ROOT / 'frontend') as key:
            self.assertTrue(release.signing_is_excluded(key.name))
        with tempfile.TemporaryDirectory() as folder:
            key = Path(folder) / 'fixture.p12'
            self.assertFalse(release.signing_is_excluded(key))
            key.write_text('disposable exclusion fixture, not a private key')
            self.assertTrue(release.signing_is_excluded(key))

    def test_test_processes_do_not_inherit_deployed_configuration(self):
        configured = {'DATABASE_URL': 'fixture', 'GOOGLE_APPLICATION_CREDENTIALS': 'fixture',
                      'HYPED_KEYSTORE_PASSWORD': 'fixture', 'FIREBASE_PROJECT_ID': 'fixture',
                      'TOKEN_SIGNING_ENABLED': 'true', 'SPRING_PROFILES_ACTIVE': 'production',
                      'ORG_GRADLE_PROJECT_INVITE_HOST': 'fixture'}
        with patch.dict(release.os.environ, configured):
            isolated = release.isolated_environment()
        self.assertTrue(all(key not in isolated for key in configured))
        self.assertIn('PATH', isolated)

    def test_bundle_metadata_and_invite_host_must_match(self):
        xml = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.hyped.hyped" android:versionName="0.1.0" android:versionCode="1">
          <application android:usesCleartextTraffic="false"><activity><intent-filter android:autoVerify="true">
          <data android:scheme="https" android:host="links.hyped.example" />
          </intent-filter></activity></application></manifest>'''
        release.manifest_version(xml, '0.1.0', 1, 'links.hyped.example')
        for modified in (xml.replace('versionCode="1"', 'versionCode="2"'),
                         xml.replace('Traffic="false"', 'Traffic="true"'),
                         xml.replace('links.hyped.example', 'foreign.example')):
            with self.assertRaises(ValueError):
                release.manifest_version(modified, '0.1.0', 1, 'links.hyped.example')


if __name__ == '__main__':
    unittest.main()
