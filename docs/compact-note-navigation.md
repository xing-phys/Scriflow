# Compact note navigation — 0.33.2-dev.1

The top-left note bar now contains separate controls in this order:

| Control | Action |
| --- | --- |
| Notebook name | Return to the bookshelf after the existing writer/save barrier |
| Previous arrow | Previous page, unavailable at the first page |
| x / y | Page management and thumbnails |
| Next arrow | Next page, unavailable at the last page |
| xx% | Fit width/height/page, zoom presets and App settings |
| Three dots | Background, figures, page information and export |

The toolbox no longer contains previous/next page, overview or bookshelf buttons.
It has 12 controls including its grip and orientation button. The four remaining
arrow tools represent Undo/Redo and previous/next link views, not page turns.

The bar measures its content instead of reserving the old fixed width plus a
weighted blank area before the menu. Text controls have 4 dp horizontal padding.
The title is capped at 160 dp and ellipsized; it shrinks first when space is
constrained. On narrow screens, the device-status island moves below the note
bar. Page-counter digits reserve only the width needed by the current notebook's
total count, so page-number changes during writing keep the same ink exclusion.
Unchanged idle status polls do not reposition the windows.

Every action retains its existing readiness, writer-flush and error guards.
The status windows still install native exclusions before presentation and use
the Activity popup token. The toolbox retains its higher application sublayer.
Saved notes, PDF resources, native live input and stored formats are unchanged.

Validation passed **246,339 host assertions in 61 suites**, Debug/Release lint
and builds, and permanent-certificate verification. Production status callbacks
are exercised with synthetic popup/layout adapters for compact widths, narrow
stacking and failed-show exclusion cleanup. Counter callbacks check digit-width
stability, independent labels, focus/page-count changes and unchanged idle polls.
The toolbox sizing fixture now covers its 12 controls.

The signed artifact is `build/releases/scriflow-0.33.2-dev.1.apk` (versionCode 65),
prepared through `scripts/build-release.sh` with the existing permanent key.
Its adjacent SHA-256 checksum verifies the delivered bytes.

The owner requested continuing without the tablet. Physical acceptance of the
new tap targets, title truncation, page navigation and e-ink refresh behavior
remains pending. Public stable 0.33.1 is unchanged.
