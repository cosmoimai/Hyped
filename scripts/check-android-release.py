#!/usr/bin/env python3
"""Real Gradle configuration checks using only a disposable local signing fixture.
No release artifact is built and no production credentials are needed or printed.
"""
import base64
import os
import json
from pathlib import Path
import secrets
import subprocess
import tempfile

android = Path(__file__).resolve().parents[1] / 'frontend/android'
base = os.environ.copy()
for key in ['INVITE_HOST', 'HYPED_KEYSTORE_PATH', 'HYPED_KEYSTORE_PASSWORD',
            'HYPED_KEY_ALIAS', 'HYPED_KEY_PASSWORD', 'HYPED_DISABLE_FIREBASE', 'HYPED_FIREBASE_PROJECT_ID']:
    base.pop(key, None)


def check(url, host, expected, extra=None, task=':app:validateReleaseConfiguration'):
    env = base | (extra or {}) | {'INVITE_HOST': host}
    defines = base64.b64encode(('HYPED_API_BASE_URL=' + url).encode()).decode()
    command = ['./gradlew', task, '-Pdart-defines=' + defines, '--console=plain']
    if task == ':app:bundleRelease':
        command.append('--dry-run')
    result = subprocess.run(command, cwd=android, env=env, capture_output=True, text=True)
    if expected is None:
        assert result.returncode == 0, 'Valid release configuration was rejected'
    else:
        assert result.returncode != 0 and expected in result.stdout + result.stderr, \
            'Release configuration rejection did not match the expected safe reason'


for url in ['http://api.hyped.app/api/v1', 'https://api.example.com/api/v1',
            'https://127.0.0.1/api/v1', 'https://api.hyped.app/api/v1?secret=test',
            'https://user:pass@api.hyped.app/api/v1']:
    check(url, 'links.hyped.app', 'HTTPS production HYPED_API_BASE_URL')
for host in ['', 'links.hyped.example', '127.0.0.1']:
    check('https://api.hyped.app/api/v1', host, 'owned production INVITE_HOST')
check('https://api.hyped.app/api/v1', 'links.hyped.app', 'controlled release signing')
check('http://api.hyped.app/api/v1', 'links.hyped.app', 'HTTPS production HYPED_API_BASE_URL', task=':app:bundleRelease')
with tempfile.TemporaryDirectory(prefix='hyped-signing-check-') as directory:
    store = str(Path(directory) / 'fixture.p12')
    password = secrets.token_hex(24)
    env = {'HYPED_KEYSTORE_PATH': store, 'HYPED_KEYSTORE_PASSWORD': password,
           'HYPED_KEY_ALIAS': 'fixture', 'HYPED_KEY_PASSWORD': password}
    # Pass ephemeral passwords through the child environment, never command arguments.
    subprocess.run(['keytool', '-genkeypair', '-noprompt', '-keystore', store,
                    '-storetype', 'PKCS12', '-storepass:env', 'HYPED_KEYSTORE_PASSWORD',
                    '-keypass:env', 'HYPED_KEY_PASSWORD', '-alias', 'fixture',
                    '-keyalg', 'RSA', '-keysize', '2048', '-validity', '1',
                    '-dname', 'CN=Android Debug,O=Android,C=US'],
                   env=base | env, check=True, capture_output=True)
    check('https://api.hyped.app/api/v1', 'links.hyped.app', 'valid non-debug private key', env)
    Path(store).unlink()
    subprocess.run(['keytool', '-genkeypair', '-noprompt', '-keystore', store,
                    '-storetype', 'PKCS12', '-storepass:env', 'HYPED_KEYSTORE_PASSWORD',
                    '-keypass:env', 'HYPED_KEY_PASSWORD', '-alias', 'fixture',
                    '-keyalg', 'RSA', '-keysize', '2048', '-validity', '1',
                    '-dname', 'CN=Hyped Disposable Configuration Test'],
                   env=base | env, check=True, capture_output=True)
    check('https://api.hyped.app/api/v1', 'links.hyped.app',
          'local Firebase Android client configuration', env | {'HYPED_DISABLE_FIREBASE': 'true'})
    if (android / 'app/google-services.json').is_file():
        check('https://api.hyped.app/api/v1', 'links.hyped.app', 'approved HYPED_FIREBASE_PROJECT_ID', env)
        check('https://api.hyped.app/api/v1', 'links.hyped.app', 'match approved project and Android package',
              env | {'HYPED_FIREBASE_PROJECT_ID': 'not-the-configured-project'})
        config = json.loads((android / 'app/google-services.json').read_text())
        env['HYPED_FIREBASE_PROJECT_ID'] = config['project_info']['project_id']
        check('https://api.hyped.app/api/v1', 'links.hyped.app', None, env)
        check('https://api.hyped.app/api/v1', 'links.hyped.app', None, env, task=':app:bundleRelease')
        print('14 negative + 2 valid Gradle release/AAB configuration checks passed (no release artifact built)')
    else:
        print('12 negative Gradle release/AAB configuration checks passed; valid checks need local Firebase client config')
