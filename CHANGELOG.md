# Changelog

This project has not yet published its first public release. Version numbers
below identify development milestones. Detailed historical verification is in
[development history](docs/development-history.md); pending checks in those
records describe their original milestone, not the current acceptance status.

## 0.28.0-beta.1 — Public beta preparation (unreleased)

- License Scribblow's source, documentation, and original artwork under MIT;
  include the MIT notice in APKs and retain the Gradle Wrapper's Apache 2.0
  license and notices separately.
- Adopt three-part prerelease numbering for public testing. Android
  `versionCode` advances to 29 independently of the displayed version.
- Separate public-release signing from development signing; ordinary contributor
  builds can produce an unsigned release without private credentials.
- Restrict diagnostics to development entry points and keep internal screens
  private in release builds.
- Fix the lint indentation error and add automated build/test checks.
- Reorganize user, build, release, privacy, and development documentation.
- Sign local release candidates with the dedicated Scribblow release key,
  verified against its recorded public certificate fingerprint.

## 0.27 — Scribblow identity

- Adopt the Scribblow name, flowing-S monochrome launcher icon, and PDF credits.
- Preserve application ID, notebook storage, page identities, and update
  compatibility with the development signing identity.

## 0.26 — Firmware-managed refresh

- Use Huawei refresh policy 13 and remove manual waveform overrides introduced
  in 0.23–0.25. Physical testing on the target tablet confirmed scrolling and
  immediate writing without transition flashes or freezing. See
  [the refresh record](docs/fast-refresh.md) for scope and limits.

## 0.22 — Durable document foundation

- Add persistent page/stroke IDs, a version-1-to-version-2 page migration,
  editable `.inkbook` backup/restore, and resumable restore publication.
- Add document/device boundaries, coordinate transforms, independent background
  work, and standard Gradle tooling. PDF import, rich text, and AI remain future
  work rather than implemented features.

## 0.20–0.21 — Native eraser and consistent ink

- Move the temporary eraser trail onto native handwriting.
- Match pressure-aware brush outlines across writing, scrolling, thumbnails,
  and vector PDF export.

## 0.14–0.19 — Compact tools and widths

- Add draggable, rotatable icon tools, a compact device-status bar, pen/eraser
  width sliders, and eraser preview.
- Correct slider completion so the selected width is retained on release.

## 0.10–0.13 — Library, scrolling, overview, and PDF

- Add named notebooks, continuous finger scrolling, page thumbnails and jumps,
  and whole-notebook vector PDF export.

## 0.7–0.9 — Editable notebooks

- Add durable native handwriting restoration, whole-stroke erasing, undo/redo,
  multiple pages, and blank/lined paper.
- Preserve the original notebook in place as the app grows.

Earlier ink and display experiments are retained in the
[device test record](docs/device-testing.md) and [diagnostics guide](docs/ink-probe.md).
