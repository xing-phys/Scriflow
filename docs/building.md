# Building Scriflow

Both build routes compile the same Java sources and resources. They read
`app/version.properties`, retain application ID `org.eink.inkprobe`, and target
Android API 29 with Java 8 bytecode. Building successfully does not establish
support for another device: native writing and pressure rendering require the
[tested Huawei firmware](../README.md#compatibility-and-status).

Both routes package the root [MIT license](../LICENSE) as `assets/LICENSE` in
the APK. The verification helper checks that the packaged copy matches the
source license exactly.

## Prerequisites

The bootstrap scripts support **Linux x86_64**. Install a JDK providing `java`,
`javac`, and `keytool`, plus Python 3.8+, curl, and unzip. ADB is optional for USB
installation and device checks. Android Studio is not required.

Tool downloads are version-pinned and checksum-verified. They remain in ignored
`.tools/`; project state and generated artifacts are ignored too. The first
bootstrap and Gradle dependency resolution need network access. A cached build
can run without it. Runtime APKs have no third-party dependencies or bundled
Huawei firmware code.

## Gradle: contributor and IDE route

```bash
./scripts/bootstrap-gradle.sh
JAVA_HOME="$PWD/.tools/jdk-17.0.21+9" ./gradlew hostTest :app:lint :app:assembleDebug :app:assembleRelease
```

The bootstrap installs the local JDK 17, SDK platform 29, and Build Tools 35.0.0,
and writes ignored `local.properties` if it does not exist. Gradle is pinned to
8.11.1 and the Android plugin to 8.9.2. Set the IDE's Gradle JDK to the same
local JDK when importing the project. Nothing changes the system Java install.

`hostTest` runs the Android-independent tests in `scripts/test.sh`. Full lint
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

## Standalone development build

```bash
./scripts/bootstrap.sh
./scripts/test.sh
./scripts/build.sh
```

This route uses SDK platform 29 and Build Tools 30.0.3 without Gradle. After
bootstrap it builds offline and writes **`build/scriflow-debug.apk`**. It is
always a development APK with debugging enabled, not the public release route.

Both debug build routes call `scripts/prepare-debug-key.sh`. It preserves an
existing `.tools/debug.keystore`; on a clean checkout it creates a new ignored
local development key. Private key files are restricted to their owner. Keep
that file to continue updating your own debug
installation. A different checkout's generated key will not update an existing
installation signed by another key. Never commit private keys.

## Inspect and install

The verification helper checks the packaged name, icon, version, application
ID, API level, exact permission set, component exposure, debugging flag, and
signature state:

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

Repeat with `--es template blank`. These checks use synthetic drawings under
`files/notebook-selftest/`, separate from actual notebooks. Further diagnostics
are in the [ink probe guide](ink-probe.md),
[refresh investigation](fast-refresh.md), and [device record](device-testing.md).
Host and synthetic checks do not establish physical pen latency or the absence
of visible transitions. Complete the [physical release checks](releasing.md#tablet-acceptance)
on the actual signed APK before distribution.
