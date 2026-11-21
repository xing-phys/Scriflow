#!/usr/bin/env python3
"""Repeated real ebook gestures on an emulator, retaining crash logs on failure."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED, ZIP_STORED
import hashlib
import json
import os
import re
import struct
import subprocess
import time
import uuid
import zlib

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'build/ebook-turn-check'
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = 'org.eink.inkprobe'
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
FIXTURE = OUT / 'turns.epub'
REMOTE = '/data/local/tmp/scriflow-turns-' + uuid.uuid4().hex + '.epub'
ENTRY = PACKAGE + '/.notebook.NotebookEbookSelfTestActivity'


def adb(*args, check=True, timeout=30):
    return subprocess.run(['adb', '-s', SERIAL, *args], capture_output=True, check=check, timeout=timeout)


def make_fixture():
    # A unique identity makes cleanup safe even if this script is run repeatedly.
    identity = uuid.uuid4().hex
    items = ''.join(f'<item id="c{i}" href="c{i}.xhtml" media-type="application/xhtml+xml"/>' for i in range(70))
    spine = ''.join(f'<itemref idref="c{i}"/>' for i in range(70))
    nav = ''.join(f'<li><a href="c{i}.xhtml">Section {i}</a></li>' for i in range(70))
    with ZipFile(FIXTURE, 'w') as book:
        book.writestr('mimetype', 'application/epub+zip', compress_type=ZIP_STORED)
        book.writestr('META-INF/container.xml', '<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>')
        book.writestr('OPS/book.opf', f'<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:scriflow:turns:{identity}</dc:identifier><dc:title>Page turn stress fixture</dc:title><dc:language>en</dc:language></metadata><manifest><item id="nav" href="nav.xhtml" properties="nav" media-type="application/xhtml+xml"/>{items}</manifest><spine>{spine}</spine></package>')
        book.writestr('OPS/nav.xhtml', f'<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol>{nav}</ol></nav></body></html>')
        for i in range(70):
            prose = ''.join(f'<p id="p{j}">Section {i}, paragraph {j}. Quantum fluctuations, entanglement and transport evolve through this reading test.</p>' for j in range(6))
            book.writestr(f'OPS/c{i}.xhtml', f'<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Section {i}</title></head><body><h1>Section {i}</h1>{prose}</body></html>', compress_type=ZIP_DEFLATED)
    return hashlib.sha256(FIXTURE.read_bytes() + b'epub').hexdigest()


def saved(book):
    raw = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/ebooks/' + book + '/state').stdout
    magic, size = struct.unpack('>II', raw[:8]); data = raw[8:8+size]
    if magic != 0x45424b31 or len(raw) != size+12 or zlib.crc32(data) != struct.unpack('>I', raw[-4:])[0]:
        raise AssertionError('The reading state frame is invalid')
    return json.loads(data)


def wait_until(predicate, message, seconds=40):
    until = time.monotonic() + seconds
    while time.monotonic() < until:
        result = predicate()
        if result:
            return result
        time.sleep(.25)
    raise AssertionError(message)


def reader_ready():
    raw = adb('exec-out', 'run-as', PACKAGE, 'cat', 'cache/ebook-reader-check.json', check=False)
    try:
        result = json.loads(raw.stdout)
        if not result.get('passed'):
            raise AssertionError('The ebook failed to open: ' + str(result))
        return result
    except (ValueError, TypeError):
        return None


def foreground(pid):
    current = adb('shell', 'pidof', PACKAGE, check=False).stdout.strip()
    activity = adb('shell', 'dumpsys', 'activity', 'activities').stdout
    if current != pid or not re.search(rb'mResumedActivity:.*NotebookEbookSelfTestActivity', activity):
        raise AssertionError('The ebook process or foreground activity changed during a page turn')


def reader_errors():
    logs = adb('logcat', '-d', '-v', 'brief', '-s', 'GeckoConsole:E', check=False).stdout.decode(errors='replace')
    errors = [line for line in logs.splitlines() if 'JavaScript Error:' in line and '/reader.js' in line]
    if errors:
        raise AssertionError('The reader reported an uncaught page-turn error: ' + errors[-1])


if adb('shell', 'getprop', 'ro.kernel.qemu').stdout.strip() != b'1':
    raise SystemExit('This test uses disposable emulator data only.')
book = make_fixture()
try:
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('logcat', '-c')
    adb('push', str(FIXTURE), REMOTE)
    adb('shell', 'run-as', PACKAGE, 'mkdir', '-p', 'cache/ebook-check-input')
    adb('shell', 'run-as', PACKAGE, 'cp', REMOTE, 'cache/ebook-check-input/turns.epub')
    adb('shell', 'run-as', PACKAGE, 'rm', '-f', 'cache/ebook-reader-check.json')
    adb('shell', 'wm', 'dismiss-keyguard')
    adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', ENTRY, '--es', 'fixture', 'turns.epub', '--es', 'mode', 'read')
    result = wait_until(reader_ready, 'The test book did not finish opening')
    assert result['book'] == book
    pid = adb('shell', 'pidof', PACKAGE).stdout.strip()
    foreground(pid)
    size = adb('shell', 'wm', 'size').stdout.decode()
    width, height = map(int, re.findall(r'(\d+)x(\d+)', size)[-1])
    before = saved(book).get('location', '')
    steps = 0
    for cycle in range(int(os.environ.get('EBOOK_TEST_CYCLES', '3'))):
        for forward, count in ((True, 18), (False, 9)):
            for index in range(count):
                if index % 4 == 3:
                    start, end = (int(width*.8), int(width*.2)) if forward else (int(width*.2), int(width*.8))
                    adb('shell', 'input', 'swipe', str(start), str(int(height*.5)), str(end), str(int(height*.5)), '220')
                else:
                    x, y = (int(width*.8), int(height*.45)) if forward else (int(width*.15), int(height*.18))
                    adb('shell', 'input', 'tap', str(x), str(y))
                steps += 1
                if steps % 5 == 0:
                    foreground(pid)
                    reader_errors()
                time.sleep(.18)
            time.sleep(1)
            after = saved(book).get('location', '')
            if not after or after == before:
                raise AssertionError(f'Reading position did not change in cycle {cycle+1}; the reader may have stalled')
            before = after
            print(f'PASS: {steps} taps/swipes kept the ebook active and advanced its saved position', flush=True)
    foreground(pid)
    (OUT/'result.json').write_text(json.dumps({'passed': True, 'gestures': steps, 'book': book}, indent=2)+'\n')
    print(f'Android ebook page turns: {steps} gestures passed.', flush=True)
except Exception:
    (OUT/'failure-logcat.txt').write_bytes(adb('logcat', '-d', '-v', 'threadtime', check=False).stdout)
    (OUT/'failure-activities.txt').write_bytes(adb('shell', 'dumpsys', 'activity', 'activities', check=False).stdout)
    raise
finally:
    adb('shell', 'am', 'force-stop', PACKAGE, check=False)
    adb('shell', 'run-as', PACKAGE, 'rm', '-rf', 'files/ebooks/' + book, check=False)
    adb('shell', 'run-as', PACKAGE, 'rm', '-f', 'cache/ebook-check-input/turns.epub', check=False)
    adb('shell', 'rm', '-f', REMOTE, check=False)
