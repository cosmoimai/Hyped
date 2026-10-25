#!/usr/bin/env python3
"""Prepare/start a private local backend. Never provisions cloud services or publishes."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
HOME_DIR = Path.home() / '.config/hyped/local-backend'


def save(path, data):
    # A private parent and atomic replacement keep credentials out of partially written files.
    temporary = path.with_suffix(path.suffix + '.tmp')
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, 'w') as stream:
            json.dump(data, stream, indent=2)
            stream.write('\n')
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def random_file(path):
    if not path.exists():
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, 'wb') as stream:
            stream.write(secrets.token_bytes(32))


def prepare():
    if HOME_DIR.is_symlink():
        raise ValueError('Private configuration directory must not be a symbolic link')
    HOME_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    HOME_DIR.chmod(0o700)
    registry = HOME_DIR / 'key-registry'
    if registry.is_symlink():
        raise ValueError('Local registry must not be a symbolic link')
    registry.mkdir(exist_ok=True, mode=0o700)
    registry.chmod(0o700)
    wrapping = registry / 'wrapping-key.bin'
    if not wrapping.exists() and any(registry.glob('*.key')):
        raise ValueError('Existing registry is missing its wrapping key; restore the matching development key')
    random_file(wrapping)
    random_file(HOME_DIR / 'identity-lookup-hmac.bin')
    private = HOME_DIR / 'access-token-private.pem'
    public = HOME_DIR / 'access-token-public.pem'
    if not private.exists():
        if public.exists():
            raise ValueError('Restore the matching private signing key before continuing')
        descriptor = os.open(private, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, 'wb') as stream:
            subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:3072'],
                           stdout=stream, stderr=subprocess.DEVNULL, check=True)
    if not public.exists():
        descriptor = os.open(public, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, 'wb') as stream:
            subprocess.run(['openssl', 'pkey', '-in', str(private), '-pubout'],
                           stdout=stream, stderr=subprocess.DEVNULL, check=True)
    config = HOME_DIR / 'environment.json'
    if config.exists():
        settings = json.loads(config.read_text())
    else:
        firebase = ROOT / 'frontend/android/app/google-services.json'
        if not firebase.is_file():
            raise ValueError('Download your Firebase Android google-services.json first')
        project = json.loads(firebase.read_text())['project_info']['project_id']
        settings = {
            'DATABASE_URL': 'jdbc:postgresql://127.0.0.1:5432/hyped',
            'DATABASE_USERNAME': 'hyped', 'DATABASE_PASSWORD': 'hyped',
            'FIREBASE_IDENTITY_ENABLED': 'true', 'FIREBASE_PROJECT_ID': project,
            'GOOGLE_APPLICATION_CREDENTIALS': str(HOME_DIR / 'firebase-admin.json'),
            'TOKEN_SIGNING_ENABLED': 'true', 'TOKEN_ISSUER': 'hyped-local-development',
            'TOKEN_AUDIENCE': 'hyped-android-local', 'TOKEN_KEY_ID': 'local-dev-1',
            'TOKEN_RSA_PRIVATE_KEY': private.as_uri(), 'TOKEN_RSA_PUBLIC_KEY': public.as_uri(),
            'IDENTITY_CRYPTO_ENABLED': 'true', 'IDENTITY_CRYPTO_ENVIRONMENT': 'local-development',
            'IDENTITY_LOOKUP_HMAC_KEY': (HOME_DIR / 'identity-lookup-hmac.bin').as_uri(),
            'INVITATION_ENABLED': 'true', 'INVITATION_ENVIRONMENT': 'local-development',
            'INVITATION_PUBLIC_BASE_URL': 'https://localhost:8443',
            'INVITATION_ENCRYPTION_KEY': base64.b64encode(secrets.token_bytes(32)).decode(),
            'INVITATION_LOOKUP_HMAC_KEY': base64.b64encode(secrets.token_bytes(32)).decode(),
            'ANDROID_PUSH_ENABLED': 'false', 'ACCOUNT_DELETION_ENABLED': 'false',
            'LEGAL_PUBLICATION_READY': 'false', 'LEGAL_OPERATOR': '', 'LEGAL_CONTACT_EMAIL': ''}
    settings.update({'SERVER_ADDRESS': '127.0.0.1', 'SERVER_PORT': '8080',
                     'SPRING_PROFILES_ACTIVE': 'local', 'PLAY_INTEGRITY_MODE': 'observe',
                     'PERSONAL_DATA_PROTECTION_ENABLED': 'true',
                     'PERSONAL_DATA_ENVIRONMENT': 'local-development',
                     'HYPED_PERSONAL_DATA_PROVIDER': 'local',
                     'HYPED_PERSONAL_DATA_LOCAL_DIRECTORY': str(registry)})
    save(config, settings)
    print('Local keys/configuration ready outside Git. Existing keys preserved.')
    print('Next: place Firebase Admin credentials in ~/.config/hyped/local-backend/firebase-admin.json')


def consent(settings):
    print('This enables bundled draft consent documents for private loopback testing only.')
    operator = input('Responsible operator/name for this local test: ').strip()
    contact = input('Your real contact email for this local test: ').strip()
    if not operator or not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', contact):
        raise ValueError('A responsible operator and valid contact email are required')
    if input('Enable these drafts only in this private test backend? Type yes: ').strip() != 'yes':
        print('Configuration unchanged.')
        return
    settings.update({'LEGAL_OPERATOR': operator, 'LEGAL_CONTACT_EMAIL': contact, 'LEGAL_PUBLICATION_READY': 'true'})
    save(HOME_DIR / 'environment.json', settings)
    print('Private local consent testing configured; no public legal publication or approval claimed.')


def start(settings):
    if settings.get('HYPED_PERSONAL_DATA_PROVIDER') != 'local':
        raise ValueError('Run prepare first to select explicit local storage')
    credentials = Path(settings['GOOGLE_APPLICATION_CREDENTIALS']).expanduser()
    if not credentials.is_file():
        raise ValueError('Firebase Admin JSON is missing; follow docs/local-android-testing.md')
    if credentials.is_symlink():
        raise ValueError('Firebase Admin credentials must be a private regular file')
    credentials.chmod(0o600)
    identity = json.loads(credentials.read_text())
    if identity.get('type') != 'service_account' or identity.get('project_id') != settings['FIREBASE_PROJECT_ID']:
        raise ValueError('Firebase Admin credentials must match the Android Firebase project')
    prefixes = ('HYPED_', 'PLAY_', 'INVITE_', 'INVITATION_', 'DATABASE_', 'TOKEN_', 'FIREBASE_',
                'PERSONAL_DATA_', 'IDENTITY_', 'ANDROID_PUSH_', 'PUSH_', 'LEGAL_', 'TERMS_',
                'PRIVACY_', 'CONTENT_RULES_', 'SPRING_', 'ORG_GRADLE_PROJECT_', 'LOGGING_', 'SERVER_')
    excluded = {'GOOGLE_APPLICATION_CREDENTIALS', 'GOOGLE_CLOUD_PROJECT', 'JAVA_TOOL_OPTIONS',
                '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS', 'ACCOUNT_DELETION_ENABLED'}
    env = {k: v for k, v in os.environ.items() if not k.startswith(prefixes) and k not in excluded}
    env.update(settings)
    # Bind the Mac's debug backend to loopback even if private settings were edited.
    env['SERVER_ADDRESS'] = '127.0.0.1'
    env['SERVER_PORT'] = '8080'
    env['SPRING_PROFILES_ACTIVE'] = 'local'
    print('Starting backend on 127.0.0.1:8080. Logs: ~/.config/hyped/local-backend/backend.log', flush=True)
    print('Keep this terminal open. Stop with Ctrl+C. No public network listener is created.', flush=True)
    with (HOME_DIR / 'backend.log').open('a') as log:
        os.fchmod(log.fileno(), 0o600)
        result = subprocess.run(['./mvnw', '-B', '--no-transfer-progress', 'clean', 'spring-boot:run'],
                                cwd=ROOT / 'backend', env=env, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        print('Backend exited with an error; inspect ~/.config/hyped/local-backend/backend.log privately.',
              file=sys.stderr)
    return result.returncode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'consent', 'start'])
    args = parser.parse_args()
    try:
        if args.action == 'prepare':
            prepare()
        else:
            config = HOME_DIR / 'environment.json'
            if not config.is_file():
                raise ValueError('Run prepare first')
            settings = json.loads(config.read_text())
            if args.action == 'consent':
                consent(settings)
            else:
                return start(settings)
        return 0
    except ValueError as error:
        # All explicit errors above are fixed configuration instructions, not provider exceptions.
        # JSON decoding errors may contain input snippets, so print only a static diagnostic for those.
        print('Configuration JSON is invalid' if isinstance(error, json.JSONDecodeError) else str(error), file=sys.stderr)
        return 2
    except (OSError, subprocess.SubprocessError, KeyError):
        print('Local setup failed; check private files/tooling. No credential details displayed.', file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        return 130


if __name__ == '__main__':
    sys.exit(main())
