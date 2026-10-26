# Scriflow architecture: foundation milestone (0.22)

Status: implemented foundation; [PDF notebooks](pdf-import.md) extend it in 0.31.
Typed rich content and AI remain later milestones.
The immediate target is MatePad Paper on Android 10. File models and transactions stay
Android-independent so future device support does not require a new document format.

## Decisions and ownership

- Keep Java 8 and Android Views. Live handwriting remains the validated Huawei native path.
  Kotlin can be introduced incrementally; native code requires a measured need or a library.
- `NotebookInkBackend` is the editor/device boundary. `NotebookInk` implements it using firmware
  APIs. Live trails and temporary eraser marks belong to the backend; completed samples belong
  to the immutable document. Never save transient marks or render rich content during pen MOVE.
- `NotebookDocument` owns page commands/history; `NotebookBook` owns page selection/order;
  `NotebookLibrary` owns the catalog. These classes and their stores have no Android dependencies.
- `NotebookSession` bridges Activities to a single document writer. Immutable pages cross worker
  boundaries. Thumbnail replay runs on a bounded render queue; PDF/backup generation runs on
  an export worker. File-picker transfer uses a file worker. No worker may synchronously wait
  for a task submitted to itself. The export worker may request a short source-read writer barrier.
- An export captures the writer generation. A source mutation before a subsequent page read
  fails the job rather than producing a mixed-revision document. Output is prepared privately
  before the system save picker. This is conservative: even navigation can invalidate preparation.
- Native `NotebookStrokeRenderer` reconstructs pressure outlines from immutable samples.
  `NotebookPdf.OutlineSource` already separates the portable PDF writer from this device-specific
  geometry. A portable brush or persisted, versioned outline cache is a future decision; there is
  deliberately no silent fixed-width fallback. Native replay must remain identical across preview
  and export. This milestone does not make exports renderable on non-Huawei Android devices.
- `NotebookTransform` makes page/view mappings explicit and preserves pressure/event time.
  Existing page dimensions remain fixed logical units. Rotation/crop support must use transforms,
  never rescale or rewrite saved samples to match a new screen.

## Durable identities and migration

Version 0.31 adds version-4 snapshots with a source PDF page reference, durable
UUID-based outlines, and version-2 editable backups containing the unchanged
source PDF. Earlier formats remain readable. See [PDF notebooks](pdf-import.md)
for the current extension; the sections below describe the foundation history.

Version 0.30 adds version-3 page snapshots with optional physical paper
dimensions, leaving legacy page coordinates and PDF printing scale unchanged.
See [paper, zoom, and orientation](paper-zoom-orientation.md) for the current
format extension and viewport behavior. The version-2 description below records
the earlier identity migration.

Version-2 `page.nbk` retains version-1 framing, checksums and journal semantics. A snapshot now
starts with a 128-bit page ID before dimensions; every stroke starts with its 128-bit object ID
before style and samples. IDs are UUIDs; within a page stroke IDs must be unique. Page IDs must
be unique within a backup/document. Display page numbers are positions rather than permanent
object references. Development version 0.29.0-dev.1 adds a version-2 book index mapping those
positions to stable storage slots; slot 1 retains the legacy root directory. See
[page management](page-management.md) for publication, undo and recovery behavior.

Reading v1 is non-mutating. Legacy page IDs are deterministically derived from the canonical
page directory, and stroke IDs from that page ID plus original record order. The first successful
edit writes an atomic v2 snapshot and retains the v1 predecessor as `.bak`. The migration preserves
sample coordinates, style, pressure, event time, and ordering. IDs survive edits, undo/redo,
reopening and backup. Opening and exporting an old notebook does not migrate its original files;
a portable backup contains canonical snapshots with explicit IDs (version 3
from 0.30). Do not move raw v1
files around as a substitute for portable backup. Unknown versions fail closed.

After a page migrates, APKs predating that format cannot read its data. Use the editable backup format
before major updates; preserve the local signing key. No automatic downgrade migration exists.
The app still keeps history only within the running process, up to 100 actions per page.

## Editable backup and restore

Library rows have **Backup**. **Restore backup** accepts `.inkbook` files through Android's
system picker, with no broad storage permission. The archive is a ZIP with a binary `manifest`
(magic `INKB`, version 1, Java modified-UTF notebook name, page count), followed in order by
`pages/1.nbk` ... `pages/N.nbk`. Entries contain canonical snapshots, not journals or filesystem
paths. Checksums, page bounds, duplicate IDs, entry order, counts, and resource limits are checked.
The uncompressed limit is 512 MiB per backup and 64 MiB per page; the existing 1,000-page and
point/stroke limits also apply. There is no encryption in this format.

