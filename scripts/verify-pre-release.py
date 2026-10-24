#!/usr/bin/env python3
"""Local-only Android release preparation. Never publishes, uploads, or contacts testers."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SIGNING_KEYS = ('HYPED_KEYSTORE_PATH', 'HYPED_KEYSTORE_PASSWORD', 'HYPED_KEY_ALIAS', 'HYPED_KEY_PASSWORD')


def app_version(text):
    match = re.search(r'^version:\s*(\d+\.\d+\.\d+)\+(\d+)\s*$', text, re.M)
    if not match or not 0 < int(match[2]) <= 2100000000:
        raise ValueError('pubspec requires version MAJOR.MINOR.PATCH+positive Play-compatible build number')
    return match[1], int(match[2])


def junit_totals(paths):
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    files = list(paths)
    if not files:
        raise ValueError('JUnit results are missing')
    for path in files:
        report = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(report.get(key, '0'))
    if totals['tests'] == 0 or any(totals[key] for key in ('failures', 'errors', 'skipped')):
        raise ValueError('Tests failed, were skipped, or did not execute')
    return totals


def flutter_totals(path):
    passed, failed, skipped, done = set(), set(), set(), None
    for line in path.read_text().splitlines():
        try:
            event = json.loads(line)
        except ValueError:
            continue
        if not isinstance(event, dict):
            continue  # A test can print JSON arrays/scalars alongside reporter events.
        if event.get('type') == 'testDone' and not event.get('hidden', False):
            if event.get('skipped'):
                skipped.add(event['testID'])
            elif event.get('result') == 'success':
                passed.add(event['testID'])
            else:
                failed.add(event['testID'])
        elif event.get('type') == 'done':
            done = event.get('success')
    if done is not True or not passed or failed or skipped:
        raise ValueError('Flutter tests failed, were skipped, or machine results are incomplete')
    return dict(passed=len(passed), failed=len(failed), skipped=len(skipped))


def signing_is_excluded(path):
    path = Path(path).expanduser().resolve()
    try:
        relative = path.relative_to(ROOT)
    except ValueError:
        return path.is_file()
    tracked = subprocess.run(['git', 'ls-files', '--error-unmatch', str(relative)],
                             cwd=ROOT, capture_output=True).returncode == 0
    ignored = subprocess.run(['git', 'check-ignore', '-q', str(relative)], cwd=ROOT).returncode == 0
    return path.is_file() and ignored and not tracked


def signed_jar(output):
    output = output.lower()
    return ('jar verified' in output and 'jar is unsigned' not in output and
            'unsigned entries' not in output)


def isolated_environment():
    # Test/dev commands must not inherit a deployed database or provider credentials.
    prefixes = ('HYPED_', 'PLAY_', 'INVITE_', 'INVITATION_', 'DATABASE_', 'TOKEN_', 'FIREBASE_',
                'PERSONAL_DATA_', 'IDENTITY_', 'ANDROID_PUSH_', 'PUSH_', 'LEGAL_', 'TERMS_',
                'PRIVACY_', 'CONTENT_RULES_', 'SPRING_', 'ORG_GRADLE_PROJECT_')
    excluded = {'GOOGLE_APPLICATION_CREDENTIALS', 'GOOGLE_CLOUD_PROJECT', 'JAVA_TOOL_OPTIONS',
                '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS', 'ACCOUNT_DELETION_ENABLED'}
    return {k: v for k, v in os.environ.items() if not k.startswith(prefixes) and k not in excluded}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--require-aab', action='store_true', help='Fail if a real configured release AAB cannot be built')
    parser.add_argument('--last-play-version-code', type=int, help='Highest versionCode already used across all Play tracks')
    args = parser.parse_args()
    name, number = app_version((ROOT / 'frontend/pubspec.yaml').read_text())
    if args.last_play_version_code is not None and number <= args.last_play_version_code:
        parser.error('Candidate build number must exceed the highest existing Play versionCode')
    output = ROOT / 'build/verification' / time.strftime('android-%Y%m%dT%H%M%SZ', time.gmtime())
    output.mkdir(parents=True, exist_ok=False)
    output.chmod(0o700)
    report = {'source_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'source_dirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT)),
              'version_name': name, 'version_code': number, 'steps': [], 'tests': {}, 'artifacts': {},
              'ready_to_start_closed_testing': False,
              'ready_to_request_production_access': False,
              'closed_test_plan': {'tester_slots': 12, 'duration_days': 14, 'status': 'NOT_STARTED'},
              'production_access_gates': ['12 testers continuously opted in for the full 14 days',
                                          'completed physical coverage, fixed-defect regression and human exit review'],
              'manual_gates': ['physical-device smoke matrix', 'live Firebase sign-in/FCM and scheduler',
                               'owned invite domain and verified Play signing association',
                               'published approved legal documents', 'readable privacy-safe Crashlytics test crash',
                               'eligible tester roster and separately authorized closed-track handoff (not started)']}
    failures = []
    test_env = isolated_environment()

    def save():
        (output / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')

    def run(label, command, cwd=ROOT, env=None):
        print(label, flush=True)  # Never print commands, environment, signing paths, or credentials.
        log = output / (label + '.log')
        with log.open('w') as stream:
            result = subprocess.run(command, cwd=cwd, env=test_env if env is None else env,
                                    stdout=stream, stderr=subprocess.STDOUT)
        report['steps'].append({'name': label, 'passed': result.returncode == 0})
        save()
        if result.returncode:
            raise RuntimeError(label + ' failed; review the protected local log')
        return log

    def artifact(label, path):
        archived = output / path.name
        shutil.copy2(path, archived)
        report['artifacts'][label] = {'path': str(archived.relative_to(ROOT)),
                                    'sha256': hashlib.sha256(archived.read_bytes()).hexdigest()}

    try:
        # A local process lock prevents overlapping clean/build runs corrupting results.
        import fcntl
        lock = (ROOT / 'build/verification/pre-release.lock').open('w')
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        run('script-tests', [sys.executable, '-m', 'unittest', 'discover', '-s', 'scripts/tests'])
        run('flutter-toolchain', ['flutter', '--version', '--machine'])
        run('java-toolchain', ['java', '-version'])
        run('docker-required', ['docker', 'info', '--format', '{{.ServerVersion}}'])
        run('flutter-locked-dependencies', ['flutter', 'pub', 'get', '--enforce-lockfile'], ROOT / 'frontend')
        # Read-only signed/source artifacts are not inputs to Maven tests. Fresh outputs avoid stale XML/classes.
        for path in (ROOT / 'backend/target', ROOT / 'frontend/build/app/test-results/testDebugUnitTest'):
            if path.exists():
                shutil.rmtree(path)
        run('backend-clean-verify', ['./mvnw', '-B', '--no-transfer-progress', 'clean', 'verify'], ROOT / 'backend')
        report['tests']['backend'] = junit_totals((ROOT / 'backend/target/surefire-reports').glob('TEST-*.xml'))
        run('flutter-format', ['dart', 'format', '--output=none', '--set-exit-if-changed', '.'], ROOT / 'frontend')
        run('flutter-analyze', ['flutter', 'analyze'], ROOT / 'frontend')
        log = run('flutter-tests', ['flutter', 'test', '--machine'], ROOT / 'frontend')
        report['tests']['flutter'] = flutter_totals(log)
        run('android-unit-lint', ['./gradlew', ':app:testDebugUnitTest', ':app:lintDebug', '--console=plain'],
            ROOT / 'frontend/android')
        report['tests']['android'] = junit_totals(
            (ROOT / 'frontend/build/app/test-results/testDebugUnitTest').glob('TEST-*.xml'))
        run('debug-apk', ['flutter', 'build', 'apk', '--debug', '--build-name=' + name,
                          '--build-number=' + str(number)], ROOT / 'frontend')
        run('debug-manifest', [sys.executable, 'scripts/check-android-manifest.py'])
        artifact('debug_apk', ROOT / 'frontend/build/app/outputs/flutter-apk/app-debug.apk')
        run('release-configuration', [sys.executable, 'scripts/check-android-release.py'])
        run('security-scans', ['bash', 'scripts/security-scan.sh'])

        configured = all(os.environ.get(key) for key in SIGNING_KEYS)
        if configured and not signing_is_excluded(os.environ['HYPED_KEYSTORE_PATH']):
            raise RuntimeError('Release signing file must exist outside Git or be untracked and explicitly ignored')
        release_ready = configured and bool(os.environ.get('INVITE_HOST')) and bool(os.environ.get('HYPED_API_BASE_URL'))
        if not release_ready:
            report['aab'] = 'BLOCKED: real signing, owned INVITE_HOST and HTTPS HYPED_API_BASE_URL required'
            if args.require_aab:
                raise RuntimeError('Required AAB configuration is unavailable')
        else:
            release_env = os.environ.copy()  # Only this local build needs real signing configuration.
            run('release-aab', ['flutter', 'build', 'appbundle', '--release', '--build-name=' + name,
                               '--build-number=' + str(number), '--dart-define=HYPED_API_BASE_URL=' +
                               os.environ['HYPED_API_BASE_URL'], '--split-debug-info=' + str(output / 'dart-symbols')],
                ROOT / 'frontend', release_env)
            bundle = ROOT / 'frontend/build/app/outputs/bundle/release/app-release.aab'
            jar = run('aab-signature', ['jarsigner', '-J-Duser.language=en', '-verify', str(bundle)])
            if not signed_jar(jar.read_text()):
                raise RuntimeError('AAB is unsigned or contains unsigned entries')
            bundletool = ['bundletool'] if shutil.which('bundletool') else (
                ['java', '-jar', os.environ['BUNDLETOOL_JAR']] if os.environ.get('BUNDLETOOL_JAR') else None)
            if bundletool is None:
                raise RuntimeError('Signed AAB needs official bundletool or BUNDLETOOL_JAR for validation')
            run('aab-validate', bundletool + ['validate', '--bundle=' + str(bundle)])
            manifest = run('aab-manifest', bundletool + ['dump', 'manifest', '--bundle=' + str(bundle), '--module=base'])
            manifest_version(manifest.read_text(), name, number, os.environ['INVITE_HOST'])
            # Compare actual bundle signer to the configured upload key without disclosing either value.
            signer = run('aab-certificate', ['keytool', '-J-Duser.language=en', '-printcert', '-jarfile', str(bundle)])
            expected = run('upload-certificate', ['keytool', '-J-Duser.language=en', '-list', '-v', '-keystore',
                           os.environ['HYPED_KEYSTORE_PATH'], '-storepass:env', 'HYPED_KEYSTORE_PASSWORD',
                           '-alias', os.environ['HYPED_KEY_ALIAS']], env=release_env)
            fingerprint = lambda text: re.search(r'SHA256:\s*([A-Fa-f0-9:]+)', text)
            actual, desired = fingerprint(signer.read_text()), fingerprint(expected.read_text())
            if not actual or not desired or actual[1] != desired[1]:
                raise RuntimeError('AAB signer does not match configured upload certificate')
            artifact('release_aab', bundle)
            mapping = ROOT / 'frontend/build/app/outputs/mapping/release/mapping.txt'
            if mapping.is_file():
                shutil.copy2(mapping, output / 'mapping.txt')
            report['aab'] = 'BUILT_AND_VALIDATED_LOCALLY; not uploaded'
        if args.last_play_version_code is None:
            report['manual_gates'].append('highest existing Play versionCode not supplied/verified')
        run('git-diff-check', ['git', 'diff', '--check'])
        run('git-status', ['git', 'status', '--short'])
    except (Exception, KeyboardInterrupt) as error:
        # Do not stringify arbitrary tool/provider exceptions; their text can contain secrets.
        failures.append('Verification interrupted or failed; inspect step logs locally')
        report['failure_type'] = type(error).__name__
        report['failure_stage'] = report['steps'][-1]['name'] if report['steps'] else 'preflight'
    report['automated_checks_passed'] = not failures
    report['failures'] = failures
    save()
    print('AUTOMATED CHECKS ' + ('PASSED' if not failures else 'FAILED'), flush=True)
    print('Closed-testing readiness remains blocked by the recorded manual gates.', flush=True)
    print(str(output.relative_to(ROOT) / 'summary.json'), flush=True)
    return 1 if failures else 0


def manifest_version(text, name, number, host):
    manifest = ET.fromstring(text)
    a = '{http://schemas.android.com/apk/res/android}'
    if (manifest.get('package') != 'com.hyped.hyped' or manifest.get(a + 'versionName') != name or
            manifest.get(a + 'versionCode') != str(number)):
        raise ValueError('AAB version/package does not match the candidate')
    app = manifest.find('application')
    if app is None or app.get(a + 'debuggable', 'false') != 'false' or app.get(a + 'usesCleartextTraffic') != 'false':
        raise ValueError('AAB must be non-debuggable and deny cleartext')
    links = [d for activity in app.findall('activity') for f in activity.findall('intent-filter')
             if f.get(a + 'autoVerify') == 'true' for d in f.findall('data')]
    if not links or any(d.get(a+'host') != host or d.get(a+'scheme') != 'https' for d in links):
        raise ValueError('AAB invite host does not match configured HTTPS App Links')


if __name__ == '__main__':
    sys.exit(main())
