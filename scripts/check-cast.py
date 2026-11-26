#!/usr/bin/env python3
"""Android 10 emulator UI + real MediaProjection/JPEG server. Requires connected Wi-Fi.

Run after installing a debug APK: ANDROID_SERIAL=emulator-5554 python3 scripts/check-cast.py
HTTP travels through an on-device Java Socket to the bound Wi-Fi address, rather
than pretending an adb-forwarded loopback listener is a Wi-Fi server.
"""
from pathlib import Path
from io import BytesIO
import json
import os
import re
import shlex
import subprocess
import time
import urllib.parse
import xml.etree.ElementTree as ET
from PIL import Image, ImageStat

ROOT = Path(__file__).resolve().parent.parent
if (ROOT/'.tools/jdk-17.0.21+9/bin/java').exists():
    os.environ['JAVA_HOME'] = str(ROOT/'.tools/jdk-17.0.21+9')
if os.environ.get('JAVA_HOME'):
    os.environ['PATH'] = str(Path(os.environ['JAVA_HOME'])/'bin') + os.pathsep + os.environ['PATH']
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
ADB = os.environ.get('ADB', str(ROOT / '.tools/android-sdk/platform-tools/adb'))
PACKAGE = 'org.eink.inkprobe'
PASSWORD = 'Cast-Quantum-117'
checks = 0
host = None
port = 0


def adb(*args, **kwargs):
    if args and args[0] == 'shell':
        args = ('shell', shlex.join(args[1:]))
    return subprocess.run([ADB, '-s', SERIAL, *args], capture_output=True, check=True, timeout=20, **kwargs).stdout


def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/scriflow-cast-check.xml')
    return ET.fromstring(adb('exec-out', 'cat', '/sdcard/scriflow-cast-check.xml'))


def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))


def match(text):
    return lambda node: node.get('text') == text or node.get('content-desc') == text


