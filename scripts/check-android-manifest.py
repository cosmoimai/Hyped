#!/usr/bin/env python3
"""Audit actual merged debug/release manifests after Android build, without printing URLs/tokens."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
variant = sys.argv[1] if len(sys.argv) > 1 else 'debug'
assert variant in ('debug', 'release')
path = root / f'frontend/build/app/intermediates/merged_manifests/{variant}/process{variant.title()}Manifest/AndroidManifest.xml'
r = ET.parse(path).getroot()
a = '{http://schemas.android.com/apk/res/android}'
app = r.find('application')
assert app.get(a + 'allowBackup') == 'false'
assert app.get(a + 'usesCleartextTraffic') == ('true' if variant == 'debug' else 'false')
assert app.get(a + 'networkSecurityConfig') == '@xml/network_security_config'
permissions = {p.get(a+'name') for p in r.findall('uses-permission')}
assert not any('AD_ID' in p or 'AD_SERVICES' in p or 'AD_SERVICE' in p or
               'ADServices' in p or 'ADSERVICES' in p for p in permissions)
expected_exports = {
    'com.hyped.hyped.MainActivity': None,
    'com.google.firebase.iid.FirebaseInstanceIdReceiver': 'com.google.android.c2dm.permission.SEND',
    'com.google.firebase.auth.internal.GenericIdpActivity': None,
    'com.google.firebase.auth.internal.RecaptchaActivity': None,
    'com.google.android.gms.auth.api.signin.RevocationBoundService':
        'com.google.android.gms.auth.api.signin.permission.REVOCATION_NOTIFICATION',
    'androidx.profileinstaller.ProfileInstallReceiver': 'android.permission.DUMP',
}
for component in app:
    if component.get(a + 'exported') == 'true':
        name = component.get(a + 'name')
        assert name in expected_exports, 'Unexpected exported component'
        assert component.get(a + 'permission') == expected_exports[name], 'Export permission changed'
main = next(c for c in app.findall('activity') if c.get(a + 'name') == 'com.hyped.hyped.MainActivity')
links = [f for f in main.findall('intent-filter') if f.get(a + 'autoVerify') == 'true']
assert len(links) == 1
assert len(links[0].findall('data')) == 1
assert links[0].find('data').get(a + 'scheme') == 'https'
assert links[0].find('data').get(a + 'pathPrefix') == '/invite/'
for name in ('com.hyped.hyped.HypedMessagingService', 'com.hyped.hyped.HypedSmallWidgetProvider',
             'com.hyped.hyped.HypedMediumWidgetProvider'):
    assert next(c for c in app if c.get(a+'name') == name).get(a+'exported') == 'false'
print(f'Merged {variant} manifest checks passed')
