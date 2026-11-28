#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p build/share-tests
SOURCE=app/src/main/java/org/eink/inkprobe/notebook
javac -encoding UTF-8 -source 8 -target 8 -d build/share-tests \
  "$SOURCE/NotebookLocalHttp.java" "$SOURCE/NotebookLocalSessions.java" \
  "$SOURCE/NotebookShareServer.java" "$SOURCE/NotebookDownloadName.java" \
  "$SOURCE/NotebookBrowserAddress.java" tests/NotebookShareServerTest.java
java -ea -cp build/share-tests org.eink.inkprobe.notebook.NotebookShareServerTest
node --check app/src/main/assets/share/share.js
