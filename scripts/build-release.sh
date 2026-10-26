#!/usr/bin/env bash
# Prepare a verified, versioned release APK locally. This does not publish it.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for variable in SCRIBBLOW_KEYSTORE SCRIBBLOW_KEY_ALIAS SCRIBBLOW_STORE_PASSWORD SCRIBBLOW_KEY_PASSWORD; do
  [[ -n "${!variable:-}" ]] || {
    printf 'Missing %s. See docs/releasing.md.\n' "$variable" >&2; exit 1;
  }
done
[[ -f "$SCRIBBLOW_KEYSTORE" ]] || { echo 'The release keystore file does not exist.' >&2; exit 1; }
if [[ -z "${JAVA_HOME:-}" && -x .tools/jdk-17.0.21+9/bin/java ]]; then
  export JAVA_HOME="$ROOT/.tools/jdk-17.0.21+9"
fi
./gradlew --no-daemon "$@" :app:lintRelease :app:assembleRelease
python3 - <<'PY'
from pathlib import Path
import hashlib
import os
import re
import shutil
import subprocess
import tempfile

version = dict(line.split('=', 1) for line in Path('app/version.properties').read_text().splitlines()
               if '=' in line)['versionName']
if not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._+-]*', version):
    raise SystemExit('Version name is not safe for a release filename')
directory = Path('build/releases')
directory.mkdir(parents=True, exist_ok=True)
destination = directory / ('scribblow-' + version + '.apk')
fd, temporary = tempfile.mkstemp(prefix='.release-', dir=directory)
os.close(fd)
try:
    shutil.copyfile('app/build/outputs/apk/release/app-release.apk', temporary)
    # Verify the staged bytes, even if another Gradle build replaces its output.
    keytool = str(Path(os.environ['JAVA_HOME']) / 'bin/keytool') if os.environ.get('JAVA_HOME') else 'keytool'
    certificate = subprocess.run([
        keytool, '-exportcert', '-keystore', os.environ['SCRIBBLOW_KEYSTORE'],
        '-alias', os.environ['SCRIBBLOW_KEY_ALIAS'],
        '-storepass:env', 'SCRIBBLOW_STORE_PASSWORD',
    ], capture_output=True, check=False)
    if certificate.returncode:
        raise SystemExit('Cannot read the configured signing certificate; no release was staged.')
    expected = hashlib.sha256(certificate.stdout).hexdigest()
    pinned = Path('release-signing-certificate.sha256')
    if pinned.is_file() and pinned.read_text().strip().lower() != expected:
        raise SystemExit('Configured key does not match the permanent release certificate; no release was staged.')
    subprocess.run([
        'python3', 'scripts/verify-apk.py', temporary, '--variant', 'release',
        '--signature', 'signed', '--certificate-sha256', expected,
    ], check=True)
    digest = hashlib.sha256(Path(temporary).read_bytes()).hexdigest()
    os.replace(temporary, destination)
finally:
    if os.path.exists(temporary): os.unlink(temporary)
checksum = destination.with_suffix('.apk.sha256')
checksum.write_text(digest + '  ' + destination.name + '\n')
print('Prepared: ' + str(destination.resolve()))
print('Checksum: ' + str(checksum.resolve()))
PY
