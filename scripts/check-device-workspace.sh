#!/usr/bin/env bash
# Real signed-release UI checks using uniquely named disposable QA notebooks.
# Default checks do not edit/remove existing notes. Explicit cleanupNames only moves
# allowlisted disposable QA notes to Trash. The same-signer helper APK is removed at exit.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
APP="app/build/outputs/apk/release/app-release.apk"
CLASSES="app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes"
BT="$ROOT/.tools/build-tools-30.0.3"
SDK="$ROOT/.tools/platform-29/android.jar"
[[ -f "$APP" && -d "$CLASSES" ]] || { echo 'Build the signed release with scripts/build-release.sh first.' >&2; exit 1; }
if [[ -z "${SCRIFLOW_KEYSTORE:-}" && -f .tools/signing.env ]]; then
  set -a
  source .tools/signing.env
  set +a
fi
for variable in SCRIFLOW_KEYSTORE SCRIFLOW_KEY_ALIAS SCRIFLOW_STORE_PASSWORD SCRIFLOW_KEY_PASSWORD; do
  [[ -n "${!variable:-}" ]] || { printf 'Missing %s\n' "$variable" >&2; exit 1; }
done
mkdir -p build/device-qa/classes build/device-qa/dex artifacts/device-workspace
javac -encoding UTF-8 -source 8 -target 8 -classpath "$SDK:$CLASSES" -d build/device-qa/classes tools/NotebookDeviceCheck.java
python3 - <<'PY'
from pathlib import Path
Path('build/device-qa/classes.txt').write_text('\n'.join(str(p) for p in Path('build/device-qa/classes').rglob('*.class'))+'\n')
Path('build/device-qa/AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.eink.scriflowqa">
<uses-sdk android:minSdkVersion="29" android:targetSdkVersion="29"/>
<application android:label="Scriflow device QA" android:debuggable="false"/>
<instrumentation android:name="org.eink.inkprobe.notebook.NotebookDeviceCheck" android:targetPackage="org.eink.inkprobe" android:functionalTest="true"/>
</manifest>''')
PY
"$BT/d8" --min-api 29 --lib "$SDK" --classpath "$APP" --output build/device-qa/dex @build/device-qa/classes.txt
"$BT/aapt" package -f -M build/device-qa/AndroidManifest.xml -I "$SDK" -F build/device-qa/unsigned.apk
python3 - <<'PY'
from zipfile import ZipFile
with ZipFile('build/device-qa/unsigned.apk','a') as z:z.write('build/device-qa/dex/classes.dex','classes.dex')
PY
"$BT/zipalign" -f 4 build/device-qa/unsigned.apk build/device-qa/aligned.apk
"$BT/apksigner" sign --ks "$SCRIFLOW_KEYSTORE" --ks-key-alias "$SCRIFLOW_KEY_ALIAS" --ks-pass env:SCRIFLOW_STORE_PASSWORD --key-pass env:SCRIFLOW_KEY_PASSWORD --out build/device-qa/qa.apk build/device-qa/aligned.apk
"$BT/apksigner" verify --print-certs build/device-qa/qa.apk > build/device-qa/certificate.txt
python3 - <<'PY'
from pathlib import Path
import re
s=Path('build/device-qa/certificate.txt').read_text()
actual=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)',s).group(1).lower()
assert actual==Path('release-signing-certificate.sha256').read_text().strip().lower(),'QA signer mismatch'
PY
trap 'adb -d uninstall org.eink.scriflowqa >/dev/null 2>&1 || true' EXIT
adb -d install --no-incremental -r build/device-qa/qa.apk
adb -d shell am instrument -w "$@" org.eink.scriflowqa/org.eink.inkprobe.notebook.NotebookDeviceCheck | tee artifacts/device-workspace/result.txt
python3 - <<'PY'
from pathlib import Path
s=Path('artifacts/device-workspace/result.txt').read_text()
assert 'QA COMPLETE' in s and 'AssertionError' not in s and 'Exception:' not in s,'Device checks failed; see result.txt'
import re,subprocess
match=re.search(r'output=(/storage/emulated/0/Android/data/org\.eink\.inkprobe/files/qa-[0-9]+)',s)
if match: subprocess.run(['adb','-d','pull',match.group(1),'artifacts/device-workspace/'],check=True)
PY
