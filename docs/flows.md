# Flows

## 1. One-time pairing (Mac, USB)

The phone only talks to computers it trusts. `tools/make-pairing.sh` makes
Mode one of them, once.

```mermaid
sequenceDiagram
    autonumber
    participant Mac as Mac (tools/make-pairing.sh)
    participant MC as modecore pair_over_usb
    participant Phone as iPhone (USB)
    participant Mode as Mode's Documents
    Mac->>MC: pair "Mode" build/pairing/mode.rppairing
    MC->>Phone: usbmuxd → CoreDeviceProxy tunnel → RSD handshake
    MC->>Phone: untrusted tunnel service: RemoteXPC handshake
    MC->>Phone: RPPairing as host "Mode" (Trust prompt if not yet trusted)
    Phone-->>MC: paired
    MC-->>Mac: pairing file bytes (501 bytes, chmod 600)
    Mac->>Mode: devicectl copy to appDataContainer → Documents/mode.rppairing
```

Redo it only if the phone stops trusting the Mac: Developer Mode turned off and
on, or Location & Privacy reset. Free accounts without Mac tools can drop the
file into Mode's folder through Finder or the Files app instead.

## 2. Connecting from the phone

```mermaid
sequenceDiagram
    autonumber
    participant UI as Mode UI (Connect)
    participant PL as IosPhoneLink
    participant Conn as PhoneConnector
    participant VPN as LoopbackVpn / ModeTunnel
    participant MC as modecore Session
    participant RPD as remotepairingd (49152)
    UI->>PL: connect()
    PL->>PL: PairingFile.load(), currentScreen() on main
    PL->>Conn: connect(pairing, screen)
    Conn->>VPN: ensureUp()
    alt paid build, first time
        VPN->>VPN: save NETunnelProviderManager (iOS asks "Add VPN Configurations")
    end
    VPN->>VPN: startVPNTunnel, wait for Connected (≤10 s)
    Conn->>MC: open_rppairing(10.7.0.1:49152, "Mode", pairing)
    MC->>RPD: TCP via tunnel (source rewritten to 10.7.0.1)
    MC->>RPD: pair-verify with the pairing file
    MC->>RPD: create TCP listener → TLS-PSK tunnel → RSD handshake
    MC->>MC: open DisplayService audio+video stream, start RTP drains
    MC->>MC: wait 300 ms, connect touch + button/keyboard clients
    Conn->>Conn: VisionTextReader.warmUp()
    Conn-->>UI: CoreDeviceBackend + route used (962 ms)
```

## 3. Why the VPN: how the phone sees each route

```mermaid
flowchart TB
    subgraph tried["Tried on the phone, 2026-10-31"]
        L1["127.0.0.1:49152"] -->|TCP accepted| X1["pair-verify dropped"]
        L2["own Wi-Fi 192.168.31.112:49152"] -->|TCP accepted| X2["pair-verify dropped"]
        L3["own USB link 169.254.12.181:49152"] -->|TCP accepted| X3["pair-verify dropped"]
        V["10.7.0.1:49152 via loopback VPN"] --> OK["✅ connected"]
    end
    subgraph mac["Control test from the Mac"]
        M["Mac → 169.254.12.181:49152, same file and code"] --> OK2["✅ connected"]
    end
```

The phone refuses pairing on any connection that starts on the phone itself,
whichever address it targets. The VPN sends every packet for `10.7.0.1`
straight back with its source and destination swapped. The pairing service
therefore sees a connection from `10.7.0.1`, a peer, and accepts it.

```mermaid
sequenceDiagram
    participant App as Mode (10.7.1.1)
    participant Tun as ModeTunnel
    participant RPD as remotepairingd
    App->>Tun: SYN 10.7.1.1 → 10.7.0.1:49152
    Tun->>RPD: same packet, src/dst swapped: 10.7.0.1 → 10.7.1.1:49152
    RPD->>Tun: reply 10.7.1.1 → 10.7.0.1
    Tun->>App: swapped back: 10.7.0.1 → 10.7.1.1
    Note over App,RPD: Swapping two addresses leaves IP/TCP checksums valid,<br/>so nothing else is rewritten. Only 10.7.0.1 is routed into the tunnel.
```

## 4. A look (screen.capture + screen.text)

