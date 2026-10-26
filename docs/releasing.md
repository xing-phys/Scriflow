# Preparing a Scribblow release

This workflow prepares artifacts locally. It does **not** push source, create
a GitHub release, upload an APK, or install an app on a tablet. The repository
has not yet published its first public release.

## Before the first public release

- Retain the [MIT License](../LICENSE) and applicable
  [third-party notices](../THIRD_PARTY_NOTICES.md) with source distributions.
- Confirm the permanent application ID, currently `org.eink.inkprobe`, and
  preserve the dedicated release signing identity selected for Scribblow. Its
  public certificate SHA-256 is recorded in
  [release-signing-certificate.sha256](../release-signing-certificate.sha256).
  Keep a secure independent backup of the private key and credentials. Future
  updates require signing continuity.
- Preserve the existing development key and verified editable backups before
  migrating any current installation. A new public key cannot silently update
  the app signed by the old development key.
- Add authentic screenshots made with disposable notes and the actual download
  link after the repository/release exists. No placeholder release URL is used.

## Signing configuration

Release signing is explicitly separate from debug signing. Gradle uses these
environment variables:

| Variable | Meaning |
| --- | --- |
| `SCRIBBLOW_KEYSTORE` | Existing private keystore path; absolute or relative to the project root |
| `SCRIBBLOW_KEY_ALIAS` | Alias of the selected signing key |
| `SCRIBBLOW_STORE_PASSWORD` | Keystore password |
| `SCRIBBLOW_KEY_PASSWORD` | Signing-key password |

With **none** set, `assembleRelease` produces
`app/build/outputs/apk/release/app-release-unsigned.apk`. With **all four** set,
it produces signed `app/build/outputs/apk/release/app-release.apk`. Any partial
configuration fails, including empty values. The release helper requires all
four; it never falls back to a development key or silently creates a key.

Keep the keystore and credentials out of source control. `signing.env.example`
contains placeholders only. For a local setup, copy it into ignored `.tools/`
and fill in the real values privately:

```bash
mkdir -p .tools
cp signing.env.example .tools/signing.env
chmod 600 .tools/signing.env
```

Edit `.tools/signing.env`, keeping values valid shell assignments. Quote passwords
as shell literals; use single quotes for values containing `$` or backticks so
loading the file does not expand them. Then load it in your shell:

```bash
set -a
source .tools/signing.env
set +a
```

Do not paste credentials into issue reports, command histories, tracked files,
or public build logs. Keep the permanent private key in a protected location
with an independent recovery copy.

The owner's local release setup uses `.tools/release-signing/scribblow-release.keystore`
and `.tools/signing.env`. These private files are ignored by Git and restricted
to the owner. The public fingerprint file is safe to include in the repository;
the release helper checks that the configured key matches it. Contributors can
build unsigned release APKs without possessing the permanent key.

## Version numbers

The current beta candidate is **0.28.0-beta.1**, with Android `versionCode` **29**.
Its core notebook features are implemented, while broader usage, sustained
performance, and acceptance of the final signed APK still need testing. Beta
expresses that status without claiming the feature freeze and confidence of a
release candidate or a stable 1.0 release.

