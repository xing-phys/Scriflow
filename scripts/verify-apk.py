#!/usr/bin/env python3
"""Inspect the packaged app before installation or distribution.

Requires Android SDK Build Tools (aapt and apksigner), but no signing key.
Examples:
  python3 scripts/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --signature unsigned
  python3 scripts/verify-apk.py build/scriflow-debug.apk --variant debug
  python3 scripts/verify-apk.py release.apk --certificate-sha256 EXPECTED_HEX_DIGEST
"""

import argparse
from dataclasses import dataclass, field
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = 'org.eink.inkprobe'
LAUNCHER = PACKAGE + '.notebook.NotebookLibraryActivity'
DIAGNOSTICS = {PACKAGE + '.MainActivity', PACKAGE + '.notebook.NotebookSelfTestActivity'}
PERMISSIONS = {'android.permission.ACCESS_NETWORK_STATE'}
COMPONENTS = {'activity', 'activity-alias', 'service', 'receiver', 'provider'}


class VerificationError(Exception):
    pass


@dataclass
class Element:
    tag: str
    attributes: dict = field(default_factory=dict)
    children: list = field(default_factory=list)

    def walk(self):
        yield self
        for child in self.children:
            yield from child.walk()


def require(condition, message):
    if not condition:
        raise VerificationError(message)


def sdk_from_local_properties():
    properties = ROOT / 'local.properties'
    if not properties.is_file():
        return None
    # java.util.Properties uses ISO-8859-1 and backslash escapes. In particular,
    # Android Studio escapes spaces, colons, backslashes, and non-ASCII paths.
    logical_lines, pending = [], ''
    for line in properties.read_text(encoding='iso-8859-1').splitlines():
        line = pending + (line.lstrip(' \t\f') if pending else line)
        trailing_slashes = len(line) - len(line.rstrip('\\'))
        if trailing_slashes % 2:
            pending = line[:-1]
        else:
            logical_lines.append(line)
            pending = ''
    if pending:
        logical_lines.append(pending)
    sdk = None
    for line in logical_lines:
        match = re.match(r'^\s*sdk\.dir(?:\s*[:=]\s*|\s+)(.*)$', line)
        if not match:
            continue
        value, decoded, index = match.group(1), [], 0
        while index < len(value):
            character = value[index]
            index += 1
            if character == '\\' and index < len(value):
                character = value[index]
                index += 1
                if character == 'u':
                    digits = value[index:index + 4]
                    require(re.fullmatch(r'[0-9a-fA-F]{4}', digits), 'Invalid Unicode escape in local.properties sdk.dir.')
                    character = chr(int(digits, 16))
                    index += 4
                else:
                    character = {'t': '\t', 'n': '\n', 'r': '\r', 'f': '\f'}.get(character, character)
            decoded.append(character)
        sdk = ''.join(decoded)
    return (ROOT / sdk) if sdk else None


def find_tool(name, explicit):
    if explicit:
        return explicit
    candidates = []
    configured_sdk = sdk_from_local_properties()
    if configured_sdk:
        candidates.append(configured_sdk / 'build-tools/35.0.0' / name)
    for variable in ('ANDROID_HOME', 'ANDROID_SDK_ROOT'):
        if os.environ.get(variable):
            candidates.append(Path(os.environ[variable]) / 'build-tools/35.0.0' / name)
    candidates += [ROOT / '.tools/android-sdk/build-tools/35.0.0' / name,
                   ROOT / '.tools/sdk-build35/android-15' / name,
                   ROOT / '.tools/build-tools-30.0.3' / name]
    for candidate in candidates:
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return str(candidate)
    executable = shutil.which(name)
    require(executable, f'Cannot find {name}; pass --{name} or install Android SDK Build Tools.')
    return executable


def run(command, success=True):
    try:
        result = subprocess.run(command, text=True, capture_output=True, check=False)
    except OSError as error:
        raise VerificationError(f'Cannot run {command[0]}: {error}') from error
    if success and result.returncode:
        raise VerificationError(f'{Path(command[0]).name} failed:\n{result.stderr.strip() or result.stdout.strip()}')
    return result


def decode_attribute(value):
    # aapt prints strings as a quoted value followed by an optional (Raw: ...) value.
    match = re.match(r'"(?:\\.|[^"\\])*"', value)
    if match:
        try:
            return json.loads(match.group())
        except json.JSONDecodeError as error:
            raise VerificationError('Unsupported quoted manifest attribute: ' + value) from error
    match = re.fullmatch(r'\(type 0x[0-9a-fA-F]+\)(0x[0-9a-fA-F]+)', value)
    if match:
        return int(match.group(1), 16)
    return value


