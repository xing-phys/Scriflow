# AI chat — 0.39.0-alpha.4

Open **Settings → AI chat** from the time/battery/Wi-Fi pill. Select a saved
provider or tap **Add provider**, choose OpenAI, Anthropic, Google Gemini,
DeepSeek, or **Custom (OpenAI compatible)**, and give it a name. Enter your
API key and API base URL, then tap **Fetch available models**.

Models appear directly below Fetch as compact rows: a checkbox, the model's
name and a settings gear. Check the ones you want and **Save AI settings**.
There is no Model ID field, Add selected dialog or Manage saved models screen.
Detailed parameters stay behind each gear and are filled automatically.

Fetching uses the current fields, including an unsaved new key or a saved key
when its field is blank. Fetch caches public model metadata; saving publishes
the connection, encrypted key, selected models and parameters together. You can
save the connection before choosing any models, then fetch and select later.
Custom endpoints need a model-list API for discovery; earlier saved profiles
remain available offline. Known non-chat models are excluded.

Each provider profile remembers its own name, endpoint, models, and encrypted
key, including multiple profiles of the same type. A blank key field keeps an
existing saved key for the same endpoint; enter the key again when changing a
saved endpoint. **Remove saved key** removes it. Custom HTTPS endpoints may use an
empty key. Provider API subscriptions, account access and charges are managed
by the provider, independently of its chat website subscriptions.

Custom URLs accept an API base such as https://host.example/v1 or the complete
/chat/completions endpoint. Anthropic uses /messages. Cleartext endpoints,
embedded credentials, query strings and fragments are rejected. Redirects are
not followed; enter the final endpoint directly.

## Automatic defaults and optional model settings

The main list shows names and selection controls. Each **gear** opens the model's
context/input limits, maximum output, reply token budget, image support,
supported effort levels and preferred thinking effort. The source and check
date are shown there too. No manual parameter entry is required.

You can edit those values by hand. **Apply** stages changes; **Save AI settings**
saves them. **Cancel** discards form edits. Fetching again retains your overrides
while updating the underlying defaults. **Use fetched defaults** restores those
defaults in the form; Apply and Save retain the reset. Blank limits mean
unknown/provider defaults; a blank reply budget uses automatic sizing.

Thinking defaults to **Automatic (provider default)**. Fetched effort choices
come from advertised levels. The supported-level list can also be edited for
a manual configuration. Requests use the existing provider-specific effort
and thinking fields.

Up to 64 checked models are enabled per provider endpoint. The larger catalog
is cached offline. Uncheck a row and save to remove it from the chat picker;
this keeps its cached configuration and does not delete its chat sessions.
The current default remains selected if still checked; otherwise the first
checked model becomes the default. The chat's model picker can switch it later.

Anthropic and DeepSeek catalogs include native capability metadata. Google's
standard endpoint uses its native model API for limits and the same model IDs
with the chat interface. OpenAI's catalog reports identity rather than detailed
specifications. Fetch can check current/enabled models' public documentation;
opening a gear or saving a newly checked model can complete missing details.
These public GETs never carry the API key, conversation or notebook content.
Cached known details can open without a new request. Custom endpoints use their
own metadata; similar model IDs do not imply another provider's limits.

The automatic reply budget is normally 8,192 tokens, 16,384 for OpenAI, or
32,768 when thinking capabilities are reported, capped by the model's output
maximum and one quarter of a reported total context window. A manual reply
budget must fit its configured output maximum and leave input space in a known
context window. The gear shows model limits separately from a reply preference.

Known incompatible image/text requests are rejected locally. A conservative
UTF-8 text estimate guards input/context limits; images and exact token accounting
remain provider-dependent. It can reject text that an exact tokenizer could
fit. Rejected drafts remain editable. History is never silently truncated or
summarized to fit a model.

## Chat windows

