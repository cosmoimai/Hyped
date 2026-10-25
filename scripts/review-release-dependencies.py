#!/usr/bin/env python3
"""Local dependency/license inventory. No source or credentials are uploaded.
Resolve locked Flutter dependencies first; run after Maven/Gradle verification,
not concurrently with clean/build jobs. POM metadata is not legal approval.
"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.request
import urllib.error
import subprocess
import tempfile
from urllib.parse import unquote, urlparse
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/verification' / ('licenses-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
OUT.mkdir(parents=True, mode=0o700)
ENV = os.environ.copy()
for key in list(ENV):
    if key.startswith(('HYPED_', 'SPRING_', 'GOOGLE_', 'FIREBASE_', 'INVITE_', 'PLAY_INTEGRITY_')):
        ENV.pop(key)


def run(command, cwd, filename):
    with (OUT / filename).open('w') as stream:
        subprocess.run(command, cwd=cwd, env=ENV, stdout=stream, stderr=subprocess.STDOUT, check=True)


def pom_path(group, artifact, version):
    maven = Path.home() / '.m2/repository' / group.replace('.', '/') / artifact / version / f'{artifact}-{version}.pom'
    if maven.is_file():
        return maven
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1' / group / artifact / version
    cached = next(cache.glob('*/*.pom'), None)
    if cached:
        return cached
    # Fetch public dependency metadata only; never send project source/configuration.
    relative = group.replace('.', '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version + '.pom'
    if group.startswith('io.flutter'):
        repository = 'https://storage.googleapis.com/download.flutter.io/'
    elif group.startswith(('androidx.', 'com.google.android.gms', 'com.google.firebase',
                           'com.google.android.libraries', 'com.google.android.recaptcha',
                           'com.google.android.play')):
        repository = 'https://dl.google.com/dl/android/maven2/'
    else:
        repository = 'https://repo.maven.apache.org/maven2/'
    destination = OUT / 'poms' / relative
    try:
        with urllib.request.urlopen(repository + relative, timeout=20) as response:
            data = response.read(2_000_000)
        ET.fromstring(data)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        return destination
    except (urllib.error.URLError, TimeoutError, ET.ParseError):
        return None


def licenses(group, artifact, version, seen=None):
    seen = set() if seen is None else seen
    key = (group, artifact, version)
    if key in seen or len(seen) > 8:
        return []
    seen.add(key)
    path = pom_path(*key)
    if path is None:
        return []
    try:
        tree = ET.parse(path).getroot()
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        found = [n.text.strip() for n in tree.findall('m:licenses/m:license/m:name', ns) if n.text]
        if found:
            return found
        parent = tree.find('m:parent', ns)
        if parent is not None:
            fields = [parent.findtext('m:' + name, namespaces=ns) for name in ('groupId', 'artifactId', 'version')]
            if all(fields):
                return licenses(*fields, seen)
    except (ET.ParseError, OSError):
        pass
    return []


run(['flutter', 'pub', 'deps', '--json'], ROOT / 'frontend', 'flutter-deps.json')
run(['./mvnw', '-B', '--no-transfer-progress',
     'org.apache.maven.plugins:maven-dependency-plugin:3.7.0:tree', '-Dscope=runtime',
     '-DoutputType=json', '-DoutputFile=' + str(OUT / 'backend-deps.json')], ROOT / 'backend', 'maven.log')
# A read-only report task avoids treating a configuration name in CLI arguments
# as a request to build release code. Real build tasks retain the release guard.
with tempfile.TemporaryDirectory(prefix='hyped-inventory-') as directory:
    init = Path(directory) / 'inventory.gradle'
    init.write_text('''gradle.projectsEvaluated {
    def app = gradle.rootProject.findProject(':app')
    if (app == null) return
    app.tasks.register('hypedDependencyInventory') {
        doLast {
            app.configurations.getByName('releaseRuntimeClasspath').incoming.resolutionResult.allComponents.each {
                def id = it.id
                if (id instanceof org.gradle.api.artifacts.component.ModuleComponentIdentifier) {
                    println('HYPED_COMPONENT ' + id.group + ':' + id.module + ':' + id.version)
                }
            }
        }
    }
}
''')
    run(['./gradlew', ':app:hypedDependencyInventory', '-I', str(init), '--console=plain'],
        ROOT / 'frontend/android', 'android-deps.txt')
rows = []
config_file = ROOT / 'frontend/.dart_tool/package_config.json'
packages = {p['name']: p for p in json.loads(config_file.read_text())['packages']}
raw = (OUT / 'flutter-deps.json').read_text()
graph = json.loads(raw[raw.index('{'):])
for package in graph['packages']:
    if package['name'] == 'hyped':
        continue
    config = packages.get(package['name'])
    texts = []
    if config:
        uri = urlparse(config['rootUri'])
        directory = Path(unquote(uri.path)) if uri.scheme == 'file' else (config_file.parent / unquote(config['rootUri'])).resolve()
        paths = list(directory.glob('LICENSE*'))
        if not paths and package['name'] in ('flutter_test', 'flutter_web_plugins'):
            sdk_license = directory.parents[1] / 'LICENSE'
            if sdk_license.is_file():
                paths.append(sdk_license)
        for path in paths:
            if path.is_file():
                text = path.read_text(errors='replace')
                texts.append({'file': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                              'notice': text})
    rows.append({'ecosystem': 'Flutter (includes development tools)', 'name': package['name'],
                 'version': package['version'], 'kind': package.get('kind'), 'license_files': texts,
                 'metadata_missing': not texts})


def visit(node):
    if node.get('artifactId') != 'hyped':
        group, artifact, version = node['groupId'], node['artifactId'], node['version']
        rows.append({'ecosystem': 'Backend runtime', 'name': group + ':' + artifact,
                     'version': version, 'licenses': licenses(group, artifact, version)})
    for child in node.get('children', []):
        visit(child)


backend = json.loads((OUT / 'backend-deps.json').read_text())
for child in backend.get('children', []):
    visit(child)
seen = set()
for line in (OUT / 'android-deps.txt').read_text().splitlines():
    match = re.search(r'^HYPED_COMPONENT ([\w.-]+):([\w.-]+):([^\s]+)$', line)
    if not match:
        continue
    group, artifact, version = match.groups()
    key = (group, artifact, version)
    if key in seen:
        continue
    seen.add(key)
    rows.append({'ecosystem': 'Android release runtime', 'name': group + ':' + artifact,
                 'version': version, 'licenses': licenses(*key)})
missing = [r for r in rows if r.get('metadata_missing') or ('licenses' in r and not r['licenses'])]
(OUT / 'inventory.json').write_text(json.dumps(rows, indent=2) + '\n')
lines = ['# Resolved dependency and license review', '',
         'Local metadata inventory only; owner/legal approval and notice-distribution review required.',
         'Flutter includes development packages; backend licenses are separate from Android distribution.',
         'POM licenses are inherited from cached/public parents where available; missing metadata is unresolved.',
         'Google/Firebase/Play SDK service terms and runtime-downloaded Manrope font are additional reviews.', '',
         '| Scope | Artifact | Version | Observed license metadata |', '|---|---|---|---|']
for row in rows:
    value = ', '.join(row.get('licenses', [])) or ('LICENSE text archived' if row.get('license_files') else 'REVIEW REQUIRED: missing metadata')
    lines.append('| ' + ' | '.join(str(v).replace('|', '/') for v in (row['ecosystem'], row['name'], row['version'], value)) + ' |')
lines.extend(['', f'Entries: {len(rows)}; missing license metadata: {len(missing)}.',
              'Inspect inventory.json license text and raw graphs. No dependency upgrades were performed.',
              'Check copyleft/source-offer, attribution/NOTICE, font embedding and Google SDK terms explicitly.',
              'Unknown or dual-license metadata is not automatically an approved license.'])
(OUT / 'review.md').write_text('\n'.join(lines) + '\n')
print(str(OUT.relative_to(ROOT)))
print(f'{len(rows)} entries; {len(missing)} unresolved license metadata entries; legal review required')
