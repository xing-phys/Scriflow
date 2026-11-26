#!/usr/bin/env python3
"""Exercise shelf alignment, item actions and website settings on a disposable emulator."""
from pathlib import Path
import json
import os
import re
import shlex
import subprocess
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET

PACKAGE = 'org.eink.inkprobe'
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
TOKEN = uuid.uuid4().hex[:8]
REMOTE = '/data/local/tmp/scriflow-shelf-' + TOKEN
ROOT = 'Shelf' + TOKEN
CHILD = 'Nested' + TOKEN
RENAMED = 'Renamed' + TOKEN
THUMB_FOLDER = 'Thumbnail' + TOKEN
NOTE = 'ReadingOverlap-' + TOKEN
ORIGIN = 'https://ui-fixture.example'
OUTPUT = Path('build/shelf-permissions-check')
OUTPUT.mkdir(parents=True, exist_ok=True)
checks = []
note_id = 0


def adb(*args, check=True):
    return subprocess.run(['adb', '-s', SERIAL, *args], capture_output=True, check=check).stdout


def shell(*args, check=True):
    return adb('shell', shlex.join(args), check=check).decode(errors='replace').strip()


def tree():
    shell('uiautomator', 'dump', REMOTE + '.xml')
    return ET.fromstring(adb('exec-out', 'cat', REMOTE + '.xml'))


def find(label, root=None, contains=False):
    root = root if root is not None else tree()
    for node in root.iter('node'):
        values = (node.get('content-desc', ''), node.get('text', ''))
        if any((label in value if contains else label.casefold() == value.casefold()) for value in values):
            return node
    return None


def locate(label, timeout=25, contains=False):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        node = find(label, contains=contains)
        if node is not None:
            return node
        time.sleep(.2)
    raise AssertionError('Control not found: ' + label)


def wait_absent(label, timeout=25):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if find(label) is None:
            return
        time.sleep(.2)
    raise AssertionError('Control did not disappear: ' + label)


def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))


def tap_node(node):
    x1, y1, x2, y2 = bounds(node)
    shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))


def tap(label, contains=False):
    tap_node(locate(label, contains=contains))


def check(value, label):
    if not value:
        raise AssertionError(label)
    checks.append(label)
    print('PASS:', label, flush=True)


def screenshot(name):
    (OUTPUT / name).write_bytes(adb('exec-out', 'screencap', '-p'))


def scroll_to(label, contains=False):
    for _ in range(14):
        node = find(label, contains=contains)
        if node is not None:
            return node
        shell('input', 'swipe', '650', '1500', '650', '1000', '450')
        time.sleep(.3)
    raise AssertionError('Could not scroll to: ' + label)


def enter(value):
    node = next(n for n in tree().iter('node') if n.get('class') == 'android.widget.EditText')
    tap_node(node)
    shell('input', 'keyevent', 'KEYCODE_MOVE_END')
    for _ in range(len(node.get('text', '')) + 2):
        shell('input', 'keyevent', 'KEYCODE_DEL')
    shell('input', 'text', value)


def new_folder(name):
    tap('+ Folder'); locate('New folder'); enter(name); tap('Save'); locate('Open folder ' + name)


def fixture_result():
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        try:
            value = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'cache/reading-fixture.json', check=False))
            if value.get('error'):
                raise AssertionError(value['error'])
            return value
        except (ValueError, TypeError):
            time.sleep(.2)
    raise AssertionError('Notebook fixture did not complete')


def remove_note():
    if not note_id:
        return
    shell('am', 'force-stop', PACKAGE, check=False)
    shell('run-as', PACKAGE, 'rm', '-f', 'cache/reading-fixture.json', check=False)
    shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookReadingFixtureActivity',
          '--es', 'name', NOTE, '--ei', 'remove', str(note_id))
    fixture_result()


def write_preference(data):
    with tempfile.NamedTemporaryFile() as fixture:
        fixture.write(data); fixture.flush()
        adb('push', fixture.name, REMOTE + '.xmlpref')
    shell('run-as', PACKAGE, 'mkdir', '-p', 'shared_prefs')
    shell('run-as', PACKAGE, 'cp', REMOTE + '.xmlpref', 'shared_prefs/website-microphone.xml')


if shell('getprop', 'ro.kernel.qemu') != '1':
    raise SystemExit('This check uses disposable data on an emulator only.')
