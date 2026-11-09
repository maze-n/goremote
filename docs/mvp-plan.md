# GoRemote — MVP Architecture & Implementation Plan

**Project goal:** Control media playback, system volume, and sleep on a Mac from an Android phone over the same Wi-Fi network.

## Project decisions

| Decision | Selected approach |
|---|---|
| Android | Kotlin + Jetpack Compose |
| Mac backend | Go |
| Network | Local Wi-Fi only for MVP |
| Media controls | Universal macOS media-key events |
| Discovery | Bonjour / mDNS |
| Communication | HTTP + JSON |
| Deployment | Go server as a per-user macOS LaunchAgent |

## 1. Product scope

The MVP is a self-hosted Mac remote-control system. The Go backend runs on the Mac as a background service; the Android app discovers it and sends authenticated commands over the local network.

### Included in MVP

- **Playback:** Play/Pause, Next and Previous using system media-key events.
- **Volume:** Read, set, increase, decrease and mute/unmute system output volume.
- **Sleep:** Put the Mac to sleep, with a confirmation in the Android UI.
- **Discovery:** Find the Mac automatically using Bonjour/mDNS; no manual IP entry in normal use.

### Explicitly out of scope

- Cloud backend, remote internet access, user accounts, database and multi-Mac management.
- Now-playing metadata, Android home-screen widget, lock Mac and sleep timer.

## 2. Architecture

| Layer | Responsibility / technology |
|---|---|
| Android app | Kotlin, Jetpack Compose, MVVM, OkHttp/Retrofit, Android NSD |
| Transport | HTTP/JSON over local Wi-Fi |
| Go server | `net/http`, API handlers, auth middleware, command orchestration |
| macOS adapter | Go `os/exec` for system commands; Swift helper for media-key events |
| Discovery | Bonjour / DNS-SD service advertisement from the Mac |
| Runtime | Per-user `launchd` LaunchAgent |

### High-level request flow

```text
Android UI  →  HTTP/JSON over Wi-Fi  →  Go API server
                                      ↓
                             Controller interfaces
                                      ↓
                     macOS commands / Swift helper
                                      ↓
                       Media · Volume · Sleep
```

The Go backend should own the API and application logic. macOS-specific behaviour belongs behind interfaces so it can be mocked in tests and replaced independently.

## 3. macOS integration

| Capability | Initial implementation | Notes |
|---|---|---|
| Play/Pause, Next, Previous | Small Swift helper using CoreGraphics media-key events | Test with Spotify, Apple Music, Safari and Chrome. May require Accessibility permission. |
| System volume | `osascript` initially; consider native CoreAudio later | Use absolute 0–100 values for the slider. |
| Sleep | `pmset sleepnow` | Connection drops after the Mac sleeps; expected behaviour. |
| Background startup | `launchd` LaunchAgent | Prefer the logged-in user's session over a system LaunchDaemon. |

**Important technical risk:** Universal media control is the main uncertainty. macOS media-key event injection is not the same as a simple AppleScript call; validate the helper early on the target macOS version and with the media applications you intend to support.

## 4. REST API contract

Base URL: `http://<mac-ip>:8765`. Use versioned routes and consistent JSON response bodies.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/health` | Connectivity / readiness check |
| GET | `/api/v1/status` | Mac status and volume |
| POST | `/api/v1/media/play-pause` | Toggle playback |
| POST | `/api/v1/media/next` | Next track |
| POST | `/api/v1/media/previous` | Previous track |
| GET | `/api/v1/volume` | Read system volume |
| PUT | `/api/v1/volume` | Set volume |
| POST | `/api/v1/volume/mute` | Toggle mute |
| POST | `/api/v1/system/sleep` | Put Mac to sleep |

### Example request

```http
PUT /api/v1/volume
Authorization: Bearer <token>
Content-Type: application/json

{
  "level": 65
}
```

### Example response

```json
{
  "success": true,
  "data": {
    "level": 65,
    "muted": false
  }
}
```

### Authentication and LAN safety

1. Generate a random pairing PIN on the Mac and exchange it for a cryptographically random bearer token.
2. Require the bearer token on every control endpoint; do not use the PIN as the permanent credential.
3. Store the token securely on Android, e.g. using Android Keystore-backed encrypted storage.
4. Bind the server to the LAN interface or otherwise restrict access to the local network; do not expose the port to the public internet.
5. Return appropriate status codes, including `401` for invalid credentials and `503` when a required native helper is unavailable.

## 5. Go backend design

```text
backend/
├── cmd/macremote/main.go
├── internal/
│   ├── api/       # router, handlers, middleware
│   ├── media/     # controller interface + macOS adapter
│   ├── volume/    # controller interface + macOS adapter
│   ├── system/    # power controller
│   ├── discovery/ # Bonjour advertisement
│   └── pairing/   # PIN and token management
└── go.mod

macos/media-helper/main.swift
```

Keep HTTP handlers thin: validate input, call an interface, map errors to HTTP responses. Do not execute shell commands directly from handlers. Use command timeouts and avoid building shell strings from user input.

### Core interfaces

```go
type MediaController interface {
    PlayPause() error
    Next() error
    Previous() error
}

