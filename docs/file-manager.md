# Tablet files

Open **Files** from the bookshelf. This browser manages the tablet's shared
storage independently of Scriflow notebooks. On the first visit, tap **Allow
storage access**. Android 10 asks for file permissions; newer Android versions
open the app's **All files access** setting. Declining leaves the notebook
library usable. If permission was permanently denied, the access button opens
Android app settings so it can be enabled there.

## Browsing

- The home button lists internal storage, mounted SD/USB volumes exposed by
  Android, and existing Downloads, Documents, Pictures, DCIM, Music and Movies
  folders. A read-only drive can be browsed; Android rejects changes to it.
- Tap a folder to open it. The left arrow goes up; tap the current path to jump
  to an ancestor. Android Back clears selection/search before going up.
- Search matches names, ignoring case, in the current folder and its
  subfolders. Searching from storage home searches internal storage. Hidden
  entries are omitted unless **Show hidden files** is enabled under **⋮**.
- Use the grid/list icon next to **⋮** to switch between a compact list and
  thumbnail cards. The list has one column in portrait and two in landscape;
  the choice persists when you rotate or reopen Files.
- Thumbnail cards show locally decoded previews for readable PNG, JPEG, WebP,
  GIF, BMP and PDF files up to 128 MiB. Other files, folders and previews that
  cannot be decoded use a type icon. Previews are held only in a bounded memory
  cache and do not contact the network.
- **⋮ → Sort by…** offers name, newest, largest, and file type, with folders
  first. **Refresh** reloads files changed by another app or a reconnected drive.
- Search stops at 1,000 matches or 50,000 examined entries; a folder listing
  stops at 10,000 entries. A visible limit notice asks you to narrow the search.
  Protected subfolders and symbolic links are skipped and counted.

## File operations

Each entry's **⋮** menu offers Copy, Move, Rename, Delete, Details and Select.
Files also offer Open, Open with, and Share. Hold an entry for multi-selection;
then use Select all and Actions. Select all applies to the displayed results.

For **Copy** or **Move**, browse to a destination, clear any search, and tap
**Paste here**. Folders include their hidden contents. A name conflict stops
the batch before copying any items; rename the conflicting entry or choose a
different folder. Files are never deliberately replaced or merged. Overlapping
selections (a folder and its child) are handled once. Storage roots cannot be
renamed, moved or deleted, and a folder cannot be pasted into itself.

Copies stream through a temporary destination. Only a completed copy becomes
visible under its final name. Cross-volume moves keep originals until copying
finishes and check for source changes before removing the copied contents.
**Stop** cancels at a safe step and removes incomplete staging files. Completed
items remain; a failed/cancelled batch reports how many items completed. The
clipboard clears after an attempted paste so a partial move is not replayed.
Operations continue while the screen is paused and retain their owner through
rotation. Android process termination can interrupt an operation; there is no
automatic background retry. An abrupt process/device stop may leave a hidden
`.scriflow-copy-*` staging entry, which can be deleted after inspecting it.

**Delete permanently** requires confirmation, includes hidden folder contents,
and cannot be undone. It does **not** use the bookshelf's notebook Trash.
Cancellation or an access error can leave a partially deleted folder; inspect
the remaining contents before retrying.

Tap a PDF to import and open an independent notebook copy in Scriflow. Other
files use Android's Open with chooser. Share supports up to 100 individual files;
directory sharing and archive creation/extraction are not included. Installed
apps can handle additional file types. Open/share uses temporary, read-only
Android content-URI grants for the selected files, without exposing app-private
notebooks. Changes made in an external viewer do not write through these grants.

Android restricts private app data and, on newer versions, parts of `Android/`.
Symbolic links are not followed. This feature does not bypass those restrictions.
Physical MatePad Paper storage prompts, removable drives and e-ink behavior still
require owner acceptance; emulator checks do not establish Huawei behavior.

## Automated verification

`./gradlew hostTest` includes 45 real-file assertions for listing, Unicode names,
hidden files, batch conflicts, root boundaries, links, copy cancellation, changing
sources, same-volume moves and the cross-volume copy/delete path.

With the development build installed on the Android 10 x86 emulator,
`scripts/check-files.sh` repeats those filesystem assertions on Android's actual
runtime and runs 16 UI checks using disposable shared-storage files. These cover
permission denial/grant, search, folder creation, rename, copy/move, clipboard
retention, collision handling, confirmed/cancelled deletion, hidden files,
selection across screen recreation, multi-selection and Android open/share.
The check refuses physical devices, preserves existing notebooks, and cleans up
its own shared-storage fixtures. Evidence is written to `build/file-manager-check/`.
The signed ARM APK is verified separately with the permanent release certificate.
