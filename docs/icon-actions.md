# Inline action icons — 0.33.2-dev.6

Tapping the top-left three dots replaces them with function icons in the same
status bar: Insert figure, Choose a figure, Page information/PDF export,
blank/lined background (ordinary pages), and Paste figure (after copying).
The bar grows with its contents; its title can shrink and the device status
stacks below it when there is insufficient room beside it. No central menu
dialog is created. Choosing a function, interacting with the page, pressing Back
or leaving the editor restores the three dots. Stroke-triggered collapse is
deferred until the native pen is up. Dedicated functions retain their existing
pickers, page information or figure-list workflows.

The lasso panel contains six icons: Copy, Duplicate, Move to page, Cut, Delete
and Export. It has no text header or Done button. Tap outside the selected curve
to finish using the existing selection gesture; empty selections keep only their
outline. Narrow panes wrap the controls into further rows. Tooltips and
accessibility labels name each action, and copying gives a brief confirmation.

Status expansion/collapse pauses native input before changing the protected
screen rectangles. The rectangles are installed before popup placement, then
`redrawAfterOverlayChange` repairs exposed content before resuming. The note’s
viewport and underlying native surface stay in place. Selecting an icon uses
the existing native-ready barrier before executing its function. The toolbox
keeps its higher application sublayer. No permissions, notebook storage formats
or system settings change in this task; page format remains 7 from dev.5.

Validation: 248,470 host assertions in 68 suites, debug/release lint and builds,
and a permanent-signed APK verified against `release-signing-certificate.sha256`.
Production callback adapters cover expansion, conditional background/paste,
each action’s dispatch, native ordering and busy/error/lifecycle rejection.
Status placement covers expanded widths and narrow-screen stacking; lasso
adapters retain Activity-token placement and outside-dismissal coverage, and
exercise narrowing/widening after retiring previously assigned grid spans. New
symbols were rendered from production paths and visually inspected.
Logs: `artifacts/icon-actions/host.txt`, `build.txt`; icon preview: `icons.png`.
APK: `build/releases/scriflow-0.33.2-dev.6.apk` (versionCode 70).

Physical acceptance remains pending under the owner’s instruction to continue
without the tablet. When available, use portrait and landscape, main/split/
floating notes, and both finger/stylus input to check: expansion beside zoom,
each action and conditional background/paste, outside/Back collapse, native ink
under newly exposed areas, toolbox stacking, narrow lasso strips, copy/duplicate/
move/cut/delete/export, and outside dismissal followed by writing or scrolling.
