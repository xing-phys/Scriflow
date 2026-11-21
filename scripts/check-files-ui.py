#!/usr/bin/env python3
"""Exercise the installed build using disposable shared files on an Android emulator."""
import json
from pathlib import Path
import re
import shlex
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / 'build/file-manager-check'
OUTPUT.mkdir(parents=True, exist_ok=True)
PACKAGE = 'org.eink.inkprobe'
REMOTE_XML = '/data/local/tmp/scriflow-files-ui-check.xml'
checks = []


def adb(*args, check=True):
    result = subprocess.run(['adb', *args], capture_output=True, check=check)
    return result.stdout.decode('utf-8', errors='replace').strip()


def snapshot():
    adb('shell', 'uiautomator', 'dump', '--compressed', REMOTE_XML)
    xml = adb('exec-out', 'cat', REMOTE_XML)
    (OUTPUT / 'last-ui.xml').write_text(xml)
    return ET.fromstring(xml)


def matching(root, text):
    result = [n for n in root.iter('node') if n.get('content-desc', '').casefold() == text.casefold()
              or n.get('text', '').casefold() == text.casefold() and n.get('class') != 'android.widget.EditText']
    # Android displays dialog titles and action buttons with the same label.
    return sorted(result, key=lambda n: (not n.get('resource-id', '').startswith('android:id/button'),
                                        n.get('clickable') != 'true'))


def wait_for(text, seconds=35):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        root = snapshot()
        found = matching(root, text)
        if found:
            return found[0]
        time.sleep(.2)
    raise AssertionError('UI did not show: ' + text)


