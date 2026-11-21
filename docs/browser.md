# Browser and offline websites

**0.40.0-alpha.11** (versionCode **111**) bundles GeckoView
**160.0.20261104181552**, the Firefox browser engine. Website JavaScript, CSS,
fonts and math rendering no longer depend on the tablet's installed WebView.
This alpha is awaiting physical testing on the Huawei MatePad Paper.

Engine updates are monitored through weekly Dependabot checks and a daily Mozilla
security-advisory workflow. Upgrades require provenance/license verification,
tests and a new signed APK. See [browser maintenance](browser-maintenance.md).

## Voice input and power use

The first online Browser entry asks for Android microphone permission. If you
decline, it does not ask proactively again; using a website's voice control can
request permission again. Android app settings can revoke access. A foreground
HTTPS page must also show its origin in an **Allow microphone?** prompt before
it can record. Consent lasts for that document, including repeated voice input,
and resets on a new document. Different origins, hidden tabs, offline pages and
camera requests cannot receive that consent. Website voice features process
audio according to that website's terms. Native note/AI voice input is not added.

Session snapshots use a 15-second periodic interval instead of one second,
with explicit checkpoints on navigation and when leaving a page. Near-duplicate
thumbnail captures are coalesced for five seconds; a new document resets that
limit. Completed captures cancel their timeout. Hidden pages stop capture-bridge
reconnect/early-readiness timers, and connection failures back off to a maximum
30-second retry interval. Showing a page retries immediately. Normal JavaScript,
rendering and foreground responsiveness remain enabled. Battery-life improvement
has not yet been measured on the physical tablet.

Pen thickness, eraser size and stroke/region eraser mode use one configuration
across ordinary notes, AI workspaces and online/offline browser notes. The first
update preserves the normal notebook's saved choices, falling back to AI/browser
choices for settings never saved there. Existing open screens refresh the shared
configuration when resumed. Popup placement uses the full workspace and free
space beside the actual pen, eraser or note button, without covering other
controls. The browser's note picker also opens near its Notes button.

## Browse and save

1. Tap **Browser** in the bookshelf's shortcut row.
2. Tap the **dotted button** to expand the floating panel if hidden. Enter a
   website address and tap the **right arrow**, or the keyboard's Go key. Addresses without
   a scheme use HTTPS. The app's existing restriction on cleartext HTTP remains.
3. Use the arrows for Back/Forward, the circular arrow to Reload, and pinch to
   zoom. The arrows also work offline after section links.
4. Once the page is readable, scroll to load any lazy images you want to keep. Tap
   the **floppy-disk icon** (Save) and wait for **Saved to Saved websites · available offline**.
5. Tap the **books icon** (Bookshelf), then the **Saved websites** folder, and choose a page.
   The browser's **folder icon** opens the same folder directly.

Saving uses the rendered document and does not wait for all network requests to
finish. A slow image or background request no longer leaves a readable page
waiting indefinitely for capture. Math and fonts get a short settling interval;
unavailable resources are reported in the saved copy's status. The capture
connection also reconnects after cached-page restoration. Navigation during
saving cancels that attempt; storage, size-limit and connection failures are
reported as capture errors rather than asking you to wait for rendering again.

Each fresh entry from the bookshelf opens the native start page with pinned
websites and currently open tabs. Existing website tabs do not load until
you select them. A direct Open online link still opens its requested address,
and screen recreation keeps the current page. The start page never fetches
websites or favicons. Live pages use the engine's
normal mobile layout, JavaScript and fonts, including MathJax and KaTeX.
Ordinary website dialogs and HTML selection controls use native dialogs.
User-initiated new-window links open another tab; automatic popups are
blocked. Use Tabs to open an independent tab explicitly. Image pasting into
website editors is supported. File downloads, file-picker uploads and
password-manager integration are outside this version.
The bundled engine increases APK size. Its separate cookie profile may require
signing into websites again after updating from the earlier WebView browser.

Live browser sessions remain attached while rotating the tablet or returning
from a floating lookup. Rotation resizes the page and note surfaces; it preserves
forms, scroll, tab documents and active captures. Switching to an already-open
tab reuses its session, and closing a background tab does not hide/reactivate the
current page. Reload is for the explicit Reload action; navigation, a reclaimed
renderer or process restart can still require loading a document. Websites may
also refresh their own content.