def parse_manifest(tree):
    """Reconstruct aapt xmltree's indentation hierarchy, including typed booleans."""
    roots, stack = [], []
    for line in tree.splitlines():
        match = re.match(r'^(\s*)E: ([^\s(]+)(?:\s.*)?$', line)
        if match:
            indent = len(match.group(1))
            element = Element(match.group(2))
            while stack and stack[-1][0] >= indent:
                stack.pop()
            (stack[-1][1].children if stack else roots).append(element)
            stack.append((indent, element))
            continue
        match = re.match(r'^\s*A: ([^=(]+)(?:\(0x[0-9a-fA-F]+\))?=(.*)$', line)
        if match:
            require(stack, 'Manifest attribute appeared without an element.')
            key = match.group(1)
            require(key not in stack[-1][1].attributes, f'Duplicate manifest attribute: {key}')
            stack[-1][1].attributes[key] = decode_attribute(match.group(2))
    require(len(roots) == 1 and roots[0].tag == 'manifest', 'Cannot read a complete APK manifest.')
    return roots[0]


def read_version(path):
    require(path.is_file(), f'Missing version properties: {path}')
    properties = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if line and not line.startswith(('#', '!')) and '=' in line:
            key, value = line.split('=', 1)
            properties[key.strip()] = value.strip()
    require(properties.get('versionName') and properties.get('versionCode', '').isdigit(),
            f'Invalid versionName/versionCode in {path}')
    return properties['versionName'], int(properties['versionCode'])


def component_name(element):
    name = element.attributes.get('android:name', '')
    if name.startswith('.'):
        return PACKAGE + name
    return PACKAGE + '.' + name if name and '.' not in name else name


def launcher_filter(component):
    for intent in component.children:
        if intent.tag != 'intent-filter':
            continue
        actions = {child.attributes.get('android:name') for child in intent.children if child.tag == 'action'}
        categories = {child.attributes.get('android:name') for child in intent.children if child.tag == 'category'}
        if 'android.intent.action.MAIN' in actions and 'android.intent.category.LAUNCHER' in categories:
            return True
    return False


def verify_manifest(manifest, badging, variant, version):
    name, code = version
    require(manifest.attributes.get('package') == PACKAGE, f'Expected application ID {PACKAGE}.')
    require(manifest.attributes.get('android:versionName') == name, f'Expected versionName {name}.')
    require(manifest.attributes.get('android:versionCode') == code, f'Expected versionCode {code}.')
    applications = [child for child in manifest.children if child.tag == 'application']
    require(len(applications) == 1, 'Expected one application declaration.')
    application = applications[0]
    require(bool(application.attributes.get('android:debuggable', 0)) == (variant == 'debug'),
            f'Expected debuggable={str(variant == "debug").lower()} for {variant}.')
    require(application.attributes.get('android:allowBackup') == 0, 'Automatic Android backup must remain disabled.')
    require('android:icon' in application.attributes, 'Missing launcher icon.')
    labels = re.findall(r"^application-label(?:-[^:]+)?:'([^']*)'$", badging, re.MULTILINE)
    require(labels and set(labels) == {'Scriflow'}, 'Expected the Scriflow label in every packaged locale.')
    sdk = [child for child in manifest.children if child.tag == 'uses-sdk']
    require(len(sdk) == 1 and sdk[0].attributes.get('android:minSdkVersion') == 29
            and sdk[0].attributes.get('android:targetSdkVersion') == 29, 'Expected minSdk=29 and targetSdk=29.')
    permissions = {element.attributes.get('android:name') for element in manifest.walk()
                   if element.tag.startswith('uses-permission')}
    require(permissions == PERMISSIONS, f'Unexpected permissions: {sorted(permissions, key=str)}; expected {sorted(PERMISSIONS)}.')
    components = [child for child in application.children if child.tag in COMPONENTS]
    names = [component_name(component) for component in components]
    require(all(names) and len(names) == len(set(names)), 'Missing or duplicate component names.')
    launchers = [component_name(component) for component in components if launcher_filter(component)]
    require(launchers == [LAUNCHER], f'Expected only the notebook library launcher; found {launchers}.')
    launcher = next(component for component in components if component_name(component) == LAUNCHER)
    require(bool(launcher.attributes.get('android:exported', 0)), 'Launcher must be explicitly exported.')
    if variant == 'debug':
        require(DIAGNOSTICS <= set(names), 'Debug APK is missing diagnostic activity declarations.')
    if variant == 'release':
        require(not application.attributes.get('android:testOnly', 0), 'Release APK must not be marked testOnly.')
        require(not (DIAGNOSTICS & set(names)), 'Release APK still declares a diagnostic activity.')
        exported = set()
        for component in components:
            default_exported = component.tag != 'provider' and any(
                child.tag == 'intent-filter' for child in component.children)
            if bool(component.attributes.get('android:exported', default_exported)):
                exported.add(component_name(component))
        require(exported == {LAUNCHER}, f'Release APK exports unexpected components: {sorted(exported)}.')