From the bookshelf, tap **AI chat**, then **New chat** or **Open chat history**.
The conversation opens as the main window in an independent workspace, with no
notes open or minimized. To open a note, tap the toolbox notes button and choose
one; it appears as a floating window with both corner controls. Only notes you
explicitly minimize appear in the notes tray. Closing or minimizing the main
chat returns to the bookshelf.

Tap the **sparkle in a chat bubble** in the toolbox to toggle a tray beside
the button, just like the notes tray. It contains **+** for a new floating chat,
**History** for saved sessions, and bubble icons for minimized chats. Each
minimized chat's tooltip/accessibility name identifies its title and model.
The tray remains available when no chat windows are open, so History does not
require creating a blank conversation. **+** uses the selected default model.
Each new window starts a new session without replacing or stopping other chats.

Chats and notes share their actual corner controls, gestures and window geometry:

- **Bottom-left options:** tap for Position, Minimize, Close chat and size
  presets; drag to move a floating window immediately after finger movement.
- **Bottom-right minimize:** tap to hide the chat in the toolbox tray; drag
  to resize. A thin boundary follows the finger while content keeps its previous
  size, then refits on release. The top-left corner stays anchored and the
  right/bottom edges stay within the workspace. All four borders keep the
  separate clipped outer frame. Cancelling a drag restores its original size.
- **Position:** Left, Right, Top, Bottom, Floating window or Main window. A dock reserves
  the remaining area for notes. Docking another chat floats the previous chat
  dock. Main window makes the selected chat the background and presents notes
  as floating windows. You can minimize every note; the top-left note bar hides
  and the device status remains. Use the toolbox notes tray to restore notes.
  In a workspace opened from a note, minimizing or closing the main chat returns
  the note workspace. In the independent bookshelf workspace it returns to the
  bookshelf.
- **Size:** Smaller pane, Equal split / Reset size, or Larger pane.

Windows keep independent conversations, drafts, models and pending replies.
Touching an overlapping chat focuses it and brings it in front after the
current touch gesture. Its relative order is restored with the windows.
Minimizing preserves a reply already requested with Send and releases the
Markdown WebView until restoration. Closing stops that window's request and
keeps its saved history and draft. Other windows continue independently.
Window identities, position/size, original destinations and minimized states
restore after rotation, Settings, backgrounding and app reopen. Leaving the
editor stops pending requests. A visible partial reply is saved; an unanswered
question without a partial reply waits for an explicit Retry.
At most 64 chat windows can be retained at once; close one to free a slot.

The conversation has no title bar. New chat and History live in the toolbox
tray; AI settings open from the normal app Settings. The bottom-left window
options menu includes **Copy last AI reply**.

Tap the **model name in the message bar**, beside Send, to choose a saved
model for a new floating chat. This control shows the model's friendly name;
provider/model ID details remain in its tooltip/accessibility name.
The selected model also becomes the default for subsequent chats opened from the toolbox.
Existing windows keep their original session and destination, including when
the default model changes in Settings. A cached model's parameters remain
available to its history even if that model is unchecked in Settings.

## Messages and Markdown

Write a message and tap the **up-arrow icon at its right**, or use **Ctrl+Enter**
on a keyboard; Enter inserts a line break. The AI receives the conversation's
earlier turns so you can ask follow-up questions. The rounded message bar
contains the window grips, attachment button, model picker and send control.
Short messages share their row; a long message or a line break expands the editor
to the full bar width above those controls, up to six visible lines. The button shows a **stop square** during generation, a
**retry arrow** for an unanswered question, or a **save icon** after a storage
failure. Tooltips and accessibility labels describe the current action.
Stop cancels the local request and saves any visible partial reply. Retry resends an unanswered question after interruption or failure;
the app never retries automatically. Cancellation may arrive after a provider
has started processing, so provider billing can still apply.

