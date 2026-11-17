#!/usr/bin/env python3
"""Real GeckoView, private book IO, reflow, font and notebook-link checks on an emulator."""
from pathlib import Path
import json
import os
import subprocess
import time

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'build/ebook-native-check';OUT.mkdir(parents=True,exist_ok=True)
PACKAGE='org.eink.inkprobe';SERIAL=os.environ.get('ANDROID_SERIAL','emulator-5554')


def adb(*args,check=True):
    return subprocess.run(['adb','-s',SERIAL,*args],capture_output=True,check=check)


if adb('shell','getprop','ro.kernel.qemu').stdout.strip()!=b'1':raise SystemExit('Native ebook checks are restricted to an emulator.')
adb('shell','wm','dismiss-keyguard')
subprocess.run(['python3','tests/make-ebook-fixtures.py'],cwd=ROOT,check=True)
adb('shell','run-as',PACKAGE,'mkdir','-p','cache/ebook-check-input')
remote='/data/local/tmp/scriflow-ebook-native-'+str(os.getpid())
adb('shell','mkdir','-p',remote)
fixtures=ROOT/'build/ebook-fixtures'
names=['quantum.epub','quantum.fb2','quantum.fb2.zip','quantum.txt','quantum.html','comic.cbz','sample-cp1252.mobi','sample-ncx.mobi','sample-kf8.azw3','chicken.djvu','DjVu3Spec_bundled.djvu','large-bundled.djvu','scripted.epub']
for name in names+['sample-drm-v1.mobi','encrypted.epub']:
    adb('push',str(fixtures/name),remote+'/'+name)
    adb('shell','run-as',PACKAGE,'cp',remote+'/'+name,'cache/ebook-check-input/'+name)
font=Path('/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf')
adb('push',str(font),remote+'/custom.ttf')
adb('shell','run-as',PACKAGE,'cp',remote+'/custom.ttf','cache/ebook-check-input/custom.ttf')
results=[]
try:
    adb('shell','am','force-stop',PACKAGE)
    for name,mode in [(name,'read') for name in names]+[('comic.cbz','reopen-title'),('sample-drm-v1.mobi','reject'),('encrypted.epub','reject')]+[('quantum.epub','reflow'),('quantum.epub','restore'),('quantum.epub','font'),('quantum.epub','ink'),('quantum.epub','snapshot')]:
        # Opening another book closes the previous activity but keeps the shared
        # engine, as the real shelf does. Restore deliberately kills the process.
        if mode=='restore':
            time.sleep(2)
            adb('shell','am','force-stop',PACKAGE)
            time.sleep(1)
        adb('shell','run-as',PACKAGE,'rm','-f','cache/ebook-reader-check.json')
        adb('shell','am','start','-W','-f','0x10008000','-n',PACKAGE+'/.notebook.NotebookEbookSelfTestActivity','--es','fixture',name,'--es','mode',mode)
        deadline=time.monotonic()+120;result=None
        while time.monotonic()<deadline:
            raw=adb('exec-out','run-as',PACKAGE,'cat','cache/ebook-reader-check.json',check=False)
            if raw.returncode==0:
                try:result=json.loads(raw.stdout);break
                except json.JSONDecodeError:pass
            time.sleep(.4)
        if result is None:
            (OUT/'timeout-log.txt').write_bytes(adb('logcat','-d','-t','250',check=False).stdout)
            raise AssertionError('Native reader timed out: '+name+' '+mode)
        (OUT/(name+'.'+mode+'.json')).write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n')
        if not result.get('passed'):raise AssertionError(name+' '+mode+': '+str(result.get('error')))
        results.append({'fixture':name,'mode':mode,'book':result.get('book')})
        print('PASS: Android Gecko reader',name,mode,flush=True)
        time.sleep(1)
    (OUT/'result.json').write_text(json.dumps({'passed':True,'checks':results},indent=2)+'\n')
    print('Native ebook reader:',len(results),'checks passed.',flush=True)
finally:
    adb('shell','am','force-stop',PACKAGE,check=False)
    adb('shell','run-as',PACKAGE,'rm','-rf','cache/ebook-check-input',check=False)
    adb('shell','rm','-rf',remote,check=False)
