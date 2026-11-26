#!/usr/bin/env python3
"""Exercise compact native dialogs on a disposable Android emulator."""
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
TOKEN = uuid.uuid4().hex
REMOTE = '/data/local/tmp/scriflow-dialog-' + TOKEN
CHAT = 'files/ai-chat-sessions/' + str(uuid.uuid4())
CHECKS = []
NOTE_NAME = 'ReadingOverlap-' + uuid.uuid4().hex[:8]
PDF_NAME = 'ReadingOverlap-' + uuid.uuid4().hex[:8]
notebook_id = 0
pdf_id = 0
OUTPUT = Path('build/dialog-check')
OUTPUT.mkdir(parents=True, exist_ok=True)


def adb(*args, check=True):
    return subprocess.run(['adb', '-s', SERIAL, *args], capture_output=True, check=check).stdout


def shell(*args, check=True):
    return adb('shell', shlex.join(args), check=check).decode(errors='replace').strip()


def tree():
    shell('uiautomator', 'dump', REMOTE + '.xml')
    return ET.fromstring(adb('exec-out', 'cat', REMOTE + '.xml'))


def locate(label, timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        root = tree()
        found = [node for node in root.iter('node') if node.get('content-desc', '').casefold() == label.casefold()
                 or node.get('text', '').casefold() == label.casefold() and node.get('class') != 'android.widget.EditText']
        if found:
            return sorted(found, key=lambda node: node.get('clickable') != 'true')[0]
        time.sleep(.2)
    raise AssertionError('Control not found: ' + label)


def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))


def tap(label):
    x1, y1, x2, y2 = bounds(locate(label))
    shell('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def check(condition, message):
    if not condition:
        raise AssertionError(message)
    CHECKS.append(message)
    print('PASS:', message, flush=True)


def screenshot(name):
    (OUTPUT / name).write_bytes(adb('exec-out', 'screencap', '-p'))


def wait_json(name, predicate=lambda value: True):
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        try:
            data = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'cache/' + name, check=False))
            if predicate(data):
                return data
        except (ValueError, TypeError):
            pass
        time.sleep(.2)
    raise AssertionError('Diagnostic state not ready: ' + name)


def open_outline():
    shell('am', 'start', '-W', '-f', '0x20000000', '-n', PACKAGE + '/.notebook.NotebookReadingSelfTestActivity',
          '--ez', 'openOutline', 'true')
    locate('Table of contents')


def remove_notebook(name, notebook_id):
    if not notebook_id:
        return
    # Stop the debug editor before deleting its test notebook, as in the reader checks.
    shell('am', 'force-stop', PACKAGE, check=False)
    shell('run-as', PACKAGE, 'rm', '-f', 'cache/reading-fixture.json', check=False)
    shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookReadingFixtureActivity',
          '--es', 'name', name, '--ei', 'remove', str(notebook_id))
    result = wait_json('reading-fixture.json')
    if result.get('error'):
        raise AssertionError('Could not clean up disposable notebook: ' + result['error'])


if shell('getprop', 'ro.kernel.qemu') != '1':
    raise SystemExit('This check uses disposable data on an emulator only.')

