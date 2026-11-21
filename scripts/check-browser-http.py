#!/usr/bin/env python3
"""Exercise explicit HTTP navigation and offline capture on a disposable emulator."""
import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import threading
import time
import uuid

PACKAGE = 'org.eink.inkprobe'
ACTIVITY = PACKAGE + '/.notebook.NotebookBrowserSelfTestActivity'
TOKEN = 'HttpFixture' + uuid.uuid4().hex[:10]
REMOTE = '/data/local/tmp/' + TOKEN
LIVE = 'http://www.incompleteideas.net/IncIdeas/BitterLesson.html'
FILES = ('shared_prefs/browser-tabs.xml', 'shared_prefs/browser-ui.xml',
         'shared_prefs/notebookSettings.xml', 'files/browser-library.nbr',
         'files/browser-library.nbr.bak', 'files/browser-library.nbr.tmp', 'files/browser-test.json')


def adb(*args, check=True):
    return subprocess.run(['adb', '-s', 'emulator-5554', *args], capture_output=True, check=check).stdout


def shell(*args):
    return adb('shell', shlex.join(args)).decode(errors='replace').strip()


def private(path):
    process = subprocess.run(['adb', '-s', 'emulator-5554', 'exec-out', 'run-as', PACKAGE, 'cat', path], capture_output=True)
    return process.stdout if process.returncode == 0 else None


def entries():
    output = subprocess.run(['adb', '-s', 'emulator-5554', 'shell', 'run-as', PACKAGE, 'ls', 'files/websites'], capture_output=True)
    return set(re.findall(r'\b[a-f0-9-]{36}\b', output.stdout.decode(errors='replace')))


def command(**extras):
    args = ['am', 'start', '-n', ACTIVITY, '-f', '0x20000000']
    for key, value in extras.items():
        args.extend(['--ez' if isinstance(value, bool) else '--es', key, str(value).lower() if isinstance(value, bool) else value])
    shell(*args)


def report(predicate, timeout=60):
    end = time.monotonic() + timeout
    last = None
    while time.monotonic() < end:
        try:
            last = json.loads(private('files/browser-test.json') or b'{}')
            if predicate(last): return last
        except (TypeError, ValueError): pass
        time.sleep(.5)
    raise AssertionError('Browser did not reach the expected document: ' + json.dumps(last))


class Fixture(BaseHTTPRequestHandler):
    requests = []
    def do_GET(self):
        self.requests.append(self.path)
        if self.path == '/image.svg':
            content = b'<svg xmlns="http://www.w3.org/2000/svg" width="30" height="30"><rect width="30" height="30"/></svg>'
            self.send_response(200); self.send_header('Content-Type', 'image/svg+xml')
        else:
            page = 'two' if self.path.endswith('/two') else 'one'
            target = 'one' if page == 'two' else 'two'
            content = ('<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">'
                       '<title>' + TOKEN + ' ' + page + '</title><body style="margin:0">'
                       '<a href="/' + TOKEN + '/' + target + '" style="display:block;height:500px;font-size:32px">'
                       'Open the other HTTP page</a><p>Offline fixture text</p><img src="/image.svg"></body>').encode()
            self.send_response(200); self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', str(len(content))); self.end_headers(); self.wfile.write(content)
    def log_message(self, *_): pass


def run(live):
    if shell('getprop', 'ro.kernel.qemu') != '1': raise SystemExit('This check requires a disposable emulator.')
    saved = {path: private(path) for path in FILES}; before = entries()
    server = ThreadingHTTPServer(('127.0.0.1', 0), Fixture)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    base = 'http://10.0.2.2:' + str(server.server_port) + '/' + TOKEN
    try:
        shell('am', 'force-stop', PACKAGE)
        shell('run-as', PACKAGE, 'rm', '-f', 'files/browser-test.json')
        shell('am', 'start', '-W', '-n', ACTIVITY, '--es', 'websiteUrl', LIVE if live else base + '/one', '--ez', 'softwareInk', 'true')
        if live:
            result = report(lambda value: value.get('title') == 'The Bitter Lesson' and value.get('url', '').startswith(LIVE))
            assert result.get('bodyLength', 0) > 1000, result
            print('PASS: supplied live HTTP article loads and renders in Gecko', flush=True)
            shell('am', 'force-stop', PACKAGE)
            shell('run-as', PACKAGE, 'rm', '-f', 'files/browser-test.json')
            shell('am', 'start', '-W', '-n', ACTIVITY, '--es', 'websiteUrl', base + '/one', '--ez', 'softwareInk', 'true')
        first = report(lambda value: value.get('title') == TOKEN + ' one' and value.get('brokenImages') == 0)
        assert first.get('url') == base + '/one', first
        print('PASS: explicit HTTP address and image load', flush=True)
        shell('input', 'tap', str(first.get('pageWidth', 1404) - 100), str(min(700, first.get('pageHeight', 1872) // 3)))
        second = report(lambda value: value.get('title') == TOKEN + ' two' and value.get('brokenImages') == 0)
        assert second.get('url') == base + '/two', second
        print('PASS: tapping an HTTP link opens the next page', flush=True)
        if second.get('toolboxCollapsed'):
            grip = next(control for control in second.get('toolboxControls', []) if control.get('name', '').startswith('Expand browser toolbox'))
            shell('input', 'tap', str(grip['x'] + grip['width'] // 2), str(grip['y'] + grip['height'] // 2))
            command(click='refresh-test-report')
            second = report(lambda value: value.get('url') == base + '/two' and not value.get('toolboxCollapsed'))
        assert any(control.get('name') == 'Unencrypted HTTP connection' for control in second.get('toolboxControls', [])), second
        print('PASS: the address controls identify the unencrypted connection', flush=True)
        command(click='Save page for offline reading')
        end = time.monotonic() + 60; created = set()
        while time.monotonic() < end:
            created = entries() - before
            complete = [key for key in created if private('files/websites/' + key + '/website.nws')]
            if complete: break
            time.sleep(.5)
        assert len(complete) == 1, 'HTTP page was not published as an offline snapshot'
        snapshot = private('files/websites/' + complete[0] + '/page.html') or b''
        assert TOKEN.encode() in snapshot and b'data:image/svg+xml;base64,' in snapshot, 'HTTP image or content was not embedded'
        print('PASS: HTTP page and image are captured for offline reading', flush=True)
        server.shutdown(); server.server_close(); server = None
        command(openSaved=complete[0])
        offline = report(lambda value: value.get('offline') and value.get('pageReady') and value.get('nativeUrl', '').startswith('file:'))
        assert complete[0] in offline.get('nativeUrl', ''), offline
        print('PASS: saved HTTP page reopens with its image while the server is stopped', flush=True)
    finally:
        if server is not None: server.shutdown(); server.server_close()
        shell('am', 'force-stop', PACKAGE)
        for key in entries() - before:
            if re.fullmatch(r'[a-f0-9-]{36}', key): shell('run-as', PACKAGE, 'rm', '-rf', 'files/websites/' + key)
        with tempfile.TemporaryDirectory(prefix='scriflow-http-restore-') as directory:
            for path, data in saved.items():
                if data is None: shell('run-as', PACKAGE, 'rm', '-f', path)
                else:
                    local = Path(directory) / 'saved'; local.write_bytes(data)
                    adb('push', str(local), REMOTE)
                    shell('run-as', PACKAGE, 'cp', REMOTE, path)
        shell('rm', '-f', REMOTE)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--live', action='store_true', help='Also load the reported public article')
    run(parser.parse_args().live)
