#!/usr/bin/env python3
"""Exercise collection layouts and browser Home on a disposable Android emulator."""
import base64
import io
import json
from pathlib import Path
import re
import shlex
import struct
import subprocess
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET
from PIL import Image

PACKAGE = 'org.eink.inkprobe'
TOKEN = 'CollectionUI' + uuid.uuid4().hex[:8]
REMOTE = '/data/local/tmp/' + TOKEN
FIXTURE = '/sdcard/Download/' + TOKEN
PREFS = ('notebookSettings', 'collection-layout', 'browser-tabs', 'browser-ui')
CHECKS = []


def adb(*args, check=True, data=None):
    return subprocess.run(['adb', *args], input=data, capture_output=True, check=check).stdout


def shell(*args, check=True):
    return adb('shell', shlex.join(args), check=check).decode(errors='replace').strip()


def tree():
    shell('uiautomator', 'dump', REMOTE + '.xml')
    return ET.fromstring(adb('exec-out', 'cat', REMOTE + '.xml'))


def locate(predicate, seconds=20):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        root = tree()
        for node in root.iter('node'):
            if predicate(node):
                return root, node
        time.sleep(.2)
    raise AssertionError('Control did not appear')


def named(value):
    return lambda n: n.get('text') == value or n.get('content-desc') == value


def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds', ''))))


