#!/usr/bin/env bash
# Execute production AI protocol, transport, durable history, and lifecycle code without paid API requests.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
CACHE="$ROOT/.tools/ai-tests"
JSON="$CACHE/json-20240303.jar"
SHA=3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed
mkdir -p "$CACHE" build/ai-tests
if [[ ! -f "$JSON" ]] || ! printf '%s  %s\n' "$SHA" "$JSON" | sha256sum --check --status; then
  curl -fsSL https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar -o "$JSON.tmp"
  printf '%s  %s\n' "$SHA" "$JSON.tmp" | sha256sum --check --status
  mv "$JSON.tmp" "$JSON"
fi
javac -encoding UTF-8 -source 8 -target 8 -cp "$JSON" -d build/ai-tests \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookAiConfig.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookAiModel.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookAiModelLibrary.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookAiAttachment.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookAiConversation.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookAiHistory.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookAiClient.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookAiSession.java \
  tests/NotebookAiTest.java tests/NotebookAiHistoryTest.java tests/NotebookAiModelsTest.java
java -ea -cp "build/ai-tests:$JSON" org.eink.inkprobe.notebook.NotebookAiTest
java -ea -cp "build/ai-tests:$JSON" org.eink.inkprobe.notebook.NotebookAiHistoryTest
java -ea -cp "build/ai-tests:$JSON" org.eink.inkprobe.notebook.NotebookAiModelsTest
