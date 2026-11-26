#!/usr/bin/env python3
"""Real Gecko POST downloads, collision handling, progress and cancellation on a disposable emulator."""
import json
from pathlib import Path
import re
import shlex
import subprocess
import threading
import time
import uuid
import xml.etree.ElementTree as ET
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SERIAL = 'emulator-5554'
PACKAGE = 'org.eink.inkprobe'
ACTIVITY = PACKAGE + '/.notebook.NotebookBrowserSelfTestActivity'
TOKEN = 'ScriflowDownloadTest' + uuid.uuid4().hex[:10]
DATA = (TOKEN.encode() + b'\x00\xff') * 18000
NAME = TOKEN + '.bin'
REQUESTS = []


def adb(*args, check=True):
    return subprocess.run(['adb', '-s', SERIAL, *args], capture_output=True, check=check).stdout


def shell(*args, check=True):
    return adb('shell', shlex.join(args), check=check).decode(errors='replace').strip()


def private(path):
    result = subprocess.run(['adb', '-s', SERIAL, 'exec-out', 'run-as', PACKAGE, 'cat', path], capture_output=True)
    return result.stdout if result.returncode == 0 else b''


def command(**extras):
    args = ['am', 'start', '-n', ACTIVITY, '-f', '0x20000000']
    for key, value in extras.items():
        args += ['--ez' if isinstance(value, bool) else '--es', key, str(value).lower() if isinstance(value, bool) else str(value)]
    shell(*args)


def report(predicate, timeout=60):
    end = time.monotonic() + timeout
    last = {}
    while time.monotonic() < end:
        try:
            last = json.loads(private('files/browser-test.json') or b'{}')
            if predicate(last): return last
        except ValueError: pass
        time.sleep(.3)
    raise AssertionError('Browser did not reach expected state: ' + json.dumps(last))


def items():
    try:
        root = ET.fromstring(private('shared_prefs/browser-downloads.xml'))
        return [json.loads(node.text) for node in root if node.tag == 'string' and node.text and TOKEN in node.text]
    except (ValueError, ET.ParseError): return []


def wait_items(predicate, timeout=45):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        result = items()
        if predicate(result): return result
        time.sleep(.25)
    raise AssertionError('Download did not reach expected state: ' + repr(items()))


def tap_download():
    # The page consists of one large form button; the floating controls are on the left.
    shell('input', 'tap', '1000', '600')


