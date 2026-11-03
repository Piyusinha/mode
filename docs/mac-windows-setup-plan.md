# Mode Setup for macOS, Windows and Linux

Implementation plan, revised 2026-11-03. It replaces the first proposal
(two native desktop apps, a Mode-hosted anisette service, a signed release
service). Nothing here is built yet.

## 1. What changed and why

The first proposal rebuilt, by hand and twice, what the open-source sideloading
tools already ship. Mode's free build is the same kind of app those tools serve
every day: an app that needs a remote-pairing file and a loopback VPN to reach
its own phone's developer services (like StikDebug). So this plan follows their
path instead of building a new one.

| First proposal | This plan | Why |
|---|---|---|
| SwiftUI app on Mac + WinUI 3 app on Windows | **One Tauri app** (Rust + web UI) for Mac, Windows and Linux | iLoader already does exactly this; one UI to build, Linux for free |
| Mode-hosted anisette-v3 server | **No Mode server.** A user-choosable public anisette server first; local anisette later | The server only supplies machine-identity headers; it never sees the Apple password or 2FA code. Nothing to run or police |
| Signed release-manifest service | **GitHub Releases** for the IPA; Tauri's signed updater for the desktop app | What iLoader and SideStore use |
| Desktop-written setup handshake (nonce file) | **Mode's own onboarding checks** on the phone | The checks already exist in Mode |
| Paid build with Mode's VPN extension, signed by the desktop app | **Every account gets the free build + LocalDevVPN** (App Store) | Free build needs no special entitlements; isideload can sign it today |
| Free-account renewal only from the computer | **SideStore refreshes Mode on the phone** (optional); the computer still works | That is how StikDebug users stay signed |

## 2. How the open-source tools do it

