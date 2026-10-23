#!/usr/bin/env bash
# Portable, pinned Android SDK pieces; everything downloaded stays in .tools/.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for required in curl python3 unzip java javac keytool; do
  command -v "$required" >/dev/null || { echo "Missing prerequisite: $required" >&2; exit 1; }
done
[[ "$(uname -s)" == Linux ]] && [[ "$(uname -m)" == x86_64 ]] || {
  echo 'This bootstrap supports Linux x86_64 only.' >&2; exit 1;
}
mkdir -p .tools/cache
BASE=https://dl.google.com/android/repository
# SHA-1 and sizes are pinned from Google's repository2-1.xml. Verify the current
# HTTPS metadata against those pins before fetching an archive; no silent updates.
if [[ ! -f .tools/cache/repository2-1.xml ]]; then
  curl --fail --location --retry 3 --connect-timeout 20 --max-time 120 \
    "$BASE/repository2-1.xml" -o .tools/cache/repository2-1.xml.part
  mv .tools/cache/repository2-1.xml.part .tools/cache/repository2-1.xml
fi
python3 - <<'PY'
import xml.etree.ElementTree as ET
pins = {
    'platforms;android-29': ('platform-29_r05.zip', 78293913, '9d8a7e0ffa5168dbca6c60355b9129c6c7572aff'),
    'build-tools;30.0.3': ('build-tools_r30.0.3-linux.zip', 53134793, '2076ea81b5a2fc298ef7bf85d666f496b928c7f1'),
}
root = ET.parse('.tools/cache/repository2-1.xml').getroot()
for path, pin in pins.items():
    package = next(p for p in root.findall('remotePackage') if p.get('path') == path)
    archives = package.findall('archives/archive')
    archive = next(a for a in archives if a.findtext('host-os', 'linux') == 'linux')
    complete = archive.find('complete')
    actual = (complete.findtext('url'), int(complete.findtext('size')), complete.findtext('checksum'))
    if actual != pin:
        raise SystemExit(f'Google SDK metadata differs from pinned archive for {path}: {actual}')
print('Verified pinned SDK archives against Google repository metadata.')
PY
fetch() {
  local filename="$1" expected_size="$2" expected_sha="$3"
  if ! python3 - "$filename" "$expected_size" "$expected_sha" <<'PY'
import hashlib, pathlib, sys
p = pathlib.Path('.tools/cache') / sys.argv[1]
if not p.is_file() or p.stat().st_size != int(sys.argv[2]):
    sys.exit(1)
sys.exit(0 if hashlib.sha1(p.read_bytes()).hexdigest() == sys.argv[3] else 1)
PY
  then
    rm -f ".tools/cache/$filename"
    curl --fail --location --retry 3 --connect-timeout 20 --max-time 900 --continue-at - \
      "$BASE/$filename" -o ".tools/cache/$filename.part"
    python3 - "$filename" "$expected_size" "$expected_sha" <<'PY'
import hashlib, pathlib, sys
p = pathlib.Path('.tools/cache') / (sys.argv[1] + '.part')
if p.stat().st_size != int(sys.argv[2]) or hashlib.sha1(p.read_bytes()).hexdigest() != sys.argv[3]:
    p.unlink()
    raise SystemExit('SDK archive checksum/size mismatch; discarded download')
p.rename(p.with_suffix(''))
PY
  fi
}
if [[ ! -f .tools/platform-29/android.jar ]]; then
  fetch platform-29_r05.zip 78293913 9d8a7e0ffa5168dbca6c60355b9129c6c7572aff
  stage="$(mktemp -d "$ROOT/.tools/platform-stage.XXXXXX")"
  unzip -q .tools/cache/platform-29_r05.zip -d "$stage"
  platform_dir="$(dirname "$(find "$stage" -name android.jar -print -quit)")"
  [[ -f "$platform_dir/android.jar" ]] || { echo 'Missing android.jar in archive' >&2; exit 1; }
  mv "$platform_dir" .tools/platform-29
  rm -rf "$stage"
fi
if [[ ! -x .tools/build-tools-30.0.3/aapt ]]; then
  fetch build-tools_r30.0.3-linux.zip 53134793 2076ea81b5a2fc298ef7bf85d666f496b928c7f1
  stage="$(mktemp -d "$ROOT/.tools/build-stage.XXXXXX")"
  unzip -q .tools/cache/build-tools_r30.0.3-linux.zip -d "$stage"
  tools_dir="$(dirname "$(find "$stage" -name aapt -type f -print -quit)")"
  [[ -f "$tools_dir/aapt" ]] || { echo 'Missing aapt in archive' >&2; exit 1; }
  mv "$tools_dir" .tools/build-tools-30.0.3
  rm -rf "$stage"
fi
.tools/build-tools-30.0.3/aapt version
printf '%s\n' 'Android API 29 and Build Tools 30.0.3 ready in .tools/.'
