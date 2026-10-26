#!/usr/bin/env bash
# Pinned app runtime dependencies shared by the offline builder and PDF tests.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile
import hashlib
import urllib.request

target = Path('.tools/pdfbox')
target.mkdir(parents=True, exist_ok=True)
artifacts = [
    ('com/tom-roush/pdfbox-android/2.0.27.0/pdfbox-android-2.0.27.0.aar',
     '30277f879cfd571db2a137582c95516a0d4ea6778e945519bc58ca93d57d88c7'),
    ('org/bouncycastle/bcprov-jdk15to18/1.72/bcprov-jdk15to18-1.72.jar',
     'ea66ea8a450810b2193e8bf9a7ad3e46307c9896224c0f407d1b7d96ba1221cc'),
    ('org/bouncycastle/bcpkix-jdk15to18/1.72/bcpkix-jdk15to18-1.72.jar',
     'd9b97477b72499bcee02f5a906510810257ff36a94bf69fbca0b1e65e7ffdb6e'),
    ('org/bouncycastle/bcutil-jdk15to18/1.72/bcutil-jdk15to18-1.72.jar',
     'd92184bdeb3105a11ad9e36acbd66b5f8eed091b08b9c8f3e2549e42b7f131f1'),
]
for artifact, digest in artifacts:
    path = target / artifact.rsplit('/', 1)[1]
    if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
        data = urllib.request.urlopen('https://repo.maven.apache.org/maven2/' + artifact, timeout=90).read()
        if hashlib.sha256(data).hexdigest() != digest:
            raise SystemExit('Dependency checksum failed: ' + path.name)
        temporary = path.with_suffix(path.suffix + '.part')
        temporary.write_bytes(data)
        temporary.replace(path)
with ZipFile(target / 'pdfbox-android-2.0.27.0.aar') as archive:
    for name in archive.namelist():
        if name == 'classes.jar' or name.startswith('assets/'):
            archive.extract(name, target)
print('Verified PDFBox Android 2.0.27.0 and Bouncy Castle 1.72.')
PY
