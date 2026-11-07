//! What is installed on the phone: Mode, SideStore, LocalDevVPN.

use idevice::{IdeviceService, installation_proxy::InstallationProxyClient};
use serde::{Deserialize, Serialize};

use crate::{Result, device};

/// Mode's bundle ID before signing; signed copies add the team: "app.heymode.mode.ABCDE12345".
pub const MODE_BUNDLE: &str = "app.heymode.mode";

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AppOnPhone {
    pub bundle_id: String,
    pub name: String,
    pub version: String,
    /// Signed with a developer certificate (this app, SideStore, Xcode…), so it uses one of a free
    /// account's 3 app slots; App Store apps don't.
    #[serde(default)]
    pub sideloaded: bool,
}

/// The apps the person installed (App Store and signed ones).
pub async fn installed(udid: &str) -> Result<Vec<AppOnPhone>> {
    let provider = device::provider(udid).await?;
    let mut proxy = InstallationProxyClient::connect(&provider).await?;
    let apps = proxy.get_apps(Some("User"), None).await?;
    let mut out: Vec<AppOnPhone> = apps
        .into_iter()
        .map(|(bundle_id, v)| {
            let d = v.as_dictionary();
            let text = |k: &str| d.and_then(|d| d.get(k)).and_then(|v| v.as_string()).unwrap_or_default().to_string();
            let mut name = text("CFBundleDisplayName");
            if name.is_empty() {
                name = text("CFBundleName");
            }
            let signer = text("SignerIdentity");
            let sideloaded = signer.contains("Development") || signer.contains("Developer");
            AppOnPhone { bundle_id, name, version: text("CFBundleShortVersionString"), sideloaded }
        })
        .collect();
    out.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(out)
}

/// Mode, however it was signed (the release keeps "app.heymode.mode"; this app adds the team).
pub fn mode(apps: &[AppOnPhone]) -> Option<&AppOnPhone> {
    apps.iter().find(|a| is_mode(&a.bundle_id))
}

/// Every copy of Mode (an Xcode build and signed ones can sit side by side).
pub fn modes(apps: &[AppOnPhone]) -> impl Iterator<Item = &AppOnPhone> {
    apps.iter().filter(|a| is_mode(&a.bundle_id))
}

pub fn is_mode(bundle_id: &str) -> bool {
    bundle_id == MODE_BUNDLE
        || bundle_id.strip_prefix(MODE_BUNDLE).and_then(|rest| rest.strip_prefix('.')).is_some_and(|team| !team.contains('.'))
}

pub fn sidestore(apps: &[AppOnPhone]) -> Option<&AppOnPhone> {
    apps.iter().find(|a| a.name == "SideStore")
}

pub fn local_dev_vpn(apps: &[AppOnPhone]) -> Option<&AppOnPhone> {
    apps.iter().find(|a| a.name.eq_ignore_ascii_case("LocalDevVPN"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn finds_mode_however_it_was_signed() {
        assert!(is_mode("app.heymode.mode"));
        assert!(is_mode("app.heymode.mode.ABCDE12345"));
        assert!(!is_mode("app.heymode.mode.ABCDE12345.widgets"));
        assert!(!is_mode("app.heymode.modem"));
        assert!(!is_mode("com.SideStore.SideStore"));
    }
}