def tap_control(description, timeout=20):
    end = time.monotonic() + timeout
    target = '/sdcard/' + TOKEN + '.xml'
    while time.monotonic() < end:
        shell('rm', '-f', target, check=False)
        shell('uiautomator', 'dump', target, check=False)
        try:
            root = ET.fromstring(adb('exec-out', 'cat', target, check=False))
            for node in root.iter('node'):
                if node.get('content-desc') == description or node.get('text') == description:
                    bounds = [int(x) for x in re.findall(r'\d+', node.get('bounds', ''))]
                    if len(bounds) == 4:
                        shell('input', 'tap', str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
                        return
        except ET.ParseError: pass
        time.sleep(.3)
    shown = []
    try:
        for node in root.iter('node'):
            value = node.get('content-desc', '') or node.get('text', '')
            if value in ('Downloads', 'Close downloads', 'Cancel download') or value.startswith('Actions for download '): shown.append(value)
    except UnboundLocalError: pass
    Path('build/browser-download-failure.png').write_bytes(adb('exec-out', 'screencap', '-p', check=False))
    Path('build/browser-download-failure.xml').write_bytes(adb('exec-out', 'cat', target, check=False))
    raise AssertionError('Control not found: ' + description + '; visible download controls: ' + repr(shown))


class Fixture(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path not in ('/normal', '/slow'):
            self.send_error(404); return
        page = ('<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">'
                '<title>' + TOKEN + self.path + '</title><body style="margin:0">'
                '<form action="/download' + self.path + '" method="post">'
                '<input name="token" value="one-use"><button style="width:100%;height:450px;font-size:32px">Download</button>'
                '</form></body>').encode()
        self.send_response(200); self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Set-Cookie', 'scriflow-test=present; Path=/')
        self.send_header('Content-Length', str(len(page))); self.end_headers(); self.wfile.write(page)

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        REQUESTS.append((self.path, self.headers.get('Cookie', ''), body))
        slow_count = sum(1 for path, _, _ in REQUESTS if path.endswith('/slow'))
        payload = DATA if self.path.endswith('/normal') else DATA * (5 if slow_count == 1 else 30)
        self.send_response(200); self.send_header('Content-Type', 'application/octet-stream')
        self.send_header('Content-Disposition', 'attachment; filename="' + NAME + '"')
        self.send_header('Content-Length', str(len(payload))); self.end_headers()
        try:
            if self.path.endswith('/normal'): self.wfile.write(payload)
            else:
                for pos in range(0, len(payload), 16384):
                    self.wfile.write(payload[pos:pos + 16384]); self.wfile.flush(); time.sleep(.04)
        except (BrokenPipeError, ConnectionResetError): pass

    def log_message(self, *_): pass


def main():
    if shell('getprop', 'ro.kernel.qemu') != '1': raise SystemExit('This test is restricted to the disposable emulator.')
    server = ThreadingHTTPServer(('127.0.0.1', 0), Fixture)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    base = 'http://10.0.2.2:' + str(server.server_port)
    try:
        # The AVD is launched with -read-only; start from a clean test profile.
        shell('pm', 'clear', PACKAGE)
        shell('pm', 'grant', PACKAGE, 'android.permission.RECORD_AUDIO')
        shell('pm', 'revoke', PACKAGE, 'android.permission.WRITE_EXTERNAL_STORAGE', check=False)
        shell('am', 'start', '-W', '-n', ACTIVITY, '--es', 'websiteUrl', base + '/normal', '--ez', 'softwareInk', 'true')
        report(lambda x: x.get('title') == TOKEN + '/normal' and x.get('pageReady'))
        tap_download()
        first = wait_items(lambda x: any(v.get('state') in ('COMPLETE', 'FAILED') for v in x))
        completed = next(v for v in first if v.get('state') in ('COMPLETE', 'FAILED'))
        assert completed['state'] == 'COMPLETE', completed
        assert completed['bytes'] == len(DATA), completed
        downloaded = adb('exec-out', 'cat', '/sdcard/Download/' + completed['name'])
        assert downloaded == DATA, 'Downloaded bytes differ from the original response'
        assert len(REQUESTS) == 1 and REQUESTS[0][0] == '/download/normal' and 'scriflow-test=present' in REQUESTS[0][1] and REQUESTS[0][2] == b'token=one-use', REQUESTS
        print('PASS: original authenticated POST response is saved exactly once without storage permission', flush=True)
        report(lambda x: x.get('url') == base + '/normal' and x.get('pageReady'))
        tap_download()
        result = wait_items(lambda x: sum(v.get('state') == 'COMPLETE' for v in x) >= 2)
        complete = [v for v in result if v.get('state') == 'COMPLETE']
        assert len({v['name'] for v in complete}) == 2, complete
        assert all(adb('exec-out', 'cat', '/sdcard/Download/' + v['name']) == DATA for v in complete), complete
        print('PASS: duplicate filenames do not overwrite existing downloads', flush=True)
        command(fixtureNavigate=base + '/slow'); report(lambda x: x.get('url') == base + '/slow' and x.get('pageReady'))
        tap_download()
        wait_items(lambda x: any(v.get('state') == 'DOWNLOADING' for v in x))
        command(showBrowserDownloads=True)
        report(lambda x: 'Downloads' in x.get('nativeLabels', []))
        print('PASS: download manager opens while a transfer is active', flush=True)
        # Activity recreation keeps the foreground service and the original transfer alive.
        command(recreate=True)
        final = wait_items(lambda x: sum(v.get('state') == 'COMPLETE' for v in x) >= 3, timeout=100)
        assert any(v['bytes'] == len(DATA) * 5 for v in final if v['state'] == 'COMPLETE'), final
        print('PASS: a download completes through activity recreation', flush=True)
        # Cancel another response from the real manager action bar.
        report(lambda x: x.get('url') == base + '/slow' and x.get('pageReady'))
        tap_download()
        current = wait_items(lambda x: any(v.get('state') == 'DOWNLOADING' for v in x))
        running = next(v for v in current if v.get('state') == 'DOWNLOADING')
        # Open through the controls a reader uses, including the collapsed toolbox.
        state = report(lambda x: x.get('url') == base + '/slow' and x.get('pageReady'))
        if state.get('toolboxCollapsed'):
            command(click='Expand browser toolbox; drag to move'); time.sleep(.5)
        command(click='Browser menu'); tap_control('Downloads')
        tap_control('Actions for download ' + running['name'])
        tap_control('Cancel download')
        canceled = wait_items(lambda x: any(v.get('state') == 'CANCELED' for v in x), timeout=45)
        assert any(v.get('state') == 'CANCELED' and not v.get('uri') for v in canceled), canceled
        print('PASS: cancel removes the incomplete destination', flush=True)
        tap_control('Close downloads')
        tap_download()
        wait_items(lambda x: any(v.get('state') == 'DOWNLOADING' for v in x))
        shell('am', 'force-stop', PACKAGE)
        shell('am', 'start', '-W', '-n', ACTIVITY, '--es', 'websiteUrl', base + '/normal', '--ez', 'softwareInk', 'true', '--ez', 'showBrowserDownloads', 'true')
        recovered = wait_items(lambda x: any(v.get('state') == 'INTERRUPTED' for v in x), timeout=60)
        assert any(v.get('state') == 'INTERRUPTED' and not v.get('uri') for v in recovered), recovered
        assert all(adb('exec-out', 'cat', '/sdcard/Download/' + v['name']) == DATA for v in recovered if v.get('state') == 'COMPLETE' and v['bytes'] == len(DATA))
        print('PASS: interrupted transfers are cleaned up without touching completed files', flush=True)
    finally:
        server.shutdown(); server.server_close()
        shell('am', 'force-stop', PACKAGE)
        for entry in items():
            name = entry.get('name', '')
            if name.startswith(TOKEN) and '/' not in name and '\\' not in name:
                shell('rm', '-f', '/sdcard/Download/' + name, check=False)
        shell('rm', '-f', '/sdcard/' + TOKEN + '.xml', check=False)
        print('Fixture files removed from disposable emulator.', flush=True)


if __name__ == '__main__': main()
