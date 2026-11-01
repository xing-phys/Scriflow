# Privacy and local storage

Notebooks work locally on the tablet. Scriflow has no app account, analytics,
advertising, crash-reporting service, or cloud sync. Optional [AI chat](ai-chat.md)
uses Android's `INTERNET` permission to contact the provider you configure when
you explicitly send/retry a chat message or fetch the model catalog. Ordinary notebook editing and
Markdown rendering do not make network requests.

## Notes and settings

Notebooks are stored in the app's private directory. They contain handwriting
coordinates, pressure, timing, pen styles, page order, paper choices, and notebook
names. Inserted figures are stored as PNG pixels within each page. Markdown blocks
retain their original text and source/rendered vector appearances. PDF notebooks also retain the complete original PDF and outline headings.
App preferences remember tool widths, tool position, reading position, and
second-note workspace placement, PDF crop areas, page mode and PDF contrast.
Reading preferences are stored separately from page content and excluded from
editable notebook backups, like other local view settings.
Scriflow does not add encryption to its notebook files; protection of the
tablet and its storage depends on the device and its configuration.

Markdown compilation uses a short-lived local WebView with bundled scripts and
fonts, no Java bridge and blocked network loads. Reading saved blocks uses native
PDF rendering. External Markdown images require embedding; remote URLs are
shown as placeholders. Unsaved typing has private recovery drafts, retained
across interruption until successful publication to the note. Those drafts are
not included in notebook backups until the block is committed.

Uninstalling the app or clearing its storage removes its private notes and
settings. Android automatic app backup is disabled. Make editable `.inkbook`
backups through the library and keep them outside the app's private storage.

## Optional AI chat

Send shares the chat's text, explicitly appended image history and Markdown formatting instructions directly
with the configured provider over HTTPS. Notebook pages, handwriting, figures
and PDFs are not attached automatically. Content pasted into chat becomes part
of that conversation. Selection controls can append Markdown source, selected
handwriting or image copies to a local chat draft. Appending does not send them;
Send submits the draft. Pending images can be removed first. Sent images are
included in subsequent follow-ups and stored in the private transcript.
Fetch available models authenticates with your entered/saved key and sends no
conversation or notebook content. Your provider's data and retention policies apply.
OpenAI requests set `store: false`; other provider policies still apply.
There are no automatic provider calls, retries, analytics or background sync.

API keys are encrypted with AES-GCM using Android Keystore and kept in private
preferences. They do not enter the Markdown WebView, notebook exports/backups,
text-field saved state or logs. Settings → AI chat → Remove saved key removes
the selected provider's credential. Chat histories and unsent drafts are
unencrypted private files, independent of notebooks and excluded from editable
backups. Each provider/endpoint/model has its own current history. New chat
clears that history and draft after confirmation; closing keeps them.
Clearing app storage or uninstalling removes histories and credentials.

Chat rendering uses bundled scripts and fonts, blocked WebView network loads,
sanitized content and no JavaScript bridge. Links cannot navigate and external
images are placeholders. The native HTTP client sends API requests separately,
rejects cleartext endpoints, and never follows redirects with your credential.

Page deletion in development version 0.29.0-dev.1 removes a page from the visible
notebook and future exports/backups. Its private ink files remain for undo and
recovery; this is not secure erasure or immediate storage reclamation. Independently
saved PDFs and backups also keep the content they contained when created.

## Exports and backups

Figure import, selected-ink export, PDF export and editable backup use Android's system file picker. Scriflow
receives access to the location you choose rather than requesting broad storage
permission. If you choose a cloud-backed file provider, that provider handles
the transfer and its own privacy terms apply.

- PDFs contain notebook names and visible page content; they are for reading
  and sharing, rather than restoring an editable notebook. Exports from imported
  PDFs also preserve original document metadata and catalog resources, including
  attachments when present. They are not a redaction tool.
- `.inkbook` backups contain the notebook's name, ordered pages, handwriting
  samples, pressure, styles, inserted figure pixels, Markdown source/appearances,
  and persistent object IDs. They exclude undo
  history and device settings. PDF notebooks include the entire original PDF,
  including source pages removed from the note, plus note outlines.
  **Backups are unencrypted.**
- Export preparation uses private temporary files. Share exports or backups
  only with people and storage providers you intend to receive their contents.

## Permission and diagnostics

`ACCESS_NETWORK_STATE` reads connection state for the Wi-Fi indicator.
`INTERNET` permits optional AI chat requests. These are the only declared
permissions; cleartext traffic is disabled. The status bar also shows the
tablet's time and battery level.
Following an external PDF link opens another installed app, such as a browser;
that app handles its network access. Long-pressing a PDF word sends that selected
word to the installed Eudic app through its documented floating-window URL
scheme. Eudic handles its own permissions, storage and any network use. Text
extraction stays on the tablet; Scriflow does not upload PDFs or run cloud OCR.
The clipboard changes only when you explicitly tap Copy.

Development builds include diagnostic screens and checks. Diagnostics can
export firmware metadata and captured diagnostic handwriting when explicitly
used. These tools are excluded from release entry points. Before sharing a bug
report, review screenshots, notebook titles, handwriting, device details, and
logs for personal information. Use a disposable notebook whenever possible.