```mermaid
sequenceDiagram
    autonumber
    participant C as Caller (HandsCheck / agent)
    participant B as CoreDeviceBackend
    participant S as ModecoreSession → modecore
    participant D as DecodedScreens
    participant V as Vision OCR
    C->>B: ScreenCapture(maxSize = 1000)
    B->>S: screenshotPng() (new capture-service connection each shot)
    S-->>B: PNG 1206×2622 (~4 MB)
    B->>B: remember (time, PNG)
    B->>D: decode once, scale to 1000 px, JPEG 0.7
    B-->>C: {jpeg_b64, bounds, img_px}
    C->>B: ScreenText()
    B->>B: reuse the remembered PNG (no action since, < 1.5 s old)
    B->>D: same decoded image, scaled to 1400 px
    D->>V: recognise text (accurate, language auto)
    V-->>B: boxes as fractions, bottom-left origin
    B-->>C: [{text, confidence, source:"ocr", x, y, w, h}] in points
```

Measured on the phone: **1413 ms** for the first look (picture 694 ms + text
719 ms) and **825 ms** for the next (417 + 408). Before the speed fix, a look
took about 7–8 s (two screenshots, two decodes, full-size OCR).

## 5. A tap

```mermaid
sequenceDiagram
    participant C as Caller
    participant B as CoreDeviceBackend
    participant M as modecore
    participant P as Phone (dtuhidd → backboardd)
    C->>B: Tap(91, 452) in points
    B->>B: clear remembered screenshot, map to (14835, 33892) of 65535
    B->>M: touch(from = to, hold 50 ms, travel 0)
    M->>P: touchscreen CONTACT, stamped with mach_absolute_time
    M->>P: wait 50 ms, RELEASE
    Note over M,P: Accepted only because this client's screen stream is open.
```

A scroll is the same touch with travel: the finger goes down, moves in
16 ms frames at about 800 pt/s, rests 100 ms so the list stops where the
finger stops, then lifts.

## 6. Typing

```mermaid
flowchart LR
    T["TypeText(text)"] --> Q{"Every character on a<br/>US keyboard?"}
    Q -- yes --> K["HID keyboard: key down/up per character,<br/>Shift held where needed (~55 ms for 'Uber')"]
    Q -- "no (Hindi, accents, emoji)" --> P["Pasteboard service: set text<br/>then Cmd+V on the HID keyboard"]
    P --> A["The app may ask 'Allow Paste?'<br/>(the agent taps Allow, or the user allows it per app)"]
```

## 7. The hands check (today's test screen)

It proves the hands work from the phone. Everything it does comes from what
the user types in; there is no task logic in it.

```mermaid
sequenceDiagram
    autonumber
    participant U as You
    participant H as HandsCheck
    participant B as Backend
    U->>H: app "Settings", tap "Wi-Fi", type ""
    H->>H: beginBackgroundTask (Mode goes to the background)
    H->>B: LaunchApp("Settings") → com.apple.Preferences (149 ms)
    H->>H: wait 2.5 s
    H->>B: look (picture + text)
    H->>H: first text containing "Wi-Fi" → (91, 452)
    H->>B: Tap(91, 452)
    H->>H: wait 1.5 s
    H->>B: look again
    H->>B: LaunchApp(Mode's own bundle id): back to Mode
    H-->>U: log with timings + both screenshots and their text
```

## 8. The agent loop (planned, Phase 6)

```mermaid
flowchart TB
    start(["Task, e.g. 'turn on Dark Mode'"]) --> look
    look["LOOK: small JPEG + OCR text with positions"] --> ask
    ask["ASK the model: task, screen, steps so far,<br/>tools generated from Op"] --> decide
    decide{"Model returns<br/>one tool call"}
    decide -- "tap / scroll / type / open_app / home / press_key" --> act["ACT through PhoneBackend,<br/>wait ~1 s to settle"]
    act --> limit{"Step limit<br/>reached?"}
    limit -- no --> look
    limit -- yes --> stop(["Stop and report"])
    decide -- "ask_user (stuck, or about to pay / book / send / delete)" --> user(["Notify, wait for you"])
    decide -- "done(summary)" --> done(["Notify: done"])
```

Example: "open General in Settings". Nobody codes where General is.

| Step | Model sees | Model does |
|---|---|---|
| 1 | Home Screen | `open_app("Settings")` |
| 2 | Settings, top: Wi-Fi, Bluetooth… no General | `scroll(201, 600, dy=-400)` |
| 3 | General at (110, 520) | `tap(110, 520)` |
| 4 | General screen | `done("Opened General")` |

Expected pace: 3–5 s per step (look ~1 s, model 1–3 s, act + settle ~1 s).
