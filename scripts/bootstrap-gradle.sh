#!/usr/bin/env bash
# Local Gradle toolchain; does not alter system Java.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
./scripts/bootstrap.sh
mkdir -p .tools/cache
python3 - <<'PY'
import hashlib, subprocess, tarfile, zipfile
from pathlib import Path
packages = [
 ('temurin17.tar.gz', 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz',
  'sha256', '8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711', '.tools/jdk-17.0.21+9'),
]
for name, url, algorithm, checksum, installed in packages:
    archive = Path('.tools/cache') / name
    if not archive.exists():
        partial = archive.with_suffix(archive.suffix + '.part')
        subprocess.run(['curl','--fail','--location','--retry','3','--connect-timeout','20',url,'-o',str(partial)],check=True)
        partial.rename(archive)
    if hashlib.new(algorithm, archive.read_bytes()).hexdigest() != checksum:
        raise SystemExit('Checksum mismatch: ' + str(archive))
    if not Path(installed).exists():
        if name.endswith('.tar.gz'):
            with tarfile.open(archive) as package: package.extractall('.tools')
        else:
            with zipfile.ZipFile(archive) as package: package.extractall('.tools/sdk-build35')
PY
mkdir -p .tools/android-sdk/platforms .tools/android-sdk/build-tools
ln -sfn ../../platform-29 .tools/android-sdk/platforms/android-29
if command -v adb >/dev/null; then
  PLATFORM_TOOLS="$(dirname "$(readlink -f "$(command -v adb)")")"
  if [[ -f "$PLATFORM_TOOLS/source.properties" ]]; then
    ln -sfn "$PLATFORM_TOOLS" .tools/android-sdk/platform-tools
  fi
fi
if [[ ! -f local.properties ]]; then printf 'sdk.dir=%s/.tools/android-sdk\n' "$ROOT" > local.properties; fi
bash scripts/bootstrap-browser-sdk.sh
printf '%s\n' 'Ready. Run: JAVA_HOME="$PWD/.tools/jdk-17.0.21+9" ./gradlew :app:assembleDebug hostTest'
