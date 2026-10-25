# Compact tools and status - 0.15

## Use

The floating palette now uses **36 dp buttons** with **20 dp icons**, compared
with 48 dp buttons and 26 dp icons in 0.14. It has a single row horizontally or
a single column vertically. Tap the direction icon beside the six-dot grip to
switch. Its symbol shows the orientation that tapping will choose. The selected
Pen/Eraser still has a 3 dp border; other borders remain 1 dp.

Drag the grip to reposition the strip. Position and orientation are remembered
across notebooks and app restarts. The menu's **Reset tools** returns to a
horizontal strip at the top center. Rotation preserves the normalized position
and clamps the resized window within the writing area. In a narrow/small window,
buttons can reduce to 28 dp, then the strip scrolls along its own axis instead of
wrapping into another row or column. The grip always moves the window, even in
that fallback.

The top bar is **24 dp high** at the tablet's normal font size, down from roughly
42 dp. It can grow for a larger system font. The left side shows **notebook name
and current page / total pages**. Long names ellipsize in the middle so the page
suffix remains readable. The right side shows **time, battery icon and percentage,
and Wi-Fi icon**. Time respects the tablet's 12/24-hour preference. Wi-Fi means
connected/disconnected; a crossed icon indicates no Wi-Fi connection. The app
does not display signal strength or network names.

The routine **Saved · N strokes**, **Autosave on**, and **Overview** header text
are removed. Completed strokes still save automatically. Use the grid icon for
overview/jumps. The menu retains save information; an actual save error still
pauses writing and shows a tappable **Not saved · Retry** in the header.

## Layout and native ink

`NotebookToolStrip` measures the single-axis layout in physical pixels. On the
MatePad Paper at normal density, 13 controls (including grip and orientation)
occupy **528 × 48 dp** horizontally or **48 × 528 dp** vertically. Native overlay
exclusion follows the resized bounds before the popup window changes. Rotation
pauses handwriting, updates layout, waits for the writer barrier, and restores
the canonical viewport once to reveal previously covered writing.

Every toolbar contact retains the 0.14 native input protection. Buttons dispatch
UP actions synchronously, with no pressed inversion, ripple, or fade. Palette
scrolling cannot steal the grip's drag stream. Existing page dimensions, vectors,
undo history, fractional scroll anchors, and PDF data are unchanged; the smaller
header simply gives the writing viewport more visible space.

## Device status without repeated redraws

`NotebookDeviceStatus` listens only while the editor is resumed. Its minute clock
uses Android time broadcasts; battery data comes from the sticky
[battery broadcast](https://developer.android.com/reference/android/os/BatteryManager#EXTRA_LEVEL).
A passive [network callback](https://developer.android.com/reference/android/net/ConnectivityManager#registerNetworkCallback(android.net.NetworkRequest,%20android.net.ConnectivityManager.NetworkCallback,%20android.os.Handler))
observes physical Wi-Fi networks, including when a VPN is the default connection.
It never requests or changes a network. `ACCESS_NETWORK_STATE` is a normal
Android permission; there is no runtime permission dialog, `INTERNET` permission,
Wi-Fi scanning, signal polling, or location access. Callback generations ignore
late events from an earlier Activity pause/resume cycle.

`NotebookDeviceStatusState` coalesces updates and publishes only changed visible
values while the editor is ready and input has been idle for at least a second.
Repeated battery broadcasts with the same percentage and network capability
changes that leave Wi-Fi connected cause no redraw. Clock, battery, and Wi-Fi
events during writing remain pending; the next idle refresh applies only their
latest values. There are no per-second timers or animated status widgets.
Fixed-width device-status slots avoid relaying out the header for minute or
percentage changes. Native pen input, scrolling, tool contacts, and dialogs all
hold these updates until writing can resume safely.

## Verification and tablet acceptance

All **17,392 host assertions** pass. The new **325 strip-layout assertions** cover
both orientations, actual tablet density, narrow/short windows, no wrapping,
clamped dimensions, control retention, and invalid/overflowing inputs. The new
**120 status-state assertions** cover coalescing through long strokes, latest
state delivery at idle, duplicate broadcasts, battery scale/rounding/unknowns,
and time-format/time-change handling. Existing overlay clipping, scrolling,
storage, editing, and PDF regressions pass.

The complete API 29 APK builds as versionCode 16, versionName 0.15, signed with
the existing key. Both layouts and the slim header were visually reviewed using
production icon paths and strip measurements. No tablet/ADB access was used;
live broadcasts, physical refresh behavior, and continued native pen latency
still need tablet checks. Install `build/ink-notebook-0.15.apk` over the existing
app without uninstalling.

1. Check the smaller row and toggle it to a column and back. Every icon should
   remain present, and only Pen/Eraser selection changes border thickness.
2. Move both orientations near every edge. Rotate again, leave/reopen the app,
   and confirm position/orientation and reachable controls. Try Reset tools.
3. Verify notebook name/page on the left and time, battery percentage, and Wi-Fi
   on the right. Check a long notebook name and a different page.
4. Leave the pen idle across a minute boundary, then write across another one.
   The clock should update after idle without interrupting the stroke.
5. Compare the battery percentage with Android, toggle Wi-Fi, and repeat with
   the VPN running. The icon should follow physical Wi-Fi connection state.
6. Scroll, write, erase, Undo/Redo, open overview, and export. Existing writing
   should stay intact, without a routine save counter occupying the header.
