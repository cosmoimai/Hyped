#!/usr/bin/env python3
"""Build and validate a signed staging AAB locally; never uploads to Play."""
import hashlib
import base64
import re
import json
import os
from pathlib import Path
import subprocess
import sys
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CONFIG = Path.home() / '.config/hyped/android-signing/internal-testing.json'
OUT = ROOT / 'build/verification/internal-testing'
OUT.mkdir(parents=True, exist_ok=True, mode=0o700)
OUT.chmod(0o700)
if not CONFIG.is_file() or CONFIG.stat().st_mode & 0o077:
    sys.exit('Owner-only local signing configuration is required; see internal-testing-build.md')
values = json.loads(CONFIG.read_text())
keys = ('HYPED_KEYSTORE_PATH', 'HYPED_KEYSTORE_PASSWORD', 'HYPED_KEY_ALIAS', 'HYPED_KEY_PASSWORD')
if any(not isinstance(values.get(k), str) or not values[k] for k in keys):
    sys.exit('Local signing configuration is incomplete')
store = Path(values['HYPED_KEYSTORE_PATH']).resolve()
if store.is_relative_to(ROOT) or not store.is_file() or store.stat().st_mode & 0o077:
    sys.exit('Owner-only upload keystore outside the repository is required')
env = os.environ | {k: values[k] for k in keys} | {
    'HYPED_INTERNAL_TESTING': 'true', 'INVITE_HOST': '',
    'HYPED_FIREBASE_PROJECT_ID': 'hyped-41a30',
    'HYPED_DISABLE_FIREBASE': 'false',
}
# Prevent inherited Gradle properties from silently enabling an invite hostname.
env.pop('ORG_GRADLE_PROJECT_INVITE_HOST', None)
java_home = Path('/opt/homebrew/opt/openjdk@21')
if java_home.exists():
    env['JAVA_HOME'] = str(java_home)
    env['PATH'] = str(java_home / 'bin') + os.pathsep + env['PATH']

def run(label, command, cwd=ROOT):
    result = subprocess.run(command, cwd=cwd, env=env, capture_output=True)
    (OUT / (label + '.log')).write_bytes(result.stdout + result.stderr)
    if result.returncode:
        sys.exit(label + ' failed; inspect the excluded local verification log')
    return result.stdout.decode(errors='replace')

run('bundle-build', ['flutter', 'build', 'appbundle', '--release',
    '--dart-define=HYPED_API_BASE_URL=https://hyped-staging-api-jmi2i42hbq-as.a.run.app/api/v1'], ROOT / 'frontend')
bundle = ROOT / 'frontend/build/app/outputs/bundle/release/app-release.aab'
verification = run('signature', ['jarsigner', '-verify', '-verbose', '-certs', str(bundle)])
if ('jar verified' not in verification.lower() or 'jar is unsigned' in verification.lower()
        or 'unsigned entries' in verification.lower()):
    sys.exit('AAB signature verification failed')
# Compare the actual signer to our dedicated upload key, without exposing passwords.
run('bundle-certificate', ['keytool', '-printcert', '-jarfile', str(bundle), '-rfc'])
certificate = run('upload-certificate', ['keytool', '-exportcert', '-rfc', '-keystore', str(store),
    '-storepass:env', 'HYPED_KEYSTORE_PASSWORD', '-alias', values['HYPED_KEY_ALIAS']])
actual = (OUT / 'bundle-certificate.log').read_text()
def certificate_der(text):
    blocks = re.findall(r'-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----', text, re.S)
    return [base64.b64decode(''.join(block.split()), validate=True) for block in blocks]

if not certificate_der(certificate) or certificate_der(actual) != certificate_der(certificate):
    sys.exit('AAB signer does not match the upload key')
tool = os.environ.get('BUNDLETOOL_JAR')
if not tool or not Path(tool).is_file():
    sys.exit('Signed AAB built; set BUNDLETOOL_JAR to complete official bundle validation')
run('bundle-validate', ['java', '-jar', tool, 'validate', '--bundle=' + str(bundle)])
manifest = run('manifest', ['java', '-jar', tool, 'dump', 'manifest', '--bundle=' + str(bundle), '--module=base'])
xml = ET.fromstring(manifest)
a = '{http://schemas.android.com/apk/res/android}'
app = xml.find('application')
if xml.get('package') != 'com.hyped.hyped' or app is None or app.get(a + 'debuggable', 'false') != 'false' or app.get(a + 'usesCleartextTraffic') != 'false':
    sys.exit('Release manifest identity or security check failed')
archive = OUT / ('hyped-internal-' + xml.get(a + 'versionName') + '-' + xml.get(a + 'versionCode') + '.aab')
shutil.copy2(bundle, archive)
bundle = archive
receipt = {
    'source_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
    'source_dirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT)),
    'purpose': 'INTERNAL TESTING ONLY; staging backend; verified App Links disabled',
    'package': xml.get('package'), 'version_name': xml.get(a + 'versionName'),
    'version_code': xml.get(a + 'versionCode'),
    'aab': str(bundle), 'sha256': hashlib.sha256(bundle.read_bytes()).hexdigest(),
    'signature_verified': True, 'bundletool_validated': True,
    'uploaded': False, 'production_ready': False,
}
(OUT / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
(OUT / 'sha256.txt').write_text(receipt['sha256'] + '  ' + bundle.name + '\n')
print(json.dumps(receipt, indent=2))
