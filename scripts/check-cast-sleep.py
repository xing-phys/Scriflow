#!/usr/bin/env python3
"""Exercise one real MediaProjection session across screen sleep on an emulator."""
from pathlib import Path
import os
import re
import shlex
import subprocess
import time
import urllib.parse
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
os.environ['JAVA_HOME'] = str(ROOT / '.tools/jdk-17.0.21+9')
os.environ['PATH'] = str(Path(os.environ['JAVA_HOME']) / 'bin') + os.pathsep + os.environ['PATH']
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
PACKAGE = 'org.eink.inkprobe'
PASSWORD = 'Cast-Resume-117'
XML = '/sdcard/scriflow-cast-sleep.xml'
JAR = '/data/local/tmp/scriflow-cast-sleep.jar'


def adb(*args):
    if args and args[0] == 'shell':
        args = ('shell', shlex.join(args[1:]))
    return subprocess.run(['adb', '-s', SERIAL, *args], capture_output=True, check=True, timeout=30).stdout


def nodes():
    adb('shell', 'uiautomator', 'dump', XML)
    return ET.fromstring(adb('exec-out', 'cat', XML)).iter('node')


def find(predicate, scroll=False):
    for _ in range(24):
        root = list(nodes())
        for node in root:
            if predicate(node):
                return node
        if scroll:
            view = next((n for n in root if n.get('class') == 'android.widget.ScrollView'), None)
            if view is not None:
                x, y, xx, yy = bounds(view)
                adb('shell', 'input', 'swipe', str((x+xx)//2), str(yy-60), str((x+xx)//2), str(max(y+60, yy-600)), '450')
        time.sleep(.2)
    raise AssertionError('Could not find the expected cast control')


def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))


def tap(predicate, scroll=False):
    x, y, xx, yy = bounds(find(predicate, scroll))
    adb('shell', 'input', 'tap', str((x+xx)//2), str((y+yy)//2))


def match(text):
    return lambda n: n.get('text') == text or n.get('content-desc') == text


def http(host, port, method, path, cookie='', body=None):
    data = adb('shell', 'env', 'CLASSPATH=' + JAR, 'app_process', '/system/bin',
               'org.eink.inkprobe.NotebookCastHttp', host, str(port), method, path, cookie or '-',
               '-' if body is None else urllib.parse.urlencode(body))
    head, payload = data.split(b'\r\n\r\n', 1)
    lines = head.decode().split('\r\n')
    headers = dict(line.lower().split(': ', 1) for line in lines[1:])
    return int(lines[0].split()[1]), headers, payload


def frame(host, port, cookie, expected):
    for _ in range(50):
        result = http(host, port, 'GET', '/frame', cookie)
        if result[0] == expected:
            return result
        time.sleep(.2)
    raise AssertionError('Cast frame state did not reach ' + str(expected))


if adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1':
    raise SystemExit('This check is restricted to an emulator.')
client = ROOT / 'build/cast-sleep-client'
client.mkdir(parents=True, exist_ok=True)
java = ROOT / '.tools/jdk-17.0.21+9/bin/javac'
subprocess.run([str(java), '-source', '8', '-target', '8', '-d', str(client), str(ROOT/'tools/NotebookCastHttp.java')], check=True)
subprocess.run([str(ROOT/'.tools/android-sdk/build-tools/36.0.0/d8'), '--min-api', '29', '--lib',
                str(ROOT/'.tools/platform-29/android.jar'), '--output', str(client/'client.jar'),
                str(client/'org/eink/inkprobe/NotebookCastHttp.class')], check=True)
adb('push', str(client/'client.jar'), JAR)
try:
    for _ in range(90):
        if b'inet ' in adb('shell', 'ip', '-4', 'addr', 'show', 'wlan0'):
            break
        time.sleep(.5)
    else:
        raise AssertionError('Emulator Wi-Fi did not obtain an address')
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.notebook.NotebookLibraryActivity')
    tap(lambda n: n.get('content-desc', '').endswith('tap to open settings'))
    tap(match('Screen cast to a browser…'), scroll=True)
    tap(lambda n: n.get('class') == 'android.widget.EditText')
    adb('shell', 'input', 'text', PASSWORD)
    if b'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    tap(match('Start casting'))
    tap(lambda n: n.get('resource-id') == 'android:id/button1')
    address = find(lambda n: bool(re.fullmatch(r'http://\d+\.\d+\.\d+\.\d+:\d+/', n.get('text', '')))).get('text')
    parsed = urllib.parse.urlparse(address); host, port = parsed.hostname, parsed.port
    code, headers, _ = http(host, port, 'POST', '/login', body={'password': PASSWORD})
    assert code == 200
    cookie = headers['set-cookie'].split(';')[0]
    tap(match('Done')); tap(match('Done')); find(match('Scriflow'))
    frame(host, port, cookie, 200)
    before = adb('shell', 'pidof', PACKAGE + ':cast').strip()
    adb('shell', 'input', 'keyevent', 'KEYCODE_SLEEP')
    assert frame(host, port, cookie, 204)[2] == b''
    print('PASS: Screen sleep clears the viewer frame and pauses capture', flush=True)
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    find(match('Scriflow'))
    frame(host, port, cookie, 200)
    assert adb('shell', 'pidof', PACKAGE + ':cast').strip() == before
    print('PASS: Unlocking resumes the same cast without another permission prompt', flush=True)
except BaseException:
    try:
        adb('shell', 'uiautomator', 'dump', XML)
        (ROOT/'build/cast-sleep-failure.xml').write_bytes(adb('exec-out', 'cat', XML))
        (ROOT/'build/cast-sleep-failure.log').write_bytes(adb('logcat', '-d', '-t', '1500'))
    except (subprocess.SubprocessError, OSError):
        pass
    raise
finally:
    subprocess.run(['adb', '-s', SERIAL, 'shell', 'input', 'keyevent', 'KEYCODE_WAKEUP'], capture_output=True)
    subprocess.run(['adb', '-s', SERIAL, 'shell', 'am', 'force-stop', PACKAGE], capture_output=True)
    subprocess.run(['adb', '-s', SERIAL, 'shell', 'rm', '-f', XML, JAR], capture_output=True)
