#!/usr/bin/env bash
# Prepare a local development identity without ever replacing an existing key.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
DESTINATION="$ROOT/.tools/debug.keystore"
if [[ -e "$DESTINATION" ]]; then
  [[ -f "$DESTINATION" && -s "$DESTINATION" ]] || {
    echo 'The existing .tools/debug.keystore is not a usable file; it was preserved.' >&2
    exit 1
  }
  chmod go-rwx "$DESTINATION"
  exit 0
fi
umask 077
mkdir -p "$ROOT/.tools"
STAGE="$(mktemp -d "$ROOT/.tools/debug-key.XXXXXX")"
trap 'rm -rf "$STAGE"' EXIT
KEYTOOL=keytool
if [[ -n "${JAVA_HOME:-}" ]]; then KEYTOOL="$JAVA_HOME/bin/keytool"; fi
"$KEYTOOL" -genkeypair -keystore "$STAGE/debug.keystore" -storetype JKS \
  -storepass android -keypass android -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname 'CN=Android Debug,O=Scribblow,C=US' >/dev/null 2>&1
# Linking is atomic and cannot overwrite a key created by a concurrent build.
if ! ln "$STAGE/debug.keystore" "$DESTINATION" 2>/dev/null; then
  [[ -f "$DESTINATION" && -s "$DESTINATION" ]] || {
    echo 'Could not publish the development key.' >&2; exit 1;
  }
fi
printf '%s\n' 'Local development signing key ready; keep it to preserve update compatibility.'