Replies stream into the chat as text arrives. Display updates are coalesced for
e-ink and wait for handwriting to settle. A provider that responds with ordinary
JSON instead of a stream is displayed when its complete response arrives. A
partial reply is saved if the stream stops or is interrupted.
Adjust their text size under **Settings → Text size → AI chat replies**; open
chats are restored with the new size after returning from Settings.
The chat reuses the note-block Markdown engine for headings, lists, tables,
highlighted code, LaTeX/MathJax, Mermaid diagrams, and multilingual text.
Use `$…$` and `$$…$$` for inline and display math. Existing rendered turns stay
attached when a follow-up arrives. **Copy last AI reply** in the window options
copies the latest answer as original Markdown; ordinary text selection also
works. Chat Markdown does not open links or load remote images. A rendering
problem retains the original reply, which remains available through that menu.

## Saved sessions

Each newly created window starts a new session by default. Conversations and
unsent text/image drafts save automatically. **+** starts another floating
session while keeping the previous window, without a deletion prompt. Untouched
empty sessions are not added to history. Close chat is in the bottom-left
options menu. The toolbox icon opens the tray; it does not close conversations.

Tap the **history list icon in the toolbox AI tray** to find earlier sessions,
shared across notebooks and providers. Rows show an automatic title from the
first question or draft, provider/model, last activity time, message count and
a draft indicator. The most recently saved session appears first. Tap a row to
reopen its messages, attachments and unsent draft in another window. If that
session already has a window, it is restored and raised instead of creating a
second writer. Only that session's messages
are sent on follow-ups. Opening history or a saved session makes no provider
request. Tap a row's **trash icon**, then confirm, to delete that session's local
transcript and drafts. Deleting a session shown in another window closes that
window, including the active window if it owns that session.
Other sessions and provider-side records are unaffected.

Reopened sessions use their original provider profile, endpoint and model. API keys are
looked up separately and are not included in history. A saved chat remains
readable if its key is missing or cannot be unlocked. If that provider now uses
a different endpoint in Settings, the session remains readable; configure its
original endpoint and key before sending. Restored windows retain their original
provider/model even when Settings changes the default; credentials are resolved
again when returning to the editor. Interrupted questions wait for an explicit Retry.

Existing version-1/2 conversations are imported into history automatically,
including saved drafts and images. Older files store only a configuration
fingerprint. If its provider/model cannot be matched to saved Settings, opening
it asks whether to use the current configuration. It never sends automatically.

## Append selected notebook content

After selecting handwriting with Lasso, tap the **chat-bubble sparkle** beside
the existing copy/export controls. The same icon appears when selecting an
image or Markdown block. Configure the AI provider first. Appending opens or
raises the last active chat and prepares a draft; a minimized target is restored,
and a new chat is created if none exists. Tap a chat before selecting content to
choose the destination. Appending never sends a request by itself
and does not edit the notebook or clipboard.

Selected Markdown appends its original source to the message field. Selected
handwriting becomes a tightly cropped PNG containing only the selected strokes,
with the native pressure outlines and white background. Images append a copy
of their source pixels. Image copies are bounded to 2048 pixels per edge and
1 MiB each, downscaling if necessary. Up to four attachments can accompany a
message; the chat has a 16 MiB total attachment budget in addition to text.

Pending images appear as removable **Ink/Image ×** chips above the message
field. Review them, add your question, and tap the **send arrow**. Sent images appear in
the conversation and remain available to the model on follow-up turns.
Appending while a reply is being generated prepares the next draft without
changing the in-flight request. Unsent image drafts survive closing and are
restored when their session is reopened from History.
Your selected provider and model must support image input to analyze images.

Tap **+** to the left of the message field and choose a notebook to attach all
of its pages, handwriting, figures and source PDF content as a PDF snapshot.
Each notebook PDF is limited to 8 MiB. If the notebook changes during export,
preparation fails and can be retried; no partial attachment is sent. Attaching
does not contact the provider. The finished notebook appears as its own
removable chip and is sent only with the message.