def find(predicate, scroll=False):
    for _ in range(12):
        root = tree()
        for node in root.iter('node'):
            if predicate(node):
                return node
        if scroll:
            view = next((node for node in root.iter('node') if node.get('class') == 'android.widget.ScrollView'), None)
            if view is not None:
                x, y, xx, yy = bounds(view)
                distance = min(600, (yy-y)//2)
                adb('shell', 'input', 'swipe', str((x+xx)//2), str(yy-60), str((x+xx)//2), str(yy-60-distance), '550')
        time.sleep(.15)
    labels = [node.get('text') or node.get('content-desc') for node in root.iter('node')
              if node.get('text') or node.get('content-desc')]
    raise AssertionError('Expected cast UI control was not found. Visible: ' + ', '.join(labels[:30]))


def tap(predicate, scroll=False):
    x, y, xx, yy = bounds(find(predicate, scroll))
    adb('shell', 'input', 'tap', str((x+xx)//2), str((y+yy)//2))


def hide_keyboard():
    if b'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')


def check(condition, message):
    global checks
    assert condition, message
    checks += 1
    print('PASS: ' + message, flush=True)


def http(method, path, cookie=None, body=None):
    response = adb('shell', 'env', 'CLASSPATH=/data/local/tmp/scriflow-cast-http.jar', 'app_process', '/system/bin',
                   'org.eink.inkprobe.NotebookCastHttp', host, str(port), method, path, cookie or '-',
                   '-' if body is None else urllib.parse.urlencode(body))
    head, data = response.split(b'\r\n\r\n', 1)
    lines = head.decode().split('\r\n')
    code = int(lines[0].split()[1])
    values = dict(line.lower().split(': ', 1) for line in lines[1:])
    # Cookie token uses only lowercase hex; other headers can be normalized here.
    assert len(data) == int(values['content-length']), f"Truncated HTTP response: {code}, {len(data)} / {values['content-length']} bytes"
    return code, values, data


def frame(cookie, code=200):
    for _ in range(35):
        result = http('GET', '/frame', cookie)
        if result[0] == code:
            return result
        time.sleep(.2)
    raise AssertionError('Expected cast frame state was not reached: ' + str(result[:2]))


def attached(value):
    expected = ('capture_attached=' + str(value).lower()).encode()
    for _ in range(40):
        diagnostic = adb('shell', 'dumpsys', 'activity', 'service', PACKAGE + '/.notebook.NotebookCastService')
        if expected in diagnostic:
            return diagnostic
        time.sleep(.1)
    raise AssertionError('Capture surface did not reach ' + expected.decode())


def settings():
    tap(lambda n: n.get('content-desc', '').endswith('tap to open settings'))
    find(match('Settings'))


def start_cast():
    global host, port
    tap(lambda n: n.get('class') == 'android.widget.EditText')
    adb('shell', 'input', 'text', PASSWORD)
    hide_keyboard()
    tap(match('Start casting'))
    tap(lambda n: n.get('resource-id') == 'android:id/button1')
    address = find(lambda n: bool(re.fullmatch(r'http://\d+\.\d+\.\d+\.\d+:\d+/', n.get('text', '')))).get('text')
    parsed = urllib.parse.urlparse(address); host, port = parsed.hostname, parsed.port
    return address


if adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1':
    raise SystemExit('Casting checks require an emulator, never the owner tablet.')
client = ROOT/'build/cast-device-client'
client.mkdir(parents=True, exist_ok=True)
subprocess.run(['javac', '-source', '8', '-target', '8', '-d', str(client), str(ROOT/'tools/NotebookCastHttp.java')], check=True)
subprocess.run([str(ROOT/'.tools/android-sdk/build-tools/36.0.0/d8'), '--min-api', '29', '--lib',
                str(ROOT/'.tools/platform-29/android.jar'), '--output', str(client/'client.jar'),
                str(client/'org/eink/inkprobe/NotebookCastHttp.class')], check=True)
adb('push', str(client/'client.jar'), '/data/local/tmp/scriflow-cast-http.jar')
try:
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookLibraryActivity')
    settings(); tap(lambda n: n.get('content-desc') in ('Portrait', 'Portrait, selected'), scroll=True)
    tap(match('Done')); settings(); tap(match('Screen cast to a browser…'), scroll=True)
    find(match('Screen cast'))
    check(find(match('Start casting')) is not None, 'Settings opens the native cast controls')
    tap(lambda n: n.get('class') == 'android.widget.EditText'); adb('shell', 'input', 'text', 'short')
    hide_keyboard(); tap(match('Start casting'))
    check(find(lambda n: n.get('class') == 'android.widget.EditText').get('focused') == 'true', 'Short passwords are rejected before capture permission')
    adb('shell', 'input', 'keyevent', *(['KEYCODE_DEL'] * 5)); adb('shell', 'input', 'text', PASSWORD)
    hide_keyboard(); tap(match('Start casting'))
    tap(lambda n: n.get('resource-id') == 'android:id/button2')
    check(find(match('Start casting')) is not None, 'Denying Android capture returns to idle controls')
    tap(match('Start casting')); tap(lambda n: n.get('resource-id') == 'android:id/button1')
    address = find(lambda n: bool(re.fullmatch(r'http://\d+\.\d+\.\d+\.\d+:\d+/', n.get('text', '')))).get('text')
    parsed = urllib.parse.urlparse(address); host, port = parsed.hostname, parsed.port
    check(host != '127.0.0.1' and port > 0, 'Capture starts a server on the actual Wi-Fi address')
    ui_pid = adb('shell', 'pidof', PACKAGE).strip()
    capture_pid = adb('shell', 'pidof', PACKAGE + ':cast').strip()
    check(ui_pid and capture_pid and ui_pid != capture_pid, 'Screen readback, JPEG allocation and HTTP run outside the pen/UI process')
    check(http('GET', '/frame')[0] == 401, 'Android JPEG endpoint rejects anonymous requests')
    check(http('POST', '/login', body={'password': 'incorrect'})[0] == 401, 'Android server rejects incorrect passwords')
    code, headers, _ = http('POST', '/login', body={'password': PASSWORD})
    cookie = headers['set-cookie'].split(';')[0]
    check(code == 200 and 'httponly' in headers['set-cookie'], 'Android server authorizes an independent private browser session')
    check(http('GET', '/frame', cookie)[0] == 204, 'Cast controls remain private while the session runs')
    tap(match('Done')); tap(match('Done')); find(match('Scriflow'))
    live = frame(cookie)[2]; image = Image.open(BytesIO(live))
    check(max(image.size) == 1872 and max(ImageStat.Stat(image).stddev) > 8, 'Lecture capture retains the tablet-sized source pixels in a nonblank JPEG')
    (ROOT/'build/cast-tablet-live.jpg').write_bytes(live)
    browser = PACKAGE + '/.notebook.NotebookBrowserSelfTestActivity'
    adb('shell', 'am', 'start', '-W', '-n', browser, '--ez', 'softwareInk', 'true')
    frame(cookie); attached(True)
    adb('shell', 'am', 'start', '-W', '-f', '0x20000000', '-n', browser,
        '--ez', 'castContact', 'true', '--ez', 'castProtect', 'true')
    time.sleep(.7)
    held_diagnostic = attached(False)
    held = frame(cookie)[2]
    time.sleep(.5)
    held_count = re.search(rb'frames_encoded=(\d+)', held_diagnostic).group(1)
    held_again = frame(cookie)[2]
    held_count_again = re.search(rb'frames_encoded=(\d+)', attached(False)).group(1)
    if held_again != held or held_count_again != held_count:
        print('Protected capture diagnostics:', 'same picture=', held_again == held,
              'published frames=', held_count.decode(), 'then', held_count_again.decode(), flush=True)
    check(held_again == held and held_count_again == held_count,
          'Protected pen contact detaches capture and retains the last safe picture for viewers')
    adb('shell', 'am', 'start', '-W', '-f', '0x20000000', '-n', browser,
        '--ez', 'castContact', 'false', '--ez', 'castProtect', 'true')
    time.sleep(.7)
    attached(True); frame(cookie)
    check(True, 'Capture and live frames resume after protected contact ends')
    adb('shell', 'am', 'start', '-W', '-f', '0x20000000', '-n', browser, '--es', 'click', 'Bookshelf')
    find(match('Scriflow')); live = frame(cookie)[2]
    time.sleep(3.5)
    diagnostic = adb('shell', 'dumpsys', 'activity', 'service', PACKAGE + '/.notebook.NotebookCastService')
    check(b'capture_attached=false' in diagnostic, 'Capture detaches when all viewers stop requesting frames')
    tap(match('+ Folder'))
    fresh = False
    for _ in range(30):
        result = http('GET', '/frame', cookie)
        if result[0] == 200 and result[2] != live:
            fresh = True; break
        time.sleep(.1)
    check(fresh, 'An idle capture wakes with current app-popup content instead of stalling on a stored image')
    tap(lambda n: n.get('resource-id') == 'android:id/button2'); frame(cookie)
    settings(); paused = frame(cookie, 204)
    check(paused[1]['x-cast-state'] == 'paused' and not paused[2], 'Settings clears the published frame before displaying private preferences')
    # Rotate through the app's real orientation preference, rather than changing only a mock frame.
    tap(lambda n: n.get('content-desc') in ('Landscape', 'Landscape, selected'), scroll=True)
    tap(match('Done')); find(match('Scriflow')); rotated = frame(cookie)[2]
    rotated_image = Image.open(BytesIO(rotated))
    check(rotated_image.width > rotated_image.height, 'Live capture resizes through actual Android landscape rotation')
    (ROOT/'build/cast-tablet-landscape.jpg').write_bytes(rotated)
    settings(); tap(lambda n: n.get('content-desc') in ('Portrait', 'Portrait, selected'), scroll=True)
    tap(match('Done')); find(match('Scriflow')); frame(cookie)
    adb('shell', 'cmd', 'statusbar', 'expand-notifications')
    check(frame(cookie, 204)[2] == b'', 'The notification shade pauses screen publication')
    adb('shell', 'cmd', 'statusbar', 'collapse'); frame(cookie)
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    check(frame(cookie, 204)[2] == b'', 'Leaving Scriflow removes the cached frame')
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookLibraryActivity'); frame(cookie)
    settings(); tap(match('Screen cast to a browser…'), scroll=True); tap(match('Stop casting'))
    find(match('Casting stopped.'))
    check(b'NotebookCastService' not in adb('shell', 'dumpsys', 'activity', 'services', PACKAGE).split(b'Active services:')[-1], 'Stop releases the foreground capture service')
    start_cast()
    check(http('GET', '/frame', cookie)[0] == 401, 'Restart requires a new browser session even with the same password')
    adb('shell', 'svc', 'wifi', 'disable')
    check(find(lambda n: 'Wi-Fi disconnected' in n.get('text', '')).get('text') != '', 'Wi-Fi loss closes the cast session')
    adb('shell', 'svc', 'wifi', 'enable')
    # The test network may take a few seconds to reconnect.
    for _ in range(30):
        if b'inet ' in adb('shell', 'ip', '-4', 'addr', 'show', 'wlan0'):
            break
        time.sleep(.3)
    start_cast()
    code, headers, _ = http('POST', '/login', body={'password': PASSWORD})
    sleep_cookie = headers['set-cookie'].split(';')[0]
    tap(match('Done')); tap(match('Done')); find(match('Scriflow')); frame(sleep_cookie)
    adb('shell', 'input', 'keyevent', 'KEYCODE_SLEEP')
    check(frame(sleep_cookie, 204)[2] == b'', 'Turning off the screen clears shared frames without ending the cast')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP'); adb('shell', 'wm', 'dismiss-keyguard')
    find(match('Scriflow')); frame(sleep_cookie)
    check(True, 'Unlocking resumes the same cast without another Android permission prompt')
    ui_pid = adb('shell', 'pidof', PACKAGE).strip().decode()
    adb('shell', 'su', '0', 'kill', '-9', ui_pid)
    stopped = False
    for _ in range(20):
        if b'NotebookCastService' not in adb('shell', 'dumpsys', 'activity', 'services', PACKAGE):
            stopped = True; break
        time.sleep(.1)
    check(stopped, 'A dead pen/UI controller cannot leave its separate capture service broadcasting')
    (ROOT/'build/cast-android-result.json').write_text(json.dumps({'passed': True, 'checks': checks, 'android': 29, 'transport': 'on-device Java Socket to Wi-Fi binding', 'capture_process': 'separate :cast process'}, indent=2)+'\n')
    print(f'Android screen cast: {checks} checks passed.', flush=True)
finally:
    try:
        adb('shell', 'svc', 'wifi', 'enable')
        adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'rm', '-f', '/sdcard/scriflow-cast-check.xml')
        adb('shell', 'rm', '-f', '/data/local/tmp/scriflow-cast-http.jar')
    except subprocess.SubprocessError:
        print('Emulator unavailable during cleanup.', flush=True)
