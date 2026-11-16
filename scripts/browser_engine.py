#!/usr/bin/env python3
"""Check or refresh the stable GeckoView pin and provenance; never execute downloaded code."""
import argparse
import hashlib
import io
import os
from pathlib import Path
import re
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime

ROOT = Path(__file__).resolve().parent.parent
MAVEN = 'https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/'
PIN = re.compile(r'org\.mozilla\.geckoview:geckoview:([^\s\'\"]+)')
VERSION = re.compile(r'([1-9]\d{1,3}\.\d+(?:\.\d+)?)\.(\d{14})')
LICENSE = 'chrome/toolkit/content/global/license.html'
BUILD = 'chrome/toolkit/content/global/buildconfig.html'
SOURCE = re.compile(r'href=[\"\'](https://(?:github\.com/mozilla-firefox/firefox/commit/|hg\.mozilla\.org/releases/mozilla-release/rev/)([a-f0-9]{40}))[\"\']')


def version_parts(value):
    match = VERSION.fullmatch(value)
    if not match:
        raise ValueError('Expected an exact stable GeckoView version, without beta/nightly or dynamic selectors: ' + value)
    datetime.strptime(match[2], '%Y%m%d%H%M%S')
    release = tuple(int(p) for p in match[1].split('.'))
    return release + (0,) * (3 - len(release)), int(match[2])


def pinned(root=ROOT):
    values = PIN.findall((root / 'app/build.gradle').read_text())
    if len(values) != 1:
        raise ValueError('Expected exactly one direct GeckoView stable dependency.')
    version_parts(values[0])
    return values[0]


def fetch(url, limit=2 * 1024 * 1024):
    request = urllib.request.Request(url, headers={'User-Agent': 'Scriflow-engine-maintenance'})
    with urllib.request.urlopen(request, timeout=45) as response:
        if not response.url.startswith('https://'):
            raise ValueError('Engine metadata must use HTTPS.')
        data = response.read(limit + 1)
    if len(data) > limit:
        raise ValueError('Upstream metadata exceeds its size limit.')
    return data


def latest(metadata):
    document = ET.fromstring(metadata)
    if document.findtext('groupId') != 'org.mozilla.geckoview' or document.findtext('artifactId') != 'geckoview':
        raise ValueError('Unexpected Maven repository identity.')
    versions = []
    for node in document.findall('versioning/versions/version'):
        try:
            version_parts(node.text or '')
            versions.append(node.text)
        except ValueError:
            continue
    if not versions:
        raise ValueError('No stable GeckoView versions were published in the metadata.')
    return max(versions, key=version_parts)


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def bounded_member(archive, name, limit):
    entry = archive.getinfo(name)
    if entry.file_size > limit:
        raise ValueError('Archive metadata exceeds its size limit: ' + name)
    return archive.read(entry)


def inspect(aar, version):
    version_parts(version)
    with zipfile.ZipFile(aar) as outer:
        omni_bytes = bounded_member(outer, 'assets/omni.ja', 64 * 1024 * 1024)
    with zipfile.ZipFile(io.BytesIO(omni_bytes)) as omni:
        constants = bounded_member(omni, 'modules/AppConstants.sys.mjs', 1024 * 1024).decode('utf-8')
        app = re.findall(r'MOZ_APP_VERSION:\s*"([0-9.]+)"', constants)
        build = re.findall(r'MOZ_BUILDID:\s*"(\d{14})"', constants)
        if len(app) != 1 or len(build) != 1 or app[0] + '.' + build[0] != version:
            raise ValueError('The engine inside the AAR does not match the requested version.')
        html = bounded_member(omni, BUILD, 1024 * 1024).decode('utf-8')
        sources = set(SOURCE.findall(html))
        if len(sources) != 1:
            raise ValueError('Expected one official Mozilla source revision in buildconfig.html.')
        source, revision = sources.pop()
        inventory = bounded_member(omni, LICENSE, 4 * 1024 * 1024)
        if b'Mozilla Public License' not in inventory:
            raise ValueError('The archive has no recognizable license inventory.')
    return source, revision, inventory


def source_record(version, checksum, source, revision):
    archive = (source.replace('/commit/', '/archive/') + '.zip' if '/commit/' in source
               else source.replace('/rev/', '/archive/') + '.zip')
    return f'''Scriflow bundles the unmodified Mozilla GeckoView browser engine.
Artifact: org.mozilla.geckoview:geckoview:{version}
AAR: {MAVEN}{version}/geckoview-{version}.aar
AAR SHA-256: {checksum}
Source revision: {revision}
Complete engine source: {source}
Source archive: {archive}
Java sources: {MAVEN}{version}/geckoview-{version}-sources.jar

Mozilla Public License 2.0: geckoview-MPL-2.0.txt
Upstream engine/component license inventory: geckoview-third-party.html
The engine's source files and included components retain their upstream licenses.
Scriflow's application and capture code remain MIT-licensed.
'''