def tap_node(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def tap(text):
    tap_node(wait_for(text))


def permission(button):
    root = snapshot()
    node = next(n for n in root.iter('node') if n.get('resource-id', '').endswith(button))
    tap_node(node)


def enter(text):
    root = snapshot()
    node = next(n for n in root.iter('node') if n.get('class') == 'android.widget.EditText')
    tap_node(node)
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    adb('shell', 'input', 'keyevent', *(['KEYCODE_DEL'] * (len(node.get('text', '')) + 2)))
    adb('shell', 'input text ' + shlex.quote(text.replace(' ', '%s')))


def exists(path):
    return adb('shell', '[ -e ' + shlex.quote(path) + ' ] && printf yes || printf no') == 'yes'


def check(condition, label):
    if not condition:
        raise AssertionError(label)
    checks.append(label)
    print('PASS:', label, flush=True)


def actions(name, action):
    tap('Actions for ' + name)
    tap(action)


if adb('shell', 'getprop', 'ro.kernel.qemu') != '1':
    raise SystemExit('UI check is restricted to an emulator.')

token = 'ScriflowFilesCheck-' + uuid.uuid4().hex[:8]
fixture = '/sdcard/Download/' + token
local = OUTPUT / 'payload.txt'
local.write_text('non-equilibrium quantum dynamics\n')
adb('shell', 'mkdir', '-p', fixture + '/source/sub', fixture + '/destination')
adb('push', str(local), fixture + '/source/readme.txt')
adb('push', str(local), fixture + '/source/sub/nested.txt')
adb('push', str(local), fixture + '/source/.hidden.txt')
original_rotation = adb('shell', 'settings', 'get', 'system', 'user_rotation')
original_auto = adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation')
original_size = adb('shell', 'wm', 'size')
try:
    # Denial is reversible and scoped to this disposable emulator, without clearing app data.
    for permission_name in ('READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE'):
        adb('shell', 'pm', 'revoke', PACKAGE, 'android.permission.' + permission_name, check=False)
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'wm', 'dismiss-keyguard')
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookLibraryActivity')
    tap('Files')
    wait_for('Allow storage access')
    tap('Allow storage access')
    permission('permission_deny_button')
    check(wait_for('Allow storage access') is not None, 'Denying storage access leaves an actionable Files screen')
    tap('Allow storage access')
    permission('permission_allow_button')
    tap('Internal storage')
    tap('Search files')
    enter(token)
    tap('Search files')
    tap(token)
    wait_for('source')
    check(True, 'Permission grant enables storage browsing and recursive name search')

    tap('File options')
    tap('New folder')
    enter('Created')
    tap('Create')
    wait_for('Created')
    check(exists(fixture + '/Created'), 'New folder creates a real shared-storage directory')
    tap('source')
    tap('Go to Internal storage / Download / ' + token)
    wait_for('destination')
    check(True, 'Tapping an ancestor breadcrumb jumps directly to that folder')
    tap('source')
    actions('readme.txt', 'Rename')
    enter('renamed.txt')
    tap('Rename')
    wait_for('renamed.txt')
    check(exists(fixture + '/source/renamed.txt') and not exists(fixture + '/source/readme.txt'), 'Rename updates the filesystem')

    actions('renamed.txt', 'Copy')
    tap('Parent folder')
    tap('destination')
    tap('File options')
    tap('New folder')
    enter('PasteTarget')
    tap('Create')
    wait_for('PasteTarget')
    check(wait_for('1 to copy') is not None, 'Creating a destination folder preserves the pending clipboard')
    tap('Paste here')
    wait_for('renamed.txt')
    check(exists(fixture + '/source/renamed.txt') and exists(fixture + '/destination/renamed.txt'), 'Copy and paste retain the original and publish a complete destination')
    check(adb('shell', 'cat', fixture + '/destination/renamed.txt') == local.read_text().strip(), 'Copied bytes match the original payload')

    actions('renamed.txt', 'Copy')
    tap('Paste here')
    # A refused paste reports the conflict in the file view and keeps existing bytes.
    root = snapshot()
    check(any('Already exists:' in n.get('text', '') for n in root.iter('node')), 'Paste reports a collision instead of replacing an existing file')
    tap('File options')
    tap('Refresh')
    wait_for('renamed.txt')
    actions('renamed.txt', 'Move')
    tap('Parent folder')
    tap('Created')
    tap('Paste here')
    wait_for('renamed.txt')
    check(not exists(fixture + '/destination/renamed.txt') and exists(fixture + '/Created/renamed.txt'), 'Move removes only the original after successful publication')

    actions('renamed.txt', 'Delete…')
    tap('Cancel')
    check(exists(fixture + '/Created/renamed.txt'), 'Cancelling delete keeps the selected file')
    actions('renamed.txt', 'Delete…')
    tap('Delete permanently')
    wait_for('This folder is empty.')
    check(not exists(fixture + '/Created/renamed.txt'), 'Confirmed deletion removes the real file')

    tap('Parent folder')
    tap('source')
    tap('File options')
    tap('Show hidden files')
    wait_for('.hidden.txt')
    check(True, 'Hidden files can be explicitly displayed')
    tap('File options')
    tap('Select files')
    tap('Select all')
    wait_for('3 selected')
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    adb('shell', 'settings', 'put', 'system', 'user_rotation', '1')
    adb('shell', 'wm', 'size', '1500x2000')
    wait_for('3 selected')
    check(True, 'Selection and current folder survive display configuration changes')
    tap('Actions')
    tap('Copy')
    tap('Parent folder')
    tap('destination')
    tap('Paste here')
    wait_for('renamed.txt')
    check(exists(fixture + '/destination/sub/nested.txt') and exists(fixture + '/destination/.hidden.txt'), 'Multi-selection copies folders and hidden files')

    actions('renamed.txt', 'Open with…')
    root = snapshot()
    check(any(n.get('package') and n.get('package') != PACKAGE for n in root.iter('node')),
          'Open with launches an installed viewer or Android chooser without a file-URI crash')
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    wait_for('renamed.txt')
    actions('renamed.txt', 'Share')
    root = snapshot()
    check(any(n.get('package') in {'android', 'com.android.systemui'} for n in root.iter('node')),
          'Share launches Android with an individually granted content URI')
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    wait_for('renamed.txt')
    screenshot = subprocess.run(['adb', 'exec-out', 'screencap', '-p'], capture_output=True, check=True).stdout
    (OUTPUT / 'files.png').write_bytes(screenshot)
    (OUTPUT / 'ui-result.json').write_text(json.dumps({'passed': True, 'checks': checks}, indent=2) + '\n')
    print('File manager Android UI:', len(checks), 'checks passed.', flush=True)
finally:
    override = re.search(r'Override size: (\d+x\d+)', original_size)
    adb('shell', 'wm', 'size', override.group(1) if override else 'reset')
    adb('shell', 'settings', 'put', 'system', 'user_rotation', original_rotation)
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', original_auto)
    adb('shell', 'rm', '-rf', fixture)
    adb('shell', 'rm', '-f', REMOTE_XML)