def contains_signing_block(apk):
    # The APK Signing Block's footer is immediately before the ZIP central directory.
    with apk.open('rb') as package:
        package.seek(0, os.SEEK_END)
        size = package.tell()
        tail_size = min(size, 65557)  # EOCD + largest possible ZIP comment.
        package.seek(size - tail_size)
        tail = package.read()
        marker = tail.rfind(b'PK\x05\x06')
        require(marker >= 0 and marker + 22 <= len(tail), 'Missing APK ZIP end-of-directory record.')
        comment_size = struct.unpack_from('<H', tail, marker + 20)[0]
        require(marker + 22 + comment_size == len(tail), 'Invalid APK ZIP directory or trailing data.')
        offset = struct.unpack_from('<I', tail, marker + 16)[0]
        require(offset != 0xffffffff, 'ZIP64 APKs are not supported by this verification helper.')
        if offset < 24:
            return False
        package.seek(offset - 16)
        return package.read(16) == b'APK Sig Block 42'


def verify_signature(apk, apksigner, signature, expected_digest):
    result = run([apksigner, 'verify', '--verbose', '--print-certs', str(apk)], success=False)
    if signature == 'signed':
        require(result.returncode == 0, 'APK signature does not verify:\n' + (result.stderr.strip() or result.stdout.strip()))
        digests = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', result.stdout, re.MULTILINE)
        require(digests, 'apksigner did not report a signer certificate.')
        if expected_digest:
            expected = re.sub(r'[:\s]', '', expected_digest).lower()
            require(re.fullmatch('[0-9a-f]{64}', expected), 'Expected certificate SHA-256 must contain 64 hexadecimal digits.')
            require({digest.lower() for digest in digests} == {expected}, 'Signing certificate does not match the expected SHA-256 digest.')
        return ', '.join(digests)
    require(not expected_digest, '--certificate-sha256 requires --signature signed.')
    require(result.returncode != 0, 'Expected an unsigned APK, but its signature verifies.')
    with zipfile.ZipFile(apk) as package:
        jar_signatures = [name for name in package.namelist() if re.fullmatch(
            r'META-INF/[^/]+\.(SF|RSA|DSA|EC)', name, flags=re.IGNORECASE)]
    require(not jar_signatures and not contains_signing_block(apk), 'Expected unsigned APK still contains signing data.')
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--variant', choices=('debug', 'release'), default='release')
    parser.add_argument('--signature', choices=('signed', 'unsigned'), default='signed')
    parser.add_argument('--aapt', help='Path to Android SDK Build Tools aapt.')
    parser.add_argument('--apksigner', help='Path to Android SDK Build Tools apksigner.')
    parser.add_argument('--version-properties', type=Path, default=ROOT / 'app/version.properties')
    parser.add_argument('--certificate-sha256', help='Expected release signer certificate SHA-256 (optional).')
    args = parser.parse_args()
    try:
        require(args.apk.is_file(), f'APK not found: {args.apk}')
        aapt = find_tool('aapt', args.aapt)
        apksigner = find_tool('apksigner', args.apksigner)
        version = read_version(args.version_properties)
        badging = run([aapt, 'dump', 'badging', str(args.apk)]).stdout
        manifest = parse_manifest(run([aapt, 'dump', 'xmltree', str(args.apk), 'AndroidManifest.xml']).stdout)
        verify_manifest(manifest, badging, args.variant, version)
        with zipfile.ZipFile(args.apk) as package:
            require('assets/LICENSE' in package.namelist(), 'APK is missing its MIT license notice.')
            require(package.read('assets/LICENSE') == (ROOT / 'LICENSE').read_bytes(),
                    'Packaged license differs from the project LICENSE.')
        certificate = verify_signature(args.apk, apksigner, args.signature, args.certificate_sha256)
        print(f'Verified {args.apk}: Scriflow {version[0]} ({version[1]}), {args.variant}, {args.signature}.')
        print('Package, API level, permissions, launcher, and component exposure are correct.')
        if certificate:
            print('Signer certificate SHA-256: ' + certificate)
        return 0
    except (VerificationError, OSError, zipfile.BadZipFile, struct.error) as error:
        print(f'APK verification failed: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