def check(root=ROOT):
    version = pinned(root)
    text = (root / 'licenses/geckoview-SOURCE.txt').read_text()
    fields = dict(re.findall(r'^([^:\n]+): (.+)$', text, re.M))
    if fields.get('Artifact') != 'org.mozilla.geckoview:geckoview:' + version:
        raise ValueError('GeckoView pin and provenance differ. Run: python3 scripts/browser_engine.py refresh')
    if fields.get('AAR') != MAVEN + version + '/geckoview-' + version + '.aar':
        raise ValueError('Unexpected engine artifact URL.')
    if not re.fullmatch('[a-f0-9]{64}', fields.get('AAR SHA-256', '')):
        raise ValueError('Missing engine SHA-256.')
    if not SOURCE.fullmatch('href="' + fields.get('Complete engine source', '') + '"'):
        raise ValueError('Expected an official Mozilla engine source URL.')
    if fields.get('Source revision') != fields['Complete engine source'].rsplit('/', 1)[-1]:
        raise ValueError('Engine source revision and URL differ.')
    if fields.get('Java sources') != MAVEN + version + '/geckoview-' + version + '-sources.jar':
        raise ValueError('Java source URL does not match the pinned engine.')
    for name in ('geckoview-MPL-2.0.txt', 'geckoview-third-party.html'):
        if not (root / 'licenses' / name).is_file():
            raise ValueError('Missing license: ' + name)
    return version, fields


def resolved(version):
    cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
    values = list((cache / 'caches/modules-2/files-2.1/org.mozilla.geckoview/geckoview' / version).glob('*/*.aar'))
    if len(values) != 1:
        raise ValueError('Build the app first or specify --aar with the exact resolved GeckoView AAR.')
    return values[0]


def verify(aar, root=ROOT):
    version, fields = check(root)
    if digest(aar) != fields['AAR SHA-256']:
        raise ValueError('Resolved GeckoView AAR checksum differs from the recorded checksum.')
    source, revision, inventory = inspect(aar, version)
    if fields['Complete engine source'] != source or fields['Source revision'] != revision:
        raise ValueError('Recorded source revision differs from the engine archive. Run browser_engine.py refresh.')
    if (root / 'licenses/geckoview-SOURCE.txt').read_text() != source_record(version, fields['AAR SHA-256'], source, revision):
        raise ValueError('Engine provenance links differ from the archive-derived record. Run browser_engine.py refresh.')
    if inventory != (root / 'licenses/geckoview-third-party.html').read_bytes():
        raise ValueError('Recorded third-party notices differ from the engine archive.')


def download(version, destination):
    url = MAVEN + version + '/geckoview-' + version + '.aar'
    with urllib.request.urlopen(url, timeout=60) as response, destination.open('wb') as output:
        total = 0
        while True:
            block = response.read(1024 * 1024)
            if not block:
                break
            total += len(block)
            if total > 512 * 1024 * 1024:
                raise ValueError('Engine artifact exceeds the download size limit.')
            output.write(block)


def refresh(version, aar, root=ROOT):
    version_parts(version)
    # Read and validate everything before changing any repository file.
    source, revision, inventory = inspect(aar, version)
    record = source_record(version, digest(aar), source, revision)
    gradle = (root / 'app/build.gradle').read_text()
    pinned(root)
    gradle = PIN.sub('org.mozilla.geckoview:geckoview:' + version, gradle)
    (root / 'app/build.gradle').write_text(gradle)
    (root / 'licenses/geckoview-third-party.html').write_bytes(inventory)
    (root / 'licenses/geckoview-SOURCE.txt').write_text(record)
    verify(aar, root)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('check', 'verify', 'latest', 'refresh'))
    parser.add_argument('--version', help='Exact stable version for refresh; defaults to the current Gradle pin.')
    parser.add_argument('--aar', type=Path, help='Use an already downloaded AAR; verify otherwise uses the Gradle cache.')
    args = parser.parse_args()
    try:
        version = args.version or pinned()
        version_parts(version)
        if args.command == 'latest':
            upstream = latest(fetch(MAVEN + 'maven-metadata.xml'))
            print('Bundled:', pinned()); print('Latest stable:', upstream)
        elif args.command == 'check':
            check(); print('GeckoView pin and provenance agree:', pinned())
        elif args.command == 'verify':
            verify(args.aar or resolved(pinned())); print('Resolved GeckoView checksum, source and notices verified:', pinned())
        else:
            if args.aar:
                refresh(version, args.aar)
            else:
                with tempfile.TemporaryDirectory(prefix='scriflow-engine-') as temporary:
                    aar = Path(temporary) / 'engine.aar'; download(version, aar); refresh(version, aar)
            print('Refreshed engine pin, SHA-256, source links and license inventory:', version)
            print('Review the diff, run CI and browser smoke tests, then build a signed release with scripts/build-release.sh.')
        return 0
    except (ValueError, OSError, zipfile.BadZipFile, KeyError, ET.ParseError) as error:
        print('Engine maintenance failed: ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
