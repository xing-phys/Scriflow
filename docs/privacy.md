# Privacy and local storage

Scriflow works locally on the tablet. It has no accounts, analytics, advertising,
crash-reporting service, cloud sync, or app-initiated network requests. The APK
does not request Android's `INTERNET` permission.

## Notes and settings

Notebooks are stored in the app's private directory. They contain handwriting
coordinates, pressure, timing, pen styles, page order, paper choices, and notebook
names. PDF notebooks also retain the complete original PDF and outline headings.
App preferences remember tool widths, tool position, and reading position.
Scriflow does not add encryption to its notebook files; protection of the
tablet and its storage depends on the device and its configuration.

Uninstalling the app or clearing its storage removes its private notes and
settings. Android automatic app backup is disabled. Make editable `.inkbook`
backups through the library and keep them outside the app's private storage.

Page deletion in development version 0.29.0-dev.1 removes a page from the visible
notebook and future exports/backups. Its private ink files remain for undo and
recovery; this is not secure erasure or immediate storage reclamation. Independently
saved PDFs and backups also keep the content they contained when created.

## Exports and backups

PDF export and editable backup use Android's system file picker. Scriflow
receives access to the location you choose rather than requesting broad storage
permission. If you choose a cloud-backed file provider, that provider handles
the transfer and its own privacy terms apply.

- PDFs contain notebook names and visible page content; they are for reading
  and sharing, rather than restoring an editable notebook. Exports from imported
  PDFs also preserve original document metadata and catalog resources, including
  attachments when present. They are not a redaction tool.
- `.inkbook` backups contain the notebook's name, ordered pages, handwriting
  samples, pressure, styles, and persistent page/stroke IDs. They exclude undo
  history and device settings. PDF notebooks include the entire original PDF,
  including source pages removed from the note, plus note outlines.
  **Backups are unencrypted.**
- Export preparation uses private temporary files. Share exports or backups
  only with people and storage providers you intend to receive their contents.

## Permission and diagnostics

`ACCESS_NETWORK_STATE` is the only declared permission. It reads connection
state for the Wi-Fi indicator; it does not let Scriflow send data over the
network. The status bar also shows the tablet's time and battery level.
Following an external PDF link opens another installed app, such as a browser;
that app handles its network access.

Development builds include diagnostic screens and checks. Diagnostics can
export firmware metadata and captured diagnostic handwriting when explicitly
used. These tools are excluded from release entry points. Before sharing a bug
report, review screenshots, notebook titles, handwriting, device details, and
logs for personal information. Use a disposable notebook whenever possible.
