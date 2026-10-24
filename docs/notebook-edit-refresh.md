# Edit refresh fix — 0.8.1

## Report and cause

The user confirmed that 0.8 erasing, undo, and redo work, but sometimes all ink
disappears and reappears together. This differs from the earlier black/white
whole-screen flashing. Version 0.8 released and recreated `HWEditView` after
every edit, then restored its paper and tracks separately. The new surface could
post a frame containing only the paper before its tracks were restored.

The locally saved firmware implementation confirms that `SurfacePicture.bindBitmap`
requests an immediate draw, as does `HWEditView.setTrackList(List)`. For an existing
surface, `setTrackList` replaces its track model and draws paper plus replacement
tracks in one locked canvas before posting. Rebinding the paper is unnecessary
for a stroke-only edit. This provides a source-based explanation of the visual
gap; the tablet was not instrumented during this offline fix.

## Change

The Activity pauses input for an edit while retaining the visible native surface
and paper bitmap. After saving, the adapter replaces the completed tracks on that
same surface with one `setTrackList` call. It does not rebind the background.
A no-hit erase whose native state is already clean skips even that track update.
The selected tool resumes after the edit. Initial load, paper changes, and normal
Activity lifecycle restoration keep their existing behavior.

Completed pen UPs awaiting a native notification are still submitted to the
serialized writer before the edit is queued. Ordinary edits no longer advance
the widget generation, so the retained widget's listeners remain valid. Lifecycle
changes still invalidate old callbacks. Storage, notebook format, history, and
the established live handwriting path are unchanged.

An unsettled vendor track accumulator or a changed/unavailable surface cannot
safely be reused. In those exceptional cases the Activity still restores a fresh
widget. The visual gap may remain in that safety fallback. Reflection/drawing
failures pause input explicitly.

## Verification

The existing 1,629 host assertions pass, covering editing, persistence, recovery,
and old-file compatibility. The complete API 29 app compiles and its APK signature
matches previous releases. VersionCode is 9; versionName is 0.8.1.

The optional disposable debug check `--ez inPlaceEdits true` exercises synthetic
erase/undo/redo and no-hit updates, compares native vector readback, and checks
that surface and paper identities remain unchanged. It does not open the user's
notebook. This check is included for later device validation and was not run
during the offline build. Neither host tests nor source review establish the
optical behavior of the e-ink panel.

Install `build/ink-notebook-0.8.1.apk` as an update, keeping the existing app
installed. With several strokes visible, erase one, undo, and redo repeatedly.
Unaffected strokes should remain visible. Also erase blank space, then return to
Pen and check continued handwriting. Reopening should retain the edited page.