Release names use `MAJOR.MINOR.PATCH` with an optional suffix such as
`-beta.1` or `-rc.1`. The earlier two-part development versions remain in the
history. Future beta builds can use `0.28.0-beta.2`, followed by a release
candidate once changes settle. Every new distributed APK must increase
`versionCode`, including when only the prerelease suffix changes. Moving to
1.0.0 will be a separate decision about the supported core experience, not a
requirement for GitHub publication. See [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
and [Android versioning](https://developer.android.com/studio/publish/versioning).

## Build and verify

1. Update `app/version.properties` with a higher `versionCode` and the intended
   `versionName`; summarize user-visible changes in `CHANGELOG.md`.
2. Bootstrap and run contributor checks before loading signing credentials:

   ```bash
   ./scripts/bootstrap-gradle.sh
   JAVA_HOME="$PWD/.tools/jdk-17.0.21+9" ./gradlew hostTest :app:lint :app:assembleDebug :app:assembleRelease
   ```

3. Load the chosen signing environment and build the candidate:

   ```bash
   ./scripts/build-release.sh
   ```

   Use `./scripts/build-release.sh --offline` once Gradle dependencies are cached.
   The helper runs release lint and assembly, copies the candidate privately,
   verifies those exact bytes and the configured signing certificate, then
   stages a versioned APK and SHA-256 checksum under
   `build/releases/`. For this beta these are `scribblow-0.28.0-beta.1.apk` and
   `scribblow-0.28.0-beta.1.apk.sha256`. Debugging is disabled, diagnostic screens are
   absent from the manifest, and the library is the only exported component.

4. Compare the printed signer certificate SHA-256 with the intended permanent
   certificate. For later builds, pin that known digest during verification:

   ```bash
   python3 scripts/verify-apk.py build/releases/scribblow-0.28.0-beta.1.apk --certificate-sha256 YOUR_KNOWN_CERTIFICATE_SHA256
   (cd build/releases && sha256sum -c scribblow-0.28.0-beta.1.apk.sha256)
   ```

   Substitute the current version and the actual known digest. The APK file's
   SHA-256 checksum and the signer's certificate SHA-256 identify different
   things; publish both clearly in the release notes.

The verifier checks version, application ID, API 29, Scribblow label/icon,
`ACCESS_NETWORK_STATE` as the only permission, disabled automatic backup,
component exposure, debugging state, and cryptographic signature validity.
Those checks do not establish the tablet behaviors below.

## Tablet acceptance

Run on **the exact signed candidate** on MatePad Paper HMW-W09, firmware
`HMW-W09 2.1.0.222(C00E170R1P8)`, Android 10/API 29. Record APK checksum, signer
digest, version, device/firmware, and which steps passed. Use a disposable
notebook and keep real notes backed up.

- [ ] Install/update with the intended certificate; the existing library remains.
- [ ] Check the launcher name and icon, notebook creation, renaming, page
  addition, and blank/lined paper.
- [ ] Write at several widths, wait for saving, close/reopen, and continue writing.
- [ ] Erase at normal and large widths; check trail cleanup, undo, and redo.
- [ ] Scroll with a finger and write immediately after release on both visible
  pages; check for flashes, inversion, freezes, and misplaced strokes.
- [ ] Move/rotate tools, adjust widths, open/close the overview, and jump pages.
- [ ] Export PDF to an actual provider; inspect every test page and pressure
  shape. Cancel/retry the picker and exercise a failed destination write.
- [ ] Save an editable `.inkbook` to an actual provider, restore it as a new
  notebook, and compare page order, names, writing, and styles. Cancel/retry
  the picker and confirm failure does not replace an existing notebook.
- [ ] Repeat write/reopen and scrolling after a longer writing session. Report
  memory/performance observations without claiming untested maximum capacity.

Earlier physical results are retained in the [device record](device-testing.md)
and [refresh record](fast-refresh.md). They do not replace acceptance of a new
APK. Synthetic debug checks are described in [Building](building.md).

## Moving from a development key

If the public certificate differs from the installed development certificate,
Android rejects an ordinary update. Plan the migration before removing anything:

1. Keep the current APK and its development key. In the installed app, save
   **every notebook** as an editable `.inkbook` outside private app storage.
   Retain a second independent copy; optional PDFs provide readable copies too.
2. Restore each backup into the current app as a separate notebook and check its
   pages, or verify it on another supported installation. Confirm the exported
   files can still be accessed outside Scribblow.
3. Only after those copies have been verified, deliberately uninstall the old
   installation if required to install the chosen public identity. Uninstalling
   erases private notebooks and settings; this step is irreversible without
   usable independent backups.
4. Install the new APK and restore the verified backups. Check notebook contents
   again before removing any recovery copy. Undo history and device preferences
   are not included in backups.

Do not use raw private version-1 files as a portable migration format, downgrade
an APK over version-2 pages, or assume a signature change preserves app data.

## Publish only after acceptance

Review the exact files selected for source distribution: exclude private keys,
filled signing environment files, `.tools/`, build outputs, personal device
captures, and recovery copies. Include the MIT license and third-party notices,
preserve the selected key, add genuine screenshots, and record remaining limitations.

Mark a GitHub beta release as a **pre-release** and use its full version in the
tag and title, such as `v0.28.0-beta.1`. This identifies it as a test build
without changing the ordinary APK installation process.

When publishing is explicitly authorized, attach only the accepted signed APK
and its checksum to the release, document its signer certificate, compatibility,
installation/backup procedure and known limits, and add the real release link
to the README. Keep the permanent signing key private. The local build helper
performs none of those publishing actions.
