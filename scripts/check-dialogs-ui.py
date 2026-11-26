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
from PIL import Image

PACKAGE = 'org.eink.inkprobe'
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
TOKEN = uuid.uuid4().hex
REMOTE = '/data/local/tmp/scriflow-dialog-' + TOKEN
CHAT = 'files/ai-chat-sessions/' + str(uuid.uuid4())
SAVED_ID = str(uuid.uuid4())
SAVED = 'files/ai-chat-sessions/' + SAVED_ID
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
    for attempt in range(4):
        try:
            shell('uiautomator', 'dump', REMOTE + '.xml')
            return ET.fromstring(adb('exec-out', 'cat', REMOTE + '.xml'))
        except (subprocess.CalledProcessError, ET.ParseError):
            if attempt == 3:
                raise
            time.sleep(1)



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


def locate_prefix(prefix, timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        for node in tree().iter('node'):
            if node.get('content-desc', '').startswith(prefix):
                return node
        time.sleep(.2)
    raise AssertionError('Control not found with prefix: ' + prefix)


def tap_node(node):
    x1, y1, x2, y2 = bounds(node)
    shell('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def same_row(first, second):
    a, b = bounds(first), bounds(second)
    return min(a[3], b[3]) > max(a[1], b[1])


def tap_choice(label, timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        for node in tree().iter('node'):
            if node.get('text', '').removeprefix('✓ ') == label:
                tap_node(node)
                return
        time.sleep(.2)
    raise AssertionError('Reading choice not found: ' + label)


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


def has_top_border(name, title):
    with Image.open(OUTPUT / name).convert('RGB') as image:
        left, right = image.width // 4, 3 * image.width // 4
        for y in range(4, min(bounds(title)[1], image.height)):
            if sum(max(image.getpixel((x, y))) < 120 for x in range(left, right)) > (right - left) * .9:
                return True
    return False


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
    with tempfile.TemporaryDirectory() as fixture_dir:
        java = Path('.tools/jdk-17.0.21+9/bin')
        subprocess.run([str(java / 'javac'), '-cp', 'build/ai-tests', '-d', str(OUTPUT),
                        'tests/NotebookAiUiFixture.java'], check=True)
        subprocess.run([str(java / 'java'), '-cp', str(OUTPUT) + ':build/ai-tests:.tools/ai-tests/json-20240303.jar',
                        'org.eink.inkprobe.notebook.NotebookAiUiFixture', fixture_dir, SAVED_ID], check=True)
        adb('push', str(Path(fixture_dir) / 'ai-chat-sessions' / (SAVED_ID + '.chat')), REMOTE + '.saved')
    shell('run-as', PACKAGE, 'cp', REMOTE + '.saved', SAVED + '.chat')
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
    title = locate('Chat history')
    delete = locate('Delete saved chat: Unreadable saved chat')
    close = locate('Close')
    tasks = shell('dumpsys', 'activity', 'activities')
    check(any('mResumedActivity' in line and PACKAGE + '/.notebook.NotebookLibraryActivity' in line for line in tasks.splitlines()),
          'Chat history stays over the bookshelf without opening a note workspace')
    check(same_row(title, close) and bounds(close)[0] > bounds(title)[0],
          'Chat history close button shares the title row')
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
    check(True, 'Closing chat history leaves the bookshelf visible')

    # Selecting a real saved chat opens the AI workspace only after the selection.
    tap('AI chat'); tap('Open chat history')
    tap_node(locate_prefix('Open saved chat: Dialog fixture;'))
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        if any('mResumedActivity' in line and PACKAGE + '/.notebook.NotebookAiActivity' in line
               for line in shell('dumpsys', 'activity', 'activities').splitlines()):
            break
        time.sleep(.5)
    else:
        raise AssertionError('Selected chat did not open the AI activity')
    time.sleep(3)  # Let the chat renderer settle before starting UIAutomator.
    editor = locate('Message AI')
    menu = locate('chat options; drag to move floating window')
    add = locate('Attach a notebook')
    send = locate('Send message (Ctrl+Enter)')
    minimize = locate('Minimize chat; drag to resize floating window')
    tasks = shell('dumpsys', 'activity', 'activities')
    check(any('mResumedActivity' in line and PACKAGE + '/.notebook.NotebookAiActivity' in line for line in tasks.splitlines()),
          'Selecting a saved chat opens that chat in the AI workspace')
    check(bounds(menu)[2] <= bounds(add)[0] and bounds(add)[2] <= bounds(editor)[0]
          and bounds(editor)[2] <= bounds(send)[0] and bounds(send)[2] <= bounds(minimize)[0],
          'The four chat controls sit outside the message editor in two pairs')
    tap('chat options; drag to move floating window'); tap('Position…'); tap('Floating window')
    locate('Message AI'); screenshot('chat-floating.png')
    check(True, 'A saved chat can be placed in a floating window')
    tap('chat options; drag to move floating window'); tap('Close chat')
    locate('Scriflow')

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
    title = locate('Table of contents'); add = locate('Add heading'); edit = locate('Edit headings'); close = locate('Close')
    check(all(same_row(title, control) for control in (add, edit, close)) and bounds(edit)[0] < bounds(add)[0] < bounds(close)[0],
          'Note contents places its editing and close icons in the title row')
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
    locate('Reading view'); tap('Double page'); locate('✓ Double page')
    tap('Cover, then 2–3'); locate('✓ Cover, then 2–3')
    check(True, 'Reading settings stays open and updates multiple selected options')
    screenshot('reading-settings.png')
    before = wait_json('reading-ui-state.json')['time']; shell('input', 'tap', '1300', '1500')
    wait_json('reading-ui-state.json', lambda value: value.get('editable') and value.get('time', 0) > before)
    shell('am', 'start', '-W', '-f', '0x20000000', '-n', PACKAGE + '/.notebook.NotebookReadingSelfTestActivity',
          '--ez', 'openInfo', 'true')
    title = locate('This page'); export = locate('Export PDF'); reset = locate('Reset tools'); close = locate('Close')
    check(all(same_row(title, control) for control in (export, reset, close)) and bounds(title)[0] < bounds(export)[0] < bounds(reset)[0] < bounds(close)[0],
          'Page information places its export, reset and close icons beside the title')
    screenshot('page-information.png')
    check(has_top_border('page-information.png', title), 'Page information has a visible top border above its title')
    before = wait_json('reading-ui-state.json')['time']; tap('Reset tools')
    wait_json('reading-ui-state.json', lambda value: value.get('editable') and value.get('time', 0) > before)
    check(True, 'Reset tools closes the information panel and returns to the note')
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
    tap('Files'); tap('Downloads'); tap(PDF_NAME + '.pdf')
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
    open_outline(); title = locate('Table of contents'); close = locate('Close'); add = locate('Add heading'); edit = locate('Edit headings')
    check(all(same_row(title, control) for control in (add, edit, close)), 'PDF contents uses the same compact title controls')
    screenshot('pdf-contents.png')
    before = wait_json('reading-ui-state.json')['time']; tap('Close')
    wait_json('reading-ui-state.json', lambda value: value.get('editable') and value.get('time', 0) > before)
    check(True, 'Imported PDF contents uses the same working compact outline dialog')
    shell('am', 'start', '-W', '-f', '0x20000000', '-n', PACKAGE + '/.notebook.NotebookReadingSelfTestActivity',
          '--ez', 'openReading', 'true')
    locate('Reading view'); check(not any(n.get('content-desc') == 'Close reading settings' for n in tree().iter('node')), 'PDF reading settings has no redundant close button')
    tap_choice('Double page'); locate('✓ Double page'); tap_choice('Cover, then 2–3'); locate('✓ Cover, then 2–3')
    check(True, 'PDF reading settings accepts multiple changes without dismissing')
    tap_choice('Single page'); tap_choice('Pages 1–2'); tap_choice('Off')
    before = wait_json('reading-ui-state.json')['time']; shell('input', 'tap', '1300', '1500')
    wait_json('reading-ui-state.json', lambda value: value.get('editable') and value.get('time', 0) > before)
    shell('am', 'start', '-W', '-f', '0x20000000', '-n', PACKAGE + '/.notebook.NotebookReadingSelfTestActivity',
          '--ez', 'openReading', 'true')
    locate('✓ Single page'); locate('✓ Pages 1–2'); locate('✓ Off')
    tap_choice('Double page'); tap_choice('Cover, then 2–3'); tap_choice('Manual…')
    # Crop uses a non-focusable popup, so UI Automator cannot enumerate its controls.
    crop = wait_json('reading-ui-state.json', lambda value: value.get('cropWidth', 0) > 0)
    x = round(crop['cropLeft'] + crop['cropWidth'] - 54 * crop['density'])
    y = round(crop['cropTop'] + crop['cropHeight'] - 23 * crop['density'])
    before = crop['time']; shell('input', 'tap', str(x), str(y))
    wait_json('reading-ui-state.json', lambda value: value.get('editable') and value.get('time', 0) > before)
    shell('am', 'start', '-W', '-f', '0x20000000', '-n', PACKAGE + '/.notebook.NotebookReadingSelfTestActivity',
          '--ez', 'openReading', 'true')
    locate('✓ Double page'); locate('✓ Cover, then 2–3'); locate('✓ Off')
    check(True, 'Canceling a crop preserves other choices and the previous crop mode')
    before = wait_json('reading-ui-state.json')['time']; shell('input', 'tap', '1300', '1500')
    wait_json('reading-ui-state.json', lambda value: value.get('editable') and value.get('time', 0) > before)
    shell('am', 'start', '-W', '-f', '0x20000000', '-n', PACKAGE + '/.notebook.NotebookReadingSelfTestActivity',
          '--ez', 'openInfo', 'true')
    title = locate('This page'); locate('Export PDF'); locate('Reset tools'); locate('Close')
    screenshot('pdf-page-information.png')
    check(has_top_border('pdf-page-information.png', title), 'PDF page information has a visible top border')
    tap('Export PDF'); locate('Save here')
    check(True, 'The PDF page information export icon opens the in-app save picker')
    remove_notebook(PDF_NAME, pdf_id)
    pdf_id = 0
    (OUTPUT / 'result.json').write_text(json.dumps({'passed': True, 'checks': CHECKS}, indent=2) + '\n')
    print('Compact dialog Android UI:', len(CHECKS), 'checks passed.', flush=True)
except BaseException:
    (OUTPUT / 'failure-screen.png').write_bytes(adb('exec-out', 'screencap', '-p', check=False))
    (OUTPUT / 'failure-logcat.txt').write_bytes(adb('logcat', '-d', '-t', '1500', check=False))
    raise
finally:
    if notebook_id:
        remove_notebook(NOTE_NAME, notebook_id)
    if pdf_id:
        remove_notebook(PDF_NAME, pdf_id)
    shell('rm', '-f', '/sdcard/Download/' + PDF_NAME + '.pdf', check=False)
    shell('run-as', PACKAGE, 'rm', '-f', CHAT + '.chat', CHAT + '.chat.tmp', CHAT + '.gone', SAVED + '.chat', SAVED + '.chat.tmp', SAVED + '.gone', check=False)
    shell('rm', '-f', REMOTE + '.xml', REMOTE + '.chat', REMOTE + '.saved', check=False)
