# Preparing a Scriflow release

The local build workflow prepares artifacts without publishing or installing
them. The first public beta, [0.28.0-beta.1](https://github.com/xing-phys/Scriflow/releases/tag/v0.28.0-beta.1),
was published as **Scribblow** on 2026-10-26 after the owner's tablet acceptance.
**Scriflow 0.28.0-beta.2** was published on 2026-10-26 with the owner’s explicit
authorization. **Scriflow 0.29.0-dev.2** follows on the same date after the owner's
tablet acceptance and explicit release authorization. Publishing is a separate
step described below.

**Scriflow 0.29.0-dev.3** is an explicitly authorized development prerelease
of the floating dock/status UI update. No tablet is connected for this release;
physical acceptance remains pending and is disclosed in the release notes.

**Scriflow 0.30.0-dev.1** is the next explicitly authorized development
prerelease, adding paper sizes, zoom, and screen orientation settings. No tablet
is connected for this release either; physical acceptance remains pending.

## Before publishing

- Retain the [MIT License](../LICENSE) and applicable
  [third-party notices](../THIRD_PARTY_NOTICES.md) with source distributions.
- Confirm the permanent application ID, currently `org.eink.inkprobe`, and
  preserve the dedicated release signing identity originally selected for
  Scribblow and retained for Scriflow. Its public certificate SHA-256 is recorded in
  [release-signing-certificate.sha256](../release-signing-certificate.sha256).
  Keep a secure independent backup of the private key and credentials. Future
  updates require signing continuity.
- Preserve the existing development key and verified editable backups before
  migrating any current installation. A new public key cannot silently update
  the app signed by the old development key.
- Keep the README download link current. Any screenshots added should use
  disposable notes rather than personal handwriting or private notebooks.

## Signing configuration

Release signing is explicitly separate from debug signing. Gradle uses these
environment variables:

| Variable | Meaning |
| --- | --- |
| `SCRIFLOW_KEYSTORE` | Existing private keystore path; absolute or relative to the project root |
| `SCRIFLOW_KEY_ALIAS` | Alias of the selected signing key |
| `SCRIFLOW_STORE_PASSWORD` | Keystore password |
| `SCRIFLOW_KEY_PASSWORD` | Signing-key password |

The `SCRIFLOW_*` names are preferred. For existing private setups, a complete
legacy `SCRIBBLOW_*` set is accepted only when **no** `SCRIFLOW_*` variables are
set. Never mix the two variable families. If any new variable is present, all
four new variables must be nonempty; legacy values cannot fill missing entries.

With **neither family** set, `assembleRelease` produces
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
with its existing alias `scribblow`, and `.tools/signing.env`. The filename
and alias deliberately retain the former name: preserving the same key is what
allows Scriflow to update the public Scribblow beta. The local environment file
now uses `SCRIFLOW_*` variables. Restore these private files on a new computer
and adjust the keystore path if necessary; do not generate a replacement key
for public updates. These files are ignored by Git and restricted to the owner.
The public fingerprint file is safe to include in the repository;
the release helper checks that the configured key matches it. Contributors can
build unsigned release APKs without possessing the permanent key.

## Version numbers

The current source and public prerelease version is **0.30.0-dev.2**, with Android
`versionCode` **35**, adding page outlines, direct sensor rotation, and palm/pinch
safeguards. Version **0.30.0-dev.1** (`versionCode` **34**) introduced physical
paper sizes, zoom, and orientation preferences. The owner explicitly requested implementation, commit,
push, and APK publication on 2026-10-26. Physical validation remains pending.
Publish the verified staged file without rebuilding or changing its internal
version. The previous **0.29.0-dev.3** (`versionCode` **33**) introduced compact
tools and floating status islands with tablet validation pending. Earlier
**Scriflow 0.29.0-dev.2** (`versionCode` **32**) was accepted on the tablet and
released for lasso selection and page management.
**Scriflow 0.28.0-beta.2** (`versionCode` **30**) and **Scribblow 0.28.0-beta.1**
(`versionCode` **29**) remain available with their original APKs and checksums.
The notebook features are implemented, while broader usage and sustained
performance still need testing. The prerelease status reflects those limits.

Release names use `MAJOR.MINOR.PATCH` with an optional suffix such as
`-beta.1` or `-rc.1`. The earlier two-part development versions remain in the
history. Further beta builds can use `0.29.0-beta.1`, followed by a release
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
   `build/releases/`. For this release these are `scriflow-0.30.0-dev.2.apk` and
   `scriflow-0.30.0-dev.2.apk.sha256`. Debugging is disabled, diagnostic screens are
   absent from the manifest, and the library is the only exported component.

4. Compare the printed signer certificate SHA-256 with the intended permanent
   certificate. For later builds, pin that known digest during verification:

   ```bash
   python3 scripts/verify-apk.py build/releases/scriflow-0.30.0-dev.2.apk --certificate-sha256 YOUR_KNOWN_CERTIFICATE_SHA256
   (cd build/releases && sha256sum -c scriflow-0.30.0-dev.2.apk.sha256)
   ```

   Substitute the current version and the actual known digest. The APK file's
   SHA-256 checksum and the signer's certificate SHA-256 identify different
   things; publish both clearly in the release notes.

The verifier checks version, application ID, API 29, Scriflow label/icon,
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
- [ ] Tap the grip to collapse/expand in both orientations, drag the collapsed
  grip, reopen to check remembered state, and check writing around both status
  islands and across the former header area.
- [ ] Export PDF to an actual provider; inspect every test page and pressure
  shape. Cancel/retry the picker and exercise a failed destination write.
- [ ] Save an editable `.inkbook` to an actual provider, restore it as a new
  notebook, and compare page order, names, writing, and styles. Cancel/retry
  the picker and confirm failure does not replace an existing notebook.
- [ ] Repeat write/reopen and scrolling after a longer writing session. Report
  memory/performance observations without claiming untested maximum capacity.
- [ ] Complete the paper-size, zoom, and rotation checks in
  [the 0.30 feature record](paper-zoom-orientation.md), including PDF dimensions,
  backup round trips, writing near page margins, and rotation after pen lift.

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
   files can still be accessed outside the app.
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

For **0.30.0-dev.2**, the owner requested follow-up fixes to the previous
explicitly authorized implementation and APK release workflow. Publish as a development prerelease with tablet
validation pending. This authorization does not establish physical acceptance.
Keep that limitation visible in the release, README, and device record; do not
carry over an earlier APK's acceptance to this build.

Review the exact files selected for source distribution: exclude private keys,
filled signing environment files, `.tools/`, build outputs, personal device
captures, and recovery copies. Include the MIT license and third-party notices,
preserve the selected key, and record remaining limitations. Any screenshots
must use disposable notes.

Mark a GitHub development or beta release as a **pre-release** and use its full version in the
tag and title, such as `v0.29.0-dev.2`. This identifies it as a test build
without changing the ordinary APK installation process.

When publishing is explicitly authorized, attach only the accepted signed APK
and its checksum to the release, document its signer certificate, compatibility,
installation/backup procedure and known limits, and add the real release link
to the README. Keep the permanent signing key private. The local build helper
performs none of those publishing actions.
