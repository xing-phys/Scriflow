# Screen cast on local Wi-Fi

Open **Settings → Screen cast → Screen cast to a browser…** on the tablet.
Set an **8–128 character password**, or use **Generate password** and remember
the displayed value. Tap **Start casting** and approve Android's screen-capture
prompt. Android asks again for each new cast session.

The panel displays an address such as **http://192.168.1.42:8080/**. On your Mac,
phone or another tablet connected to the same Wi-Fi, open that complete address
in a browser and enter the password. Return to a Scriflow note, PDF, ebook or
browser page on the tablet to share it. Multiple devices can watch together;
each browser signs in independently. The viewer supports portrait/landscape
screens and a full-screen button where the browser provides it.

Alpha.2 improves lecture use: the tablet's native pixels are retained up to a
2048-pixel longest edge, with higher JPEG quality. A waiting frame request
receives changes as soon as the capture worker publishes them. The browser
keeps the previous decoded picture until the next is ready, including recovery
from an incomplete image. Full-screen sharing has a white pause/connection
panel instead of exposing the dark page between images.
During a lasso or eraser gesture, viewers keep the last safe picture until
shortly after the stylus lifts. This deliberately pauses the video while those
tools are in use, giving the tablet's drawing hardware more time for the trail.
An all-black replacement is confirmed for 250 ms before display, so short
compositor blackouts do not flash over a lecture page. Persistent black
content is still displayed; this check runs in the viewer, not the tablet's
input process.
When updating from alpha.1, restart casting and reload the viewer page to load
the new viewer code.

Viewers can watch the screen. Writing, navigation and other controls operate
on the tablet. There is no remote input, audio, recording, cloud service or
Internet requirement. Up to 16 browser sessions are allowed; idle sessions
expire after 30 minutes. Signing out disconnects that browser only.

**Stop casting** in the panel or Android's persistent **Scriflow screen cast**
notification ends every session and closes the local server. Start again to
set a different password. Passwords and sessions are held in memory and are
not saved in preferences or backups. Casting never starts automatically.

## Privacy and connection limits

- The panel and all of Scriflow's Settings stay private. The viewer clears its
  image while they are open. Leaving Scriflow or opening another app pauses
  sharing; returning to a focused Scriflow screen resumes it. Loss of app
  window focus, the notification shade and split-screen mode also pause it.
  Brief app-owned popup handoffs retain the last safe picture while new pixels
  are blocked, avoiding a display restart for every tool popup. Settings and
  genuine app-background transitions clear immediately.
- Turning off or locking the display, disconnecting Wi-Fi, changing the Wi-Fi
  address or Android stopping capture ends the session. Start again after
  reconnecting; the panel displays the current address.
- This is a **local HTTP connection without transport encryption**. The
  password prevents unauthenticated viewing, but does not protect against
  traffic interception on an untrusted network. Use trusted Wi-Fi. Do not
  forward the port on a router or expose it to the Internet. Other content
  visible inside Scriflow, including signed-in browser pages, is shared.
- Guest Wi-Fi and access-point/client isolation can prevent devices from
  reaching the tablet even when the network name matches. Browser or OS
  local-network access prompts must be allowed. The first version uses a
  Wi-Fi IPv4 address; IPv6-only networks and hotspot hosting are not supported.
- The address includes the port; use **http://**, not https://. Port 8080 is
  preferred; an available port is selected if another app already uses it.
  Use the exact address shown on the tablet.

## Implementation and verification

Android's [MediaProjection](https://developer.android.com/media/grow/media-projection)
captures the composed display, including native/browser surfaces, through an
unexported foreground service in a separate private `:cast` process. The UI
process sends small IPC visibility/contact messages, without copying pixels
or waiting for encoding. JPEG allocations, GC and HTTP do not share its heap.
Foreground app/window focus gates frame
publication; revocable generation tickets discard late encodes and queued
frames from before resuming. Genuine background/focus loss and absent viewers
detach the capture surface. Controller death ends the session; an expiring
visibility lease also prevents a stalled controller from publishing indefinitely.
Capture follows rotation using the existing virtual display and a resized
ImageReader. A lowest-priority worker acquires only the latest image at the
next capture slot, reuses pixel/JPEG work buffers, and discards intermediate
compositor frames. Resolution is capped at 2048 pixels; JPEG quality is 94.
The ceiling is eight frames/s normally and six during ordinary pen contact.
Measured encoding cost increases the interval to reserve at least half the
worker's time normally and three quarters during pen contact. Lasso and eraser
contact detach the capture surface until 300 ms after the last lift; an in-flight
frame cannot replace the held image. These are scheduling
ceilings, not measured Huawei frame rates or pen-latency guarantees. Android's
GPU, memory bandwidth and Wi-Fi are still shared hardware resources.
Frames stay in memory. This targets writing, reports and lectures; it is not
a 30/60 fps video stream.

The server binds specifically to the connected Wi-Fi IPv4 interface, closes
on network loss/address changes, uses random HttpOnly/SameSite session cookies,
checks Host and Origin, throttles login attempts, and bounds request sizes,
workers and session state. Bundled HTML/CSS/JavaScript make no external requests.
Each viewer has one bounded waiting request and two image slots; there is no
growing playback queue. Identical JPEGs do not advance the frame sequence.
Authorization is checked both before and after a waiting request. Every image
request requires authentication. Viewers clear images on private/background pause,
disconnect, hidden tabs and expired sessions. Notebook/backup formats are unchanged.

Run `bash scripts/test-cast.sh` for real-socket authentication, isolation,
expiry, throttling, malformed requests and frame-ownership regressions. Run
the Android and browser checks described by `scripts/check-cast.py` and
`scripts/test-cast-viewer.cjs` for the complete interaction. Physical Huawei
native dashed-line feedback, optical pen latency, e-ink performance and the
new Safari/Wi-Fi presentation behavior remain owner testing checks. The owner
confirmed the initial cast works before this optimization.
