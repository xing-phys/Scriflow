#!/usr/bin/env bash
# Build a dependency-free Java Android app without Gradle or network access.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
SDK="$ROOT/.tools/platform-29/android.jar"
BT="$ROOT/.tools/build-tools-30.0.3"
[[ -f "$SDK" && -x "$BT/aapt" ]] || {
  echo 'Run ./scripts/bootstrap.sh first.' >&2; exit 1;
}
rm -rf build/classes build/dex build/unsigned.apk build/aligned.apk
mkdir -p build/classes build/dex
python3 - <<'PY'
from pathlib import Path
files = sorted(Path('app/src/main/java').rglob('*.java'))
if not files:
    raise SystemExit('No Java sources found')
Path('build/java-sources.txt').write_text('\n'.join('"' + str(p) + '"' for p in files) + '\n')
PY
# The Android plugin owns the application ID/debug flag; the offline packager supplies them here.
python3 - <<'MANIFEST'
import xml.etree.ElementTree as E
E.register_namespace('android', 'http://schemas.android.com/apk/res/android')
tree = E.parse('app/src/main/AndroidManifest.xml')
tree.getroot().set('package', 'org.eink.inkprobe')
from pathlib import Path
version = dict(line.split('=', 1) for line in Path('app/version.properties').read_text().splitlines() if '=' in line)
for key in ('versionCode', 'versionName'):
    tree.getroot().set('{http://schemas.android.com/apk/res/android}' + key, version[key])
tree.getroot().find('application').set('{http://schemas.android.com/apk/res/android}debuggable', 'true')
tree.write('build/AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
MANIFEST
# Keep the JDK bootstrap classes: Android SDK stubs intentionally omit the
# LambdaMetafactory methods javac needs. D8 below desugars Java 8 lambdas
# and validates against the Android API library for the device runtime.
javac -encoding UTF-8 -source 8 -target 8 -classpath "$SDK" \
  -d build/classes @build/java-sources.txt
# d8 supports response files, avoiding command line limits as sources grow.
find "$ROOT/build/classes" -name '*.class' -print | sort > build/dex-sources.txt
"$BT/d8" --min-api 23 --lib "$SDK" --output build/dex @build/dex-sources.txt
"$BT/aapt" package -f -M build/AndroidManifest.xml -I "$SDK" -F build/unsigned.apk
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
with ZipFile('build/unsigned.apk', 'a', compression=ZIP_DEFLATED) as apk:
    for dex in sorted(Path('build/dex').glob('*.dex')):
        apk.write(dex, dex.name)
PY
"$BT/zipalign" -p -f 4 build/unsigned.apk build/aligned.apk
# Local debug identity only. Never commit or redistribute this key.
if [[ ! -f .tools/debug.keystore ]]; then
  keytool -genkeypair -keystore .tools/debug.keystore -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Android Debug,O=Ink Probe,C=US'
fi
"$BT/apksigner" sign --ks .tools/debug.keystore --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android --out build/inkprobe-debug.apk build/aligned.apk
"$BT/apksigner" verify --verbose build/inkprobe-debug.apk
"$BT/aapt" dump badging build/inkprobe-debug.apk | head -n 3
printf '%s\n' "Built $ROOT/build/inkprobe-debug.apk"
