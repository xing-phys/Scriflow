# Browser and offline websites

**0.40.0-alpha.2** (versionCode **102**) bundles GeckoView
**160.0.20261104181552**, the Firefox browser engine. Website JavaScript, CSS,
fonts and math rendering no longer depend on the tablet's installed WebView.
This alpha is awaiting physical testing on the Huawei MatePad Paper.

## Browse and save

1. Tap **Browser** in the bookshelf's shortcut row.
2. Tap the **dotted button** to expand the floating panel if hidden. Enter a
   website address and tap **Go**, or the keyboard's Go key. Addresses without
   a scheme use HTTPS. The app's existing restriction on cleartext HTTP remains.
3. Use the arrows for Back/Forward, the circular arrow to Reload, and pinch to
   zoom. The arrows also work offline after section links.
4. Let the page load, and scroll to load any lazy images you want to keep. Tap
   **Save** and wait for **Saved to Saved websites · available offline**.
5. Tap **Bookshelf**, then the **Saved websites** folder, and choose a page.
   The browser's **Saved websites** button opens the same folder directly.

The browser starts without a home-page request. Live pages use the engine's
normal mobile layout, JavaScript and fonts, including MathJax and KaTeX.
Ordinary website dialogs and HTML selection controls use native dialogs.
User-initiated new-window links open in the same screen; automatic popups are
blocked. File downloads, uploads and additional tabs are outside this version.
The bundled engine increases APK size. Its separate cookie profile may require
signing into websites again after updating from the earlier WebView browser.

## Floating controls and Zen

All app bars live in one floating panel: address, Back/Forward, Reload, Save,
Bookshelf and status. Tap the dotted grip to fold the panel into a small button.
Drag it whether expanded or collapsed. It stays on screen after rotation and
keyboard changes, with scrolling if space is short. Position and collapse state
are remembered. The page always fills the screen; folding controls does not
change its width or reflow content.

Tap the fullscreen-corners icon for **Zen mode**. Only the small top-right clock
with seconds remains. Tap the clock or press Android Back to restore the previous
expanded/collapsed state. Zen survives recreation; new browser screens start
outside Zen. Seconds run only while the clock is visible in the foreground.
Errors expose the controls for recovery.

Outside Zen, Android Back first closes an expanded panel, then follows page
history, then leaves the browser. The panel's arrows operate history directly.

## Read offline

Saved websites is a permanent folder at the bookshelf root in both list and
thumbnail layouts. It is separate from notebook folders and selection/Trash.
Entries show their title, source address, save time and size, newest first.
Tap an entry to open its local copy with Wi-Fi disabled or after restarting the
app. No revisit of the original address is required.

New saves contain the rendered HTML document, embedded CSS, images, fonts,
readable canvases and accessible frame contents. Already rendered math stays
visible without running the original site's math scripts. Unavailable resources
are counted and reported beside the save/offline status. Existing MHTML saves
from alpha.1 remain readable through a separate legacy reader.

The offline reader disables page JavaScript and network requests before opening
the archive. New HTML snapshots also carry a restrictive content security
policy. Pinch zoom and scrolling work. **Open online** opens the original address
in a separate live browser screen. External links offer an explicit online
opening. Internal section links stay within the selected copy; **Back/Forward**
return from a section jump or replay it. Native history restores across Activity
recreation and cannot navigate to another archive or an online page.

The delete icon removes only that snapshot after confirmation. Saving the same
page again creates a separate snapshot. Each save covers one page; it does not
crawl linked pages, capture later updates or load content that has not appeared.
Interactive forms, cross-origin frames, streamed media and services needing
scripts or a server may require the online original. A blocked resource or
site-specific behavior can produce an incomplete copy; check important saves
before relying on them offline.

## Storage and verification

An app-owned isolated extension freezes the rendered DOM and embeds HTTPS
resources using parsed CSS. The extension accepts capture commands only from
the native browser owner, with a per-save identifier, resource/time/size limits
and acknowledged chunks. It exposes no JavaScript API to website code. Every
new session configures its network policy through a trusted built-in bootstrap
before loading any page; offline sessions block remote requests even from
frames and styles. Certificate errors are never bypassed.

New saves use `page.html` and NWS2 metadata, including missing-resource counts.
NWS1 metadata and `page.mht` remain supported. Complete archive/metadata pairs
publish with a directory rename only after validation. A separate serialized
worker handles storage, independently of the handwriting writer. Changed pages,
failed captures and timeouts cannot publish an entry. Interrupted temporary
saves stay hidden and are reclaimed before a later save once a day old.

Snapshots live in the app's private `websites/` directory. They do not change
notebook, catalog or chat formats and are excluded from `.inkbook` backups.
Uninstalling or clearing app storage removes them. See [Privacy](privacy.md).

Host tests cover publication, restart, mixed archive generations, failures,
corruption, deletion, path boundaries, URL policy, toolbar geometry, clock
lifecycle and history callbacks. Real browser fixtures reopen captures in fresh
processes with the server stopped and network/scripts disabled, checking math
fonts, imported CSS, images, canvases, frames, section history and CSP. Android
emulator checks use the production browser to visit a GitHub repository and the
reported Quarto math page, save the latter, and reopen it without networking.
These checks do not establish physical e-ink refresh, pen/touch behavior or
compatibility with every website; owner tablet acceptance remains pending.

Engine APIs and sources: [GeckoView](https://mozilla.github.io/geckoview/),
[GeckoView extensions](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html),
[license and source notices](../THIRD_PARTY_NOTICES.md).