original = subprocess.run(['adb', '-s', SERIAL, 'exec-out', 'run-as', PACKAGE, 'cat',
                           'shared_prefs/website-microphone.xml'], capture_output=True)
try:
    shell('am', 'force-stop', PACKAGE)
    shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookLibraryActivity')
    new_folder(ROOT)
    tap('Open folder ' + ROOT)
    breadcrumb = locate('‹ ' + ROOT)
    shelf = locate('Bookshelf')
    first = locate('Notebook')
    check(bounds(shelf)[2] <= bounds(breadcrumb)[0] and bounds(breadcrumb)[2] <= bounds(first)[0]
          and max(bounds(shelf)[1], bounds(breadcrumb)[1], bounds(first)[1]) < min(bounds(shelf)[3], bounds(breadcrumb)[3], bounds(first)[3]),
          'Bookshelf, breadcrumb, and right-hand actions share one row')
    check(bounds(breadcrumb)[2] - bounds(breadcrumb)[0] >= 3 * (bounds(shelf)[2] - bounds(shelf)[0])
          and find('Import PDF') is None and find('Import book') is None,
          'Compact shortcuts leave room for the breadcrumb and omit redundant import buttons')
    screenshot('folder-toolbar.png')
    new_folder(CHILD)
    tap('Open folder ' + CHILD)
    locate('‹ ' + ROOT + ' / ' + CHILD)
    tap('‹ ' + ROOT + ' / ' + CHILD)
    tap('Manage folder ' + CHILD); tap('Rename folder'); enter(RENAMED); tap('Save')
    locate('Open folder ' + RENAMED)
    check(True, 'Nested folder navigation and the anchored rename action work')
    tap('Manage folder ' + RENAMED); tap('Remove folder (keep items)')
    wait_absent('Open folder ' + RENAMED)
    tap('Bookshelf')
    tap('Manage folder ' + ROOT); tap('Remove folder (keep items)')
    wait_absent('Open folder ' + ROOT)

    shell('am', 'force-stop', PACKAGE)
    shell('run-as', PACKAGE, 'rm', '-f', 'cache/reading-fixture.json')
    shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookReadingFixtureActivity', '--es', 'name', NOTE)
    note_id = fixture_result()['id']
    shell('am', 'force-stop', PACKAGE)
    shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookLibraryActivity')
    locate('Open folder Saved websites')
    tap_node(locate('; tap to open settings', contains=True))
    tap_node(locate('Thumbnails', contains=True)); tap('Done')
    locate('Tap name or date for actions', contains=True)
    new_folder(THUMB_FOLDER)
    tap('Manage folder ' + THUMB_FOLDER); locate('Rename folder'); shell('input', 'keyevent', 'KEYCODE_BACK')
    tap('Open folder ' + THUMB_FOLDER); locate('‹ ' + THUMB_FOLDER)
    tap('‹ ' + THUMB_FOLDER)
    tap('Manage folder ' + THUMB_FOLDER); tap('Remove folder (keep items)')
    wait_absent('Manage folder ' + THUMB_FOLDER)
    check(True, 'Thumbnail folders open from their previews and expose actions from their names')
    name = scroll_to('Manage notebook ' + NOTE)
    root = tree()
    cards = []
    for node in root.iter('node'):
        descendants = list(node.iter('node'))
        if (any(n.get('content-desc') == 'Manage notebook ' + NOTE for n in descendants)
                and any(n.get('content-desc') == 'Open notebook ' + NOTE for n in descendants)
                and any(n.get('content-desc', '').startswith('Manage notebook ' + NOTE + ', last opened ')
                        and n.get('text', '') not in ('', '—') and 'Last opened' not in n.get('text', '') for n in descendants)):
            cards.append(node)
    check(bool(cards), 'Thumbnail card contains its preview, name and opening time without a redundant label')
    card = min(cards, key=lambda node: (bounds(node)[2] - bounds(node)[0]) * (bounds(node)[3] - bounds(node)[1]))
    check(sum(n.get('content-desc') == 'Manage notebook ' + NOTE for n in card.iter('node')) == 1,
          'Thumbnail card has no separate three-dot control')
    screenshot('thumbnail-card.png')
    tap_node(name); locate('Move to Trash'); shell('input', 'keyevent', 'KEYCODE_BACK')
    date = locate('Manage notebook ' + NOTE + ', last opened ', contains=True)
    tap_node(date); locate('Rename'); shell('input', 'keyevent', 'KEYCODE_BACK')
    check(True, 'Tapping the thumbnail name or date opens the item action bar')
    tap_node(locate('Open notebook ' + NOTE))
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        tasks = shell('dumpsys', 'activity', 'activities')
        if any('mResumedActivity' in line and PACKAGE + '/.notebook.NotebookActivity' in line for line in tasks.splitlines()):
            break
        time.sleep(.2)
    else:
        raise AssertionError('Tapping the notebook preview did not open the notebook')
    check(True, 'Tapping the thumbnail preview opens the notebook')
    shell('am', 'force-stop', PACKAGE)
    shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookLibraryActivity')
    tap_node(locate('; tap to open settings', contains=True))
    tap_node(locate('List', contains=True)); tap('Done')
    locate('Open folder Saved websites')
    tap_node(scroll_to('Manage notebook ' + NOTE)); tap('Move to Trash')
    wait_absent('Manage notebook ' + NOTE)
    tap_node(locate('Trash (', contains=True))
    item = locate('Manage deleted item ' + NOTE)
    menu = locate('Manage notebook ' + NOTE)
    a, b = bounds(item), bounds(menu)
    check(abs((a[1]+a[3])-(b[1]+b[3])) <= 4,
          'Trash item overflow is vertically centered with its notebook row')
    tap_node(menu)
    restore, delete = locate('Restore notebook'), locate('Delete permanently')
    check(abs(bounds(restore)[1] - b[3]) < 160 and bounds(restore)[1] == bounds(delete)[1],
          'Trash actions appear together in a compact bar near the selected item')
    screenshot('trash-actions.png')
    tap('Delete permanently'); locate('Permanently delete “' + NOTE + '”?'); tap('Cancel')
    locate('Manage deleted item ' + NOTE)
    tap('Manage notebook ' + NOTE); tap('Restore notebook')
    check(True, 'Canceling permanent deletion and restoring the notebook both work')
    deleted_id = note_id
    remove_note(); note_id = 0
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        value = shell('run-as', PACKAGE, 'cat', 'shared_prefs/notebook-opened.xml', check=False)
        if not value or all(node.get('name') != str(deleted_id) for node in ET.fromstring(value)):
            break
        time.sleep(.2)
    else:
        raise AssertionError('Deleted notebook retained its last-opened timestamp')
    check(True, 'Permanent deletion clears the timestamp before its notebook slot can be reused')

    # Set up one disposable remembered origin, then change it through real Settings UI.
    shell('am', 'force-stop', PACKAGE)
    write_preference(('<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n'
                      '<map><int name="' + ORIGIN + '" value="1" /></map>\n').encode())
    shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookLibraryActivity')
    tap_node(locate('; tap to open settings', contains=True))
    tap_node(scroll_to('Manage website permissions…'))
    tap(ORIGIN + ' · Allowed'); tap('Block'); locate(ORIGIN + ' · Blocked')
    check(True, 'Settings can change a remembered website from Allow to Block')
    shell('am', 'force-stop', PACKAGE)
    preference = adb('exec-out', 'run-as', PACKAGE, 'cat', 'shared_prefs/website-microphone.xml').decode()
    check('value="2"' in preference and ORIGIN in preference, 'Website blocking persists after the app stops')
    (OUTPUT / 'result.json').write_text(json.dumps({'passed': True, 'checks': checks}, indent=2) + '\n')
    print('Shelf and permissions Android UI:', len(checks), 'checks passed.', flush=True)
except BaseException:
    (OUTPUT / 'failure-screen.png').write_bytes(adb('exec-out', 'screencap', '-p', check=False))
    (OUTPUT / 'failure-logcat.txt').write_bytes(adb('logcat', '-d', '-t', '1500', check=False))
    raise
finally:
    if note_id:
        remove_note()
    shell('am', 'force-stop', PACKAGE, check=False)
    if original.returncode == 0:
        write_preference(original.stdout)
    else:
        shell('run-as', PACKAGE, 'rm', '-f', 'shared_prefs/website-microphone.xml', check=False)
    shell('rm', '-f', REMOTE + '.xml', REMOTE + '.xmlpref', check=False)
