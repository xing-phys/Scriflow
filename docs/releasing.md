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

**0.32.0-dev.1** (`versionCode` **42**) is the compact-layout update for
bookshelf grids, folders/Trash, page insertion and foreground screen-on settings.
The owner explicitly authorizes commit, push and release after automated
verification. Publish as a development prerelease with the permanent signing
identity; tablet installation and acceptance remain pending. Check cover/power sleep and both
orientations on the MatePad before claiming physical acceptance. The library
catalog upgrades to version 2; page and backup formats are unchanged.

**Scriflow 0.31.0-dev.5** (`versionCode` **41**) restores the optimized direct
native AHW live path from `0.30.0-dev.3` after the owner's dev.4 latency report.
Delete the Java-driven second pen and callback bitmap drawing. Preserve PDFs,
stored ink, rotation history, page format and signing identity. Both width
settings use direct AHW; identical pressure across zoom remains unresolved.
Continue the authorized signed APK workflow as a development prerelease. Source
comparison and build tests do not measure physical latency; no tablet is
connected. Staged files: `scriflow-0.31.0-dev.5.apk` and
`scriflow-0.31.0-dev.5.apk.sha256`.

**Scriflow 0.31.0-dev.4** (`versionCode` **40**) preserves rotation history and
adds both requested pen width modes, defaulting to page width. The unchanged
installed native brush receives page-space input in page mode, while screen
mode retains direct AHW with a constant native nib. Publish within the owner's
authorized implementation/push/APK workflow as a development prerelease. Host
contracts and cached firmware analysis do not establish acceptance of the new
live framebuffer bridge; disclose that no tablet is connected. Staged files:
`scriflow-0.31.0-dev.4.apk` and `scriflow-0.31.0-dev.4.apk.sha256`.

**Scriflow 0.31.0-dev.3** (`versionCode` **39**) fixes the owner's blocking
handwriting failure: “Cannot update notebook ink: Could not clip handwriting to
its page.” Draw the complete pressure outline under a Canvas rectangle clip;
remove the fallible boolean path intersection. Rendering errors use “Writing
paused” rather than claiming storage failed. Continue the authorized signed APK
prerelease workflow, with tablet acceptance pending. Staged files:
`scriflow-0.31.0-dev.3.apk` and `scriflow-0.31.0-dev.3.apk.sha256`.

**Scriflow 0.31.0-dev.2** (`versionCode` **38**) follows the owner's PDF testing
feedback on 2026-10-27. It fixes export stream ownership, zoom display tiles,
new-stroke capture geometry, navigation history, external-link confirmation,
and large-PDF import/open overhead. Continue the authorized APK release workflow
as a development prerelease; native handwriting/visual checks and timing on the
tablet remain pending. Preserve the permanent signing identity. Staged files:
`scriflow-0.31.0-dev.2.apk` and `scriflow-0.31.0-dev.2.apk.sha256`.

**Scriflow 0.31.0-dev.1** (`versionCode` **37**) adds PDF notebooks, source
preserving PDF export, editable outlines, and view history. The owner explicitly
requested implementation, commit, push, and APK release on 2026-10-26. Publish
as a development prerelease with physical tablet acceptance pending; no tablet
is connected. Preserve the permanent signing identity. The staged artifacts are
`scriflow-0.31.0-dev.1.apk` and `scriflow-0.31.0-dev.1.apk.sha256`.

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

The current source and public development prerelease is **0.32.0-dev.1**, Android
`versionCode` **42**, for the compact bookshelf and management update. It retains
the direct native handwriting restored in **0.31.0-dev.5**, `versionCode` **41**. **0.31.0-dev.4**
(`versionCode` **40**) added rotation history and pen modes, but its manual
page-mode drawing was rejected for severe latency.
**0.31.0-dev.3** (`versionCode` **39**) fixed the handwriting clipping failure from dev.2.
**0.31.0-dev.2** (`versionCode` **38**) fixed PDF and ink regressions. **0.31.0-dev.1**
(`versionCode` **37**) added PDF notebooks and outlines. The previous
**0.30.0-dev.3** (`versionCode` **36**) synchronized screen direction across the app. Version
**0.30.0-dev.2** (`versionCode` **35**) added page outlines, direct sensor rotation,
and palm/pinch safeguards; the owner reports those bugs are solved. Version **0.30.0-dev.1** (`versionCode` **34**) introduced physical
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
   `build/releases/`. For this release these are `scriflow-0.32.0-dev.1.apk` and
   `scriflow-0.32.0-dev.1.apk.sha256`. Debugging is disabled, diagnostic screens are
   absent from the manifest, and the library is the only exported component.

4. Compare the printed signer certificate SHA-256 with the intended permanent
   certificate. For later builds, pin that known digest during verification:

   ```bash
   python3 scripts/verify-apk.py build/releases/scriflow-0.32.0-dev.1.apk --certificate-sha256 YOUR_KNOWN_CERTIFICATE_SHA256
   (cd build/releases && sha256sum -c scriflow-0.32.0-dev.1.apk.sha256)
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

For **0.32.0-dev.1**, the owner explicitly authorizes commit, push and release
once ready. Publish the verified signed APK and checksum as a development
prerelease. Disclose the catalog-v2 upgrade and pending physical validation of
layout, handwriting, cover/power sleep and battery behavior. Retain the
permanent signing identity and do not imply tablet acceptance from host checks.

For **0.31.0-dev.5**, the owner explicitly authorizes restoring the optimized
native writing implementation. Continue the established commit/push/APK workflow
with the same key and a higher versionCode. Keep the latency report visible;
verify the old live callbacks are restored and the second pen is absent from
the APK. Do not claim tablet latency acceptance from host checks.

For **0.31.0-dev.4**, the owner's requested follow-up fixes and explicit choice
of both width modes continue the authorized signed APK workflow. Retain
development status, preserve the existing key/files, and disclose pending
physical acceptance of the manual native page-width bridge. The owner confirms
the preceding dev.3 clipping fix; this does not accept the new brush routing.

For **0.31.0-dev.3**, the owner explicitly requires fixing the blocking error
reported after dev.2. This continues the authorized implementation/push/APK
workflow. Verify the exact renderer under forced path-operation failure and
save/reopen tests; publish as a development prerelease and disclose that no
tablet is connected for physical confirmation.

For **0.31.0-dev.2**, this is a follow-up correction within the owner's authorized
implementation/push/APK workflow. Keep development status and the unverified
native restoration and tablet timing visible. Old strokes retain their prior
rendering because their capture scale was not recorded; do not claim their
original low-latency geometry has been recovered.

For **0.31.0-dev.1**, the user's explicit instruction authorizes publishing a
development prerelease after automated verification, with tablet acceptance
pending. The checklist remains the physical acceptance record; do not claim
that host tests verify stylus latency, display refresh, or system picker behavior.

For **0.30.0-dev.3**, the owner requested follow-up fixes to the previous
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
