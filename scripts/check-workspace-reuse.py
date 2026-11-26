#!/usr/bin/env python3
"""Verify saved note views across a permanently deleted and reused notebook slot."""
import json
import os
import shlex
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

SERIAL=os.environ.get('ANDROID_SERIAL','emulator-5554')
PACKAGE='org.eink.inkprobe'
FIXTURE=PACKAGE+'/.notebook.NotebookReadingFixtureActivity'
READER=PACKAGE+'/.notebook.NotebookReadingSelfTestActivity'
OLD='ReadingOverlap-'+uuid.uuid4().hex[:8]
NEW='ReadingOverlap-'+uuid.uuid4().hex[:8]
PREF='shared_prefs/notebook.NotebookReadingSelfTestActivity.xml'


def adb(*args,check=True,data=None):return subprocess.run(['adb','-s',SERIAL,*args],input=data,capture_output=True,check=check)
def shell(*args,check=True,data=None):return adb('shell',shlex.join(args),check=check,data=data).stdout.decode(errors='replace').strip()
def private(path):return adb('exec-out','run-as',PACKAGE,'cat',path,check=False).stdout
def state(path):
    try:return json.loads(private(path))
    except ValueError:return None
def wait(predicate,description,timeout=45):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        result=predicate()
        if result:return result
        time.sleep(.25)
    raise AssertionError(description)
def stop():shell('am','force-stop',PACKAGE)
def fixture(name,remove=0):
    stop();shell('run-as',PACKAGE,'rm','-f','cache/reading-fixture.json')
    args=['am','start','-W','-n',FIXTURE,'--es','name',name]
    if remove:args+=['--ei','remove',str(remove)]
    shell(*args);value=wait(lambda:state('cache/reading-fixture.json'),'Notebook fixture did not finish')
    if value.get('error'):raise AssertionError(value['error'])
    return value['id']
def generation(book):
    root=ET.fromstring(private('shared_prefs/notebook-generations.xml'))
    return int(next(n.get('value') for n in root if n.get('name')==str(book)))
def install_layout(book,gen):
    layout='2&#10;'+str(book)+',3,FLOATING,false,0.5,0.2,0.2,0.5,0.5,true,2.0,0.5,0.5,'+str(gen)+'&#10;'
    xml=('<?xml version="1.0" encoding="utf-8" standalone="yes"?><map><string name="noteWindows">'+layout+'</string></map>').encode()
    shell('run-as',PACKAGE,'sh','-c','cat > '+PREF,data=xml)
def open_reader(book):
    shell('run-as',PACKAGE,'rm','-f','cache/reading-ui-state.json')
    shell('am','start','-W','-n',PACKAGE+'/.notebook.NotebookLibraryActivity')
    # The shelf loads the selected catalog into the same session, as in normal use.
    time.sleep(2)
    shell('am','start','-W','-n',READER)
    return wait(lambda:(lambda v:v if v and v.get('editable') and v.get('notebookId')==book else None)(state('cache/reading-ui-state.json')),
                'Reader did not open the reused notebook')


def main():
    if shell('getprop','ro.kernel.qemu')!='1':raise SystemExit('This test is restricted to a disposable emulator.')
    book=0
    try:
        shell('pm','clear',PACKAGE)
        book=fixture(OLD);old_book=book;old=generation(book);fixture(OLD,book);book=0
        book=fixture(NEW);new=generation(book)
        assert book==old_book and new>old,'Reused slot did not get a new identity'
        stop();install_layout(book,old);initial=open_reader(book)
        assert initial.get('navigationPage')==1 and abs(initial.get('zoom',0)-1)<.01,initial
        print('PASS: a reused notebook slot does not inherit the deleted note’s saved page or zoom',flush=True)
        stop();install_layout(book,new);restored=open_reader(book)
        restored=wait(lambda:(lambda v:v if v and v.get('notebookId')==book and v.get('zoom',0)>1.9 else None)(state('cache/reading-ui-state.json')),
                      'The same notebook did not retain its saved zoom')
        assert restored.get('navigationPage')==3,restored
        print('PASS: the same notebook still restores its saved page and zoom',flush=True)
    finally:
        if book:fixture(NEW,book)
        stop()


if __name__=='__main__':main()
