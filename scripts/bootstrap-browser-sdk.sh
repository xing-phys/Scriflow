#!/usr/bin/env bash
# Build SDK required by the bundled browser. The app's min/target API remain 29.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
python3 - <<'PY'
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from urllib.request import urlopen
import hashlib, shutil, tempfile, zipfile

root = Path.cwd()
packages = [
    ('platform-37.2_r02.zip', 68094624, 'd52b3062af96f7c4dfdca5a479cd1db33ae83762', '.tools/platform-37.2', 'android.jar'),
    ('build-tools_r36_linux.zip', 63737259, 'b0b6376977657e8ad9b969bacf4093601da2c6fb', '.tools/build-tools-36.0.0', 'aapt'),
]
cache = root / '.tools/cache'
cache.mkdir(parents=True, exist_ok=True)
def install(package):
    name, size, checksum, destination, marker = package
    archive = cache / name
    if not archive.is_file():
        temporary = archive.with_suffix('.part')
        with urlopen('https://dl.google.com/android/repository/' + name, timeout=180) as response, temporary.open('wb') as output:
            shutil.copyfileobj(response, output, 1024 * 1024)
        temporary.replace(archive)
    if archive.stat().st_size != size or hashlib.sha1(archive.read_bytes()).hexdigest() != checksum:
        raise SystemExit('Checksum mismatch: ' + name)
    destination = root / destination
    if (destination / marker).is_file(): return
    stage = Path(tempfile.mkdtemp(prefix='browser-sdk-', dir=root / '.tools'))
    try:
        with zipfile.ZipFile(archive) as contents:
            for item in contents.infolist():
                if not (stage / item.filename).resolve().is_relative_to(stage.resolve()): raise SystemExit('Unsafe SDK path')
            contents.extractall(stage)
        directory = next(stage.rglob(marker)).parent
        shutil.move(str(directory), destination)
        for tool in ['aapt', 'aapt2', 'aidl', 'apksigner', 'd8', 'zipalign']:
            if (destination / tool).is_file(): (destination / tool).chmod(0o755)
    finally: shutil.rmtree(stage)
    print('Prepared ' + destination.name, flush=True)
with ThreadPoolExecutor(max_workers=2) as executor: list(executor.map(install, packages))
# Minor API levels are not recognized through legacy source.properties alone.
(root / '.tools/platform-37.2/package.xml').write_text('''<?xml version="1.0" encoding="UTF-8"?>
<repo:repository xmlns:repo="http://schemas.android.com/repository/android/common/02" xmlns:sdk="http://schemas.android.com/sdk/android/repo/repository2/03" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<localPackage path="platforms;android-37.2" obsolete="false"><type-details xsi:type="sdk:platformDetailsType"><api-level>37.2</api-level><extension-level>24</extension-level><base-extension>true</base-extension><layoutlib api="15"/></type-details><revision><major>2</major></revision><display-name>Android SDK Platform 37.2</display-name></localPackage>
</repo:repository>
''')
for relative, target in [('platforms/android-37.2', '.tools/platform-37.2'), ('build-tools/36.0.0', '.tools/build-tools-36.0.0')]:
    link = root / '.tools/android-sdk' / relative
    link.parent.mkdir(parents=True, exist_ok=True)
    if link.is_symlink(): link.unlink()
    if not link.exists(): link.symlink_to(root / target)
PY