Saved websites remember reading coordinates separately for each saved copy.
Closing/reopening a copy or restarting the app returns to that point. Scrolls are
checkpointed after a short pause and on leaving the reader; returning to the top
is remembered too. Restoring a position never enables saved-page JavaScript or
network access, and touching the page takes priority over a pending restoration.

## Start page, bookmarks and collections

The start page has a **Search or enter a website** field, pinned shortcuts,
open tabs, and icons for Bookmarks, History, Tabs, New tab, Saved websites,
Add shortcut, Browser data and Bookshelf. The icons wrap on narrower screens.
Wikipedia and GitHub are initial shortcuts; removing them is remembered.
Hold a shortcut to edit, unpin or move it earlier/later. Up to 32 shortcuts can
be pinned. **Home** returns to this page without discarding the current website.
Back can return from Home to that website.
The tab picker stays open as you close tabs in either list or thumbnail view,
updates its count/selection and preserves its scroll position. Tap Done or select
a tab when finished.

Use the **grid/list icons** beside Pinned websites and Open tabs to choose
thumbnails or a list independently. Open or close a tab directly from Home;
closing the selected tab keeps Home visible. Bookmarks and the tab picker have
their own switches. Each view remembers its choice independently. Preview cards
retain separate menu/close buttons and the selected-tab marker.

Saved websites also offers list and thumbnail views, with one list column in
portrait and two in landscape. Its thumbnails use the browser's local preview
for the same address when available; that preview can reflect a later visit than
the offline copy. Missing previews show a placeholder without a network request.

Previews are monochrome 4:3 images of pages already opened in the browser. A site
without a preview has a local placeholder; browsing a library never opens sites
or requests favicons to create thumbnails. The disposable private cache holds
up to 160 images or 16 MB, outside notebook backups. Android may evict it, and a
later visit refreshes the image. Clearing browser data also resets these previews;
bookmark links and saved offline pages remain independent.

The floating panel's **star icon** (Bookmark) saves the current address. Its **⋯**
menu also offers bookmarking, pinning, collections, history, data settings,
copying/sharing the address and opening another installed browser. Bookmarks
are live links; **Save** still creates a separate offline copy.

In Bookmarks, use the star to add a link, the folder-plus to create a collection,
and the three dots beside the collection selector to rename or remove it.
Add or edit a name/address, choose a collection and optionally pin
the link. Search names/addresses, create or rename collections, move bookmarks,
and remove links. Removing a collection moves its bookmarks to Unsorted and
preserves pins. Removing a bookmark also removes its pin, while offline copies
stay available. Collections, pins and history use a private, atomic catalog
with a recovery generation, separate from Gecko's profile and website archives.

## Tabs, history and search

The **stacked-squares icon** (Tabs) opens the tab picker and shows the current
tab count. The **+** icon starts a new tab with pinned websites, and **×** closes
a tab. The current tab has a checkmark and a stronger outline. Titles and addresses
are separate, with long text shortened to leave room for the close control.
Up to 12 tabs may be open. Each loaded tab keeps its own
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

Open the **gear icon** (Browser data) from the start page or **⋯ → Browser data settings**.
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

Address, Back/Forward, Reload, Save, Bookshelf and Notes share one floating panel.
The time/battery/Wi-Fi pill stays at the screen's top-right, including while the
panel is collapsed, matching the bookshelf and notes. The compact panel is at most
400 dp wide. Toolbox actions and the dotted grip use the same 32 dp circular
buttons and 20 dp monochrome symbols. Browser and note actions share this
single window above floating and split notes. Opening a note adds a separated
writing row; minimizing or closing every note hides it. Browser buttons keep
their positions, with one Notes button, one Zen button and one dotted grip.
The writing row provides Pen, Eraser, Lasso, Add page, Outline and AI chat.
Routine title/tab-count status text takes no extra row; saving progress and
capture warnings appear only while relevant. The toolbox and device status
stay above floating chats as well as notes. Activating a note or chat brings
that content window forward while leaving the shared controls accessible.
Closing or minimizing the browser's last chat keeps the browser and notes open.
Action rows use 6 dp gaps and wrap without stretching their buttons. Library/start-page controls keep
their larger size. Actions wrap into balanced rows on narrow screens; none
need sideways scrolling. Hold an action icon to see its name; accessibility labels identify
each action as well.

