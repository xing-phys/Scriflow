#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
command -v node >/dev/null || { echo 'Markdown assets require Node.js 20 or newer.' >&2; exit 1; }
mkdir -p .tools
exec flock .tools/markdown-assets.lock node scripts/build-markdown-assets.cjs
