//! Mode Setup's engine: everything the desktop app does to an iPhone, with no UI.
//!
//! Built on the same open-source stack as iLoader (nab138, MIT): `isideload` signs and installs,
//! `idevice` (Jackson Coxson, MIT) talks to the phone. The window app and the `mode-setup`
//! command line only call into this crate; see docs/mac-windows-setup-plan.md.
//!
//! Secrets (Apple password, session, certificates, pairing records) stay on this computer, in its
//! keychain. Nothing here logs them.

pub mod account;
pub mod apps;
pub mod device;
pub mod error;
pub mod install;
pub mod pairing;
pub mod release;
pub mod secure_storage;
pub mod system;

pub use error::{Result, SetupError};

/// The name this app goes by in keychains and on the phone.
pub const APP_NAME: &str = "Mode Setup";

/// Must be called once at start: picks the TLS crypto for every download and sign-in.
pub fn init() {
    let _ = isideload::init();
}