def tap(predicate, hold=False):
    _, node = locate(predicate); x1, y1, x2, y2 = bounds(node)
    x, y = str((x1 + x2) // 2), str((y1 + y2) // 2)
    if hold: shell('input', 'swipe', x, y, x, y, '800')
    else: shell('input', 'tap', x, y)


def check(condition, message):
    if not condition: raise AssertionError(message)
    CHECKS.append(message); print('PASS:', message, flush=True)


def status():
    return bounds(locate(lambda n: 'battery ' in n.get('content-desc', '') and 'Wi-Fi' in n.get('content-desc', ''))[1])


def choose_orientation(name):
    tap(lambda n: 'battery ' in n.get('content-desc', '') and 'Wi-Fi' in n.get('content-desc', ''))
    locate(named('Settings'))
    tap(lambda n: n.get('content-desc', '') in (name, name + ', selected'))
    locate(named('Done')); tap(named('Done'))


def pref(name):
    result = subprocess.run(['adb', 'exec-out', 'run-as', PACKAGE, 'cat', 'shared_prefs/' + name + '.xml'], capture_output=True)
    return result.stdout if result.returncode == 0 else None


def put_private(directory, data, local):
    local.write_bytes(data); adb('push', str(local), REMOTE + '.data')
    shell('run-as', PACKAGE, 'cp', REMOTE + '.data', directory)
    shell('rm', '-f', REMOTE + '.data')


def set_pref(name, data, local):
    path = 'shared_prefs/' + name + '.xml'
    if data is None: shell('run-as', PACKAGE, 'rm', '-f', path)
    else: put_private(path, data, local)


def utf(value):
    raw = value.encode('ascii'); return struct.pack('>H', len(raw)) + raw


def report(after):
    end = time.monotonic() + 30
    while time.monotonic() < end:
        raw = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/browser-test.json', check=False)
        try:
            result = json.loads(raw)
            if result.get('time', 0) > after: return result
        except (ValueError, TypeError): pass
        time.sleep(.4)
    raise AssertionError('Browser self-test did not report its state')


def browser_command(description):
    shell('am', 'start', '-n', PACKAGE + '/.notebook.NotebookBrowserSelfTestActivity',
          '-f', '0x20000000', '--es', 'click', description)


if shell('getprop', 'ro.kernel.qemu') != '1':
    raise SystemExit('This check is restricted to a disposable emulator.')

original = {name: pref(name) for name in PREFS}
saved_ids = [str(uuid.uuid4()), str(uuid.uuid4())]
with tempfile.TemporaryDirectory(prefix='scriflow-collection-') as temporary:
    local = Path(temporary) / 'data'
    try:
        shell('am', 'force-stop', PACKAGE)
        shell('mkdir', '-p', FIXTURE)
        png = Path(temporary) / 'a-image.png'; Image.new('RGB', (200, 100), (0, 0, 0)).save(png)
        adb('push', str(png), FIXTURE + '/a-image.png')
        local.write_text('fixture'); adb('push', str(local), FIXTURE + '/b-note.txt')
        pdf = Path(temporary) / 'c-paper.pdf'; Image.new('RGB', (200, 100), (0, 0, 0)).save(pdf, 'PDF')
        adb('push', str(pdf), FIXTURE + '/c-paper.pdf')
        local.write_text('fixture'); adb('push', str(local), FIXTURE + '/d-book.epub')
        for i, identity in enumerate(saved_ids):
            title = TOKEN + (' A' if i == 0 else ' B')
            html = b'<!doctype html><!-- saved by scriflow --><html><body>Offline fixture</body></html>'
            metadata = struct.pack('>I', 0x4e575332) + utf(title) + utf('https://example.invalid/' + identity)
            metadata += struct.pack('>qqi', int(time.time() * 1000) + i * 1000, len(html), 0)
            path = 'files/websites/' + identity
            shell('run-as', PACKAGE, 'mkdir', '-p', path)
            put_private(path + '/website.nws', metadata, local); put_private(path + '/page.html', html, local)

        shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookLibraryActivity')
        locate(named('Files')); choose_orientation('Portrait'); locate(named('Files'))
        shelf_status = status(); tap(named('Files')); locate(named('Files'))
        check(status() == shelf_status, 'Bookshelf and Files use the same top-right status position')
        root = tree()
        check(any(named('Folder')(n) for n in root.iter('node')), 'Storage locations use folder icons')
        check(not any(n.get('class') == 'android.widget.EditText' for n in root.iter('node')),
              'The search field is hidden while browsing')
        check(not any('items · Hold to select' in n.get('text', '') or n.get('text') == 'Choose storage or a common folder'
                      for n in root.iter('node')), 'The normal file view has no instructional footer')
        screenshot = Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).convert('RGB')
        screenshot.save('build/file-manager-polish-controls.png')
        controls = ('Bookshelf', 'Parent folder', 'Storage locations', 'Search files', 'File options')
        centered = True
        for control in controls:
            node = next(n for n in root.iter('node') if named(control)(n))
            x1, y1, x2, y2 = bounds(node); inset = min(x2 - x1, y2 - y1) // 6
            points = [(x, y) for y in range(y1 + inset, y2 - inset) for x in range(x1 + inset, x2 - inset)
                      if max(screenshot.getpixel((x, y))) < 96]
            if not points: centered = False; continue
            center_x = (min(p[0] for p in points) + max(p[0] for p in points)) / 2
            center_y = (min(p[1] for p in points) + max(p[1] for p in points)) / 2
            if abs(center_x - (x1 + x2) / 2) > 5 or abs(center_y - (y1 + y2) / 2) > 5: centered = False
        check(centered, 'Icon-only file-manager controls draw their symbols at the button center')
        search_button = bounds(locate(named('Search files'))[1]); tap(named('Search files'))
        search_field = bounds(locate(named('Search files by name'))[1])
        check(abs((search_field[1] + search_field[3]) - (search_button[1] + search_button[3])) <= 4,
              'Search opens in the existing navigation row')
        shell('input', 'text', 'draft')
        choose_orientation('Landscape')
        _, draft = locate(named('Search files by name'))
        check(draft.get('text') == 'draft', 'An unsubmitted search survives rotation')
        choose_orientation('Portrait'); locate(named('Search files by name'))
        tap(named('Close search'))
        check(not any(n.get('class') == 'android.widget.EditText' for n in tree().iter('node')),
              'Closing search returns to the compact toolbar')
        tap(named('Downloads')); tap(named(TOKEN)); locate(named('a-image.png'))
        root = tree()
        if any(named('Show files as list')(n) for n in root.iter('node')): tap(named('Show files as list'))
        locate(named('PNG file')); locate(named('PDF file')); locate(named('EPUB file'))
        check(True, 'List icons distinguish image, PDF and EPUB files')
        tap(named('Search files')); shell('input', 'text', 'a-image'); tap(named('Search files'))
        filtered = False
        for _ in range(15):
            found = list(tree().iter('node'))
            if any(named('a-image.png')(n) for n in found) and not any(named('b-note.txt')(n) for n in found):
                filtered = True; break
            time.sleep(.2)
        check(filtered, 'Search filters the current folder')
        tap(named('Close search')); locate(named('b-note.txt'))
        check(True, 'Closing search restores the unfiltered folder')
        a = bounds(locate(named('a-image.png'))[1]); b = bounds(locate(named('b-note.txt'))[1])
        check(a[1] < b[1], 'Portrait file list uses one column')
        tap(named('Show file thumbnails')); root, a_node = locate(named('a-image.png'))
        locate(named('PNG file')); locate(named('PDF file')); locate(named('EPUB file'))
        check(True, 'Thumbnail cards retain their file-type badges')
        a, b = bounds(a_node), bounds(locate(named('b-note.txt'))[1])
        check(a[0] < b[0] and a[1] == b[1], 'File thumbnails use a multi-column grid')
        parents = {child: parent for parent in root.iter() for child in parent}
        samples = {}
        for filename in ('a-image.png', 'c-paper.pdf'):
            node = next(n for n in root.iter('node') if named(filename)(n))
            left, top, right, bottom = bounds(parents[parents[node]])
            samples[filename] = (left + (right - left) // 4, (top + bounds(node)[1]) // 2)
        shown = set()
        for _ in range(12):
            screenshot = Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).convert('RGB')
            for filename, point in samples.items():
                if max(screenshot.getpixel(point)) < 80: shown.add(filename)
            if len(shown) == len(samples): break
            time.sleep(.5)
        check('a-image.png' in shown, 'A local image displays actual thumbnail pixels')
        check('c-paper.pdf' in shown, 'A local PDF displays actual first-page thumbnail pixels')
        screenshot.save('build/file-manager-polish-thumbnails.png')
        tap(named('a-image.png'), hold=True); locate(named('1 selected')); tap(named('Cancel selection'))
        check(True, 'Thumbnail cards retain file selection')
        choose_orientation('Landscape'); locate(named('a-image.png'))
        check(any(named('Show files as list')(n) for n in tree().iter('node')), 'File thumbnail choice survives rotation')
        tap(named('Show files as list'))
        a = bounds(locate(named('a-image.png'))[1]); b = bounds(locate(named('b-note.txt'))[1])
        check(a[0] < b[0] and a[1] == b[1], 'Landscape file list uses two columns')
        landscape_status = status(); tap(named('Bookshelf')); locate(named('Open folder Saved websites'))
        check(status() == landscape_status, 'Landscape bookshelf and Files align the status position')
        tap(named('Open folder Saved websites')); locate(named(TOKEN + ' B'))
        check(status() == landscape_status, 'Saved websites aligns the same status position')
        root = tree()
        if any(named('Show saved websites as list')(n) for n in root.iter('node')): tap(named('Show saved websites as list'))
        a = bounds(locate(named(TOKEN + ' A'))[1]); b = bounds(locate(named(TOKEN + ' B'))[1])
        check(a[0] != b[0] and a[1] == b[1], 'Landscape saved-page list uses two columns')
        tap(named('Show saved website thumbnails')); locate(named('Read offline: ' + TOKEN + ' A'))
        check(True, 'Saved pages switch to thumbnail cards')
        choose_orientation('Portrait'); locate(named('Read offline: ' + TOKEN + ' A'))
        check(any(named('Show saved websites as list')(n) for n in tree().iter('node')), 'Saved-page thumbnail choice survives rotation')

        shell('am', 'force-stop', PACKAGE)
        one, two = str(uuid.uuid4()), str(uuid.uuid4())
        encoded = struct.pack('>I', 0x4e425431) + utf(one) + struct.pack('>I', 2)
        encoded += utf(one) + utf('https://example.invalid/one') + utf('OpenOne') + b'\x00'
        encoded += utf(two) + utf('https://example.invalid/two') + utf('OpenTwo') + b'\x00'
        xml = ('<?xml version="1.0" encoding="utf-8" standalone="yes"?><map><string name="tabs">'
               + base64.b64encode(encoded).decode() + '</string></map>').encode()
        set_pref('browser-tabs', xml, local)
        before = int(time.time() * 1000)
        shell('am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookBrowserSelfTestActivity', '--ez', 'softwareInk', 'true')
        result = report(before)
        check(result['home'] and result['tabCount'] == 3 and 'Open tabs' in result['nativeLabels'], 'Browser Home shows existing tabs without opening them')
        browser_command('Open tab OpenOne'); result = report(result['time'])
        check(not result['home'] and result['selectedTab'] == one, 'A Home tab card opens its existing tab')
        if result.get('toolboxCollapsed'):
            browser_command('Expand browser toolbox; drag to move'); result = report(result['time'])
        browser_command('Home'); result = report(result['time'])
        check(result['home'], 'Home keeps the current website available')
        title = next(tab['title'] for tab in result['tabs'] if tab['id'] == one)
        browser_command('Close tab ' + title); result = report(result['time'])
        check(result['home'] and result['tabCount'] == 2 and result['selectedTab'] != one, 'Closing the current tab from Home leaves Home open')
        browser_command('New tab'); result = report(result['time'])
        check(result['home'] and result['tabCount'] == 3, 'The new Home shortcut opens a fresh tab')
        print('Collection Android UI:', len(CHECKS), 'checks passed.', flush=True)
    finally:
        shell('am', 'force-stop', PACKAGE, check=False)
        for name, value in original.items(): set_pref(name, value, local)
        for identity in saved_ids: shell('run-as', PACKAGE, 'rm', '-rf', 'files/websites/' + identity, check=False)
        shell('rm', '-rf', FIXTURE, check=False); shell('rm', '-f', REMOTE + '.xml', REMOTE + '.data', check=False)
