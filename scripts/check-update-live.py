#!/usr/bin/env python3
"""Check and download a published release with a disposable, differently signed emulator build."""
import os
import re
import shlex
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

SERIAL=os.environ.get('ANDROID_SERIAL','emulator-5554')
PACKAGE='org.eink.inkprobe'
BASE=Path(sys.argv[1]) if len(sys.argv)>1 else Path('/tmp/scriflow-update-older-debug.apk')
EXPECTED=sys.argv[2] if len(sys.argv)>2 else '0.48.0-beta.5'
REMOTE='/data/local/tmp/ScriflowLiveUpdate'+uuid.uuid4().hex[:8]+'.xml'


def adb(*args,check=True):return subprocess.run(['adb','-s',SERIAL,*args],capture_output=True,check=check)
def shell(*args,check=True):return adb('shell',shlex.join(args),check=check).stdout.decode(errors='replace').strip()
def tree():
    shell('uiautomator','dump',REMOTE);return ET.fromstring(adb('exec-out','cat',REMOTE).stdout)
def find(label,contains=False,timeout=45):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        for node in tree().iter('node'):
            for value in (node.get('text',''),node.get('content-desc','')):
                if (label in value) if contains else (label.casefold()==value.casefold()):return node
        time.sleep(.3)
    raise AssertionError('Control not found: '+label)
def tap(node):
    b=list(map(int,re.findall(r'\d+',node.get('bounds',''))));shell('input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))


def main():
    if shell('getprop','ro.kernel.qemu')!='1':raise SystemExit('This test is restricted to a disposable emulator.')
    if not BASE.is_file():raise SystemExit('Build the disposable older debug APK first.')
    try:
        adb('install','-r','-d',str(BASE));shell('pm','clear',PACKAGE)
        shell('am','start','-W','-n',PACKAGE+'/.notebook.NotebookLibraryActivity')
        tap(find('; tap to open settings',True));tap(find('Check for updates'))
        available=find('Update '+EXPECTED+' is available.',timeout=90)
        if available is None:raise AssertionError('Published update was not discovered')
        print('PASS: the live GitHub release and checksum assets were discovered',flush=True)
        tap(find('Download update'))
        end=time.monotonic()+360;last=0
        while time.monotonic()<end:
            values=[n.get('text','') for n in tree().iter('node')]
            if 'The installer is signed by a different key.' in values:
                print('PASS: the full published APK and checksum downloaded; its different signer was rejected',flush=True);break
            failure=next((v for v in values if v.startswith(('Could not ','The release checksum','The downloaded update','The update server','The update connection','GitHub\'s update'))),None)
            if failure:raise AssertionError(failure)
            now=time.monotonic()
            if now-last>=30:
                current=next((v for v in values if v.startswith(('Downloading ','Verifying '))), 'Starting…')
                print('Update test:',current,flush=True);last=now
            time.sleep(2)
        else:raise AssertionError('Published update did not finish in time')
        files=shell('run-as',PACKAGE,'ls','files/updates',check=False)
        if '.apk' in files:raise AssertionError('Rejected installer or partial file was left behind: '+files)
    finally:
        shell('am','force-stop',PACKAGE,check=False);shell('rm','-f',REMOTE,check=False)
        print('Disposable live-update test finished.',flush=True)


if __name__=='__main__':main()
