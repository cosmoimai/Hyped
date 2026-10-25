import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('local_backend', Path(__file__).parents[1] / 'local-backend.py')
local = importlib.util.module_from_spec(spec)
spec.loader.exec_module(local)


class LocalBackendSetupTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        root = Path(self.folder.name)
        self.repo = root / 'repo'
        self.home = root / 'private'
        firebase = self.repo / 'frontend/android/app/google-services.json'
        firebase.parent.mkdir(parents=True)
        firebase.write_text(json.dumps({'project_info': {'project_id': 'local-test-project'}}))
        patches = (patch.object(local, 'ROOT', self.repo), patch.object(local, 'HOME_DIR', self.home))
        for item in patches:
            item.start()
            self.addCleanup(item.stop)
        with contextlib.redirect_stdout(io.StringIO()):
            local.prepare()

    def settings(self):
        return json.loads((self.home / 'environment.json').read_text())

    def test_prepare_preserves_keys_and_keeps_private_local_defaults(self):
        paths = [self.home / 'access-token-private.pem', self.home / 'identity-lookup-hmac.bin',
                 self.home / 'key-registry/wrapping-key.bin']
        hashes = [hashlib.sha256(p.read_bytes()).hexdigest() for p in paths]
        with contextlib.redirect_stdout(io.StringIO()):
            local.prepare()
        self.assertEqual(hashes, [hashlib.sha256(p.read_bytes()).hexdigest() for p in paths])
        for path in paths + [self.home / 'environment.json']:
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        settings = self.settings()
        self.assertEqual(settings['HYPED_PERSONAL_DATA_PROVIDER'], 'local')
        self.assertEqual(settings['SERVER_ADDRESS'], '127.0.0.1')
        self.assertEqual(settings['SPRING_PROFILES_ACTIVE'], 'local')
        self.assertEqual(settings['LEGAL_PUBLICATION_READY'], 'false')

    def test_lost_wrapping_key_is_not_replaced_over_existing_records(self):
        key = self.home / 'key-registry/wrapping-key.bin'
        key.unlink()
        (key.parent / 'record.key').write_bytes(b'fixture')
        with self.assertRaisesRegex(ValueError, 'missing its wrapping key'):
            local.prepare()
        self.assertFalse(key.exists())

    def test_missing_admin_credentials_prevent_backend_start(self):
        with patch.object(local.subprocess, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'Firebase Admin JSON is missing'):
                local.start(self.settings())
            run.assert_not_called()

    def test_foreign_firebase_project_prevents_backend_start(self):
        path = self.home / 'firebase-admin.json'
        path.write_text(json.dumps({'type': 'service_account', 'project_id': 'another-project'}))
        with patch.object(local.subprocess, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'must match'):
                local.start(self.settings())
            run.assert_not_called()

    def test_start_fences_production_environment_and_forces_loopback(self):
        (self.home / 'firebase-admin.json').write_text(json.dumps({
            'type': 'service_account', 'project_id': 'local-test-project'}))
        settings = self.settings() | {'SERVER_ADDRESS': '0.0.0.0', 'SPRING_PROFILES_ACTIVE': 'production',
                                      'DEBUG': 'true', 'LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB_SERVLET': 'TRACE'}
        with patch.dict(os.environ, {'DATABASE_URL': 'deployed-fixture', 'SPRING_PROFILES_ACTIVE': 'production',
                                    'JAVA_TOOL_OPTIONS': 'fixture', 'PUSH_SCHEDULER_EMAIL': 'fixture',
                                    'DEBUG': 'true', 'TRACE': 'true', 'LOGGING_LEVEL_ROOT': 'DEBUG'}):
            with patch.object(local.subprocess, 'run') as run, contextlib.redirect_stdout(io.StringIO()):
                run.return_value.returncode = 0
                self.assertEqual(local.start(settings), 0)
                environment = run.call_args.kwargs['env']
                self.assertIn('-Dhyped.build.directory=' + str(self.home / 'maven-target'), run.call_args.args[0])
        self.assertEqual(environment['SERVER_ADDRESS'], '127.0.0.1')
        self.assertEqual(environment['SPRING_PROFILES_ACTIVE'], 'local')
        self.assertEqual(environment['DATABASE_URL'], 'jdbc:postgresql://127.0.0.1:5432/hyped')
        self.assertNotIn('JAVA_TOOL_OPTIONS', environment)
        self.assertNotIn('PUSH_SCHEDULER_EMAIL', environment)
        self.assertEqual(environment['DEBUG'], 'false')
        self.assertEqual(environment['TRACE'], 'false')
        self.assertEqual(environment['LOGGING_LEVEL_ROOT'], 'INFO')
        self.assertEqual(environment['LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB'], 'INFO')
        self.assertEqual(environment['LOGGING_LEVEL_ORG_HIBERNATE_SQL'], 'OFF')
        self.assertEqual(environment['LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND'], 'OFF')
        self.assertNotIn('LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB_SERVLET', environment)

    def test_local_consent_requires_deliberate_confirmation(self):
        before = (self.home / 'environment.json').read_bytes()
        with patch('builtins.input', side_effect=['Local operator', 'operator@example.com', 'no']):
            with contextlib.redirect_stdout(io.StringIO()):
                local.consent(self.settings())
        self.assertEqual((self.home / 'environment.json').read_bytes(), before)

    def test_failed_start_is_reported_without_dumping_private_logs(self):
        (self.home / 'firebase-admin.json').write_text(json.dumps({
            'type': 'service_account', 'project_id': 'local-test-project'}))
        errors = io.StringIO()
        with patch.object(local.subprocess, 'run') as run:
            run.return_value.returncode = 1
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(errors):
                self.assertEqual(local.start(self.settings()), 1)
        self.assertIn('Backend exited with an error', errors.getvalue())
        self.assertNotIn('service_account', errors.getvalue())


if __name__ == '__main__':
    unittest.main()
