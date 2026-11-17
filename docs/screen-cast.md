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
unexported foreground service. Foreground app/window focus gates frame
publication; revocable generation tickets discard late encodes and queued
frames from before resuming. The capture surface detaches while paused.
Capture follows rotation using the existing virtual display and a resized
ImageReader. JPEG encoding runs on a background thread, with the latest frame
coalesced at up to four frames per second and a 1600-pixel longest edge.
Frames stay in memory. This is intended for sharing notes and reading rather
than video or latency-sensitive remote interaction.

The server binds specifically to the connected Wi-Fi IPv4 interface, closes
on network loss/address changes, uses random HttpOnly/SameSite session cookies,
checks Host and Origin, throttles login attempts, and bounds request sizes,
workers and session state. Bundled HTML/CSS/JavaScript make no external requests.
Every image request requires authentication. Viewers clear images on pause,
disconnect, hidden tabs and expired sessions. Notebook/backup formats are unchanged.

Run `bash scripts/test-cast.sh` for real-socket authentication, isolation,
expiry, throttling, malformed requests and frame-ownership regressions. Run
the Android and browser checks described by `scripts/check-cast.py` and
`scripts/test-cast-viewer.cjs` for the complete interaction. Physical Huawei
native ink, e-ink performance, Safari and real cross-device Wi-Fi acceptance
remain owner testing checks.