type VolumeController interface {
    Get() (int, error)
    Set(level int) error
    ToggleMute() error
}

type PowerController interface {
    Sleep() error
}
```

## 6. Android app design

Use a small MVVM structure. Keep networking and discovery out of composables; expose connection state and actions through a ViewModel/repository.

```text
android/app/src/main/java/.../macremote/
├── data/
│   ├── api/          # API interface and HTTP client
│   ├── discovery/    # Android NSD discovery
│   └── repository/  # remote-control repository
├── ui/
│   ├── remote/      # RemoteScreen + ViewModel
│   └── pairing/     # Pairing screen
└── MainActivity.kt
```

### Main screen

- Connection status and selected Mac name.
- Large Play/Pause button, with Previous and Next controls.
- Volume slider, current percentage and mute control.
- Sleep Mac action with an explicit confirmation dialog.
- Disconnected state with retry / rediscovery.

### Discovery flow

```text
1. Start Android NSD discovery for _macremote._tcp.
2. Resolve the matching service to host and port.
3. Call GET /api/v1/health.
4. Show the Mac as available.
5. Pair with PIN if this phone is not yet trusted.
6. Store token and connect.
```

## 7. Milestones and deliverables

| Milestone | Implementation | Exit criteria |
|---|---|---|
| M1 — Go API | Server, routes, mocked controllers, curl tests | All endpoints work against mocks. |
| M2 — Media | Swift helper and Go adapter | Media keys work in target apps. |
| M3 — System controls | Volume, mute and sleep adapters | Volume verified; sleep tested safely. |
| M4 — Android UI | Compose screen, API client, ViewModel | Phone controls Mac using a manually configured IP. |
| M5 — Discovery & pairing | Bonjour advertisement, Android NSD, PIN/token | Automatic discovery and authenticated pairing. |
| M6 — Packaging | `launchd`, permissions, logging and reliability | Server starts with the logged-in user session and behaves reliably. |

### Recommended implementation order

**Finish and validate the Go backend before building the Android UI.** First use mocked controllers to prove the API, then integrate real macOS behaviour. This isolates platform issues from Android UI and networking issues.

## 8. Testing checklist

| Area | Test | Expected result |
|---|---|---|
| Media | Play/Pause, Next, Previous in target apps | Active system media session responds. |
| Volume | Set 0, 25, 65, 100; read back | Reported and actual system volume agree. |
| Mute | Mute then unmute | Mute state changes and restores sensibly. |
| Sleep | Confirm sleep command | Mac sleeps; client marks connection unavailable. |
| Discovery | Restart server and rescan | Android finds service without manual IP. |
| Network | Wi-Fi drop and reconnect | Clear disconnected state and successful retry. |
| Security | Missing/invalid token | Control endpoints reject request. |
| Startup | Restart Mac / log in | LaunchAgent starts service in user session. |

### Router caveat

Bonjour relies on multicast DNS. Guest Wi-Fi, client isolation, VLAN boundaries or router multicast filtering may prevent discovery even when ordinary IP connectivity works. Provide a manual IP fallback for troubleshooting.

## 9. Schedule estimate

Estimated effort: approximately **5–7 focused development days** for a first working MVP, assuming access to the target Mac and no major macOS permission or compatibility blockers.

| Day | Focus |
|---|---|
| 1 | Go project structure, interfaces, API and mocked controllers |
| 2 | Swift media helper and universal playback validation |
| 3 | Volume, mute and sleep integration |
| 4 | Kotlin Compose UI and HTTP integration |
| 5 | Bonjour discovery and pairing |
| 6–7 | `launchd`, permissions, device/router testing and fixes |

## 10. MVP acceptance criteria

- The Android app discovers the Mac on the same Wi-Fi network.
- A paired Android device can play/pause, skip forward/backward and control system volume.
- The app can request Mac sleep after user confirmation.
- The API rejects unauthenticated control requests.
- The Mac server starts in the logged-in user's session and produces useful logs.
- The app clearly handles sleep, server restart, Wi-Fi loss and discovery failure.

## 11. Risks and decisions to validate early

| Risk | Mitigation |
|---|---|
| Universal media-key injection | Prototype Swift helper first; validate on target macOS and apps. |
| macOS permissions | Document Accessibility requirements and test after clean install. |
| mDNS blocked by network | Add manual IP entry as a fallback; explain client isolation. |
| Sleep disconnects client | Treat loss of connection after sleep as expected, not a command failure. |
| Shell command safety | Use fixed executable paths/arguments, timeouts and no user-controlled shell interpolation. |

## 12. Suggested first coding task

Create the Go server with mocked `MediaController`, `VolumeController` and `PowerController` implementations. Implement the versioned API and write curl-based tests for every endpoint. Once the contract is stable, replace each mock with its macOS adapter.

Keep the MVP intentionally small: no database, no cloud service, no WebSocket and no multi-device management. The first meaningful technical proof is reliable universal media-key control from a Go process running in the logged-in macOS session.

---

*GoRemote • MVP Architecture & Implementation Plan • Prepared 9 November 2026*
