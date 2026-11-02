# AI chat — 0.36.0-alpha.3

Open **Settings → AI chat** from the time/battery/Wi-Fi pill. Choose OpenAI,
Anthropic, Google Gemini, DeepSeek, or **Custom (OpenAI compatible)**. Enter
your API key, check the API base URL, then tap **Fetch available models**.
Choose a model from the model field's dropdown and tap **Save AI settings**.
Fetching uses the current fields, including an unsaved new key or a saved key
when its field is blank; it does not save or replace your configuration.
The provider's returned catalog can include non-chat models. Choose a model
supporting chat, and image input for handwriting/image selections. Manual model
entry remains available if your custom endpoint has no model-list API.
Each provider remembers its own configuration. A blank key field keeps an
existing saved key; **Remove saved key** removes it. Custom HTTPS endpoints
may use an empty key. API subscriptions, account access and usage charges are
managed by your provider.

Preset model IDs are editable examples checked against provider documentation
on 2026-11-01: `gpt-6-luna`, `claude-sonnet-5-5`, `gemini-3.8-flash`, and
`deepseek-flash`. Select a model your API account can access. Presets use the
provider's public API, rather than a ChatGPT, Claude, or Gemini website login.
Custom URLs accept either an API base (for example `https://host.example/v1`)
or the complete `/chat/completions` endpoint. Anthropic uses `/messages`.
HTTP endpoints, embedded URL credentials, query strings and URL fragments are
rejected. Redirects are not followed; enter the final endpoint directly.

Tap the **sparkle in a chat bubble** in the toolbox to open the floating chat.
Drag the title to move it and the lower-right handle to resize it. Position and
size are remembered. Resizing holds the top-left corner fixed, changes the
right/bottom edges within the visible screen, and redraws a foreground border
above the contents. Write a message and tap **Send**, or use **Ctrl+Enter**
on a keyboard; Enter inserts a line break. The AI receives the conversation's
earlier turns so you can ask follow-up questions. **Stop** cancels the local
request. **Retry** resends an unanswered question after interruption or failure;
the app never retries automatically. Cancellation may arrive after a provider
has started processing, so provider billing can still apply.

Replies appear as complete messages, avoiding token-by-token refresh on e-ink.
The chat reuses the note-block Markdown engine for headings, lists, tables,
highlighted code, LaTeX/MathJax, Mermaid diagrams, and multilingual text.
Use `$…$` and `$$…$$` for inline and display math. Existing rendered turns stay
attached when a follow-up arrives. **Copy** copies the latest AI answer as
original Markdown; ordinary text selection also works. Chat Markdown does not
open links or load remote images. A rendering problem retains the original
reply, which remains available through Copy.

## Saved sessions

Opening chat from the toolbox starts a new session by default. Conversations
and unsent text/image drafts save automatically. **+** starts another session
while keeping the previous one, without a deletion prompt. Untouched empty
sessions are not added to history. The **×** closes the panel; the toolbox icon
also toggles it. Closing or leaving the editor stops pending requests and keeps
the transcript and draft.

Tap the **history list icon** in the chat header to find earlier sessions,
shared across notebooks and providers. Rows show an automatic title from the
first question or draft, provider/model, last activity time, message count and
a draft indicator. The most recently saved session appears first. Tap a row to
reopen its messages, attachments and unsent draft. Only that session's messages
are sent on follow-ups. Opening history or a saved session makes no provider
request. Tap a row's **trash icon**, then confirm, to delete that session's local
transcript and drafts. Other sessions and provider-side records are unaffected.

Reopened sessions use their original provider, endpoint and model. API keys are
looked up separately and are not included in history. A saved chat remains
readable if its key is missing or cannot be unlocked. If that provider now uses
a different endpoint in Settings, the session remains readable; configure its
original endpoint and key before sending. Returning from Settings or rotating
restores the active session if the Settings provider/endpoint/model has not
changed; changing an API key alone preserves it. Interrupted questions wait for
an explicit Retry.

Existing version-1/2 conversations are imported into history automatically,
including saved drafts and images. Older files store only a configuration
fingerprint. If its provider/model cannot be matched to saved Settings, opening
it asks whether to use the current configuration. It never sends automatically.

## Append selected notebook content

