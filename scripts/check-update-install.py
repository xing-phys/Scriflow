#!/usr/bin/env python3
"""Exercise the Android installer and update cleanup with disposable debug APKs on an emulator."""
import hashlib
import os
import re
import shlex
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
PACKAGE = 'org.eink.inkprobe'
BASE = Path('build/scriflow-debug.apk')
CANDIDATE = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/tmp/scriflow-update-fixture-133.apk')
WRONG_SIGNER = Path(sys.argv[2]) if len(sys.argv) > 2 else None
VERSION = '0.49.0-rc.2'
TOKEN = 'ScriflowUpdateTest' + uuid.uuid4().hex[:8]
REMOTE = '/data/local/tmp/' + TOKEN
PRIVATE = 'files/updates/scriflow-' + VERSION + '.apk'


def adb(*args, check=True, data=None):
    return subprocess.run(['adb', '-s', SERIAL, *args], input=data, capture_output=True, check=check)


def shell(*args, check=True, data=None):
    return adb('shell', shlex.join(args), check=check, data=data).stdout.decode(errors='replace').strip()


def tree():
    shell('uiautomator', 'dump', REMOTE + '.xml')
    return ET.fromstring(adb('exec-out', 'cat', REMOTE + '.xml').stdout)


def find(label, timeout=30, contains=False):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        for node in tree().iter('node'):
            for value in (node.get('text', ''), node.get('content-desc', '')):
                if (label.casefold() in value.casefold()) if contains else (label.casefold() == value.casefold()): return node
        time.sleep(.3)
    raise AssertionError('Control not found: ' + label)


def tap(node):
    bounds = [int(x) for x in re.findall(r'\d+', node.get('bounds', ''))]
    if len(bounds) != 4: raise AssertionError('Invalid control bounds')
    shell('input', 'tap', str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))


def exists():
    return adb('shell', 'run-as', PACKAGE, 'ls', PRIVATE, check=False).returncode == 0


def wait(predicate, label, timeout=60):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if predicate(): return
        time.sleep(.3)
    raise AssertionError(label)


def seed(code, auto, source=CANDIDATE):
    shell('run-as', PACKAGE, 'mkdir', '-p', 'files/updates', 'shared_prefs')
    shell('run-as', PACKAGE, 'cp', REMOTE + '.apk', PRIVATE)
    digest = hashlib.sha256()
    with source.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''): digest.update(block)
    checksum = digest.hexdigest()
    xml = ('<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>'
           '<string name="readyVersion">' + VERSION + '</string>'
           '<long name="readyCode" value="' + str(code) + '" />'
           '<string name="readySha256">' + checksum + '</string>'
           '<boolean name="autoDelete" value="' + str(auto).lower() + '" />'
           '</map>\n').encode()
    shell('run-as', PACKAGE, 'sh', '-c', 'cat > shared_prefs/app-updates.xml', data=xml)


def settings():
    shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookLibraryActivity')
    tap(find('; tap to open settings', contains=True))
    find('Check for updates')


def main():
    if shell('getprop', 'ro.kernel.qemu') != '1': raise SystemExit('This test is restricted to a disposable emulator.')
    if not BASE.is_file() or not CANDIDATE.is_file(): raise SystemExit('Build the base and disposable candidate APK first.')
    try:
        adb('install', '-r', '-d', str(BASE)); shell('pm', 'clear', PACKAGE)
        adb('push', str(CANDIDATE), REMOTE + '.apk')
        seed(132, False); settings()
        assert find('Installed version: 0.49.0-rc.1') is not None
        toggle = find('Delete installer after a successful update')
        assert toggle.get('checked') == 'false' and exists(), 'Installer should be kept while the option is off'
        tap(toggle); wait(lambda: not exists(), 'Turning on cleanup did not remove the already installed fixture')
        print('PASS: About shows the version and the cleanup preference controls retained installers', flush=True)
        if WRONG_SIGNER:
            if not WRONG_SIGNER.is_file(): raise AssertionError('Missing wrong-signer fixture')
            shell('am', 'force-stop', PACKAGE); adb('push', str(WRONG_SIGNER), REMOTE + '.apk')
            seed(133, True, WRONG_SIGNER); shell('appops', 'set', PACKAGE, 'REQUEST_INSTALL_PACKAGES', 'allow')
            settings(); tap(find('Install update'))
            find('The installer is signed by a different key.', timeout=120)
            assert 'versionCode=132 ' in shell('dumpsys', 'package', PACKAGE), 'Wrong signer changed the installed app'
            print('PASS: an installer with a different signing key is rejected', flush=True)
            shell('am', 'force-stop', PACKAGE); adb('push', str(CANDIDATE), REMOTE + '.apk')
        shell('am', 'force-stop', PACKAGE); seed(133, True)
        shell('appops', 'set', PACKAGE, 'REQUEST_INSTALL_PACKAGES', 'allow')
        settings(); tap(find('Install update'))
        tap(find('Install', timeout=120))
        wait(lambda: 'versionCode=133 ' in shell('dumpsys', 'package', PACKAGE), 'Android did not install the candidate update', timeout=120)
        wait(lambda: not exists(), 'Installer was not removed after the successful update', timeout=60)
        print('PASS: verified installer updates the app and is removed only after success', flush=True)
    finally:
        shell('am', 'force-stop', PACKAGE, check=False)
        shell('rm', '-f', REMOTE + '.apk', REMOTE + '.xml', check=False)
        print('Disposable emulator fixture removed.', flush=True)


if __name__ == '__main__': main()
