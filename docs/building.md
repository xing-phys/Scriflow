# Building Scriflow

The Gradle build and development wrapper compile the same Java sources and resources. They read
`app/version.properties`, retain application ID `org.eink.inkprobe`, and target
Android API 29 with Java 8 bytecode. Building successfully does not establish
support for another device: native writing and pressure rendering require the
[tested Huawei firmware](../README.md#compatibility-and-status).

Both routes package the root [MIT license](../LICENSE) as `assets/LICENSE` in
the APK, together with the PDF runtime's upstream licenses and notices. The
verification helper checks that the packaged MIT license matches the source
exactly. Runtime dependency attribution is in [Third-party notices](../THIRD_PARTY_NOTICES.md).

## Prerequisites

The bootstrap scripts support **Linux x86_64**. Install a JDK providing `java`,
`javac`, and `keytool`, plus Python 3.8+, curl, and unzip. ADB is optional for USB
installation and device checks. Android Studio is not required.

Markdown asset compilation also requires **Node.js 20+ and npm**. The first
build downloads the lockfile-pinned renderer packages and commit/checksum-pinned
fonts into `.tools/markdown-renderer`; subsequent cached builds work offline.
Both builders call `scripts/build-markdown-assets.sh`. Host renderer/export
tests additionally require Google Chrome (override its executable with
`CHROME_BIN`) and Poppler's `pdftotext` (`poppler-utils` on Ubuntu).
Host AI protocol/lifecycle tests additionally cache checksum-pinned JSON-java
20240303 in `.tools/ai-tests` on the first run. Android uses built-in `org.json`;
this test dependency is not included in the APK. Tests use connection fixtures
and do not need provider credentials or make paid API requests.

Tool downloads are version-pinned and checksum-verified. They remain in ignored
`.tools/`; project state and generated artifacts are ignored too. The first
bootstrap and Gradle dependency resolution need network access. A cached build
can run without it. Runtime APKs include PDFBox Android **2.0.27.0** and its
Bouncy Castle **1.72** dependencies for preserving source PDFs and appending
vector handwriting. They contain no bundled Huawei firmware code. Rendering
PDF pages for display uses Android's native PDF renderer.

## Gradle: contributor and IDE route

```bash
./scripts/bootstrap-gradle.sh
JAVA_HOME="$PWD/.tools/jdk-17.0.21+9" ./gradlew hostTest :app:lint :app:assembleDebug :app:assembleRelease
```

The bootstrap installs local JDK 17, SDK platform 37.2 and Build Tools 36.0.0,
plus platform 29 for host PDF fixtures. It writes ignored `local.properties` if
absent. Gradle is pinned to 9.6.0 and the Android plugin to 9.5.1. The modern
compile SDK satisfies GeckoView; minSdk and targetSdk remain 29. Set the IDE's Gradle JDK to the same
local JDK when importing the project. Nothing changes the system Java install.

`hostTest` runs the core tests, offline browser Markdown checks and PDF
import/export structural tests in `scripts/test.sh`. The PDF tests execute the
shipped PDFBox Android bytecode with host logging/bitmap/AssetManager adapters
and the exact AAR font/CMap resources. Markdown fixtures run the packaged assets
in Chrome and pass through the production exporter, with Poppler checking text.
Native display/input still requires Android.
Full lint
is a separate check from APK assembly. The API-29 target is deliberate for this
firmware-specific sideloaded app; raising it requires device acceptance.

With all release-signing environment variables unset, contributor builds need
no private credentials and produce:

| Artifact | Purpose |
| --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | Installable development APK, local debug key, debugging and diagnostics enabled |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | Release packaging check, debugging disabled, unsigned and not installable as a release |

The release is signed only when all four variables described in
[Releasing](releasing.md#signing-configuration) are set. A partially configured
signing environment fails clearly instead of choosing a key. Unset those
variables when you intend to run the unsigned contributor checks.

After dependencies are cached, add `--offline`:

```bash
JAVA_HOME="$PWD/.tools/jdk-17.0.21+9" ./gradlew --offline hostTest :app:lint :app:assembleDebug :app:assembleRelease
```

## Development wrapper

```bash
./scripts/bootstrap-gradle.sh
./scripts/test.sh
./scripts/build.sh
```

The wrapper now calls Gradle so GeckoView's AAR resources, native libraries,
AndroidX dependencies and process services are merged correctly. It writes
**`build/scriflow-debug.apk`** and verifies it. The former manual Java/D8 packager
cannot package the bundled browser engine. After caching all dependencies,
Gradle also supports offline builds. This is always a development APK with
debugging enabled, not the public release route.

The browser engine is pinned to GeckoView **160.0.20261104181552** from Mozilla's
Maven repository. New offline capture assets use the lockfile in
`renderer/browser`; their licenses and SHA-256 inventory ship in every APK.
The browser engine makes APKs larger. Owner releases package compressed ARM64
and ARMv7 libraries; development APKs also include x86_64 for emulator checks. See
[third-party notices](../THIRD_PARTY_NOTICES.md) for source and license details.

Both debug build routes call `scripts/prepare-debug-key.sh`. It preserves an
existing `.tools/debug.keystore`; on a clean checkout it creates a new ignored
local development key. Private key files are restricted to their owner. Keep
that file to continue updating your own debug
installation. A different checkout's generated key will not update an existing
installation signed by another key. Never commit private keys.

## Inspect and install

The verification helper checks the packaged name, icon, version, application
ID, API level, exact permission set, component exposure, debugging flag, bundled
browser engine/capture assets and signature state:

```bash
python3 scripts/verify-apk.py build/scriflow-debug.apk --variant debug
python3 scripts/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --signature unsigned
```

Back up important notebooks before installing. Install the development APK
over an existing installation only if its signature matches:

```bash
adb -d install --no-incremental -r build/scriflow-debug.apk
adb -d shell am start -W -n org.eink.inkprobe/.notebook.NotebookLibraryActivity
./scripts/read-display-policy.sh
```

If Android reports a signature mismatch, stop and use the
[migration procedure](releasing.md#moving-from-a-development-key). Do not
uninstall first: doing so deletes private notes.

## Device verification

Debug builds expose isolated native restoration checks; release builds do not
declare those diagnostic screens. For example, with a debug APK installed:

```bash
adb -d shell am start -S -W -n org.eink.inkprobe/.notebook.NotebookSelfTestActivity --es template lined --ez inPlaceEdits true --ez scrollRefresh true
adb -d exec-out run-as org.eink.inkprobe cat files/notebook-selftest/result.json
```

The debug-only browser fixture runs the production browser and writes native
history/scroll and online rendered-DOM observations to `files/browser-test.json`:

```bash
adb -s emulator-5580 shell am start -n org.eink.inkprobe/.notebook.NotebookBrowserSelfTestActivity --es websiteUrl https://github.com/torvalds/linux
adb -s emulator-5580 exec-out run-as org.eink.inkprobe cat files/browser-test.json
```

For a saved page, replace the URL extra with `--es savedWebsiteId SAVE_UUID`.
Follow-up single-top intents can exercise `--es fragment SECTION_ID`,
`--es click Back`, `--es click Forward`, and `--ez recreate true`. This fixture
is absent from release APKs; online DOM observations use only the app-owned
isolated extension, without exposing an API to pages. Offline checks keep page
JavaScript disabled and report native history/scroll state instead. Always
choose the intended test device explicitly.

Repeat with `--es template blank`. These checks use synthetic drawings under
`files/notebook-selftest/`, separate from actual notebooks. Further diagnostics
are in the [ink probe guide](ink-probe.md),
[refresh investigation](fast-refresh.md), and [device record](device-testing.md).
Host and synthetic checks do not establish physical pen latency or the absence
of visible transitions. Complete the [physical release checks](releasing.md#tablet-acceptance)
on the actual signed APK before distribution.
