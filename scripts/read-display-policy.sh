#!/usr/bin/env bash
# Query effective vendor policy from shell UID. Does not change display settings.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p build/policy/classes build/policy/dex
javac -source 8 -target 8 -d build/policy/classes tools/InkPolicy.java
.tools/build-tools-30.0.3/d8 --min-api 29 --output build/policy/dex build/policy/classes/InkPolicy.class
python3 - <<'PY'
from zipfile import ZipFile
with ZipFile('build/policy/probe.jar', 'w') as archive:
    archive.write('build/policy/dex/classes.dex', 'classes.dex')
PY
REMOTE="/data/local/tmp/inkprobe-policy-$$.jar"
trap 'adb -d shell rm -f "$REMOTE" >/dev/null 2>&1 || true' EXIT
adb -d push build/policy/probe.jar "$REMOTE" >/dev/null
adb -d shell "CLASSPATH=$REMOTE app_process /system/bin InkPolicy"