After selecting handwriting with Lasso, tap the **chat-bubble sparkle** beside
the existing copy/export controls. The same icon appears when selecting an
image or Markdown block. Configure the AI provider first. Appending opens or
raises the current chat and prepares a draft; it never sends a request by itself
and does not edit the notebook or clipboard.

Selected Markdown appends its original source to the message field. Selected
handwriting becomes a tightly cropped PNG containing only the selected strokes,
with the native pressure outlines and white background. Images append a copy
of their source pixels. Image copies are bounded to 2048 pixels per edge and
1 MiB each, downscaling if necessary. Up to four images can accompany a message;
the chat has an 8 MiB total image budget in addition to its text budget.

Pending images appear as removable **Ink/Image ×** chips above the message
field. Review them, add your question, and tap **Send**. Sent images appear in
the conversation and remain available to the model on follow-up turns.
Appending while a reply is being generated prepares the next draft without
changing the in-flight request. Unsent image drafts survive closing and are
restored when their session is reopened from History.
Your selected provider and model must support image input to analyze images.

## Data and request handling

Sending transmits this chat's text, explicitly appended image history, and the app's Markdown
formatting instructions directly to the configured provider over HTTPS.
Scriflow does not upload handwriting, notes, figures or PDFs automatically.
Paste text or use the selection's append icon to share it, then press Send.
Fetching models sends an authenticated GET request to the configured model-list
endpoint, without any conversation or notebook content.
The provider's privacy and retention policies
apply to submitted content. OpenAI requests explicitly set `store: false`;
that setting does not override the provider's other data policies.

API keys are encrypted using AES-GCM with an app-owned Android Keystore key.
They are never placed in the Markdown WebView, saved text-field state, notebook
files, exported backups or logs. **Remove saved key** removes the stored
credential for that provider. Conversation history and unsent drafts are
unencrypted files in app-private storage, outside notebook backups. Android
automatic backup remains disabled. Uninstalling or clearing app storage removes
these local settings and histories.

Each session is bounded to 80 messages, 128 KiB of UTF-8 per message, and 1 MiB
total including the draft, with a separate 8 MiB image budget. There is no
automatic summarization or mixing of sessions. Session archive format 1 wraps
the existing version-2 conversation snapshot with checksummed metadata. Earlier
AI APKs cannot read these session archives; notebook and backup format 8 is
unchanged. Questions and attachments are saved atomically before a provider
request starts. Storage errors retain the in-memory text and offer Retry/Save;
starting another session waits for the current chat to save. Damaged history
is reported and kept for recovery, and can be deleted from History. New chat
starts fresh without overwriting the damaged archive. The previous durable
transcript survives a failed replacement. Deletion markers prevent late saves
or cancelled replies from recreating a deleted chat. Replies that hit a
provider's output-token limit are marked so you can ask it to continue.

The visible WebView has blocked network loads and the same local content
security policy and sanitizer as note Markdown. No JavaScript bridge is
installed. API requests run separately from Markdown rendering and native ink;
they use finite connection/read timeouts and bounded response reads.
Panel contacts pause native input before child dispatch. Writer barriers run
before resuming; moving or resizing updates native exclusions and restores
newly uncovered paper. History and deletion dialogs keep native input paused.
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
modal barriers, Activity session retention and the production resize
grip/placement. Fourteen real-browser cases
cover note/chat Markdown, math, CJK, diagrams, follow-up retention, local image
previews, invalid image URLs, reset, sanitization and zero external requests.

The host AI test bootstrap downloads checksum-pinned JSON-java **20240303**
into ignored `.tools/ai-tests`; Android uses its built-in `org.json` instead.
No new runtime dependency is added. Prepare tablet updates only with
`scripts/build-release.sh` and the permanent release signing certificate.
Page and backup format 8 are unchanged.

The completed build passes **272,815 JVM assertions in 96 suites**, fourteen
browser cases, debug/release lint and builds, and permanent-signer verification.
The Markdown chat screenshot was visually inspected for text, equations,
tables, code, diagrams and Chinese; it does not verify the native panel on e-ink.

No tablet is connected for this task. Physical keyboard/IME layout, drag/resize,
stylus exclusions, e-ink appearance, RAM use over long chats, and live calls
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