A restore first validates in private staging. It then publishes as a **new notebook**; existing
notebooks are never replaced. IDs and source samples are preserved, while viewport selection
starts at Page 1. Undo history, caches, device settings, and credentials are excluded.
A redo transaction (`restore.pending`, private ownership marker) makes directory/catalog
publication resumable after process death. Unexpected target files or corrupt metadata are
preserved, not adopted or overwritten. A destination-provider write failure may leave an
incomplete external backup; choose a fresh destination to retry. Losing the process during
validation/preparation requires restarting the transfer; an already published restore is recovered
without duplicating it. Abandoned pre-intent staging files may require later cache cleanup.

## Build and dependency boundary

The existing checksum-pinned offline SDK build remains supported. A standard Android Gradle
project is added for dependencies and IDE support, pinned to Gradle 8.11.1 / Android plugin
8.9.2 / JDK 17 / Build Tools 35.0.0. It still compiles and targets API 29 and Java 8 bytecode.
Both build routes use `app/version.properties`. Debug builds preserve the original local
signing key or initialize a new ignored development key on a clean checkout. Release builds
use explicit environment-based signing credentials, or produce an unsigned APK for contributor
checks. They are unshrunk to retain firmware reflection. See [Building](building.md) and
[Releasing](releasing.md) for the current setup, added after this foundation milestone.
This foundation introduced no runtime dependency or network access. Version 0.31
adds PDFBox Android for source-preserving PDF import/export; see its pinned
dependency and [third-party notices](../THIRD_PARTY_NOTICES.md).

## Next extensions (design constraints, not current features)

The next document schema should represent a page background (paper or an immutable PDF asset
plus source-page/crop/rotation) and ordered, identified objects (ink, text, Markdown, image).
Do not introduce placeholder PDF objects which today's renderer silently ignores. Add each
object type with its codec, commands, rendering, backup assets and export support together.
Use explicit schema versions and migration fixtures. Preserve original PDF vectors; tile only
its display rasterization. Evaluate a compatible PDF engine and license with one import/write/
reopen/export prototype before expanding reader features. A vector appearance and interoperable
editable PDF annotations are separate acceptance criteria.

Text/Markdown retain source text and bounded layout; cache rendered results independently.
Evaluate offline math rendering and vector PDF output before adopting KaTeX. Avoid full-page
WebView replacement of native ink. AI providers belong behind separate service interfaces,
with explicit context selection, credential protection, cancellation and batched e-ink updates.
AI edits must enter the document as undoable commands. Neither network work nor rich-content
layout may occupy the writer or the live pen callback.

## Verification and performance

Host tests cover v1 fixtures, v2 identities, exact backup round trips, unknown/corrupt/truncated
input, cancellation, path rejection, interrupted restore publication/restart and worker isolation,
in addition to existing durability/geometry/PDF regressions. Physical optical latency still
requires real stylus testing. Device regression uses disposable synthetic pages only.

Next performance acceptance should measure sustained native writing, cold first-page time,
scroll frame time and bitmap memory on large notebooks/PDFs, and save latency during background
rendering. Bounded thumbnails are implemented; editor document/history caches and scroll tiles
still need measured memory budgets before large-document claims. Activity/controller separation
is incremental: the device boundary and job ownership are extracted; the entire Activity is not
rewritten in this milestone. Snapshot publication follows the pre-existing fsync+rename scheme;
this is process-interruption recovery, not a guarantee against every power-loss/filesystem failure.

### Verified on 2026-10-25

- 20,160 host assertions passed. Offline SDK build and cached Gradle debug/release builds passed;
  the APK signing certificate matches prior releases.
- The connected tablet passed synthetic native restoration/geometry checks.
  Blank/lined edit/undo/redo and page-switch checks, plus blank-paper pause-boundary
  checks, also passed from the app UID. Native replay,
  scrolling geometry and rendered PDF boundaries agree within one source pixel, with the same
  5/10-pixel light/heavy pressure widths. Two concurrent replay threads also produced exact
  reference geometry across repeated runs.
- Synthetic Android-runtime storage baseline (100 strokes / 12,800 samples): about 49 ms total
  appends, 126 ms backup, 14 ms validation/restore, 33,221-byte archive. Every ID, style and sample
  matched. These are one-run headless measurements, not optical latency or large-document bounds.
- Backup/open document pickers launched on device; cancelling Save retained the prepared archive
  for retry. Existing 10 page files remained byte-identical after update/library/backup UI checks.
- A private pre-update raw recovery copy was retained locally under ignored `artifacts/`.
  Actual new backup-provider writes and user-driven stylus acceptance remain useful follow-up
  checks; no personal notebook was restored or overwritten for testing.
