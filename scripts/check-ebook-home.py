#!/usr/bin/env python3
"""Real Android homepage: ebook open, rename, mixed-shelf selection, folders and Trash."""
from pathlib import Path
import hashlib
import json
import os
import re
import shlex
import struct
import subprocess
import time
import xml.etree.ElementTree as ET
import zlib

ROOT=Path(__file__).resolve().parent.parent
PACKAGE='org.eink.inkprobe';SERIAL=os.environ.get('ANDROID_SERIAL','emulator-5554')
TITLE='Homepage Reader Check '+str(os.getpid());RENAMED='Homepage_reader_'+str(os.getpid());FOLDER='Reader_shelf_'+str(os.getpid())

def adb(*args,check=True):
    if args and args[0]=='shell':args=('shell',shlex.join(args[1:]))
    return subprocess.run(['adb','-s',SERIAL,*args],capture_output=True,check=check)
def tree():
    adb('shell','uiautomator','dump','/sdcard/scriflow-ebook-home.xml')
    return ET.fromstring(adb('exec-out','cat','/sdcard/scriflow-ebook-home.xml').stdout)
def bounds(node):return list(map(int,re.findall(r'\d+',node.get('bounds'))))
def label(value):return lambda n:n.get('text')==value or n.get('content-desc')==value
def find(predicate,scroll=False):
    for attempt in range(25):
        root=tree()
        for node in root.iter('node'):
            if predicate(node):return node
        if scroll:
            grid=next((n for n in root.iter('node') if n.get('class')=='android.widget.GridView'),None)
            if grid is not None:
                x,y,xx,yy=bounds(grid)
                distance=min(600,(yy-y)//2)
                start=y+40 if attempt<3 else yy-40
                end=start+distance if attempt<3 else start-distance
                adb('shell','input','swipe',str((x+xx)//2),str(start),str((x+xx)//2),str(end),'300')
        time.sleep(.2)
    raise AssertionError('Expected homepage control not found.')
def tap(predicate,scroll=False,hold=False):
    node=find(predicate,scroll);x,y,xx,yy=bounds(node);x=(x+xx)//2;y=(y+yy)//2
    if hold:adb('shell','input','swipe',str(x),str(y),str(x),str(y),'850')
    else:adb('shell','input','tap',str(x),str(y))
    time.sleep(.4)
def positive():tap(lambda n:n.get('resource-id')=='android:id/button1')
def frame(name):
    raw=adb('exec-out','run-as',PACKAGE,'cat',name).stdout
    magic,size=struct.unpack('>II',raw[:8]);data=raw[8:8+size]
    assert magic==0x45424b31 and len(raw)==size+12 and zlib.crc32(data)==struct.unpack('>I',raw[-4:])[0]
    return raw,data

if adb('shell','getprop','ro.kernel.qemu').stdout.strip()!=b'1':raise SystemExit('Homepage checks require an emulator.')
fixture='/data/local/tmp/scriflow-ebook-home.epub'
try:
    adb('push',str(ROOT/'build/ebook-fixtures/quantum.epub'),fixture)
    adb('shell','run-as',PACKAGE,'mkdir','-p','cache/ebook-check-input')
    adb('shell','run-as',PACKAGE,'cp',fixture,'cache/ebook-check-input/quantum.epub')
    adb('shell','run-as',PACKAGE,'rm','-f','cache/ebook-reader-check.json')
    adb('shell','am','start','-W','-f','0x10008000','-n',PACKAGE+'/.notebook.NotebookEbookSelfTestActivity','--es','fixture','quantum.epub','--es','mode','read','--es','label',TITLE)
    time.sleep(3)
    report=json.loads(adb('exec-out','run-as',PACKAGE,'cat','cache/ebook-reader-check.json').stdout);assert report['passed'],report
    ui=json.loads(adb('exec-out','run-as',PACKAGE,'cat','cache/ebook-chrome-check.json').stdout)
    adb('shell','input','keyevent','KEYCODE_BACK')
    find(label('Scriflow'));tap(label(TITLE),scroll=True)
    time.sleep(2)
    activity=adb('shell','dumpsys','activity','activities').stdout.decode()
    assert any('/.notebook.NotebookEbookActivity' in line for line in activity.splitlines() if 'mResumedActivity' in line)
    (ROOT/'build/ebook-reader-home-open.png').write_bytes(adb('exec-out','screencap','-p').stdout)
    print('PASS: An ebook on the homepage opens directly in the floating reader.',flush=True)
    adb('shell','input','keyevent','KEYCODE_BACK');find(label('Scriflow'))
    state_path='files/ebooks/'+report['book']+'/state';before=frame(state_path)[0]
    source=adb('exec-out','run-as',PACKAGE,'cat','files/ebooks/'+report['book']+'/source').stdout
    original_hash=hashlib.sha256(source).hexdigest()
    tap(label('Manage ebook '+TITLE),scroll=True);tap(label('Rename'))
    find(lambda n:n.get('class')=='android.widget.EditText')
    adb('shell','input','text',RENAMED)
    find(lambda n:n.get('class')=='android.widget.EditText' and n.get('text')==RENAMED);positive()
    find(label(RENAMED),scroll=True)
    print('PASS: Homepage Rename changes the ebook label.',flush=True)
    tap(label('+ Folder'));adb('shell','input','text',FOLDER);positive();find(label(FOLDER),scroll=True)
    tap(label(RENAMED),scroll=True,hold=True);find(label('1 selected'))
    tap(label('Actions'));tap(label('Move to folder…'));tap(label(FOLDER))
    tap(label(FOLDER),scroll=True);find(label(RENAMED))
    print('PASS: Shelf selection moves the ebook into a normal homepage folder.',flush=True)
    tap(label('Manage ebook '+RENAMED));tap(label('Move to Trash'))
    tap(lambda n:bool(re.fullmatch(r'Trash \(\d+\)',n.get('text','') or n.get('content-desc',''))))
    tap(label('Manage ebook '+RENAMED),scroll=True);tap(label('Restore ebook'))
    tap(label('Bookshelf'));tap(label(FOLDER),scroll=True);find(label(RENAMED))
    print('PASS: Shared Trash restores the ebook to its original folder.',flush=True)
    tap(label('Bookshelf'));tap(label('Manage folder '+FOLDER),scroll=True);tap(label('Remove folder (keep items)'))
    find(label(RENAMED),scroll=True)
    assert frame(state_path)[0]==before,'Shelf actions modified reading data.'
    assert hashlib.sha256(adb('exec-out','run-as',PACKAGE,'cat','files/ebooks/'+report['book']+'/source').stdout).hexdigest()==original_hash
    assert b'folder=0' in frame('files/ebooks/'+report['book']+'/metadata')[1]
    (ROOT/'build/ebook-homepage-android.png').write_bytes(adb('exec-out','screencap','-p').stdout)
    print('PASS: Removing the folder returns its ebook home and preserves original/reading bytes.',flush=True)
    (ROOT/'build/ebook-home-result.json').write_text(json.dumps({'passed':True,'checks':5,'book':report['book']},indent=2)+'\n')
finally:
    adb('shell','rm','-f',fixture,'/sdcard/scriflow-ebook-home.xml',check=False)
    adb('shell','run-as',PACKAGE,'rm','-rf','cache/ebook-check-input',check=False)
