use idevice::IdeviceError;

pub type Result<T> = std::result::Result<T, SetupError>;

/// What went wrong, in words the window can show as they are. Each one maps to a screen or a
/// line on the board (Unplugged, Sign-in helper down, No app slots, …).
#[derive(Debug, thiserror::Error)]
pub enum SetupError {
    #[error("This computer can't talk to iPhones yet. On Windows, install iTunes from apple.com.")]
    NoPhoneService(String),
    #[error("No iPhone is connected.")]
    NoPhone,
    #[error("Your iPhone was disconnected.")]
    Disconnected,
    #[error("Unlock your iPhone.")]
    Locked,
    #[error("Tap Trust on your iPhone.")]
    WaitingForTrust,
    #[error("Your iPhone said Don’t Trust. Unplug it and plug it back in to be asked again.")]
    TrustDenied,
    #[error("Turn on Developer Mode on your iPhone.")]
    DeveloperModeOff,
    #[error("Couldn’t reach the sign-in helper ({0}).")]
    HelperDown(String),
    #[error("Wrong Apple Account or password.")]
    WrongPassword,
    #[error("Couldn’t sign in: {0}")]
    SignIn(String),
    #[error("Your account has too many signing certificates. Remove one at developer.apple.com, then try again.")]
    CertificateLimit,
    #[error("Your free account can’t register more apps this week.")]
    AppIdLimit,
    #[error("Your free account has no app slots left on this iPhone.")]
    NoAppSlots,
    #[error("Couldn’t sign or install: {0}")]
    Install(String),
    #[error("Couldn’t download {0}.")]
    Download(String),
    #[error("The download didn’t match the published file.")]
    Checksum,
    #[error("Mode isn’t on this iPhone yet.")]
    ModeMissing,
    #[error("Couldn’t set up phone control: {0}")]
    Pairing(String),
    #[error("Couldn’t save to this computer’s keychain: {0}")]
    Keychain(String),
    #[error("{0}")]
    Phone(String),
}

impl From<IdeviceError> for SetupError {
    fn from(e: IdeviceError) -> Self {
        match e {
            IdeviceError::PasswordProtected => SetupError::Locked,
            IdeviceError::PairingDialogResponsePending => SetupError::WaitingForTrust,
            IdeviceError::UserDeniedPairing => SetupError::TrustDenied,
            IdeviceError::DeviceNotFound => SetupError::Disconnected,
            other => SetupError::Phone(other.to_string()),
        }
    }
}

/// Turns isideload's report into one of ours, by what Apple said.
pub(crate) fn from_report(context: &str, report: impl std::fmt::Display) -> SetupError {
    let text = report.to_string();
    let lower = text.to_lowercase();
    if lower.contains("maximum number of certificates") || lower.contains("max certs") {
        SetupError::CertificateLimit
    } else if lower.contains("maximum number of app ids") || lower.contains("app id limit") {
        SetupError::AppIdLimit
    } else if lower.contains("applicationverificationfailed") && lower.contains("limit") {
        SetupError::NoAppSlots
    } else if lower.contains("anisette") {
        SetupError::HelperDown(text)
    } else {
        SetupError::Install(format!("{context}: {text}"))
    }
}
