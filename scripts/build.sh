#!/usr/bin/env bash
# Build the Java Android app without Gradle; runtime dependencies are cached and pinned.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
SDK="$ROOT/.tools/platform-29/android.jar"
BT="$ROOT/.tools/build-tools-30.0.3"
[[ -f "$SDK" && -x "$BT/aapt" ]] || {
  echo 'Run ./scripts/bootstrap.sh first.' >&2; exit 1;
}
bash scripts/bootstrap-pdfbox.sh
PDF_DEPS="$ROOT/.tools/pdfbox"
rm -rf build/classes build/dex build/generated-sources build/license-assets build/unsigned.apk build/aligned.apk
mkdir -p build/classes build/dex build/generated-sources build/license-assets
cp LICENSE build/license-assets/LICENSE
cp licenses/pdfbox-android-* licenses/bouncycastle-* licenses/androidsvg-* build/license-assets/
cp -R "$PDF_DEPS/assets/." build/license-assets/
# The Android plugin owns the application ID/debug flag; the offline packager supplies them here.
python3 - <<'MANIFEST'
import xml.etree.ElementTree as E
E.register_namespace('android', 'http://schemas.android.com/apk/res/android')
tree = E.parse('app/src/main/AndroidManifest.xml')
application = tree.getroot().find('application')
overlay = E.parse('app/src/debug/AndroidManifest.xml').getroot().find('application')
android = '{http://schemas.android.com/apk/res/android}'
tools = '{http://schemas.android.com/tools}'
for activity in overlay:
    if activity.tag != 'activity':
        raise SystemExit('The offline debug merger supports only activity overrides')
    name = activity.get(android + 'name')
    existing = next((item for item in application.findall('activity')
                     if item.get(android + 'name') == name), None)
    if existing is None:
        existing = E.SubElement(application, 'activity')
    for attribute, value in activity.attrib.items():
        if not attribute.startswith(tools): existing.set(attribute, value)
tree.getroot().set('package', 'org.eink.inkprobe')
from pathlib import Path
version = dict(line.split('=', 1) for line in Path('app/version.properties').read_text().splitlines() if '=' in line)
for key in ('versionCode', 'versionName'):
    tree.getroot().set('{http://schemas.android.com/apk/res/android}' + key, version[key])
tree.getroot().find('application').set('{http://schemas.android.com/apk/res/android}debuggable', 'true')
tree.write('build/AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
MANIFEST
# Package launcher artwork and strings, and generate the R class before Java compilation.
"$BT/aapt" package -f -m -M build/AndroidManifest.xml -I "$SDK" \
  -S app/src/main/res -A build/license-assets -J build/generated-sources -F build/unsigned.apk
python3 - <<'PY'
from pathlib import Path
files = sorted(Path('app/src/main/java').rglob('*.java'))
if not files:
    raise SystemExit('No Java sources found')
files += sorted(Path('build/generated-sources').rglob('*.java'))
Path('build/java-sources.txt').write_text('\n'.join('"' + str(p) + '"' for p in files) + '\n')
PY
# Keep the JDK bootstrap classes: Android SDK stubs intentionally omit the
# LambdaMetafactory methods javac needs. D8 below desugars Java 8 lambdas
# and validates against the Android API library for the device runtime.
javac -encoding UTF-8 -source 8 -target 8 -classpath "$SDK:$PDF_DEPS/*" \
  -d build/classes @build/java-sources.txt
# d8 supports response files, avoiding command line limits as sources grow.
find "$ROOT/build/classes" -name '*.class' -print | sort > build/dex-sources.txt
find "$PDF_DEPS" -maxdepth 1 -name '*.jar' -print | sort >> build/dex-sources.txt
"$BT/d8" --min-api 23 --lib "$SDK" --output build/dex @build/dex-sources.txt
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
with ZipFile('build/unsigned.apk', 'a', compression=ZIP_DEFLATED) as apk:
    for dex in sorted(Path('build/dex').glob('*.dex')):
        apk.write(dex, dex.name)
PY
"$BT/zipalign" -p -f 4 build/unsigned.apk build/aligned.apk
# Shared with Gradle; an existing development identity is never overwritten.
bash scripts/prepare-debug-key.sh
"$BT/apksigner" sign --ks .tools/debug.keystore --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android --out build/scriflow-debug.apk build/aligned.apk
"$BT/apksigner" verify --verbose build/scriflow-debug.apk
"$BT/aapt" dump badging build/scriflow-debug.apk | head -n 3
printf '%s\n' "Built $ROOT/build/scriflow-debug.apk"
