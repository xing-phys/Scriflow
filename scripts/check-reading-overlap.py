#!/usr/bin/env python3
"""Exercise note tap scrolling and its real settings popup on an emulator."""
from pathlib import Path
import json
import os
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parent.parent
PACKAGE='org.eink.inkprobe'
SERIAL=os.environ.get('ANDROID_SERIAL','emulator-5554')
NAME='ReadingOverlap-'+uuid.uuid4().hex[:8]
XML='/data/local/tmp/scriflow-reading-overlap.xml'
checks=0


def adb(*args,check=True):
    return subprocess.run(['adb','-s',SERIAL,*args],capture_output=True,check=check,timeout=30)


def wait(predicate,message,seconds=30):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        value=predicate()
        if value is not None and value is not False:return value
        time.sleep(.2)
    raise AssertionError(message)


def private_json(name):
    raw=adb('exec-out','run-as',PACKAGE,'cat','cache/'+name,check=False).stdout
    try:return json.loads(raw)
    except ValueError:return None


def state(predicate=lambda s:True):
    def sample():
        value=private_json('reading-ui-state.json')
        return value if value and predicate(value) else None
    try:return wait(sample,'The reader did not reach the expected viewport state')
    except AssertionError as error:
        raise AssertionError(str(error)+': '+repr(private_json('reading-ui-state.json'))) from error


def nodes():
    adb('shell','uiautomator','dump',XML)
    return list(ET.fromstring(adb('exec-out','cat',XML).stdout).iter('node'))


def find(predicate):
    return wait(lambda:next((node for node in nodes() if predicate(node)),None),'The expected reading control did not appear')


def bounds(node):return list(map(int,re.findall(r'\d+',node.get('bounds',''))))


def tap(node):
    x,y,xx,yy=bounds(node);adb('shell','input','tap',str((x+xx)//2),str((y+yy)//2))


def open_menu():
    state(lambda s:s['editable'])
    adb('shell','am','start','-W','-f','0x20000000','-n',PACKAGE+'/.notebook.NotebookReadingSelfTestActivity','--ez','openReading','true')
    return find(lambda n:n.get('text')=='Reading view')


def check(condition,message):
    global checks
    if not condition:raise AssertionError(message)
    checks+=1;print('PASS: '+message,flush=True)


if adb('shell','getprop','ro.kernel.qemu').stdout.strip()!=b'1':raise SystemExit('This check is restricted to an emulator.')
book=0
try:
    adb('shell','am','force-stop',PACKAGE)
    adb('shell','run-as',PACKAGE,'rm','-f','cache/reading-fixture.json','cache/reading-ui-state.json')
    adb('shell','am','start','-W','-n',PACKAGE+'/.notebook.NotebookReadingFixtureActivity','--es','name',NAME)
    fixture=wait(lambda:private_json('reading-fixture.json'),'The disposable notebook was not created')
    book=fixture['id']
    if fixture.get('error'):raise AssertionError(fixture['error'])
    start=state(lambda s:s['editable'] and s['singlePage']==0)
    if abs(start['scroll'])>=1:raise AssertionError('Unexpected initial scroll: '+repr(start))
    check(abs(start['scroll'])<1,'The disposable notebook starts at the top in a clean test workspace')
    open_menu();find(lambda n:n.get('text')=='Tap overlap · 10%')
    check(not any(n.get('content-desc')=='Close reading settings' for n in nodes()), 'Reading settings has no redundant close button')
    slider=find(lambda n:n.get('class')=='android.widget.SeekBar' and n.get('content-desc','').startswith('Continuous reading tap overlap'))
    tap(slider)
    label=find(lambda n:n.get('text','').startswith('Tap overlap · ') and n.get('text')!='Tap overlap · 10%').get('text')
    overlap=int(re.search(r'(\d+)%',label).group(1));check(25<=overlap<=55,'The overlap slider changes its displayed percentage')
    before=state()['time'];adb('shell','input','tap','1300','1500')
    after=state(lambda s:s['editable'] and s['time']>before)
    if abs(after['scroll']-start['scroll'])>=1:raise AssertionError('Dismissal changed scroll: '+repr((start,after)))
    check(abs(after['scroll']-start['scroll'])<1,'Dismissing the reading menu does not scroll the page')
    open_menu();find(lambda n:n.get('text')==label)
    check(True,'The chosen overlap is retained when the reading menu reopens')
    before=state()['time'];adb('shell','input','tap','1300','1500')
    start=state(lambda s:s['editable'] and s['singlePage']==0 and s['time']>before)
    if abs(start['scroll'])>=1:raise AssertionError('Reopening changed scroll: '+repr(start))
    check(abs(start['scroll'])<1,'Reopening and dismissing the menu leaves the page at the top')
    adb('shell','input','tap',str(int(start['width']*.8)),str(int(start['height']*.45)))
    moved=state(lambda s:s['editable'] and s['time']>start['time'] and s['scroll']>1)
    expected=start['height']*(100-overlap)/100
    check(abs(moved['scroll']-expected)<3 and moved['navigationPage']==1,'A tap moves within the current page by a screenful minus the overlap')
    adb('shell','input','tap',str(int(start['width']*.15)),str(int(start['height']*.2)))
    state(lambda s:s['editable'] and s['time']>moved['time'] and abs(s['scroll'])<1)
    check(True,'An upper-left tap moves back to the previous screenful')
    open_menu();tap(find(lambda n:n.get('text')=='Page turns'))
    find(lambda n:n.get('text')=='✓ Page turns')
    tap(find(lambda n:n.get('text')=='Double page'));find(lambda n:n.get('text')=='✓ Double page')
    check(True,'Changing several reading choices leaves the panel open')
    tap(find(lambda n:n.get('text')=='Single page'))
    adb('shell','input','tap','1300','1500')
    paged=state(lambda s:s['editable'] and s['singlePage']==1)
    adb('shell','input','tap',str(int(paged['width']*.8)),str(int(paged['height']*.45)))
    state(lambda s:s['editable'] and s['time']>paged['time'] and s['singlePage']==2)
    check(True,'Page-turn mode still advances directly to the next page')
    (ROOT/'build/reading-overlap-result.json').write_text(json.dumps({'passed':True,'checks':checks,'overlap':overlap},indent=2)+'\n')
finally:
    adb('shell','am','force-stop',PACKAGE,check=False)
    if book:
        adb('shell','run-as',PACKAGE,'rm','-f','cache/reading-fixture.json',check=False)
        adb('shell','am','start','-W','-n',PACKAGE+'/.notebook.NotebookReadingFixtureActivity','--es','name',NAME,'--ei','remove',str(book),check=False)
        removed=wait(lambda:private_json('reading-fixture.json'),'The disposable notebook was not removed')
        if removed.get('error'):raise AssertionError(removed['error'])
    adb('shell','rm','-f',XML,check=False)
