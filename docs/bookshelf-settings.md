# Bookshelf and screen settings — 0.32

Tap the time/battery/Wi-Fi area at the top right to open Settings. It is also
visible on the bookshelf, Settings, page overview, and PDF/backup screens.
Settings saves each valid change immediately.

## Dense lists and thumbnail grids

The default bookshelf list uses 14 sp notebook names and 40 dp rows, with one
column in portrait and two in landscape. Tap a notebook to open it; **⋮** or
long press opens its actions. Titles truncate within their column.

Settings → Bookshelf switches between **List** and **Thumbnails**. The thumbnail
grid fits the selected number of rows in the available bookshelf height and
continues by scrolling for more notebooks. Rows and columns are independent:

| Setting | Portrait default | Landscape default | Range |
| --- | --- | --- | --- |
| Bookshelf thumbnail rows | 4 | 3 | 1–12 |
| Bookshelf thumbnail columns | 3 | 5 | 1–12 |
| Page overview columns | 5 | 8 | 2–12 |

First-page previews read the notebook's displayed first page, including its PDF
background. They do not open/select that notebook or change its saved position.
Only one visible preview is requested at a time. A failed preview does not
prevent opening/editing healthy notebooks. A folder occupies one grid cell.

## Folders and Trash

**+ Folder** creates a folder at the bookshelf root. Tap it to browse its notes;
**Bookshelf** or Back returns to the root. New named/quick notes created inside
a folder stay there. Imports/restored backups start at the root and can be moved.

Notebook **⋮** actions: Rename, Move to folder, Backup, Move to Trash. Folder
**⋮** actions: Rename folder, Remove folder (keep notebooks). Removing a folder
returns its active and trashed notebooks to the root. Folder names are unique
ignoring case; notebook names may repeat. Empty folders persist across restarts.

**Trash** lists deleted notebooks. **Restore notebook** returns one to its
original folder, or the root if that folder was removed. **Delete permanently**
asks for confirmation and removes its page files, retained removed pages,
outlines and original PDF. This cannot be undone. Files outside app storage,
such as exported PDFs and editable backups, are independent copies.

An interrupted permanent deletion remains unavailable for restoration and is
completed on retry/restart. Fully purged slots can be reused. Trash counts
toward the 200-notebook limit until permanently deleted. Folders have a separate
200-folder limit. Notebook/page mutations remain on the serialized writer.

The legacy first notebook shares a directory with the catalog and other books.
Its purge removes only its owned assets; a reused first slot lives separately
under `notebooks/1`. Catalogs written by this version use format 2 with folder
and trash metadata. Format-1 catalogs remain readable; the next catalog change
upgrades them. **Earlier APKs cannot open the upgraded catalog.** Preserve
editable backups before updating. Page and `.inkbook` backup formats are unchanged;
individual backups do not contain library folders or Trash.

## Editor and screen behavior

The vertical **⋮** menu follows the zoom percentage at the top left. It contains
page information, help, Reset tools, and Export PDF. The bookshelf icon is last
in the dock. Zoom presets share rows in a compact grid. Dialogs and dropdowns
have white backgrounds, explicit borders, and zero window elevation.

The add-page tool inserts immediately after the current page, then selects it.
It inherits paper size/template and preserves the identities of existing pages.
Middle insertion uses the existing managed-page order and its undo/redo history.

**Keep screen on** defaults to Off. When On, only resumed app Activities set
Android's `FLAG_KEEP_SCREEN_ON`; pausing clears it immediately. This uses no
CPU wake lock, wake-up request, polling service, lock-screen bypass, or change to
the tablet's global power settings. Cover/power-button system sleep can take
over. Android describes the foreground behavior in its
[screen-on guide](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on).
MatePad cover locking and physical power consumption still need tablet checks.

## Verification

The host suite includes real-file folder/trash/restore checks, safe legacy and
nested-slot purges, interruption/restart recovery, capacity reclamation, backup
restore into purged slots, preview isolation, and middle-page insertion.
Android builds and lint check compilation/API use. These do not establish
physical e-ink appearance, stylus latency, cover locking, or battery behavior.