try:
    with tempfile.NamedTemporaryFile() as fixture:
        fixture.write(b'disposable unreadable chat fixture')
        fixture.flush()
        adb('push', fixture.name, REMOTE + '.chat')
    shell('run-as', PACKAGE, 'mkdir', '-p', 'files/ai-chat-sessions')
    shell('run-as', PACKAGE, 'cp', REMOTE + '.chat', CHAT + '.chat')
    shell('am', 'force-stop', PACKAGE)
    shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookLibraryActivity')
    tap('AI chat')
    close = locate('Cancel')
    check(close.get('class') == 'android.widget.Button', 'Choice dialog exposes a circular Cancel control')
    tap('Cancel')
    locate('Scriflow')
    check(True, 'The circular Cancel control dismisses a choice without selecting an item')
    tap('AI chat')
    tap('Open chat history')
    locate('Chat history')
    delete = locate('Delete saved chat: Unreadable saved chat')
    close = locate('Close')
    delete_bounds, close_bounds = bounds(delete), bounds(close)
    check(delete_bounds[2] <= close_bounds[0] or delete_bounds[0] >= close_bounds[2]
          or delete_bounds[3] <= close_bounds[1] or delete_bounds[1] >= close_bounds[3],
          'History delete and close controls do not overlap')
    screenshot('chat-history.png')
    tap('Delete saved chat: Unreadable saved chat')
    locate('Delete saved chat?')
    locate('Delete')
    close = locate('Cancel')
    check(close.get('class') == 'android.widget.Button', 'Confirmation retains its Delete action and circular Cancel control')
    screenshot('confirmation.png')
    tap('Cancel')
    locate('Chat history')
    locate('Delete saved chat: Unreadable saved chat')
    check(shell('run-as', PACKAGE, 'ls', CHAT + '.chat', check=False).endswith(CHAT + '.chat'),
          'Cancel returns to history with the saved chat present')
    tap('Close')
    locate('Scriflow')
    check(True, 'Closing chat history releases the workspace and returns to the shelf')

    # The debug reader uses a software ink surface but the production outline UI.
    shell('am', 'force-stop', PACKAGE)
    shell('run-as', PACKAGE, 'rm', '-f', 'cache/reading-fixture.json', 'cache/reading-ui-state.json')
    shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookReadingFixtureActivity', '--es', 'name', NOTE_NAME)
    fixture = wait_json('reading-fixture.json')
    if fixture.get('error'):
        raise AssertionError(fixture['error'])
    notebook_id = fixture['id']
    wait_json('reading-ui-state.json', lambda value: value.get('editable') and value.get('notebookId') == notebook_id)
    open_outline()
    locate('Add heading'); locate('Edit headings'); close = locate('Close')
    check(close.get('class') == 'android.widget.Button', 'Note contents retains heading actions and the circular close control')
    screenshot('note-contents-empty.png')
    tap('Add heading')
    tap('Heading title'); shell('input', 'text', 'DialogHeading')
    tap('Save')
    locate('DialogHeading  ·  p. 1')
    tap('Edit headings'); locate('Browse headings')
    tap('DialogHeading  ·  p. 1'); locate('Edit heading'); tap('Cancel')
    locate('Table of contents'); tap('Browse headings')
    before = wait_json('reading-ui-state.json')['time']; tap('Close')
    wait_json('reading-ui-state.json', lambda value: value.get('editable') and value.get('time', 0) > before)
    check(True, 'Adding, editing-mode navigation and closing the note outline still work')
    shell('am', 'start', '-W', '-f', '0x20000000', '-n', PACKAGE + '/.notebook.NotebookReadingSelfTestActivity',
          '--ez', 'openReading', 'true')
    title = locate('Reading view'); close = locate('Close reading settings')
    check(bounds(close)[1] > bounds(title)[1], 'Reading settings places its circular close control below the heading')
    screenshot('reading-settings.png')
    before = wait_json('reading-ui-state.json')['time']; tap('Close reading settings')
    wait_json('reading-ui-state.json', lambda value: value.get('editable') and value.get('time', 0) > before)
    remove_notebook(NOTE_NAME, notebook_id)
    notebook_id = 0

    # Import an actual PDF through the production picker and use the same outline UI.
    pdf = Path('build/pdf-engine-test/source.pdf')
    if not pdf.is_file():
        raise AssertionError('Run ./scripts/test.sh first to prepare the disposable PDF fixture')
    shell('pm', 'grant', PACKAGE, 'android.permission.READ_EXTERNAL_STORAGE', check=False)
    shell('pm', 'grant', PACKAGE, 'android.permission.WRITE_EXTERNAL_STORAGE', check=False)
    adb('push', str(pdf), '/sdcard/Download/' + PDF_NAME + '.pdf')
    shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookLibraryActivity')
    tap('Import PDF'); tap('Downloads'); tap(PDF_NAME + '.pdf')
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        tasks = shell('dumpsys', 'activity', 'activities')
        if any('mResumedActivity' in line and PACKAGE + '/.notebook.NotebookActivity' in line for line in tasks.splitlines()):
            break
        time.sleep(.3)
    else:
        raise AssertionError('The disposable PDF did not open')
    shell('run-as', PACKAGE, 'rm', '-f', 'cache/reading-ui-state.json')
    shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookReadingSelfTestActivity')
    pdf_state = wait_json('reading-ui-state.json', lambda value: value.get('editable'))
    pdf_id = pdf_state['notebookId']
    open_outline(); locate('Table of contents'); locate('Close'); locate('Add heading'); locate('Edit headings')
    screenshot('pdf-contents.png')
    before = wait_json('reading-ui-state.json')['time']; tap('Close')
    wait_json('reading-ui-state.json', lambda value: value.get('editable') and value.get('time', 0) > before)
    check(True, 'Imported PDF contents uses the same working compact outline dialog')
    remove_notebook(PDF_NAME, pdf_id)
    pdf_id = 0
    (OUTPUT / 'result.json').write_text(json.dumps({'passed': True, 'checks': CHECKS}, indent=2) + '\n')
    print('Compact dialog Android UI:', len(CHECKS), 'checks passed.', flush=True)
finally:
    if notebook_id:
        remove_notebook(NOTE_NAME, notebook_id)
    if pdf_id:
        remove_notebook(PDF_NAME, pdf_id)
    shell('rm', '-f', '/sdcard/Download/' + PDF_NAME + '.pdf', check=False)
    shell('run-as', PACKAGE, 'rm', '-f', CHAT + '.chat', CHAT + '.chat.tmp', CHAT + '.gone', check=False)
    shell('rm', '-f', REMOTE + '.xml', REMOTE + '.chat', check=False)
