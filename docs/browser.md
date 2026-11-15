# Browser and offline websites

**0.40.0-alpha.3** (versionCode **103**) bundles GeckoView
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

The browser opens a native start page with pinned websites when there is no
restored page. The start page never fetches websites or favicons. Existing tabs
restore their selected address when the app restarts. Live pages use the engine's
normal mobile layout, JavaScript and fonts, including MathJax and KaTeX.
Ordinary website dialogs and HTML selection controls use native dialogs.
User-initiated new-window links open another tab; automatic popups are
blocked. Use Tabs to open an independent tab explicitly. File downloads,
uploads and password-manager integration are outside this version.
The bundled engine increases APK size. Its separate cookie profile may require
signing into websites again after updating from the earlier WebView browser.

## Start page, bookmarks and collections

The start page has a **Search or enter a website** field, pinned shortcuts,
**Add shortcut**, **Bookmarks**, **History**, **Browser data** and **Tabs**.
Wikipedia and GitHub are initial shortcuts; removing them is remembered.
Hold a shortcut to edit, unpin or move it earlier/later. Up to 32 shortcuts can
be pinned. **Home** returns to this page without discarding the current website.
Back can return from Home to that website.

The floating panel's **Bookmark** button saves the current address. Its **⋯**
menu also offers bookmarking, pinning, collections, history, data settings,
copying/sharing the address and opening another installed browser. Bookmarks
are live links; **Save** still creates a separate offline copy.

In Bookmarks, add or edit a name/address, choose a collection and optionally pin
the link. Search names/addresses, create or rename collections, move bookmarks,
and remove links. Removing a collection moves its bookmarks to Unsorted and
preserves pins. Removing a bookmark also removes its pin, while offline copies
stay available. Collections, pins and history use a private, atomic catalog
with a recovery generation, separate from Gecko's profile and website archives.

## Tabs, history and search

**Tabs** opens the tab picker; **+ New tab** starts with pinned websites, and
**×** closes a tab. Up to 12 tabs may be open. Each loaded tab keeps its own
Back/Forward history during the browser session; inactive sessions are paused.
Closing the last tab creates a new start page. Tab destinations and selection
persist across app restarts. The active tab's native history also restores
across screen recreation; other tabs reopen their addresses after recreation.
Form values are not copied into the persisted tab list.

**History** lists the latest visit to each of up to 1,000 recent addresses.
Search, reopen a page or remove an individual entry. Clearing all history closes
open pages and removes restorable tab addresses, preserving cookies, bookmarks
and offline copies. The recovery generation is cleared too, so it cannot later
restore successfully deleted history.

The start page and floating address field both accept searches. **Browser data**
lets you choose DuckDuckGo (default), Google or Bing. A query is sent to that
provider only when submitted; there are no live search suggestions. HTTPS
addresses open directly, and unsupported URL schemes remain blocked.

## Browser data settings

Open **Browser data** from the start page or **⋯ → Browser data settings**.
Choose first-party-only cookies (default), all cookies or no cookies. Blocking
cookies may affect sign-ins. Changing the policy does not delete existing
cookies; use Clear data to remove them.

**Clear browsing data** lets you select cookies/website storage, cached
images/files, and history independently, then confirm. Cookie/storage clearing
can sign you out; cache-only clearing keeps login cookies. Every operation
closes open website sessions before deletion so inactive scripts cannot recreate
data. Restorable tab addresses are removed as well. Bookmarks, pins, collections,
offline archives and notebook files remain separate and are preserved.

The host list combines recent destinations and cookie domains with cookie
counts (up to 2,000 cookie domains). Cookie values are not displayed or sent to website scripts. Filter the
list or use **Choose a website** to target a host; the list renders up to 100
matching hosts at a time. A host action clears that host's cookies, storage and
cache. Cookies shared by subdomains can affect their sign-ins. Embedded
third-party hosts have their own data entries; clearing every site requires the
global action. These controls manage the bundled Gecko profile; legacy WebView
website data from alpha.1 is separate.

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

Host tests cover bookmark/collection/pin persistence, history recovery/deletion,
tab metadata, search parsing, publication, restart, mixed archive generations, failures,
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
