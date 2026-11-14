# Browser and offline websites

Available in **0.40.0-alpha.1** (versionCode **101**), pending physical testing
on the Huawei MatePad Paper.

## Browse and save

1. Tap **Browser** in the bookshelf's shortcut row.
2. Enter a website address and tap **Go**, or the keyboard's Go key. Addresses
   without a scheme use HTTPS. This version supports HTTPS websites and retains
   the app's existing restriction on cleartext HTTP.
3. Use the arrow buttons for Back/Forward, the circular arrow to Reload, and
   pinch to zoom. Android Back follows browser history before returning.
4. Let the page load, and scroll to load any images you want to keep. Tap
   **Save** and wait for **Saved to Saved websites · available offline**.
5. Tap **Bookshelf**, then the **Saved websites** folder, and choose a page.
   The browser's **Saved websites** button opens the same folder directly.

The device supplies the Chromium-based Android WebView engine; Scriflow does
not bundle a separate Chromium installation. The browser starts with a native
address-entry prompt and makes no home-page request automatically. File
downloads and additional tabs are outside this first browser version.

## Read offline

Saved websites is a permanent folder at the bookshelf root in both list and
thumbnail layouts. It is separate from notebook folders and selection/Trash.
Entries show their title, source address, save time and size, newest first.
Tap an entry to open its local copy, even with Wi-Fi disabled or after restarting
the app. No revisit of the original address is required.

The offline reader disables scripts and network loads. Pinch zoom and scrolling
still work. **Open online** opens the original address in a separate live browser
screen. An external link offers an explicit online opening while retaining the
saved copy. Internal section links stay within the copy. The trash icon beside
an entry removes only that snapshot after confirmation. Saving the same page
again creates a separate snapshot.

Each save captures the current page and resources available to WebView at that
time, including captured images, CSS and frame contents. It does not crawl a
whole site, linked pages, later updates or unloaded content. Interactive forms,
streamed media and services requiring scripts or a server may need the original
online page. Pages restricted by a site's login or capture behavior may be
incomplete; check a saved copy before relying on it offline.

## Storage and verification

Android's `WebView.saveWebArchive` creates an MHTML file. Scriflow writes a
separate, versioned metadata file and publishes the pair with a directory rename
only after the archive callback and file validation succeed. Storage work uses
a separate serialized worker and never the handwriting writer. Failed saves
can be retried without publishing an entry. Navigation during capture is held;
a changed page, missing archive or capture timeout rejects that save. Interrupted
temporary saves stay hidden and are reclaimed before a later save once a day old.

Snapshots live in the app's private `websites/` directory. They do not change
notebook, catalog or chat formats and are excluded from `.inkbook` backups.
Uninstalling or clearing app storage removes them. See [Privacy](privacy.md).

Host tests exercise real-file publication, restart, multiple copies, failures,
corruption isolation, deletion, path/symlink boundaries and URL validation.
A Chromium fixture captures a styled page with an external image, frame and
rendered JavaScript content, passes it through production Java storage, then
reopens it in a fresh browser with network and scripts disabled and its server
stopped. Android lint/builds verify API-29 packaging. These checks cannot establish
the tablet's installed WebView behavior, page compatibility, layout, touch/zoom
or physical e-ink refresh; those remain tablet acceptance items.

API references: [WebView archive API](https://developer.android.com/reference/android/webkit/WebView#saveWebArchive(java.lang.String,%20boolean,%20android.webkit.ValueCallback))
and [WebSettings](https://developer.android.com/reference/android/webkit/WebSettings).
