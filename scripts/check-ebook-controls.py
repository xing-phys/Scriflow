#!/usr/bin/env python3
"""Touch the real Gecko menus, choose native options and verify saved settings."""
from pathlib import Path
import json
import os
import re
import struct
import subprocess
import time
import xml.etree.ElementTree as ET
import zlib

ROOT=Path(__file__).resolve().parent.parent
PACKAGE='org.eink.inkprobe'
SERIAL=os.environ.get('ANDROID_SERIAL','emulator-5554')

def adb(*args,check=True):
    return subprocess.run(['adb','-s',SERIAL,*args],capture_output=True,check=check)

def bounds(node):return list(map(int,re.findall(r'\d+',node.get('bounds'))))

def nodes():
    adb('shell','uiautomator','dump','/sdcard/scriflow-ebook-controls.xml')
    root=ET.fromstring(adb('exec-out','cat','/sdcard/scriflow-ebook-controls.xml').stdout)
    def walk(node,parents):
        yield node,parents
        for child in node:yield from walk(child,parents+[node])
    return list(walk(root,[]))

def find(predicate):
    deadline=time.monotonic()+20
    while time.monotonic()<deadline:
        for node,parents in nodes():
            if predicate(node):return node,parents
        time.sleep(.3)
    raise AssertionError('The expected reader control did not appear.')

def tap(predicate):
    node,parents=find(predicate);x,y,xx,yy=bounds(node)
    # Gecko's accessibility tree includes its embedding view offset twice on
    # this API-29 emulator. Native dialogs have no WebView ancestor.
    for index,parent in enumerate(parents):
        if parent.get('class')=='android.webkit.WebView' and index>0:
            a,b,_,_=bounds(parent);c,d,_,_=bounds(parents[index-1])
            x-=a-c;xx-=a-c;y-=b-d;yy-=b-d
            break
    adb('shell','input','tap',str((x+xx)//2),str((y+yy)//2))
    time.sleep(.4)

def label(value):return lambda node:node.get('text')==value or node.get('content-desc')==value

if adb('shell','getprop','ro.kernel.qemu').stdout.strip()!=b'1':raise SystemExit('Controls checks require an emulator.')
fixture='/data/local/tmp/scriflow-ebook-controls.epub'
try:
    adb('push',str(ROOT/'build/ebook-fixtures/quantum.epub'),fixture)
    adb('shell','run-as',PACKAGE,'mkdir','-p','cache/ebook-check-input')
    adb('shell','run-as',PACKAGE,'cp',fixture,'cache/ebook-check-input/quantum.epub')
    adb('shell','run-as',PACKAGE,'rm','-f','cache/ebook-reader-check.json')
    adb('shell','am','start','-W','-f','0x10008000','-n',PACKAGE+'/.notebook.NotebookEbookSelfTestActivity','--es','fixture','quantum.epub','--es','mode','read')
    time.sleep(3)
    tap(label('Aa'));find(label('Text and layout'))
    tap(lambda n:n.get('text')=='Font' and bounds(n)[2]-bounds(n)[0]>400)
    find(label('Reading options'));find(label('Serif'))
    (ROOT/'build/ebook-font-choices-android.png').write_bytes(adb('exec-out','screencap','-p').stdout)
    tap(label('Serif'));find(label('Font Serif'))
    print('PASS: Touch opens native font choices and applies Serif.',flush=True)
    tap(lambda n:n.get('text')=='Size' and bounds(n)[2]-bounds(n)[0]>400)
    tap(label('24 px'));find(label('Size 24 px'))
    print('PASS: Touch opens native size choices and applies 24 px.',flush=True)
    tap(label('Close reading panel'));tap(label('Books'))
    find(label('Open / import book'))
    report=json.loads(adb('exec-out','run-as',PACKAGE,'cat','cache/ebook-reader-check.json').stdout)
    assert report.get('passed'),report
    deadline=time.monotonic()+10
    while True:
        raw=adb('exec-out','run-as',PACKAGE,'cat','files/ebooks/'+report['book']+'/state').stdout
        magic,size=struct.unpack('>II',raw[:8]);payload=raw[8:8+size]
        assert magic==0x45424b31 and len(raw)==size+12 and zlib.crc32(payload)==struct.unpack('>I',raw[-4:])[0]
        state=json.loads(payload)
        if state['settings']['font']=='serif' and state['settings']['size']==24:break
        if time.monotonic()>deadline:raise AssertionError('Touch-selected settings were not saved on exit.')
        time.sleep(.2)
    print('PASS: Leaving the book durably saves the touch-selected settings.',flush=True)
    (ROOT/'build/ebook-controls-result.json').write_text(json.dumps({'passed':True,'checks':3,'book':report['book'],'settings':state['settings']},indent=2)+'\n')
finally:
    adb('shell','rm','-f',fixture,'/sdcard/scriflow-ebook-controls.xml',check=False)
    adb('shell','run-as',PACKAGE,'rm','-rf','cache/ebook-check-input',check=False)
