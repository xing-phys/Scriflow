# AI chat — 0.36.0-dev.1

Open **Settings → AI chat** from the time/battery/Wi-Fi pill. Choose OpenAI,
Anthropic, Google Gemini, DeepSeek, or **Custom (OpenAI compatible)**. Enter
your API key, check the model ID and API base URL, then tap **Save AI settings**.
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
size are remembered. Write a message and tap **Send**, or use **Ctrl+Enter**
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

**+** starts a new chat after confirming removal of this configuration's
current conversation and draft. The **×** closes the panel; the toolbox icon
also toggles it. Closing or leaving the editor stops pending requests and keeps
the transcript and draft. Returning from Settings or rotating restores an open
panel; interrupted questions wait for an explicit Retry. Each provider,
endpoint and model has its own current conversation, shared across notebooks.
Changing an API key alone keeps that conversation. There is no chat archive or
attachment picker in this version.

## Data and request handling

Sending transmits only this chat's text history and the app's Markdown
formatting instructions directly to the configured provider over HTTPS.
Scriflow does not upload handwriting, notes, figures or PDFs automatically.
Paste text into chat to share it. The provider's privacy and retention policies
apply to submitted content. OpenAI requests explicitly set `store: false`;
that setting does not override the provider's other data policies.

API keys are encrypted using AES-GCM with an app-owned Android Keystore key.
They are never placed in the Markdown WebView, saved text-field state, notebook
files, exported backups or logs. **Remove saved key** removes the stored
credential for that provider. Conversation history and unsent drafts are
unencrypted files in app-private storage, outside notebook backups. Android
automatic backup remains disabled. Uninstalling or clearing app storage removes
these local settings and histories.

History is bounded to 80 messages, 128 KiB of UTF-8 per message, and 1 MiB total
including the draft. Questions are saved atomically before a provider request
starts. Storage errors retain the in-memory text and offer Retry/Save; damaged
history is reported and requires an explicit New chat to reset. The previous
durable transcript survives a failed replacement. Replies that hit a provider's
output-token limit are marked so you can ask it to continue.

The visible WebView has blocked network loads and the same local content
security policy and sanitizer as note Markdown. No JavaScript bridge is
installed. API requests run separately from Markdown rendering and native ink;
they use finite connection/read timeouts and bounded response reads.
Panel contacts pause native input before child dispatch. Writer barriers run
before resuming; moving or resizing updates native exclusions and restores
newly uncovered paper. Clear-chat confirmation keeps native input paused.
The software keyboard's visible area is also excluded from native handwriting
while typing in chat, including while keyboard dismissal completes.

## Validation

Run `scripts/test.sh` and Android debug/release lint and builds. AI tests use
the production HTTP, transcript, and session code with deterministic connection
and worker fixtures; they make no paid provider calls. They cover all provider
formats, role order, authentication headers, redirects, errors, bounds,
cancellation, retries, file corruption, publication failure, reopening and stale
callbacks. Production window-callback checks exercise native contact and writer
ordering. Eleven real-browser cases cover note and chat Markdown, math, CJK,
diagrams, follow-up retention, reset, sanitization and zero external requests.

The host AI test bootstrap downloads checksum-pinned JSON-java **20240303**
into ignored `.tools/ai-tests`; Android uses its built-in `org.json` instead.
No new runtime dependency is added. Prepare tablet updates only with
`scripts/build-release.sh` and the permanent release signing certificate.
Page and backup format 8 are unchanged.

The completed build passes **272,567 JVM assertions in 91 suites**, eleven
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