| Piece | Who does it today | What Mode reuses |
|---|---|---|
| Apple sign-in, 2FA, certificates, App IDs, profiles, signing | [isideload](https://github.com/nab138/isideload) (Rust, MIT) | The library as-is |
| USB, install, House Arrest file copy, remote pairing | [idevice](https://github.com/jkcoxson/idevice) (Rust, MIT) | Already in Mode's `native/modecore` |
| Desktop app around both, Mac/Windows/Linux | [iLoader](https://github.com/nab138/iloader) (Tauri, MIT code; its name and logo are reserved) | Its structure and flow as reference; no branding |
| Anisette | isideload's `RemoteV3AnisetteProvider` (default `ani.iloader.app`); SideStore's public servers; [anisette-v3-server](https://github.com/Dadoum/anisette-v3-server) | isideload's `AnisetteProvider` trait, so a local provider can be added later |
| Loopback VPN | [LocalDevVPN](https://github.com/StephenDev0/StikDebug) on the App Store | Mode's free build already uses it |
| On-phone refresh every 7 days | SideStore | Recommend it; no Mode code |
| Credentials | iLoader: `keyring` crate (macOS Keychain, Windows Credential Manager, Linux Secret Service) | Same crate |

What iLoader already does, from its source (`src-tauri/src/pairing.rs`):

- Makes the remote-pairing file over USB and copies it into an app's
  `Documents` through House Arrest, for a fixed list of apps (`PAIRING_APPS`:
  SideStore, StikDebug, Protokolle, …).
- The file is the same `RpPairingFile` plist that Mode reads
  (`engine/phone-ios/.../PairingFile.kt`, `Documents/mode.rppairing`).
- Pair-verify signs the file's `identifier`; the host name is only a label
  (`idevice/src/remote_pairing/mod.rs`). So a file made by iLoader should work
  in Mode. **To confirm on the phone (step 0.1).**

Mode's free build (`iosApp/project.yml`) is the Mode app plus the widget
extension, with no app groups and no special entitlements: the easiest case
for isideload. Only the paid build adds the Network Extension entitlement,
which isideload does not handle yet ("Proper entitlement handling" is on its
TODO list).

## 3. Phases

### Phase 0 — Works with existing tools (days, little code)

Goal: anyone on Mac, Windows or Linux can set up Mode this week.

1. **Spike on the phone** (half a day):
   1. Install Mode's free IPA with iLoader's "Import IPA".
   2. Make a pairing file with iLoader and copy it into Mode as
      `Documents/mode.rppairing` (Files app or iLoader's pairing manager).
   3. Check: the widget and Live Activity work, the tunnel opens with
      LocalDevVPN, and a task runs unplugged.
   If the iLoader pairing file is refused, Mode must accept the host name the
   file was made with; record that and fix it in Mode before step 3.
2. **Release the free IPA on GitHub Releases** from CI on every tag
   (unsigned `.ipa` plus SHA-256). No server.
3. **Mode finds the pairing file** under `mode.rppairing` or
   `pairingFile.plist` (the name most apps in iLoader's list use).
4. **Upstream a one-line PR to iLoader** adding `("Mode", "mode.rppairing")`
   to `PAIRING_APPS`, so its pairing manager offers Mode by name.
5. **Setup guide in Mode's onboarding "Phone control" step and in the README:**
   1. Install iLoader (Windows: iTunes from Apple, not the Microsoft Store).
   2. Sign in, import Mode's IPA.
   3. Pairing → Mode.
   4. Install LocalDevVPN from the App Store and connect it.
   5. Optional but recommended for free accounts: install SideStore with
      iLoader (one button) and add Mode to it, so Mode renews on the phone
      every week without the computer. About SideStore: https://sidestore.io

Paid accounts follow the same steps. The paid VPN build stays the developer
path (Xcode) for now.

### Phase 1 — Mode Setup app (2–3 weeks)

A small Tauri app, built the way iLoader is built, that only sets up Mode.

- **Stack:** Tauri 2, `isideload` + `idevice` (pinned), `keyring`,
  `tauri-plugin-updater`. One codebase; CI builds `.dmg`, `.msi` and
  `.AppImage`/`.deb`.
- **Location:** `desktop/setup` in this repo. Keep it separate from
  `native/modecore`'s `idevice` pin; never upgrade modecore's pin to suit the
  desktop app.
- **One flow, one button:**
  1. Find the iPhone; ask the user to unlock it, tap Trust, and turn on
     Developer Mode (with the restart), then reconnect.
  2. Apple sign-in and 2FA. Keep the password only if the user ticks
     "Remember on this computer" (OS keychain).
  3. Choose the team; show free or paid and the real expiry.
  4. Download the latest free IPA from GitHub Releases and check its SHA-256
     (or use the copy bundled with the app).
  5. Sign and install with isideload. Keep the existing bundle ID on an
     update; never uninstall to fix an error.
  6. Make the pairing file with host name `Mode` and copy it to
     `Documents/mode.rppairing`; read it back.
  7. **Free accounts only: ask about SideStore** (see "SideStore choice"
     below). If yes, install it in the same run.
  8. Point to LocalDevVPN, then "Open Mode on your iPhone". Mode's own
     onboarding checks prove it works with the cable unplugged.
- **SideStore choice** (free accounts; paid signing lasts a year, so skip it):
  - **The question**, shown after the team is chosen:
    - Title: "Renew Mode without your computer?"
    - Body: "A free Apple account signs Mode for 7 days. SideStore, a free
      open-source app, renews it on your iPhone every week, so you won't need
      this computer again. It uses 2 of your 3 free app slots (SideStore and
      Mode)."
    - Link: "About SideStore" → https://sidestore.io (project:
      https://github.com/SideStore/SideStore).
    - Buttons: "Install SideStore too" (default) and "I'll use this computer
      every week".
  - **If yes**, the app does what iLoader does:
    1. Downloads SideStore's latest release from its GitHub releases and
       checks it.
    2. Signs and installs it with the same Apple account and certificate.
    3. Copies the signing certificate into SideStore, so its refresh reuses
       the certificate instead of revoking it.
    4. Copies the same pairing file into SideStore's Documents.
    5. Shows the last step on the phone: open SideStore → My Apps, where
       Mode shows with its expiry; refresh needs LocalDevVPN connected and
       Wi-Fi.
  - **If no**, the device page shows the expiry and reminds the user to
    connect and press Refresh before it runs out.
  - The choice can be changed later from the device page ("Install
    SideStore").
  - Mode's own onboarding says the same in one line, with the same "About
    SideStore" link, for people who set up with iLoader (Phase 0).
- **Device page:** Update Mode, Refresh signing, Make a new pairing file,
  Copy diagnostics (no passwords, tokens, keys or pairing files), Forget
  account.
- **Anisette:** isideload's remote v3 provider. Default to a public server,
  let the user change it in Settings, and show a clear error when it is down.
  No Mode server.
- **UI:** HTML/CSS in Mode's colours, type and spacing (the same values the
  design boards use), light and dark, keyboard navigation, progress announced
  to screen readers. Boards first, as for the iPhone app.
- **Code signing:** notarize the Mac build; sign the Windows installer.
  Linux ships unsigned packages as iLoader does.

### Phase 2 — Less dependence on others (when needed)

Pick these up only if Phase 1 shows the need.

- **Local anisette, no server at all:**
  - **Mac:** a local `AnisetteProvider` that asks macOS itself (AltServer
    does this).
  - **Windows:** read it from iCloud for Windows (AltServer does this), or run
    the anisette-v3 generator code inside the app.
  - The remote server stays as a fallback.
- **Paid build with Mode's own VPN extension:** add the Network Extension
  entitlement in our signing adapter for paid teams (isideload's `sign.rs`
  already injects `keychain-access-groups`), or use the App Store Connect API
  with the user's API key, which needs no password or anisette. Also upstream
  the entitlement fix to isideload.
- **Mode refreshes itself on the phone** like SideStore (it already has the
  pairing file, the tunnel and developer services), so free users never need
  the computer again after setup.

## 4. Rules that still hold

- Apple passwords, 2FA codes, certificates, private keys and pairing files
  never leave the user's computer and phone. They live in the OS keychain;
  nothing secret in logs or diagnostics.
- Never revoke certificates other tools use. Never uninstall Mode as a
  recovery step. Keep its bundle ID, data and pairing file across updates and
  refreshes.
- Free accounts: 7-day signing, 3 apps, 10 App IDs a week. Show Apple's real
  expiry. With SideStore and Mode that is 2 of the 3 apps; LocalDevVPN comes
  from the App Store and does not count.
- The developer disk image stays bundled in Mode (decision D18). Settle the
  right to distribute it before a public release; StikDebug's alternative is
  downloading it on the phone.
- "Ready" means Mode's on-phone check passed with the cable unplugged, not
  that the install finished.
- Retain upstream licence notices (MIT). Do not use iLoader's name or logo.

## 5. Tests before calling a phase done

- Fresh free account on Mac, Windows and Linux; a paid account on one of them.
- Update Mode over an existing install: conversations, memory, settings and
  provider sign-ins survive.
- Wrong password, wrong or expired 2FA code, anisette server down, App ID
  limit reached, certificate limit reached.
- Locked phone, Trust refused, Windows without Apple's iTunes, unplug during
  install, Developer Mode restart.
- Expired profile (day 8), a refresh through SideStore, a refresh through the
  desktop app.
- Pairing file missing, made for another phone, or invalidated by a Developer
  Mode reset.
- A task runs with the cable unplugged, and again after a reboot.
- Diagnostics contain no credentials, pairing files, keys or raw Apple
  responses.