OpenAI and Anthropic receive notebook PDFs using their documented
[file](https://developers.openai.com/api/docs/guides/file-inputs) and
[document](https://platform.claude.com/docs/en/build-with-claude/pdf-support)
formats. Standard Gemini uses its [native document API](https://ai.google.dev/gemini-api/docs/generate-content/document-processing)
for chats containing PDFs; set its thinking effort to Automatic for these
requests. A custom OpenAI-compatible endpoint may support OpenAI's file format.
DeepSeek and nonstandard Gemini endpoints cannot receive these PDF parts here;
Send explains the limitation and leaves the draft intact. A model must support
the relevant visual/document input, and provider limits still apply.

## Data and request handling

Sending transmits this chat's text, explicitly attached image and notebook history, and the app's Markdown
formatting instructions directly to the configured provider over HTTPS.
Scriflow does not upload handwriting, notes, figures or PDFs automatically.
Paste text or use the selection's append icon to share it, then press the send arrow.
Fetching models sends authenticated GET requests to the configured provider's
model metadata API, without conversation or notebook content. For the standard
OpenAI endpoint, selected-model specifications can also be fetched from public
OpenAI documentation without credentials. Choosing a saved model makes no chat
request; Send remains explicit.
The provider's privacy and retention policies
apply to submitted content. OpenAI requests explicitly set `store: false`;
that setting does not override the provider's other data policies.

API keys are encrypted using AES-GCM with an app-owned Android Keystore key.
They are never placed in the Markdown WebView, saved text-field state, notebook
files, exported backups or logs. **Remove saved key** removes the stored
credential for that provider. Conversation history and unsent drafts are
unencrypted files in app-private storage, outside notebook backups. Android
automatic backup remains disabled. Window preferences separately store geometry,
minimized state, public destinations, session IDs and short titles, without
credentials or full transcripts. Uninstalling or clearing app storage removes
these local settings and histories.

Chats saved with newly added provider profiles include their profile identity in
the archive header. Earlier APKs cannot read those chat archives or restore the
new window layout; existing legacy-provider chats and notebook files remain
readable by the versions that already supported their formats.

Each session is bounded to 80 messages, 128 KiB of UTF-8 per message, and 1 MiB
total including the draft, with a separate 16 MiB attachment budget. There is no
automatic summarization or mixing of sessions. Session archive format 1 wraps
a checksummed conversation snapshot. PDF-bearing snapshots or snapshots with
more than 8 MiB of attachments use version 3, which older APKs cannot read;
ordinary smaller text/image chats retain version 2. Notebook and backup format 8 is
unchanged. Questions and attachments are saved atomically before a provider
request starts. Storage errors retain the in-memory text and offer Retry/Save;
opening another window flushes the source draft and leaves its controller intact.
Each session's requests still wait for their own durable question save. Damaged history
is reported and kept for recovery, and can be deleted from History. New chat
starts fresh without overwriting the damaged archive. The previous durable
transcript survives a failed replacement. Deletion markers prevent late saves
or cancelled replies from recreating a deleted chat. Replies that hit a
provider's output-token limit are marked so you can ask it to continue.

Each visible WebView has blocked network loads and the same local content
security policy and sanitizer as note Markdown. No JavaScript bridge is
installed. API requests run separately from Markdown rendering and native ink;
they use finite connection/read timeouts and bounded response reads.
All visible chat windows and the tray contribute native pen exclusions.
Panel contacts pause native input before child dispatch. Writer barriers run
before resuming; moving or resizing updates native exclusions and restores
newly uncovered paper. Dock changes wait for the writer barrier before note
relayout. History, deletion and window-option dialogs keep native input paused.
The software keyboard's visible area is also excluded from native handwriting
while typing in chat, including while keyboard dismissal completes.

## Validation

Run `scripts/test.sh` and Android debug/release lint and builds. AI tests use
the production HTTP, transcript, and session code with deterministic connection
and worker fixtures; they make no paid provider calls. They cover all provider
formats, role order, authentication headers, redirects, errors, bounds,
cancellation, retries, file corruption, publication failure, reopening and stale
callbacks. Session-history fixtures cover independent new chats, draft/image
round trips, metadata-only listing, original destinations, legacy migration,
deletion/retry and preventing resurrection by late replies. Production
window-callback checks exercise native contact and writer ordering, history
modal barriers, multi-window suspension/restoration, active selection routing,
single ownership of reopened history, minimized request retention and renderer
disposal. State tests cover independent geometry, all docks, minimized restore
and damaged preferences. Concurrent controller tests interleave same-model
requests, cancel/close one owner, and reopen their independent archives.
The production shared note/chat resize grip is exercised at size/position limits.
Composer fixtures verify the message/send row, model placement, reserved corner
space, friendly names and icon-only Send/Stop/Retry/Save availability. Model tap
fixtures cover synchronous dialog opening before native input resumes, finger
jitter, dragging, cancellation and multiple fingers. Tray/history fixtures
cover access with no windows, draft flushing, modal barriers, late confirmed
deletions after dismissal and avoiding stale UI callbacks.
Separate border tests execute the production outer-frame drawing
with oversized children, scan all four painted edges after growth/shrink,
check invalidation at the new size, and check the popup's clipping flags. These
are host raster tests and do not reproduce Huawei's physical display compositor.
Model fixtures cover provider metadata, native Gemini pagination,
public documentation without credentials, snapshot identity, saved profiles,
supported effort parameters, bounded replies and editable context rejections.
Manual-parameter fixtures cover save/reopen, actual request fields, refresh,
invalid edits and reset to the newest defaults. Production model-row callbacks
verify name-only labels, inline selection, matching gears and worker guards.
Sixteen real-browser cases
cover live reflow, note/chat Markdown, math, CJK, diagrams, follow-up retention, local image
previews, invalid image URLs, reset, sanitization and zero external requests.

The host AI test bootstrap downloads checksum-pinned JSON-java **20240303**
into ignored `.tools/ai-tests`; Android uses its built-in `org.json` instead.
No new runtime dependency is added. Prepare tablet updates only with
`scripts/build-release.sh` and the permanent release signing certificate.
Page and backup format 8 are unchanged.

The alpha.4 validation run passed **276,370 JVM assertions in 119 suites**,
sixteen browser cases, debug/release lint and builds, and permanent-signer
verification. The final composer and multitouch cancellation changes were
rerun in their focused fixtures after the full suite.
The new action icons were visually inspected from the production vector paths.
The Markdown chat screenshot was visually inspected for text, equations,
tables, code, diagrams and Chinese; it does not verify the native panel on e-ink.

No tablet is connected for this task. Physical keyboard/IME layout, drag/resize,
multiple-window stacking, docking, stylus exclusions, e-ink appearance, RAM use
over long chats, and live calls
with the owner's provider accounts still need acceptance on MatePad Paper.

Implementation references:
[OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create),
[GPT-6 Luna](https://developers.openai.com/api/docs/models/gpt-6-luna),
[Anthropic Messages](https://platform.claude.com/docs/en/api/messages/create),
[Claude models](https://platform.claude.com/docs/en/models/overview),
[Gemini OpenAI compatibility](https://ai.google.dev/gemini-api/docs/openai),
and [DeepSeek API](https://api-docs.deepseek.com/).
Model discovery follows [OpenAI's model list](https://developers.openai.com/api/reference/resources/models/methods/list),
[Anthropic's paginated model list](https://platform.claude.com/docs/en/api/models/list)
and Gemini's OpenAI-compatible model-list API. Image payloads follow the
providers' chat image content formats, including [Anthropic vision](https://platform.claude.com/docs/en/build-with-claude/vision).
Automatic specifications follow [Claude's model capabilities](https://platform.claude.com/docs/en/api/http/models/list),
[Gemini's native model metadata](https://ai.google.dev/api/models),
[DeepSeek's model metadata](https://api-docs.deepseek.com/api/list-models/), and
[OpenAI's public model documentation](https://developers.openai.com/api/docs/models).
