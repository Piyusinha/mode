//! What is installed on the phone: Mode, SideStore, LocalDevVPN.

use idevice::{IdeviceService, installation_proxy::InstallationProxyClient};
use serde::Serialize;

use crate::{Result, device};

/// Mode's bundle ID before signing; signed copies add the team: "com.piyush.mode.ABCDE12345".
pub const MODE_BUNDLE: &str = "com.piyush.mode";

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AppOnPhone {
    pub bundle_id: String,
    pub name: String,
    pub version: String,
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
            AppOnPhone { bundle_id, name, version: text("CFBundleShortVersionString") }
        })
        .collect();
    out.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(out)
}

/// Mode, however it was signed (Xcode keeps "com.piyush.mode"; this app adds the team).
pub fn mode(apps: &[AppOnPhone]) -> Option<&AppOnPhone> {
    apps.iter().find(|a| is_mode(&a.bundle_id))
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
        assert!(is_mode("com.piyush.mode"));
        assert!(is_mode("com.piyush.mode.ABCDE12345"));
        assert!(!is_mode("com.piyush.mode.ABCDE12345.widgets"));
        assert!(!is_mode("com.piyush.modem"));
        assert!(!is_mode("com.SideStore.SideStore"));
    }
}
