//! Is this computer ready to set up an iPhone? Checked on the welcome screen, before anything else:
//! the iPhone driver (built in on macOS, iTunes' Apple Mobile Device Support on Windows, usbmuxd on
//! Linux) and the internet. Xcode is never needed: this app brings everything it uses.

use std::time::Duration;

use serde::Serialize;

/// One line of the check.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Check {
    pub id: &'static str,
    pub state: CheckState,
    pub title: &'static str,
    pub detail: String,
    /// A page to open to fix it ("Download iTunes").
    pub fix: Option<Fix>,
    /// A command to copy and run to fix it (Linux).
    pub command: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Fix {
    pub label: &'static str,
    pub url: &'static str,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum CheckState {
    Ok,
    /// Not installed.
    Missing,
    /// Installed but not running.
    Stopped,
}

/// The whole check. `ready` when the iPhone driver works: setup can't start without it. The internet
/// line only warns, so a proxy that blocks the quick check never locks anyone out.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ComputerCheck {
    /// "macos", "windows" or "linux".
    pub os: &'static str,
    pub ready: bool,
    pub checks: Vec<Check>,
}

/// Apple's iTunes for Windows, the 64-bit installer from apple.com (not the Microsoft Store one).
pub const ITUNES_DOWNLOAD: &str = "https://www.apple.com/itunes/download/win64";

pub async fn check_computer() -> ComputerCheck {
    let driver_ok = crate::device::usbmuxd().await.is_ok();
    let mut checks = vec![driver(driver_ok)];
    if cfg!(target_os = "macos") {
        checks.push(Check {
            id: "xcode", state: CheckState::Ok, title: "Xcode",
            detail: "Not needed. Mode Setup brings everything it uses.".into(), fix: None, command: None,
        });
    }
    checks.push(internet(online().await));
    ComputerCheck { os: std::env::consts::OS, ready: driver_ok, checks }
}

fn driver(ok: bool) -> Check {
    if cfg!(target_os = "macos") {
        return Check {
            id: "driver", state: if ok { CheckState::Ok } else { CheckState::Stopped }, title: "iPhone connection",
            detail: if ok { "Built in to macOS.".into() } else { "macOS isn’t answering. Restart your Mac, then check again.".into() },
            fix: None, command: None,
        };
    }
    if cfg!(target_os = "windows") {
        return windows_driver(ok, windows_service_installed());
    }
    linux_driver(ok, std::path::Path::new("/var/run/usbmuxd").exists() || std::path::Path::new("/run/usbmuxd").exists())
}

/// iTunes brings Apple Mobile Device Support, the driver that lets this computer talk to an iPhone.
pub fn windows_driver(ok: bool, service_installed: bool) -> Check {
    let (state, detail, fix) = if ok {
        (CheckState::Ok, "Apple Mobile Device Support is installed and running.".to_string(), None)
    } else if service_installed {
        (CheckState::Stopped,
         "Apple Mobile Device Support is installed but not running. Restart your computer, then check again.".to_string(),
         None)
    } else {
        (CheckState::Missing,
         "Install Apple’s iTunes from apple.com (not the Microsoft Store version). It brings the driver this app uses to talk to your iPhone. You don’t need to open it.".to_string(),
         Some(Fix { label: "Download iTunes", url: ITUNES_DOWNLOAD }))
    };
    Check { id: "driver", state, title: "iTunes (iPhone driver)", detail, fix, command: None }
}

/// usbmuxd is the small service Linux uses to talk to iPhones.
pub fn linux_driver(ok: bool, socket_exists: bool) -> Check {
    let (state, detail, command) = if ok {
        (CheckState::Ok, "usbmuxd is running.".to_string(), None)
    } else if socket_exists {
        (CheckState::Stopped, "usbmuxd is installed but not answering. Start it, then check again.".to_string(),
         Some("sudo systemctl restart usbmuxd".to_string()))
    } else {
        (CheckState::Missing, "Install usbmuxd, the service Linux uses to talk to iPhones, then plug your iPhone in. On Fedora: sudo dnf install usbmuxd.".to_string(),
         Some("sudo apt install usbmuxd".to_string()))
    };
    Check { id: "driver", state, title: "usbmuxd (iPhone driver)", detail, fix: None, command }
}

pub fn internet(ok: bool) -> Check {
    Check {
        id: "internet", state: if ok { CheckState::Ok } else { CheckState::Missing }, title: "Internet",
        detail: if ok { "Connected.".into() } else { "Connect to the internet. Setup signs Mode with your Apple account, online.".into() },
        fix: None, command: None,
    }
}

/// Can this computer reach Apple? A quick request, no sign-in.
async fn online() -> bool {
    let Ok(client) = reqwest::Client::builder()
        .user_agent(concat!("ModeSetup/", env!("CARGO_PKG_VERSION")))
        .connect_timeout(Duration::from_secs(6))
        .timeout(Duration::from_secs(8))
        .build() else { return false };
    client.head("https://developer.apple.com").send().await.is_ok()
}

#[cfg(target_os = "windows")]
fn windows_service_installed() -> bool {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;
    std::process::Command::new("sc.exe")
        .args(["query", "Apple Mobile Device Service"])
        .creation_flags(CREATE_NO_WINDOW)
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).contains("STATE"))
        .unwrap_or(false)
}

#[cfg(not(target_os = "windows"))]
fn windows_service_installed() -> bool { false }

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn windows_without_itunes_links_to_the_apple_com_installer() {
        let c = windows_driver(false, false);
        assert_eq!(c.state, CheckState::Missing);
        assert_eq!(c.fix.unwrap().url, ITUNES_DOWNLOAD);
        assert!(c.detail.contains("not the Microsoft Store"));
    }

    #[test]
    fn windows_with_the_service_stopped_asks_for_a_restart() {
        let c = windows_driver(false, true);
        assert_eq!(c.state, CheckState::Stopped);
        assert!(c.fix.is_none());
    }

    #[test]
    fn windows_driver_running_is_ok() {
        assert_eq!(windows_driver(true, true).state, CheckState::Ok);
    }

    #[test]
    fn linux_without_usbmuxd_gives_the_install_command() {
        let c = linux_driver(false, false);
        assert_eq!(c.state, CheckState::Missing);
        assert_eq!(c.command.as_deref(), Some("sudo apt install usbmuxd"));
        let c = linux_driver(false, true);
        assert_eq!(c.state, CheckState::Stopped);
        assert_eq!(c.command.as_deref(), Some("sudo systemctl restart usbmuxd"));
    }

    #[test]
    fn offline_is_not_ok() {
        assert_eq!(internet(false).state, CheckState::Missing);
    }
}
