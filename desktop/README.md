# Mode Setup (desktop)

The app that installs Mode on an iPhone from a Mac, Windows or Linux computer, gets phone control
ready, and (for free Apple accounts) can install SideStore so Mode renews on the iPhone every week.
Plan: `docs/mac-windows-setup-plan.md`. Designs: the "Mode Setup" section of the design canvas.

| Folder | What it is |
|---|---|
| `setup-core/` | The engine: iPhones, Trust, Developer Mode, Apple sign-in, signing, installing, phone control, downloads. No UI. |
| `setup-cli/` | `mode-setup`, a command line over the engine, to test each step against a real iPhone. |

The window app (Tauri) comes next and calls the same engine.

## Try it

```sh
tools/build-ipa.sh                                  # build/release/Mode.ipa (free build, unsigned)
cd desktop && cargo build
./target/debug/mode-setup phones
./target/debug/mode-setup trust                     # tap Trust; turn on Developer Mode
./target/debug/mode-setup install ../build/release/Mode.ipa [--sidestore]
./target/debug/mode-setup apps
```

`install` asks for the Apple Account, password and code in the terminal. The password goes only to
Apple; it is kept in this computer's keychain only if you answer yes to "Remember".

Windows needs Apple's iTunes (from apple.com, not the Microsoft Store) for its iPhone driver.

## Built on

- [isideload](https://github.com/nab138/isideload) (MIT, nab138): Apple sign-in, certificates, profiles, signing.
- [idevice](https://github.com/jkcoxson/idevice) (MIT, Jackson Coxson): USB, lockdown, installing, file transfer, remote pairing.
- [iLoader](https://github.com/nab138/iloader) (MIT code, nab138): the flow this follows, and the pairing-file
  layout SideStore reads. iLoader's name and logo are its own and are not used here.
- Sign-in helpers (anisette servers) run by the iLoader and SideStore projects; they never see the password.

## Desktop checks and local build

```sh
node --test desktop/setup-app/tests/phone-setup.test.cjs
cd desktop && cargo test -p mode-setup-core
# From the repository root:
tools/build-setup.sh
```

On macOS the build script signs the debug executable with a stable Apple Development
identity when exactly one is available. Set `MODE_SETUP_SIGN_IDENTITY` to select an
identity explicitly. This preserves the executable's signing identity across rebuilds;
it does not change Keychain access permissions. macOS may still ask permission when
an existing item is first accessed by this identity.

The device page checks GitHub Releases before offering an update. Checking does not
sign in or access the Keychain. Renew signing is a separate, explicit action; it reuses
an active account session when available. Back/Cancel returns to the device page.
Setup completion is confirmed by the user after testing Mode on the iPhone and again
with the cable unplugged; the desktop does not automatically read the phone's test result.