The layouts target the 10.3-inch 4:3 tablet in both portrait and landscape, such
as 1404 × 1872 and 1872 × 1404 pixels. Android density scaling keeps the controls
usable while preview columns follow the available width.

Floating dictionary/translation windows can pause the browser Activity while its
page remains visible. The visible session stays active behind that window;
sessions pause when the browser is fully covered or their tab is hidden. This
avoids clearing the webpage to white when an Eudic floating lookup opens.

| Icon | Action |
| --- | --- |
| Books | Bookshelf |
| House | Browser start page |
| Stacked squares with number | Tabs |
| Star | Bookmark this page |
| Floppy disk | Save an offline copy |
| Folder | Saved websites |
| Circular arrow | Reload |
| Fullscreen corners | Zen mode |
| Three dots | Browser menu |

Tap the dotted grip to fold the panel into a small button.
Drag it whether expanded or collapsed. It stays on screen after rotation and
keyboard changes, with scrolling if space is short. Position and collapse state
are remembered. Folding controls does not change the page width or reflow content. A docked
note reserves part of the screen and the browser reflows into the remaining area.

Tap the fullscreen-corners icon for **Zen mode**. Only the small top-right clock
with seconds remains, inside the same rounded tab used in notes. Tap the clock or press Android Back to restore the previous
expanded/collapsed state. Zen survives recreation; new browser screens start
outside Zen. Seconds run only while the clock is visible in the foreground.
Errors expose the controls for recovery.

Focused dialogs, the keyboard and chat windows handle Android Back first.
Outside Zen with the webpage focused, Back closes an expanded panel, then follows page
history, then leaves the browser. The panel's arrows operate history directly.

## Notes and image paste

Online browsing and saved pages share one open-note workspace. Open notes,
the editing note, window positions and minimized state follow you between all
browser screens, and survive restarting the app. Closing or minimizing a note
on a saved page also updates the online browser when you return. The notes
remain ordinary notebooks; their contents are not stored inside website archives.

Tap the **overlapping-window icon** (Floating or split notes) in the browser
panel. Choose a notebook to use the same native editor, writing tools, lasso,
figures and window controls as the AI workspace. In a note's corner menu, choose
**Position…** for Left, Right, Top, Bottom or Floating. A docked note shares the
screen with the webpage; floating notes leave the webpage full size. Minimize
or close a note to recover the browser area, and use Notes to reopen minimized
windows. Notes remain separate documents and save through the normal note writer.
Both floating and split editors have corner controls for Position, Minimize and
Close. Pen/eraser settings appear in front of the note. A floating Eudic lookup
from the webpage keeps the notes visible while their writing input is paused;
returning resumes the same note surface.

**Copy ink** in a lasso selection and Copy/Cut figure also put an image/png item
on Android's clipboard. The internal vector/figure clipboard remains available
for pasting inside notes. Wait for **Copied as image**, then hold in a website's
editable message field and choose **Paste**. Website editors that accept pasted
images (such as a chat composer) can attach that image; submit it with the
website's Send button. Markdown copies place source text on the system clipboard.
Scriflow does not submit it automatically. PNGs are bounded to 2048 pixels for
ink and 1 MB, and stored privately with individual read-only clipboard grants;
Android may evict these temporary files.

## Read offline

Saved websites is a permanent folder at the bookshelf root in both list and
thumbnail layouts. It is separate from notebook folders and selection/Trash.
Entries show their title, source address, save time and size, newest first.
Tap an entry to open its local copy with Wi-Fi disabled or after restarting the
app. No revisit of the original address is required.

New saves contain the rendered HTML document, embedded CSS, images, fonts,
readable canvases and accessible frame contents. Already rendered math stays
visible without running the original site's math scripts. Unavailable resources
are counted and reported beside the save/offline status. Inline styles use the
browser's parsed CSS rules, including legacy HTML comment wrappers such as the
ones on Paul Graham's mobile pages; an unreadable style does not abort capture. Existing MHTML saves
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
